package ca.weblite.jdeploy.cli.util;

import ca.weblite.jdeploy.helpers.StringUtils;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class CommandLineParser {

    // Introduce an Alias annotation
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Alias {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Help {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface PositionalArg {
        int value();
    }
    public void parseArgsToParams(Object params, String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                String[] parts = arg.substring(2).split("=", 2);
                if (parts.length == 2) {
                    String property = parts[0];
                    String value = parts[1];
                    setProperty(params, property, value);
                } else {
                    String property = parts[0];
                    setProperty(params, property, "true");
                }
            } else if (arg.startsWith("-")) {
                String alias = arg.substring(1);
                String property = getPropertyByAlias(params, alias);
                if (property != null && i + 1 < args.length) {
                    String value = args[++i]; // Get the next value from args
                    setProperty(params, property, value);
                }
            }
        }
    }

    private String getPropertyByAlias(Object object, String alias) {
        for (Field field : object.getClass().getDeclaredFields()) {
            Alias aliasAnnotation = field.getAnnotation(Alias.class);
            if (aliasAnnotation != null && aliasAnnotation.value().equals(alias)) {
                return field.getName(); // Return the property name
            }
        }
        return null;
    }
    private void setProperty(Object object, String property, String value) {
        String camelPropertyName = new StringUtils().lowerCaseWithSeparatorToCamelCase(property, "-");
        String setterMethodName = "set" + Character.toUpperCase(property.charAt(0)) +
                camelPropertyName.substring(1);
        try {
            Method setterMethod = object.getClass().getMethod(setterMethodName, String.class);
            setterMethod.invoke(object, value);
        } catch (NoSuchMethodException e) {
            try {
                Field field = object.getClass().getDeclaredField(camelPropertyName);
                boolean wasAccessible = field.isAccessible();
                field.setAccessible(true);

                // Check field type and set accordingly
                Class<?> type = field.getType();
                if (type == File.class) {
                    field.set(object, new File(value));
                } else if (type == int.class || type == Integer.class) {
                    field.set(object, Integer.parseInt(value));
                } else if (type == boolean.class || type == Boolean.class) {
                    field.set(object, Boolean.parseBoolean(value));
                } else {
                    field.set(object, value);
                }

                field.setAccessible(wasAccessible);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to set property " + property + " to value " + value, ex);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to set property " + property + " to value " + value, e);
        }
    }

    public void printHelp(Object object) {
        StringBuilder helpText = new StringBuilder();

        helpText.append("Usage:\n");
        for (Field field : object.getClass().getDeclaredFields()) {
            Help helpAnnotation = field.getAnnotation(Help.class);
            Alias aliasAnnotation = field.getAnnotation(Alias.class);
            PositionalArg positionalArgAnnotation = field.getAnnotation(PositionalArg.class);

            if (helpAnnotation != null) {
                if (aliasAnnotation != null) {
                    helpText.append(String.format("  -%s, --%s : %s\n",
                            aliasAnnotation.value(),
                            field.getName().replaceAll("([A-Z])", "-$1").toLowerCase(),
                            helpAnnotation.value()));
                } else {
                    helpText.append(String.format("  --%s : %s\n",
                            field.getName().replaceAll("([A-Z])", "-$1").toLowerCase(),
                            helpAnnotation.value()));
                }
            }

            if (positionalArgAnnotation != null) {
                helpText.append(String.format("  arg%d (%s) : %s\n",
                        positionalArgAnnotation.value(),
                        field.getName(),
                        helpAnnotation != null ? helpAnnotation.value() : "No description available"));
            }
        }

        System.out.println(helpText.toString());
    }
}

package ca.weblite.jdeploy.cli.util;

import java.lang.reflect.Method;

public class CommandLineParser {
    public void parseArgsToParams(Object params, String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String[] parts = arg.substring(2).split("=", 2);
                if (parts.length == 2) {
                    String property = parts[0];
                    String value = parts[1];
                    setProperty(params, property, value);
                }
            }
        }

    }

    private void setProperty(Object object, String property, String value) {
        String setterMethodName = "set" + Character.toUpperCase(property.charAt(0)) +
                property.substring(1).replaceAll("-", "");
        try {
            Method setterMethod = object.getClass().getMethod(setterMethodName, String.class);
            setterMethod.invoke(object, value);
        } catch (Exception e) {
            System.out.println("Error setting property: " + property + ". Error: " + e.getMessage());
        }
    }

}

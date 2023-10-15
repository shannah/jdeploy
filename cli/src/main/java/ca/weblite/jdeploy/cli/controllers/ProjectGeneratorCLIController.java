package ca.weblite.jdeploy.cli.controllers;

import ca.weblite.jdeploy.cli.util.CommandLineParser;
import ca.weblite.jdeploy.helpers.StringUtils;
import ca.weblite.jdeploy.services.ProjectGenerator;

import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;

public class ProjectGeneratorCLIController {
    private StringUtils stringUtils = new StringUtils();
    protected PrintStream out = System.out;
    protected PrintStream err = System.err;
    protected String[] args;

    public ProjectGeneratorCLIController(String[] args) {
        this.args = args;
    }

    public PrintStream getOut() {
        return out;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public PrintStream getErr() {
        return err;
    }

    public void setErr(PrintStream err) {
        this.err = err;
    }

    public void run() {
        ProjectGenerator.Params params = new ProjectGenerator.Params();
        params.setParentDirectory(new File("."));
        new CommandLineParser().parseArgsToParams(params, args);

        String[] filteredArgs = filterFlags(args);
        if (filteredArgs.length > 0) {
            String firstArg = filteredArgs[0];
            params.setMagicArg(firstArg);
        }

        try {
            File projectDirectory = new ProjectGenerator(params).generate();
            out.println("Project generated at: " + projectDirectory.getAbsolutePath());
        } catch (Exception ex) {
            err.println("Error generating project: " + ex.getMessage());
            ex.printStackTrace(err);
            new CommandLineParser().printHelp(params);
        }
    }

    public void requireNonNullProperty(Object object, String propertyName) throws Exception {
        if (object == null || propertyName == null || propertyName.isEmpty()) {
            throw new IllegalArgumentException("Object and propertyName must not be null or empty");
        }

        Field field;
        try {
            field = object.getClass().getDeclaredField(propertyName);
            field.setAccessible(true);
            if (field.get(object) == null) {
                String cliFlag = stringUtils.camelCaseToCliFlag(propertyName);
                throw new IllegalArgumentException("--" + cliFlag + " is required");
            }
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("No such property: " + propertyName);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Cannot access property: " + propertyName);
        }
    }

    private String[] filterFlags(String[] args) {
        String[] filtered = new String[args.length];
        int i = 0;
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                filtered[i++] = arg;
            }
        }

        return filtered;
    }
}

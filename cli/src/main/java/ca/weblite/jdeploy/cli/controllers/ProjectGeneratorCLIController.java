package ca.weblite.jdeploy.cli.controllers;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.builders.ProjectGeneratorRequestBuilder;
import ca.weblite.jdeploy.cli.util.CommandLineParser;
import ca.weblite.jdeploy.services.ProjectGenerator;
import java.io.File;
import java.io.PrintStream;

public class ProjectGeneratorCLIController {

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
        DIContext context = DIContext.getInstance();
        ProjectGeneratorRequestBuilder params = new ProjectGeneratorRequestBuilder();
        params.setParentDirectory(new File("."));
        new CommandLineParser().parseArgsToParams(params, args);
        String[] filteredArgs = filterFlags(args);
        if (filteredArgs.length > 0) {
            String firstArg = filteredArgs[0];
            params.setMagicArg(firstArg);
        }
        try {
            File projectDirectory = context.getInstance(ProjectGenerator.class).generate(params.build());
            out.println("Project generated at: " + projectDirectory.getAbsolutePath());
        } catch (Exception ex) {
            err.println("Error generating project: " + ex.getMessage());
            ex.printStackTrace(err);
            new CommandLineParser().printHelp(params);
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

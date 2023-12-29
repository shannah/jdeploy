package ca.weblite.jdeploy.cli.controllers;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.builders.GitHubRepositoryInitializationRequestBuilder;
import ca.weblite.jdeploy.cli.util.CommandLineParser;
import ca.weblite.jdeploy.services.GitHubRepositoryInitializer;

import java.io.File;

public class GitHubRepositoryInitializerCLIController extends BaseController {

    private int exitCode = 0;

    public GitHubRepositoryInitializerCLIController(File packageJSONFile, String[] args) {
        super(packageJSONFile, args);
    }

    public void run() {
        CommandLineParser parser = new CommandLineParser();
        GitHubRepositoryInitializationRequestBuilder params = new GitHubRepositoryInitializationRequestBuilder();
        parser.parseArgsToParams(params, args);
        params.setProjectPath(packageJSONFile.getParentFile().getPath());

        DIContext context = DIContext.getInstance();
        GitHubRepositoryInitializer gitHubRepositoryInitializer = context.getInstance(GitHubRepositoryInitializer.class);
        try {
            gitHubRepositoryInitializer.initAndPublish(params.build());
        } catch (Exception e) {
            System.err.println("Failed to initialize GitHub repository");
            e.printStackTrace(System.err);
            parser.printHelp(params);
            exitCode = 1;
        }

    }

    public int getExitCode() {
        return exitCode;
    }
}

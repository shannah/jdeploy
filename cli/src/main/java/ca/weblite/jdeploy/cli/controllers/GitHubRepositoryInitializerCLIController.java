package ca.weblite.jdeploy.cli.controllers;

import ca.weblite.jdeploy.cli.util.CommandLineParser;
import ca.weblite.jdeploy.services.GitHubRepositoryInitializer;
import ca.weblite.jdeploy.services.GitHubUsernameService;
import ca.weblite.jdeploy.services.GithubTokenService;

import java.io.File;

public class GitHubRepositoryInitializerCLIController extends BaseController {

    private GithubTokenService githubTokenService = new GithubTokenService();
    private GitHubUsernameService gitHubUsernameService = new GitHubUsernameService(githubTokenService);

    private int exitCode = 0;

    public GitHubRepositoryInitializerCLIController(File packageJSONFile, String[] args) {
        super(packageJSONFile, args);
    }

    public void run() {
        CommandLineParser parser = new CommandLineParser();
        GitHubRepositoryInitializer.Params params = new GitHubRepositoryInitializer.Params();
        parser.parseArgsToParams(params, args);
        try {
            new GitHubRepositoryInitializer(packageJSONFile, null, githubTokenService, gitHubUsernameService).initAndPublish(params);
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

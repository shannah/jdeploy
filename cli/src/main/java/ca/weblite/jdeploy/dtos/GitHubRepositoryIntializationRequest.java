package ca.weblite.jdeploy.dtos;

import ca.weblite.jdeploy.cli.util.CommandLineParser;
import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.services.GitHubRepositoryInitializer;

public class GitHubRepositoryIntializationRequest {
    private final String projectPath;

    private final String repoName;

    private final boolean isPrivate;

    public interface Params {
        @CommandLineParser.Help("The name of the repository to create")
        @CommandLineParser.PositionalArg(1)
        @CommandLineParser.Alias("n")
        String getRepoName();

        @CommandLineParser.Help("Whether the repository should be private")
        @CommandLineParser.Alias("p")
        boolean isPrivate();

        @CommandLineParser.Help("The path to the project directory")
        @CommandLineParser.Alias("d")
        String getProjectPath();

        Params setProjectPath(String projectPath);

        Params setRepoName(String repoName);

        Params setPrivate(boolean aPrivate);
    }

    public GitHubRepositoryIntializationRequest(Params params) {
        this.projectPath = params.getProjectPath();
        this.repoName = params.getRepoName();
        this.isPrivate = params.isPrivate();
    }

    public String getProjectPath() {
        return projectPath;
    }

    public String getRepoName() {
        return repoName;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

}

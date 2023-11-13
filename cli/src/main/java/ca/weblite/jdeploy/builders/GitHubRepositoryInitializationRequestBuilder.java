package ca.weblite.jdeploy.builders;

import ca.weblite.jdeploy.cli.util.CommandLineParser;
import ca.weblite.jdeploy.dtos.GitHubRepositoryIntializationRequest;
import java.io.File;
import java.io.IOException;

public class GitHubRepositoryInitializationRequestBuilder implements GitHubRepositoryIntializationRequest.Params {

    @CommandLineParser.Help("The name of the repository to create")
    @CommandLineParser.PositionalArg(1)
    @CommandLineParser.Alias("n")
    private String repoName;

    @CommandLineParser.Help("Whether the repository should be private")
    @CommandLineParser.Alias("p")
    private boolean isPrivate;

    private String projectPath;

    @Override
    public String getRepoName() {
        return repoName;
    }

    @Override
    public boolean isPrivate() {
        return isPrivate;
    }

    @Override
    public String getProjectPath() {
        if (projectPath != null) {
            return projectPath;
        }

        return new File(".").getPath();
    }

    @Override
    public GitHubRepositoryIntializationRequest.Params setProjectPath(String projectPath) {
        this.projectPath = projectPath;

        return this;
    }

    public GitHubRepositoryIntializationRequest.Params setRepoName(String repoName) {
        this.repoName = repoName;
        return this;
    }

    public GitHubRepositoryIntializationRequest.Params setPrivate(boolean aPrivate) {
        isPrivate = aPrivate;

        return this;
    }

    public GitHubRepositoryIntializationRequest build() throws IOException {
        return new GitHubRepositoryIntializationRequest(this);
    }

}

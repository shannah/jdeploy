package ca.weblite.jdeploy.dtos;

import ca.weblite.jdeploy.services.GitHubUsernameService;

import java.io.IOException;

public class GithubRepositoryDto {
    private String fullRepositoryName;
    private boolean isPrivate;

    public GithubRepositoryDto(String repositoryName, boolean isPrivate) {
        this.fullRepositoryName = repositoryName;
        this.isPrivate = isPrivate;
    }

    public String getFullRepositoryName() {
        return fullRepositoryName;
    }

    public String getRepositoryName() {
        if (fullRepositoryName != null && fullRepositoryName.contains("/") && fullRepositoryName.split("/").length > 1) {
            return fullRepositoryName.split("/")[1];
        }

        return fullRepositoryName;
    }

    public String getRepositoryUsername(GitHubUsernameService gitHubUsernameService) throws IOException {
        if (fullRepositoryName != null && fullRepositoryName.contains("/") && fullRepositoryName.split("/").length > 1) {
            return fullRepositoryName.split("/")[0];
        }

        return gitHubUsernameService.getGitHubUsername();
    }

    public void setFullRepositoryName(String fullRepositoryName) {
        this.fullRepositoryName = fullRepositoryName;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean aPrivate) {
        isPrivate = aPrivate;
    }
}

package ca.weblite.jdeploy.factories;

import ca.weblite.jdeploy.dtos.GithubRepositoryDto;

import javax.inject.Singleton;

@Singleton
public class GitHubRepositoryDtoFactory {
    public GithubRepositoryDto newGithubRepository(String repoName, boolean isPrivate) {
        return new GithubRepositoryDto(repoName, isPrivate);
    }
}

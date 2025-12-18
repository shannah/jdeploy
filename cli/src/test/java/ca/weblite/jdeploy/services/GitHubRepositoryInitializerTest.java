package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.builders.GitHubRepositoryInitializationRequestBuilder;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

class GitHubRepositoryInitializerTest {

    private File parentDirectory;

    private final String repoName = "test-jdeploy-repo-" + System.currentTimeMillis() + "-test";

    private final GithubTokenService githubTokenService = DIContext.getInstance().getInstance(GithubTokenService.class);

    private final GitHubUsernameService gitHubUsernameService = new GitHubUsernameService(githubTokenService);

    private GitHubRepositoryInitializer gitHubRepositoryInitializer;

    @BeforeEach
    public void setUp() throws Exception {
        applyAssumptions();
        parentDirectory = Files.createTempDirectory("jdeploy-test").toFile();
        FileUtils.writeStringToFile(new File(parentDirectory, "package.json"), "{}", "UTF-8");
        gitHubRepositoryInitializer = DIContext.getInstance().getInstance(GitHubRepositoryInitializer.class);
    }

    @AfterEach
    public void tearDown() throws Exception {
        applyAssumptions();
        if (parentDirectory != null && parentDirectory.exists()) {
            FileUtils.deleteDirectory(parentDirectory);
        }
        new GitHubRepositoryDeleter(githubTokenService, gitHubUsernameService).deleteGitHubRepository(repoName);
    }

    @Test
    public void testInitAndPublish() throws Exception {
        applyAssumptions();
        GitHubRepositoryInitializationRequestBuilder params = (GitHubRepositoryInitializationRequestBuilder)
                new GitHubRepositoryInitializationRequestBuilder()
                .setRepoName(repoName)
                .setProjectPath(parentDirectory.getPath());

        gitHubRepositoryInitializer.initAndPublish(params.build());
    }

    private void applyAssumptions() {
        Assumptions.assumeTrue(
                System.getenv("GH_TOKEN") != null,
                "GH_TOKEN is not provided");
        Assumptions.assumeTrue(
                System.getenv("GH_TOKEN").startsWith("ghp_"),
                "GH_TOKEN is not personal access token");
    }
}
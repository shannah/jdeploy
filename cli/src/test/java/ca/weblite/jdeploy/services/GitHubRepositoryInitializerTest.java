package ca.weblite.jdeploy.services;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class GitHubRepositoryInitializerTest {

    private File parentDirectory;

    private String repoName = "test-jdeploy-repo-" + System.currentTimeMillis() + "-test";

    private GithubTokenService githubTokenService = new GithubTokenService();

    private GitHubUsernameService gitHubUsernameService = new GitHubUsernameService(githubTokenService);

    @BeforeEach
    public void setUp() throws Exception {
        parentDirectory = Files.createTempDirectory("jdeploy-test").toFile();
        FileUtils.writeStringToFile(new File(parentDirectory, "package.json"), "{}", "UTF-8");

    }

    @AfterEach
    public void tearDown() throws Exception {
        if (parentDirectory != null && parentDirectory.exists()) {
            FileUtils.deleteDirectory(parentDirectory);
        }
        new GitHubRepositoryDeleter(githubTokenService, gitHubUsernameService).deleteGitHubRepository(repoName);
    }

    @Test
    public void testInitAndPublish() throws Exception {
        Assumptions.assumeTrue(
                System.getenv("GH_TOKEN") != null,
                "GH_TOKEN is not provided");
        Assumptions.assumeTrue(
                System.getenv("GH_TOKEN").startsWith("ghp_"),
                "GH_TOKEN is not personal access token");

        GitHubRepositoryInitializer.Params params = new GitHubRepositoryInitializer.Params();
        params.setRepoName(repoName);
        GitHubRepositoryInitializer initializer = new GitHubRepositoryInitializer(
                new File(parentDirectory, "package.json"),
                null,
                githubTokenService,
                gitHubUsernameService
        );
        initializer.initAndPublish(params);
    }
}
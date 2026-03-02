package ca.weblite.jdeploy.cli.controllers;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.services.GitHubRepositoryDeleter;
import ca.weblite.jdeploy.services.GitHubUsernameService;
import ca.weblite.jdeploy.services.GithubTokenService;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class CreateGitHubRepositoryCLIControllerTest {

    private File parentDirectory;

    private String repoName = "test-jdeploy-repo-" + System.currentTimeMillis() + "-create-test";

    private GithubTokenService githubTokenService = DIContext.getInstance().getInstance(GithubTokenService.class);

    private GitHubUsernameService gitHubUsernameService = DIContext.getInstance().getInstance(GitHubUsernameService.class);

    @BeforeEach
    public void setUp() throws Exception {
        Assumptions.assumeTrue(
                System.getenv("GH_TOKEN") != null,
                "GH_TOKEN is not provided");
        Assumptions.assumeTrue(
                System.getenv("GH_TOKEN").startsWith("ghp_"),
                "GH_TOKEN is not personal access token");
        parentDirectory = Files.createTempDirectory("jdeploy-test").toFile();
        FileUtils.writeStringToFile(new File(parentDirectory, "package.json"), "{}", "UTF-8");
    }

    @AfterEach
    public void tearDown() throws Exception {
        Assumptions.assumeTrue(
                System.getenv("GH_TOKEN") != null,
                "GH_TOKEN is not provided");
        Assumptions.assumeTrue(
                System.getenv("GH_TOKEN").startsWith("ghp_"),
                "GH_TOKEN is not personal access token");
        if (parentDirectory != null && parentDirectory.exists()) {
            FileUtils.deleteDirectory(parentDirectory);
        }
        new GitHubRepositoryDeleter(githubTokenService, gitHubUsernameService).deleteGitHubRepository(repoName);
    }

    @Test
    public void testCreatePrivateRepository() {
        CreateGitHubRepositoryCLIController controller = new CreateGitHubRepositoryCLIController(
                new File(parentDirectory, "package.json"),
                new String[] {
                        "--repo-name=" + repoName,
                        "--private"
                });
        controller.run();

        assertEquals(0, controller.getExitCode());
    }

    @Test
    public void testCreateRepositoryWithShortFlag() {
        CreateGitHubRepositoryCLIController controller = new CreateGitHubRepositoryCLIController(
                new File(parentDirectory, "package.json"),
                new String[] {
                        "-n", repoName,
                        "-p"
                });
        controller.run();

        assertEquals(0, controller.getExitCode());
    }
}

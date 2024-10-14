package ca.weblite.jdeploy.helpers;

import ca.weblite.jdeploy.BundleConstants;
import ca.weblite.jdeploy.environment.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GithubReleaseNotesHelperTest implements BundleConstants {

    private File tempDirectory;
    private Environment testEnv;
    private PrintStream err;

    @BeforeEach
    public void setUp() throws Exception {
        // Create a temporary directory for testing
        tempDirectory = Files.createTempDirectory("githubReleaseNotesTest").toFile();

        // Prepare mock environment variables
        Map<String, String> envVars = new HashMap<>();
        envVars.put("GITHUB_REPOSITORY", "testuser/testrepo");
        envVars.put("GITHUB_REF_NAME", "v1.0.0");
        envVars.put("GITHUB_REF_TYPE", "tag");

        // Create a mock environment
        testEnv = new MockEnvironment(envVars);

        // Create the release files directory
        File releaseFilesDir = new File(tempDirectory, "jdeploy/github-release-files");
        releaseFilesDir.mkdirs();

        // Create test bundle files
        new File(releaseFilesDir, "testapp-" + BUNDLE_MAC_X64 + ".zip").createNewFile();
        new File(releaseFilesDir, "testapp-" + BUNDLE_MAC_ARM64 + ".zip").createNewFile();
        new File(releaseFilesDir, "testapp-" + BUNDLE_WIN + ".exe").createNewFile();

        // Use System.err for error output
        err = System.err;
    }

    @AfterEach
    public void tearDown() throws Exception {
        // Clean up the temporary directory after tests
        deleteDirectory(tempDirectory);
    }

    @Test
    public void testCreateGithubReleaseNotes() {
        GithubReleaseNotesMutator helper = new GithubReleaseNotesMutator(tempDirectory, err, testEnv);
        String releaseNotes = helper.createGithubReleaseNotes();

        // Assert that the release notes contain expected links
        assertTrue(releaseNotes.contains("* [Mac (Intel)](https://github.com/testuser/testrepo/releases/download/v1.0.0/testapp-" + BUNDLE_MAC_X64 + ".zip)<!-- id:" + BUNDLE_MAC_X64 + "-link -->"));
        assertTrue(releaseNotes.contains("* [Mac (Apple Silicon)](https://github.com/testuser/testrepo/releases/download/v1.0.0/testapp-" + BUNDLE_MAC_ARM64 + ".zip)<!-- id:" + BUNDLE_MAC_ARM64 + "-link -->"));
        assertTrue(releaseNotes.contains("* [Windows (x64)](https://github.com/testuser/testrepo/releases/download/v1.0.0/testapp-" + BUNDLE_WIN + ".exe)<!-- id:" + BUNDLE_WIN + "-link -->"));

        // Assert that the Linux link is not present
        assertFalse(releaseNotes.contains("* [Linux (x64)]"));

        // Additional checks for content correctness can be added here
    }

    @Test
    public void testUpdateLinkInGithubReleaseNotes() {
        GithubReleaseNotesMutator helper = new GithubReleaseNotesMutator(tempDirectory, err, testEnv);
        String releaseNotes = helper.createGithubReleaseNotes();

        // Update one of the links
        String newUrl = "https://example.com/new-mac-intel.zip";
        String updatedNotes = helper.updateLinkInGithubReleaseNotes(releaseNotes, BUNDLE_MAC_X64, newUrl);

        // Assert that the Mac (Intel) link has been updated
        assertTrue(updatedNotes.contains("* [Mac (Intel)](" + newUrl + ")<!-- id:" + BUNDLE_MAC_X64 + "-link -->"));

        // Assert that other links remain unchanged
        assertTrue(updatedNotes.contains("* [Mac (Apple Silicon)](https://github.com/testuser/testrepo/releases/download/v1.0.0/testapp-" + BUNDLE_MAC_ARM64 + ".zip)<!-- id:" + BUNDLE_MAC_ARM64 + "-link -->"));
        assertTrue(updatedNotes.contains("* [Windows (x64)](https://github.com/testuser/testrepo/releases/download/v1.0.0/testapp-" + BUNDLE_WIN + ".exe)<!-- id:" + BUNDLE_WIN + "-link -->"));
    }

    // Helper method to delete directories recursively
    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                deleteDirectory(f);
            }
        }
        dir.delete();
    }

    // MockEnvironment class to simulate environment variables
    private static class MockEnvironment extends Environment {
        private final Map<String, String> variables;

        public MockEnvironment(Map<String, String> variables) {
            this.variables = variables;
        }

        @Override
        public String get(String name) {
            return variables.get(name);
        }
    }
}

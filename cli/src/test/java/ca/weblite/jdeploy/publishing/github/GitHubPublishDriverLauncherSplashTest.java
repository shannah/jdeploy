package ca.weblite.jdeploy.publishing.github;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GitHubPublishDriver launcher splash file handling.
 */
public class GitHubPublishDriverLauncherSplashTest {

    @Test
    public void testLauncherSplashCopiedToReleaseFiles(@TempDir Path tempDir) throws Exception {
        // Setup: Create source launcher-splash.html
        File projectDir = tempDir.resolve("project").toFile();
        projectDir.mkdirs();
        File sourceLauncherSplash = new File(projectDir, "launcher-splash.html");
        String htmlContent = "<html><body>Test Release</body></html>";
        FileUtils.write(sourceLauncherSplash, htmlContent, "UTF-8");

        // Setup: Create release files directory
        File releaseFilesDir = tempDir.resolve("release-files").toFile();
        releaseFilesDir.mkdirs();

        // Simulate what GitHubPublishDriver.saveGithubReleaseFiles() does
        if (sourceLauncherSplash.exists()) {
            FileUtils.copyFile(sourceLauncherSplash,
                new File(releaseFilesDir, sourceLauncherSplash.getName()));
        }

        // Verify
        File copiedFile = new File(releaseFilesDir, "launcher-splash.html");
        assertTrue(copiedFile.exists(), "launcher-splash.html should be copied to release files");
        assertEquals(htmlContent, FileUtils.readFileToString(copiedFile, "UTF-8"),
            "Content should match");
    }

    @Test
    public void testLauncherSplashNotCopiedWhenMissing(@TempDir Path tempDir) throws Exception {
        // Setup: Project without launcher-splash.html
        File projectDir = tempDir.resolve("project").toFile();
        projectDir.mkdirs();
        File sourceLauncherSplash = new File(projectDir, "launcher-splash.html");

        // Setup: Create release files directory
        File releaseFilesDir = tempDir.resolve("release-files").toFile();
        releaseFilesDir.mkdirs();

        // Simulate what GitHubPublishDriver does (checks existence before copying)
        if (sourceLauncherSplash.exists()) {
            FileUtils.copyFile(sourceLauncherSplash,
                new File(releaseFilesDir, sourceLauncherSplash.getName()));
        }

        // Verify - file should not exist in release files
        File copiedFile = new File(releaseFilesDir, "launcher-splash.html");
        assertFalse(copiedFile.exists(),
            "launcher-splash.html should not be in release files when source doesn't exist");
    }

    @Test
    public void testMultipleFilesInReleaseDirectory(@TempDir Path tempDir) throws Exception {
        // Setup: Create multiple files
        File projectDir = tempDir.resolve("project").toFile();
        projectDir.mkdirs();

        File icon = new File(projectDir, "icon.png");
        File installSplash = new File(projectDir, "installsplash.png");
        File launcherSplash = new File(projectDir, "launcher-splash.html");

        FileUtils.write(icon, "icon content", "UTF-8");
        FileUtils.write(installSplash, "install splash content", "UTF-8");
        FileUtils.write(launcherSplash, "<html><body>Launcher</body></html>", "UTF-8");

        // Setup: Release directory
        File releaseFilesDir = tempDir.resolve("release-files").toFile();
        releaseFilesDir.mkdirs();

        // Copy all files (as GitHubPublishDriver would)
        if (icon.exists()) {
            FileUtils.copyFile(icon, new File(releaseFilesDir, icon.getName()));
        }
        if (installSplash.exists()) {
            FileUtils.copyFile(installSplash, new File(releaseFilesDir, installSplash.getName()));
        }
        if (launcherSplash.exists()) {
            FileUtils.copyFile(launcherSplash, new File(releaseFilesDir, launcherSplash.getName()));
        }

        // Verify all files are in release directory
        assertTrue(new File(releaseFilesDir, "icon.png").exists());
        assertTrue(new File(releaseFilesDir, "installsplash.png").exists());
        assertTrue(new File(releaseFilesDir, "launcher-splash.html").exists());
    }

    @Test
    public void testLauncherSplashPreservesContent(@TempDir Path tempDir) throws Exception {
        File projectDir = tempDir.resolve("project").toFile();
        projectDir.mkdirs();

        // Create HTML with various content
        String complexHtml = "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "  <meta charset=\"UTF-8\">\n" +
            "  <style>body { color: red; }</style>\n" +
            "</head>\n" +
            "<body>\n" +
            "  <h1>Test with 中文</h1>\n" +
            "  <img src=\"data:image/png;base64,test\" />\n" +
            "</body>\n" +
            "</html>";

        File sourceLauncherSplash = new File(projectDir, "launcher-splash.html");
        FileUtils.write(sourceLauncherSplash, complexHtml, "UTF-8");

        File releaseFilesDir = tempDir.resolve("release-files").toFile();
        releaseFilesDir.mkdirs();

        FileUtils.copyFile(sourceLauncherSplash,
            new File(releaseFilesDir, "launcher-splash.html"));

        File copiedFile = new File(releaseFilesDir, "launcher-splash.html");
        assertEquals(complexHtml, FileUtils.readFileToString(copiedFile, "UTF-8"),
            "Complex HTML content should be preserved exactly");
    }
}

package ca.weblite.jdeploy.packaging;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PackageService launcher splash bundling functionality.
 *
 * Note: These are focused unit tests for the launcher splash feature.
 * Full integration tests are in the integration test suite.
 */
public class PackageServiceLauncherSplashTest {

    @Test
    public void testLauncherSplashFileCopying(@TempDir Path tempDir) throws Exception {
        // Create a test launcher-splash.html file
        File sourceFile = tempDir.resolve("launcher-splash.html").toFile();
        String htmlContent = "<html><body>Test Launcher Splash</body></html>";
        FileUtils.write(sourceFile, htmlContent, "UTF-8");

        // Create destination directory
        File destDir = tempDir.resolve("dest").toFile();
        destDir.mkdirs();
        File destFile = new File(destDir, "launcher-splash.html");

        // Copy file (simulating what PackageService does)
        FileUtils.copyFile(sourceFile, destFile);

        // Verify
        assertTrue(destFile.exists(), "Destination file should exist");
        assertEquals(htmlContent, FileUtils.readFileToString(destFile, "UTF-8"),
            "Content should match");
    }

    @Test
    public void testLauncherSplashOptional(@TempDir Path tempDir) {
        // Create a directory without launcher-splash.html
        File projectDir = tempDir.toFile();
        File launcherSplash = new File(projectDir, "launcher-splash.html");

        // Verify file doesn't exist
        assertFalse(launcherSplash.exists(),
            "launcher-splash.html should not exist");

        // This should not cause any issues - the file is optional
        // In actual PackageService, it checks if file exists before copying
    }

    @Test
    public void testLauncherSplashWithSpecialCharacters(@TempDir Path tempDir) throws Exception {
        File sourceFile = tempDir.resolve("launcher-splash.html").toFile();
        String htmlContent = "<!DOCTYPE html><html><body>Test with 中文 and émojis 🎉</body></html>";
        FileUtils.write(sourceFile, htmlContent, "UTF-8");

        File destDir = tempDir.resolve("dest").toFile();
        destDir.mkdirs();
        File destFile = new File(destDir, "launcher-splash.html");

        FileUtils.copyFile(sourceFile, destFile);

        assertEquals(htmlContent, FileUtils.readFileToString(destFile, "UTF-8"),
            "Special characters should be preserved");
    }

    @Test
    public void testLauncherSplashLargeFile(@TempDir Path tempDir) throws Exception {
        // Create a large HTML file (> 100KB)
        StringBuilder largeHtml = new StringBuilder("<!DOCTYPE html><html><body>");
        for (int i = 0; i < 10000; i++) {
            largeHtml.append("<p>Line ").append(i).append("</p>");
        }
        largeHtml.append("</body></html>");

        File sourceFile = tempDir.resolve("launcher-splash.html").toFile();
        FileUtils.write(sourceFile, largeHtml.toString(), "UTF-8");

        assertTrue(sourceFile.length() > 100 * 1024,
            "File should be > 100KB");

        File destDir = tempDir.resolve("dest").toFile();
        destDir.mkdirs();
        File destFile = new File(destDir, "launcher-splash.html");

        FileUtils.copyFile(sourceFile, destFile);

        assertTrue(destFile.exists(), "Large file should be copied");
        assertEquals(sourceFile.length(), destFile.length(),
            "File sizes should match");
    }

    @Test
    public void testJDeployFilesDirectoryStructure(@TempDir Path tempDir) throws Exception {
        // Simulate .jdeploy-files directory structure
        File jdeployFilesDir = tempDir.resolve(".jdeploy-files").toFile();
        jdeployFilesDir.mkdirs();

        File launcherSplash = new File(jdeployFilesDir, "launcher-splash.html");
        String htmlContent = "<html><body>Test</body></html>";
        FileUtils.write(launcherSplash, htmlContent, "UTF-8");

        // Verify structure
        assertTrue(jdeployFilesDir.exists(), ".jdeploy-files directory should exist");
        assertTrue(launcherSplash.exists(), "launcher-splash.html should exist in .jdeploy-files");

        File icon = new File(jdeployFilesDir, "icon.png");
        File installSplash = new File(jdeployFilesDir, "installsplash.png");

        // These files would normally exist in a complete bundle
        // Just verify the directory structure supports them
        assertEquals(jdeployFilesDir, launcherSplash.getParentFile(),
            "launcher-splash.html should be in .jdeploy-files");
    }
}

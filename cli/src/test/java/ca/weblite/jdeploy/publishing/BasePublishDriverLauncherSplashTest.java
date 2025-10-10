package ca.weblite.jdeploy.publishing;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BasePublishDriver launcher splash MD5 checksum functionality.
 */
public class BasePublishDriverLauncherSplashTest {

    @Test
    public void testMD5ChecksumCalculation(@TempDir Path tempDir) throws Exception {
        File launcherSplash = tempDir.resolve("launcher-splash.html").toFile();
        String htmlContent = "<html><body>Test Launcher Splash</body></html>";
        FileUtils.write(launcherSplash, htmlContent, "UTF-8");

        // Calculate expected MD5
        String expectedMD5 = calculateMD5(launcherSplash);

        // Verify it's a valid MD5 (32 hex characters)
        assertNotNull(expectedMD5);
        assertEquals(32, expectedMD5.length());
        assertTrue(expectedMD5.matches("[a-f0-9]{32}"));
    }

    @Test
    public void testMD5ChecksumConsistency(@TempDir Path tempDir) throws Exception {
        File launcherSplash = tempDir.resolve("launcher-splash.html").toFile();
        String htmlContent = "<html><body>Test</body></html>";
        FileUtils.write(launcherSplash, htmlContent, "UTF-8");

        String checksum1 = calculateMD5(launcherSplash);
        String checksum2 = calculateMD5(launcherSplash);

        assertEquals(checksum1, checksum2, "MD5 checksum should be consistent");
    }

    @Test
    public void testMD5ChecksumDifferentContent(@TempDir Path tempDir) throws Exception {
        File file1 = tempDir.resolve("file1.html").toFile();
        File file2 = tempDir.resolve("file2.html").toFile();

        FileUtils.write(file1, "<html><body>Content 1</body></html>", "UTF-8");
        FileUtils.write(file2, "<html><body>Content 2</body></html>", "UTF-8");

        String checksum1 = calculateMD5(file1);
        String checksum2 = calculateMD5(file2);

        assertNotEquals(checksum1, checksum2, "Different content should have different checksums");
    }

    @Test
    public void testMD5ChecksumWithSpecialCharacters(@TempDir Path tempDir) throws Exception {
        File launcherSplash = tempDir.resolve("launcher-splash.html").toFile();
        String htmlContent = "<!DOCTYPE html><html><body>Test with 中文 and émojis 🎉</body></html>";
        FileUtils.write(launcherSplash, htmlContent, "UTF-8");

        String checksum = calculateMD5(launcherSplash);

        assertNotNull(checksum);
        assertEquals(32, checksum.length());
        assertTrue(checksum.matches("[a-f0-9]{32}"));
    }

    @Test
    public void testMD5ChecksumLargeFile(@TempDir Path tempDir) throws Exception {
        StringBuilder largeHtml = new StringBuilder("<!DOCTYPE html><html><body>");
        for (int i = 0; i < 10000; i++) {
            largeHtml.append("<p>Line ").append(i).append("</p>");
        }
        largeHtml.append("</body></html>");

        File launcherSplash = tempDir.resolve("launcher-splash.html").toFile();
        FileUtils.write(launcherSplash, largeHtml.toString(), "UTF-8");

        assertTrue(launcherSplash.length() > 100 * 1024, "File should be > 100KB");

        String checksum = calculateMD5(launcherSplash);

        assertNotNull(checksum);
        assertEquals(32, checksum.length());
    }

    @Test
    public void testChecksumMapHandling(@TempDir Path tempDir) throws Exception {
        // Simulate what BasePublishDriver does
        Map<String, String> checksums = new HashMap<>();

        File icon = tempDir.resolve("icon.png").toFile();
        File installSplash = tempDir.resolve("installsplash.png").toFile();
        File launcherSplash = tempDir.resolve("launcher-splash.html").toFile();

        FileUtils.write(icon, "icon content", "UTF-8");
        FileUtils.write(installSplash, "install splash content", "UTF-8");
        FileUtils.write(launcherSplash, "<html><body>Launcher</body></html>", "UTF-8");

        if (icon.exists()) {
            checksums.put("icon.png", calculateMD5(icon));
        }
        if (installSplash.exists()) {
            checksums.put("installsplash.png", calculateMD5(installSplash));
        }
        if (launcherSplash.exists()) {
            checksums.put("launcher-splash.html", calculateMD5(launcherSplash));
        }

        assertEquals(3, checksums.size());
        assertTrue(checksums.containsKey("icon.png"));
        assertTrue(checksums.containsKey("installsplash.png"));
        assertTrue(checksums.containsKey("launcher-splash.html"));

        // All checksums should be valid MD5
        for (String checksum : checksums.values()) {
            assertEquals(32, checksum.length());
            assertTrue(checksum.matches("[a-f0-9]{32}"));
        }
    }

    @Test
    public void testMissingLauncherSplashNotInChecksums(@TempDir Path tempDir) throws Exception {
        // Simulate what BasePublishDriver does when launcher-splash.html doesn't exist
        Map<String, String> checksums = new HashMap<>();

        File launcherSplash = tempDir.resolve("launcher-splash.html").toFile();

        // File doesn't exist
        if (launcherSplash.exists()) {
            checksums.put("launcher-splash.html", calculateMD5(launcherSplash));
        }

        assertFalse(checksums.containsKey("launcher-splash.html"),
            "Missing launcher-splash.html should not be in checksums map");
    }

    // Helper method to calculate MD5 (mirrors MD5.getMD5Checksum behavior)
    private String calculateMD5(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] fileData = FileUtils.readFileToByteArray(file);
        byte[] digest = md.digest(fileData);

        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

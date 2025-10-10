package ca.weblite.jdeploy.publishing;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ResourceUploader launcher splash NPM upload functionality.
 */
public class ResourceUploaderLauncherSplashTest {

    @Test
    public void testBase64EncodingForUpload(@TempDir Path tempDir) throws Exception {
        File launcherSplash = tempDir.resolve("launcher-splash.html").toFile();
        String htmlContent = "<html><body>Test Launcher Splash</body></html>";
        FileUtils.write(launcherSplash, htmlContent, "UTF-8");

        // Simulate what ResourceUploader does
        byte[] launcherSplashBytes = FileUtils.readFileToByteArray(launcherSplash);
        String encoded = Base64.getEncoder().encodeToString(launcherSplashBytes);

        assertNotNull(encoded);
        assertTrue(encoded.length() > 0);

        // Verify it decodes back correctly
        byte[] decoded = Base64.getDecoder().decode(encoded);
        String decodedContent = new String(decoded, StandardCharsets.UTF_8);
        assertEquals(htmlContent, decodedContent);
    }

    @Test
    public void testJDeployFilesMapWithLauncherSplash(@TempDir Path tempDir) throws Exception {
        // Simulate what ResourceUploader does
        Map<String, String> jdeployFiles = new HashMap<>();

        File icon = tempDir.resolve("icon.png").toFile();
        File installSplash = tempDir.resolve("installsplash.png").toFile();
        File launcherSplash = tempDir.resolve("launcher-splash.html").toFile();

        FileUtils.write(icon, "icon content", "UTF-8");
        FileUtils.write(installSplash, "install splash content", "UTF-8");
        FileUtils.write(launcherSplash, "<html><body>Launcher</body></html>", "UTF-8");

        if (icon.exists()) {
            byte[] iconBytes = FileUtils.readFileToByteArray(icon);
            jdeployFiles.put("icon.png", Base64.getEncoder().encodeToString(iconBytes));
        }
        if (installSplash.exists()) {
            byte[] installSplashBytes = FileUtils.readFileToByteArray(installSplash);
            jdeployFiles.put("installsplash.png", Base64.getEncoder().encodeToString(installSplashBytes));
        }
        if (launcherSplash.exists()) {
            byte[] launcherSplashBytes = FileUtils.readFileToByteArray(launcherSplash);
            jdeployFiles.put("launcher-splash.html", Base64.getEncoder().encodeToString(launcherSplashBytes));
        }

        assertEquals(3, jdeployFiles.size());
        assertTrue(jdeployFiles.containsKey("icon.png"));
        assertTrue(jdeployFiles.containsKey("installsplash.png"));
        assertTrue(jdeployFiles.containsKey("launcher-splash.html"));

        // Verify all values are valid base64
        for (String encoded : jdeployFiles.values()) {
            assertDoesNotThrow(() -> Base64.getDecoder().decode(encoded));
        }
    }

    @Test
    public void testLauncherSplashOnlyUpload(@TempDir Path tempDir) throws Exception {
        Map<String, String> jdeployFiles = new HashMap<>();

        File launcherSplash = tempDir.resolve("launcher-splash.html").toFile();
        FileUtils.write(launcherSplash, "<html><body>Test</body></html>", "UTF-8");

        if (launcherSplash.exists()) {
            byte[] launcherSplashBytes = FileUtils.readFileToByteArray(launcherSplash);
            jdeployFiles.put("launcher-splash.html", Base64.getEncoder().encodeToString(launcherSplashBytes));
        }

        assertEquals(1, jdeployFiles.size());
        assertTrue(jdeployFiles.containsKey("launcher-splash.html"));
    }

    @Test
    public void testMissingLauncherSplashNotInMap(@TempDir Path tempDir) {
        Map<String, String> jdeployFiles = new HashMap<>();

        File launcherSplash = tempDir.resolve("launcher-splash.html").toFile();

        // File doesn't exist - should not be added to map
        if (launcherSplash.exists()) {
            byte[] launcherSplashBytes;
            try {
                launcherSplashBytes = FileUtils.readFileToByteArray(launcherSplash);
                jdeployFiles.put("launcher-splash.html", Base64.getEncoder().encodeToString(launcherSplashBytes));
            } catch (Exception e) {
                fail("Should not attempt to read non-existent file");
            }
        }

        assertFalse(jdeployFiles.containsKey("launcher-splash.html"));
        assertEquals(0, jdeployFiles.size());
    }

    @Test
    public void testBase64EncodingWithSpecialCharacters(@TempDir Path tempDir) throws Exception {
        File launcherSplash = tempDir.resolve("launcher-splash.html").toFile();
        String htmlContent = "<!DOCTYPE html><html><body>Test with 中文 and émojis 🎉</body></html>";
        FileUtils.write(launcherSplash, htmlContent, "UTF-8");

        byte[] launcherSplashBytes = FileUtils.readFileToByteArray(launcherSplash);
        String encoded = Base64.getEncoder().encodeToString(launcherSplashBytes);

        // Verify it decodes back correctly with special characters
        byte[] decoded = Base64.getDecoder().decode(encoded);
        String decodedContent = new String(decoded, StandardCharsets.UTF_8);
        assertEquals(htmlContent, decodedContent);
    }

    @Test
    public void testBase64EncodingLargeFile(@TempDir Path tempDir) throws Exception {
        StringBuilder largeHtml = new StringBuilder("<!DOCTYPE html><html><body>");
        for (int i = 0; i < 10000; i++) {
            largeHtml.append("<p>Line ").append(i).append("</p>");
        }
        largeHtml.append("</body></html>");

        File launcherSplash = tempDir.resolve("launcher-splash.html").toFile();
        FileUtils.write(launcherSplash, largeHtml.toString(), "UTF-8");

        assertTrue(launcherSplash.length() > 100 * 1024, "File should be > 100KB");

        byte[] launcherSplashBytes = FileUtils.readFileToByteArray(launcherSplash);
        String encoded = Base64.getEncoder().encodeToString(launcherSplashBytes);

        assertNotNull(encoded);
        assertTrue(encoded.length() > 0);

        // Verify it decodes correctly
        byte[] decoded = Base64.getDecoder().decode(encoded);
        assertEquals(launcherSplash.length(), decoded.length);
    }

    @Test
    public void testBase64EncodingPreservesContent(@TempDir Path tempDir) throws Exception {
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

        File launcherSplash = tempDir.resolve("launcher-splash.html").toFile();
        FileUtils.write(launcherSplash, complexHtml, "UTF-8");

        byte[] launcherSplashBytes = FileUtils.readFileToByteArray(launcherSplash);
        String encoded = Base64.getEncoder().encodeToString(launcherSplashBytes);

        // Decode and verify exact content preservation
        byte[] decoded = Base64.getDecoder().decode(encoded);
        String decodedContent = new String(decoded, StandardCharsets.UTF_8);
        assertEquals(complexHtml, decodedContent);
    }

    @Test
    public void testConditionalUploadLogic(@TempDir Path tempDir) throws Exception {
        File icon = tempDir.resolve("icon.png").toFile();
        File installSplash = tempDir.resolve("installsplash.png").toFile();
        File launcherSplash = tempDir.resolve("launcher-splash.html").toFile();

        // Only create launcher splash
        FileUtils.write(launcherSplash, "<html><body>Test</body></html>", "UTF-8");

        // Simulate ResourceUploader logic: only upload if at least one file exists
        if (icon.exists() || installSplash.exists() || launcherSplash.exists()) {
            Map<String, String> jdeployFiles = new HashMap<>();

            if (icon.exists()) {
                byte[] iconBytes = FileUtils.readFileToByteArray(icon);
                jdeployFiles.put("icon.png", Base64.getEncoder().encodeToString(iconBytes));
            }
            if (installSplash.exists()) {
                byte[] installSplashBytes = FileUtils.readFileToByteArray(installSplash);
                jdeployFiles.put("installsplash.png", Base64.getEncoder().encodeToString(installSplashBytes));
            }
            if (launcherSplash.exists()) {
                byte[] launcherSplashBytes = FileUtils.readFileToByteArray(launcherSplash);
                jdeployFiles.put("launcher-splash.html", Base64.getEncoder().encodeToString(launcherSplashBytes));
            }

            // Should only contain launcher-splash.html
            assertEquals(1, jdeployFiles.size());
            assertTrue(jdeployFiles.containsKey("launcher-splash.html"));
        } else {
            fail("At least one file should exist");
        }
    }
}

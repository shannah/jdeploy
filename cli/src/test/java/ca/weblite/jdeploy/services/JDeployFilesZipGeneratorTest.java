package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishing.PublishingContext;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("JDeployFilesZipGenerator Tests")
class JDeployFilesZipGeneratorTest {

    @TempDir
    File tempDir;

    private JDeployFilesZipGenerator generator;
    private PublishingContext publishingContext;
    private PublishTargetInterface target;
    private File packageJsonFile;
    private File releaseFilesDir;

    @BeforeEach
    void setUp() throws IOException {
        generator = new JDeployFilesZipGenerator();

        // Create package.json
        packageJsonFile = new File(tempDir, "package.json");
        releaseFilesDir = new File(tempDir, "release-files");
        releaseFilesDir.mkdirs();

        // Create publishing context
        Map<String, Object> packageJsonMap = new HashMap<>();
        packageJsonMap.put("name", "test-app");
        packageJsonMap.put("version", "1.0.0");

        PackagingContext packagingContext = new PackagingContext(
                tempDir,
                packageJsonMap,
                packageJsonFile,
                false, false, null, null, null, null,
                new PrintStream(System.out),
                new PrintStream(System.err),
                System.in,
                true, false
        );

        publishingContext = new PublishingContext(
                packagingContext,
                false,
                mock(NPM.class),
                "test-token",
                null, null, null, null
        ) {
            @Override
            public File getGithubReleaseFilesDir() {
                return releaseFilesDir;
            }
        };

        target = mock(PublishTargetInterface.class);
        when(target.getUrl()).thenReturn("https://github.com/user/test-app");
    }

    @Test
    @DisplayName("Should generate jdeploy-files.zip with all required files")
    void shouldGenerateZipWithAllFiles() throws IOException {
        // Create package.json
        String packageJson = "{"
                + "\"name\":\"test-app\","
                + "\"version\":\"1.0.0\","
                + "\"repository\":{\"url\":\"https://github.com/user/test-app\"}"
                + "}";
        FileUtils.writeStringToFile(packageJsonFile, packageJson, StandardCharsets.UTF_8);

        // Create icon.png (minimal PNG)
        byte[] pngData = createMinimalPng();
        File iconFile = new File(tempDir, "icon.png");
        Files.write(iconFile.toPath(), pngData);

        // Create optional files
        File splashFile = new File(tempDir, "installsplash.png");
        Files.write(splashFile.toPath(), pngData);

        File launcherSplashFile = new File(tempDir, "launcher-splash.html");
        FileUtils.writeStringToFile(launcherSplashFile, "<html><body>Loading...</body></html>", StandardCharsets.UTF_8);

        // Generate zip
        generator.generate(publishingContext, target);

        // Verify zip was created
        File zipFile = new File(releaseFilesDir, "jdeploy-files.zip");
        assertTrue(zipFile.exists(), "jdeploy-files.zip should be created");
        assertTrue(zipFile.length() > 0, "zip file should not be empty");

        // Verify zip contents
        try (ZipFile zip = new ZipFile(zipFile)) {
            // Check directory entry
            assertNotNull(zip.getEntry("test-app-1.0.0/.jdeploy-files/"), "Directory entry should exist");

            // Check required files
            ZipEntry appXml = zip.getEntry("test-app-1.0.0/.jdeploy-files/app.xml");
            assertNotNull(appXml, "app.xml should exist in zip");

            ZipEntry icon = zip.getEntry("test-app-1.0.0/.jdeploy-files/icon.png");
            assertNotNull(icon, "icon.png should exist in zip");

            // Check optional files
            ZipEntry splash = zip.getEntry("test-app-1.0.0/.jdeploy-files/installsplash.png");
            assertNotNull(splash, "installsplash.png should exist in zip");

            ZipEntry launcherSplash = zip.getEntry("test-app-1.0.0/.jdeploy-files/launcher-splash.html");
            assertNotNull(launcherSplash, "launcher-splash.html should exist in zip");

            // Verify app.xml content
            String appXmlContent = readStreamToString(zip.getInputStream(appXml));
            assertTrue(appXmlContent.contains("package='test-app'"), "app.xml should contain package name");
            assertTrue(appXmlContent.contains("version='1.0.0'"), "app.xml should contain version");
            assertTrue(appXmlContent.contains("icon='data:image/png;base64,"), "app.xml should contain base64 icon");
            assertTrue(appXmlContent.contains("fork='false'"), "app.xml should contain fork='false'");
            assertTrue(appXmlContent.contains("prerelease='false'"), "app.xml should contain prerelease='false'");
        }
    }

    @Test
    @DisplayName("Should work with minimal files (no optional assets)")
    void shouldWorkWithMinimalFiles() throws IOException {
        // Create package.json
        String packageJson = "{"
                + "\"name\":\"test-app\","
                + "\"version\":\"1.0.0\","
                + "\"repository\":{\"url\":\"https://github.com/user/test-app\"}"
                + "}";
        FileUtils.writeStringToFile(packageJsonFile, packageJson, StandardCharsets.UTF_8);

        // Create only required icon.png
        byte[] pngData = createMinimalPng();
        File iconFile = new File(tempDir, "icon.png");
        Files.write(iconFile.toPath(), pngData);

        // Generate zip
        generator.generate(publishingContext, target);

        // Verify zip was created
        File zipFile = new File(releaseFilesDir, "jdeploy-files.zip");
        assertTrue(zipFile.exists(), "jdeploy-files.zip should be created");

        // Verify zip contains only required files
        try (ZipFile zip = new ZipFile(zipFile)) {
            assertNotNull(zip.getEntry("test-app-1.0.0/.jdeploy-files/app.xml"), "app.xml should exist");
            assertNotNull(zip.getEntry("test-app-1.0.0/.jdeploy-files/icon.png"), "icon.png should exist");
            assertNull(zip.getEntry("test-app-1.0.0/.jdeploy-files/installsplash.png"), "installsplash.png should not exist");
            assertNull(zip.getEntry("test-app-1.0.0/.jdeploy-files/launcher-splash.html"), "launcher-splash.html should not exist");
        }
    }

    @Test
    @DisplayName("Should fail if icon.png is missing")
    void shouldFailIfIconMissing() throws IOException {
        // Create package.json without icon
        String packageJson = "{"
                + "\"name\":\"test-app\","
                + "\"version\":\"1.0.0\""
                + "}";
        FileUtils.writeStringToFile(packageJsonFile, packageJson, StandardCharsets.UTF_8);

        // Attempt to generate zip without icon
        IOException exception = assertThrows(IOException.class, () -> {
            generator.generate(publishingContext, target);
        });

        assertTrue(exception.getMessage().contains("icon.png"), "Error message should mention icon.png");
    }

    @Test
    @DisplayName("Should detect prerelease versions")
    void shouldDetectPrereleaseVersions() throws IOException {
        // Create package.json with prerelease version
        String packageJson = "{"
                + "\"name\":\"test-app\","
                + "\"version\":\"1.0.0-beta.1\","
                + "\"repository\":{\"url\":\"https://github.com/user/test-app\"}"
                + "}";
        FileUtils.writeStringToFile(packageJsonFile, packageJson, StandardCharsets.UTF_8);

        // Create icon
        byte[] pngData = createMinimalPng();
        File iconFile = new File(tempDir, "icon.png");
        Files.write(iconFile.toPath(), pngData);

        // Generate zip
        generator.generate(publishingContext, target);

        // Verify prerelease flag in app.xml
        File zipFile = new File(releaseFilesDir, "jdeploy-files.zip");
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry appXml = zip.getEntry("test-app-1.0.0-beta.1/.jdeploy-files/app.xml");
            String appXmlContent = readStreamToString(zip.getInputStream(appXml));
            assertTrue(appXmlContent.contains("prerelease='true'"), "Prerelease version should be marked as prerelease");
        }
    }

    @Test
    @DisplayName("Should use jdeploy.title if available")
    void shouldUseJdeployTitle() throws IOException {
        // Create package.json with jdeploy.title
        String packageJson = "{"
                + "\"name\":\"test-app\","
                + "\"version\":\"1.0.0\","
                + "\"jdeploy\":{\"title\":\"My Awesome App\"}"
                + "}";
        FileUtils.writeStringToFile(packageJsonFile, packageJson, StandardCharsets.UTF_8);

        // Create icon
        byte[] pngData = createMinimalPng();
        File iconFile = new File(tempDir, "icon.png");
        Files.write(iconFile.toPath(), pngData);

        // Generate zip
        generator.generate(publishingContext, target);

        // Verify title in app.xml
        File zipFile = new File(releaseFilesDir, "jdeploy-files.zip");
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry appXml = zip.getEntry("test-app-1.0.0/.jdeploy-files/app.xml");
            String appXmlContent = readStreamToString(zip.getInputStream(appXml));
            assertTrue(appXmlContent.contains("title='My Awesome App'"), "Should use jdeploy.title");
        }
    }

    @Test
    @DisplayName("Should escape XML special characters")
    void shouldEscapeXmlCharacters() throws IOException {
        // Create package.json with special characters
        String packageJson = "{"
                + "\"name\":\"test&app\","
                + "\"version\":\"1.0.0\","
                + "\"jdeploy\":{\"title\":\"Test <App> & \\\"Quotes\\\"\"}"
                + "}";
        FileUtils.writeStringToFile(packageJsonFile, packageJson, StandardCharsets.UTF_8);

        // Create icon
        byte[] pngData = createMinimalPng();
        File iconFile = new File(tempDir, "icon.png");
        Files.write(iconFile.toPath(), pngData);

        // Generate zip
        generator.generate(publishingContext, target);

        // Verify XML escaping in app.xml
        File zipFile = new File(releaseFilesDir, "jdeploy-files.zip");
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry appXml = zip.getEntry("test&app-1.0.0/.jdeploy-files/app.xml");
            String appXmlContent = readStreamToString(zip.getInputStream(appXml));
            assertTrue(appXmlContent.contains("&amp;"), "Should escape ampersand");
            assertTrue(appXmlContent.contains("&lt;"), "Should escape less-than");
            assertTrue(appXmlContent.contains("&gt;"), "Should escape greater-than");
            assertTrue(appXmlContent.contains("&quot;"), "Should escape quotes");
        }
    }

    /**
     * Creates a minimal valid PNG image (1x1 transparent pixel).
     */
    private byte[] createMinimalPng() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR chunk
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89,
                0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01, // IDAT chunk
                0x0D, 0x0A, 0x2D, (byte) 0xB4,
                0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82 // IEND chunk
        };
    }

    /**
     * Reads an InputStream to a String (Java 8 compatible).
     */
    private String readStreamToString(java.io.InputStream is) throws IOException {
        java.io.ByteArrayOutputStream result = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8.name());
    }
}

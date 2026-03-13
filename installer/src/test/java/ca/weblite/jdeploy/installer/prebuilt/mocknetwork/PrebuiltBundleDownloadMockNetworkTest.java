package ca.weblite.jdeploy.installer.prebuilt.mocknetwork;

import ca.weblite.jdeploy.installer.npm.NPMPackageVersion;
import ca.weblite.jdeploy.installer.prebuilt.PrebuiltArtifactInfo;
import ca.weblite.jdeploy.installer.prebuilt.PrebuiltBundleDownloader;
import ca.weblite.jdeploy.installer.views.InstallationForm;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Mock-network integration tests for pre-built bundle download.
 *
 * These tests use WireMock (running in Docker via docker-compose) to serve
 * real JAR files over HTTP, then verify PrebuiltBundleDownloader correctly:
 * - Downloads the JAR from the artifact URL
 * - Verifies SHA-256 integrity
 * - Extracts the native bundle from the JAR
 * - Replaces the installed app with the extracted bundle
 * - Handles errors (hash mismatch, HTTP errors, redirects)
 *
 * Start services with: ./test-infra/run-mock-network-tests.sh --up
 * Set env: GITHUB_BASE_URL=http://localhost:8080
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PrebuiltBundleDownloadMockNetworkTest {

    private static final String ENV_GITHUB_BASE_URL = "GITHUB_BASE_URL";

    @TempDir
    Path tempDir;

    private WireMockAdminClient wireMock;
    private String wireMockBaseUrl;

    @BeforeEach
    void setUp() {
        wireMockBaseUrl = System.getenv(ENV_GITHUB_BASE_URL);
        if (wireMockBaseUrl == null || wireMockBaseUrl.isEmpty()) {
            wireMockBaseUrl = System.getProperty("wiremock.url");
        }
        Assumptions.assumeTrue(
                wireMockBaseUrl != null && !wireMockBaseUrl.isEmpty(),
                "Skipping: " + ENV_GITHUB_BASE_URL + " not set (run docker-compose up first)"
        );

        wireMock = new WireMockAdminClient(wireMockBaseUrl);
        Assumptions.assumeTrue(wireMock.isAvailable(), "Skipping: WireMock not reachable at " + wireMockBaseUrl);

        try {
            wireMock.resetRequestJournal();
        } catch (IOException e) {
            fail("Failed to reset WireMock request journal: " + e.getMessage());
        }
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) {
            wireMock.removeAllDynamicStubs();
        }
    }

    // ========================================================================
    // Test helpers
    // ========================================================================

    /**
     * Creates a bundle JAR containing a single file entry (simulates a Windows .exe or Linux binary).
     */
    private byte[] createBundleJarWithSingleFile(String entryName, byte[] entryContent) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JarOutputStream jos = new JarOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(entryName);
            jos.putNextEntry(entry);
            jos.write(entryContent);
            jos.closeEntry();
        }
        return baos.toByteArray();
    }

    /**
     * Creates a bundle JAR containing a macOS .app directory structure.
     */
    private byte[] createBundleJarWithAppBundle(String appName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JarOutputStream jos = new JarOutputStream(baos)) {
            // .app directory
            jos.putNextEntry(new ZipEntry(appName + ".app/"));
            jos.closeEntry();

            jos.putNextEntry(new ZipEntry(appName + ".app/Contents/"));
            jos.closeEntry();

            jos.putNextEntry(new ZipEntry(appName + ".app/Contents/MacOS/"));
            jos.closeEntry();

            // Launcher binary
            jos.putNextEntry(new ZipEntry(appName + ".app/Contents/MacOS/Client4JLauncher"));
            jos.write("#!/bin/bash\necho prebuilt-launcher".getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();

            // Info.plist
            jos.putNextEntry(new ZipEntry(appName + ".app/Contents/Info.plist"));
            jos.write("<?xml version=\"1.0\"?><plist><dict></dict></plist>".getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();

            // app.xml
            jos.putNextEntry(new ZipEntry(appName + ".app/Contents/app.xml"));
            jos.write("<app><title>PrebuiltApp</title></app>".getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
        return baos.toByteArray();
    }

    private String computeSha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * Creates an NPMPackageVersion with artifact entries pointing to the given URLs.
     */
    private NPMPackageVersion createPackageVersionWithArtifact(
            String platformKey, String url, String sha256
    ) {
        return createPackageVersionWithArtifact(platformKey, url, sha256, null, null);
    }

    private NPMPackageVersion createPackageVersionWithArtifact(
            String platformKey, String url, String sha256,
            String cliUrl, String cliSha256
    ) {
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");

        JSONObject jdeploy = new JSONObject();
        JSONObject artifacts = new JSONObject();
        JSONObject platformArtifact = new JSONObject();
        platformArtifact.put("enabled", true);
        platformArtifact.put("url", url);
        platformArtifact.put("sha256", sha256);

        if (cliUrl != null && cliSha256 != null) {
            JSONObject cli = new JSONObject();
            cli.put("url", cliUrl);
            cli.put("sha256", cliSha256);
            platformArtifact.put("cli", cli);
        }

        artifacts.put(platformKey, platformArtifact);
        jdeploy.put("artifacts", artifacts);
        packageJson.put("jdeploy", jdeploy);

        return NPMPackageVersion.fromLocalPackageJson(packageJson);
    }

    private InstallationForm mockForm() {
        return mock(InstallationForm.class);
    }

    // ========================================================================
    // Tests
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("Connectivity: WireMock is reachable")
    void wireMockIsReachable() {
        assertTrue(wireMock.isAvailable(), "WireMock should be reachable");
    }

    @Test
    @Order(10)
    @DisplayName("Download replaces installed binary with pre-built bundle (single file)")
    void downloadReplacesInstalledBinaryWithPrebuiltBundle() throws Exception {
        // Create a bundle JAR with a fake .exe
        byte[] prebuiltExeContent = "MZ-prebuilt-signed-binary-content".getBytes(StandardCharsets.UTF_8);
        byte[] bundleJar = createBundleJarWithSingleFile("MyApp.exe", prebuiltExeContent);
        String sha256 = computeSha256(bundleJar);

        // Serve it via WireMock
        String urlPath = "/releases/download/v1.0.0/test-app-win-x64-1.0.0.jar";
        wireMock.stubGetBinaryContent(urlPath, bundleJar, "application/java-archive");

        // Create a fake "installed" exe to be replaced
        File installedExe = tempDir.resolve("MyApp.exe").toFile();
        Files.write(installedExe.toPath(), "original-generated-binary".getBytes(StandardCharsets.UTF_8));
        assertTrue(installedExe.exists());

        // Create NPMPackageVersion with artifact
        String fullUrl = wireMockBaseUrl + urlPath;
        NPMPackageVersion version = createPackageVersionWithArtifact("win-x64", fullUrl, sha256);

        // Run the downloader
        PrebuiltBundleDownloader downloader = new PrebuiltBundleDownloader();
        boolean result = downloader.downloadAndReplace(version, "win-x64", installedExe, mockForm());

        // Verify
        assertTrue(result, "Download and replace should succeed");
        assertTrue(installedExe.exists(), "Installed exe should still exist");
        String newContent = new String(Files.readAllBytes(installedExe.toPath()), StandardCharsets.UTF_8);
        assertEquals("MZ-prebuilt-signed-binary-content", newContent,
                "Installed exe should contain pre-built content");

        // Verify WireMock received the download request
        wireMock.verifyRequestMade("GET", urlPath);
    }

    @Test
    @Order(20)
    @DisplayName("Download replaces installed .app bundle with pre-built macOS bundle")
    void downloadReplacesInstalledAppBundle() throws Exception {
        // Create a bundle JAR with a .app structure
        byte[] bundleJar = createBundleJarWithAppBundle("MyApp");
        String sha256 = computeSha256(bundleJar);

        // Serve it via WireMock
        String urlPath = "/releases/download/v1.0.0/test-app-mac-arm64-1.0.0.jar";
        wireMock.stubGetBinaryContent(urlPath, bundleJar, "application/java-archive");

        // Create a fake installed .app directory to be replaced
        File installedApp = tempDir.resolve("MyApp.app").toFile();
        File macosDir = new File(installedApp, "Contents/MacOS");
        macosDir.mkdirs();
        File oldLauncher = new File(macosDir, "Client4JLauncher");
        Files.write(oldLauncher.toPath(), "original-unsigned-launcher".getBytes(StandardCharsets.UTF_8));

        String fullUrl = wireMockBaseUrl + urlPath;
        NPMPackageVersion version = createPackageVersionWithArtifact("mac-arm64", fullUrl, sha256);

        PrebuiltBundleDownloader downloader = new PrebuiltBundleDownloader();
        boolean result = downloader.downloadAndReplace(version, "mac-arm64", installedApp, mockForm());

        assertTrue(result, "Download and replace should succeed");
        assertTrue(installedApp.exists(), "Installed .app should still exist");
        assertTrue(installedApp.isDirectory(), "Installed .app should be a directory");

        // Check the launcher was replaced with pre-built version
        File newLauncher = new File(installedApp, "Contents/MacOS/Client4JLauncher");
        assertTrue(newLauncher.exists(), "Launcher should exist in replaced .app");
        String launcherContent = new String(Files.readAllBytes(newLauncher.toPath()), StandardCharsets.UTF_8);
        assertTrue(launcherContent.contains("prebuilt-launcher"),
                "Launcher should contain pre-built content");

        // Check Info.plist was extracted
        File plist = new File(installedApp, "Contents/Info.plist");
        assertTrue(plist.exists(), "Info.plist should exist");

        // Check app.xml was extracted
        File appXml = new File(installedApp, "Contents/app.xml");
        assertTrue(appXml.exists(), "app.xml should exist");
    }

    @Test
    @Order(30)
    @DisplayName("Download fails gracefully on SHA-256 mismatch (keeps original)")
    void sha256MismatchKeepsOriginal() throws Exception {
        byte[] bundleJar = createBundleJarWithSingleFile("MyApp.exe",
                "some-binary-content".getBytes(StandardCharsets.UTF_8));

        String urlPath = "/releases/download/v1.0.0/bad-hash-bundle.jar";
        wireMock.stubGetBinaryContent(urlPath, bundleJar, "application/java-archive");

        // Create installed exe
        File installedExe = tempDir.resolve("MyApp.exe").toFile();
        byte[] originalContent = "original-binary".getBytes(StandardCharsets.UTF_8);
        Files.write(installedExe.toPath(), originalContent);

        // Use a WRONG sha256
        String wrongSha256 = "0000000000000000000000000000000000000000000000000000000000000000";
        String fullUrl = wireMockBaseUrl + urlPath;
        NPMPackageVersion version = createPackageVersionWithArtifact("win-x64", fullUrl, wrongSha256);

        PrebuiltBundleDownloader downloader = new PrebuiltBundleDownloader();
        boolean result = downloader.downloadAndReplace(version, "win-x64", installedExe, mockForm());

        assertFalse(result, "Should fail due to SHA-256 mismatch");
        assertTrue(installedExe.exists(), "Original exe should still exist");
        assertArrayEquals(originalContent, Files.readAllBytes(installedExe.toPath()),
                "Original content should be preserved");
    }

    @Test
    @Order(40)
    @DisplayName("Download fails gracefully on HTTP 404 (keeps original)")
    void http404KeepsOriginal() throws Exception {
        String urlPath = "/releases/download/v1.0.0/nonexistent-bundle.jar";
        wireMock.stubGetError(urlPath, 404);

        File installedExe = tempDir.resolve("MyApp.exe").toFile();
        byte[] originalContent = "original-binary".getBytes(StandardCharsets.UTF_8);
        Files.write(installedExe.toPath(), originalContent);

        String fullUrl = wireMockBaseUrl + urlPath;
        NPMPackageVersion version = createPackageVersionWithArtifact("win-x64", fullUrl, "abcdef1234567890");

        PrebuiltBundleDownloader downloader = new PrebuiltBundleDownloader();
        boolean result = downloader.downloadAndReplace(version, "win-x64", installedExe, mockForm());

        assertFalse(result, "Should fail on HTTP 404");
        assertTrue(installedExe.exists(), "Original exe should still exist");
        assertArrayEquals(originalContent, Files.readAllBytes(installedExe.toPath()),
                "Original content should be preserved");
    }

    @Test
    @Order(50)
    @DisplayName("Download follows HTTP 302 redirect (like GitHub release URLs)")
    void followsRedirect() throws Exception {
        byte[] exeContent = "MZ-redirected-prebuilt".getBytes(StandardCharsets.UTF_8);
        byte[] bundleJar = createBundleJarWithSingleFile("MyApp.exe", exeContent);
        String sha256 = computeSha256(bundleJar);

        // Set up: initial URL returns 302, redirects to actual content
        String initialPath = "/releases/download/v1.0.0/redirect-bundle.jar";
        String actualPath = "/cdn/actual-bundle-file.jar";
        wireMock.stubGetRedirect(initialPath, wireMockBaseUrl + actualPath);
        wireMock.stubGetBinaryContent(actualPath, bundleJar, "application/java-archive");

        File installedExe = tempDir.resolve("MyApp.exe").toFile();
        Files.write(installedExe.toPath(), "original".getBytes(StandardCharsets.UTF_8));

        String fullUrl = wireMockBaseUrl + initialPath;
        NPMPackageVersion version = createPackageVersionWithArtifact("win-x64", fullUrl, sha256);

        PrebuiltBundleDownloader downloader = new PrebuiltBundleDownloader();
        boolean result = downloader.downloadAndReplace(version, "win-x64", installedExe, mockForm());

        assertTrue(result, "Should succeed after following redirect");
        String newContent = new String(Files.readAllBytes(installedExe.toPath()), StandardCharsets.UTF_8);
        assertEquals("MZ-redirected-prebuilt", newContent);

        // Both URLs should have been requested
        wireMock.verifyRequestMade("GET", initialPath);
        wireMock.verifyRequestMade("GET", actualPath);
    }

    @Test
    @Order(60)
    @DisplayName("No download attempted when artifacts section is missing")
    void noDownloadWhenNoArtifacts() throws Exception {
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");
        packageJson.put("jdeploy", new JSONObject());
        NPMPackageVersion version = NPMPackageVersion.fromLocalPackageJson(packageJson);

        File installedExe = tempDir.resolve("MyApp.exe").toFile();
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        Files.write(installedExe.toPath(), original);

        PrebuiltBundleDownloader downloader = new PrebuiltBundleDownloader();
        boolean result = downloader.downloadAndReplace(version, "win-x64", installedExe, mockForm());

        assertFalse(result, "Should return false when no artifacts");
        assertArrayEquals(original, Files.readAllBytes(installedExe.toPath()),
                "Original should be unchanged");
    }

    @Test
    @Order(70)
    @DisplayName("No download when artifact exists for different platform only")
    void noDownloadWhenWrongPlatform() throws Exception {
        NPMPackageVersion version = createPackageVersionWithArtifact(
                "mac-arm64",
                wireMockBaseUrl + "/some/url.jar",
                "somehash"
        );

        File installedExe = tempDir.resolve("MyApp.exe").toFile();
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        Files.write(installedExe.toPath(), original);

        PrebuiltBundleDownloader downloader = new PrebuiltBundleDownloader();
        boolean result = downloader.downloadAndReplace(version, "win-x64", installedExe, mockForm());

        assertFalse(result, "Should return false when platform doesn't match");
    }

    @Test
    @Order(80)
    @DisplayName("Progress UI is updated during download")
    void progressUiUpdatedDuringDownload() throws Exception {
        byte[] bundleJar = createBundleJarWithSingleFile("MyApp.exe",
                "prebuilt-content".getBytes(StandardCharsets.UTF_8));
        String sha256 = computeSha256(bundleJar);

        String urlPath = "/releases/download/v1.0.0/progress-test.jar";
        wireMock.stubGetBinaryContent(urlPath, bundleJar, "application/java-archive");

        File installedExe = tempDir.resolve("MyApp.exe").toFile();
        Files.write(installedExe.toPath(), "original".getBytes(StandardCharsets.UTF_8));

        String fullUrl = wireMockBaseUrl + urlPath;
        NPMPackageVersion version = createPackageVersionWithArtifact("win-x64", fullUrl, sha256);

        InstallationForm form = mockForm();

        PrebuiltBundleDownloader downloader = new PrebuiltBundleDownloader();
        downloader.downloadAndReplace(version, "win-x64", installedExe, form);

        // Verify progress was reported to the UI
        verify(form, atLeastOnce()).setInProgress(eq(true), anyString());
    }

    @Test
    @Order(90)
    @DisplayName("Enabled-only artifact (no url/sha256) is treated as no artifact")
    void enabledOnlyArtifactSkipped() throws Exception {
        // This simulates a source (unpublished) package.json where
        // artifacts have "enabled": true but no url/sha256 yet
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");
        JSONObject jdeploy = new JSONObject();
        JSONObject artifacts = new JSONObject();
        artifacts.put("win-x64", new JSONObject().put("enabled", true));
        jdeploy.put("artifacts", artifacts);
        packageJson.put("jdeploy", jdeploy);
        NPMPackageVersion version = NPMPackageVersion.fromLocalPackageJson(packageJson);

        File installedExe = tempDir.resolve("MyApp.exe").toFile();
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        Files.write(installedExe.toPath(), original);

        PrebuiltBundleDownloader downloader = new PrebuiltBundleDownloader();
        boolean result = downloader.downloadAndReplace(version, "win-x64", installedExe, mockForm());

        assertFalse(result, "Should skip when artifact only has enabled flag");
        assertArrayEquals(original, Files.readAllBytes(installedExe.toPath()));
    }

    @Test
    @Order(100)
    @DisplayName("HTTP 500 server error keeps original bundle intact")
    void http500KeepsOriginal() throws Exception {
        String urlPath = "/releases/download/v1.0.0/server-error-bundle.jar";
        wireMock.stubGetError(urlPath, 500);

        File installedExe = tempDir.resolve("MyApp.exe").toFile();
        byte[] original = "original-binary".getBytes(StandardCharsets.UTF_8);
        Files.write(installedExe.toPath(), original);

        String fullUrl = wireMockBaseUrl + urlPath;
        NPMPackageVersion version = createPackageVersionWithArtifact("win-x64", fullUrl, "abcdef");

        PrebuiltBundleDownloader downloader = new PrebuiltBundleDownloader();
        boolean result = downloader.downloadAndReplace(version, "win-x64", installedExe, mockForm());

        assertFalse(result, "Should fail on HTTP 500");
        assertArrayEquals(original, Files.readAllBytes(installedExe.toPath()),
                "Original content should be preserved");
    }
}

package ca.weblite.jdeploy.publishing;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.config.Config;
import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.packaging.PackagingConfig;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.publishTargets.PublishTarget;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;
import ca.weblite.jdeploy.publishing.github.GitHubPublishDriver;
import ca.weblite.jdeploy.publishing.npm.NPMPublishDriver;
import ca.weblite.jdeploy.factories.JDeployProjectFactory;
import ca.weblite.jdeploy.services.DefaultBundleService;
import ca.weblite.jdeploy.services.PlatformBundleGenerator;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.*;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Integration tests that run against mock network services (Verdaccio, WireMock).
 *
 * These tests verify the full publishing flow end-to-end by:
 * 1. Creating a real project structure with a JAR file
 * 2. Running the actual publish drivers (GitHubPublishDriver, NPMPublishDriver)
 * 3. Making real HTTP calls to mock services (WireMock for GitHub/jDeploy, Verdaccio for npm)
 * 4. Verifying the calls succeeded and correct artifacts were produced
 *
 * To run locally:
 *   cd test-infra && ./run-mock-network-tests.sh --up
 *   cd test-infra && ./run-mock-network-tests.sh --local
 *
 * Or run entirely in Docker:
 *   cd test-infra && ./run-mock-network-tests.sh
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MockNetworkPublishingTest extends BaseMockNetworkPublishingTest {

    /**
     * Dynamically register the publish.php stub via WireMock admin API.
     * This ensures the stub is present regardless of whether file-based loading succeeded.
     */
    private void ensurePublishStubRegistered() throws IOException {
        JSONObject responseBody = new JSONObject();
        responseBody.put("code", 200);
        jdeployWireMock.stubPostUrlMatching("/publish\\.php.*", 200, responseBody, 5);
    }

    // ========================================================================
    // Connectivity tests
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("Verdaccio npm registry is reachable")
    void verdaccioIsReachable() throws Exception {
        String registryUrl = getNpmRegistry();
        String pingUrl = registryUrl.endsWith("/") ? registryUrl + "-/ping" : registryUrl + "/-/ping";
        HttpURLConnection conn = (HttpURLConnection) new URL(pingUrl).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        assertEquals(200, conn.getResponseCode());
    }

    @Test
    @Order(2)
    @DisplayName("WireMock GitHub API is reachable")
    void wiremockGithubIsReachable() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(getGithubApiBaseUrl() + "/__admin/mappings").openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        assertEquals(200, conn.getResponseCode());
    }

    @Test
    @Order(3)
    @DisplayName("WireMock jDeploy registry is reachable")
    void wiremockJdeployIsReachable() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(getJdeployRegistryBaseUrl() + "/__admin/mappings").openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        assertEquals(200, conn.getResponseCode());
    }

    // ========================================================================
    // GitHub publish flow tests
    // ========================================================================

    @Test
    @Order(10)
    @DisplayName("GitHub publish: full prepare + publish flow against WireMock")
    void githubFullPrepareAndPublishFlow() throws Exception {
        File projectDir = scaffoldProject(
                "github-project", "test-github-app", "1.0.0",
                "test-app-1.0.jar", "com.example.TestApp"
        );

        GitHubPublishDriver githubDriver = createGitHubPublishDriver();
        PublishingContext publishingContext = createPublishingContext(projectDir);
        PublishTargetInterface target = createGitHubTarget("testuser", "testrepo");

        // --- Prepare ---
        githubDriver.prepare(publishingContext, target, new BundlerSettings());

        File releaseFilesDir = publishingContext.getGithubReleaseFilesDir();
        assertTrue(releaseFilesDir.exists(), "GitHub release files directory should exist");

        File packageInfoFile = new File(releaseFilesDir, "package-info.json");
        assertTrue(packageInfoFile.exists(), "package-info.json should be generated");

        String packageInfoContent = FileUtils.readFileToString(packageInfoFile, StandardCharsets.UTF_8);
        JSONObject packageInfo = new JSONObject(packageInfoContent);
        assertTrue(packageInfo.has("versions"), "package-info.json should have versions");
        assertTrue(packageInfo.getJSONObject("versions").has("1.0.0"),
                "package-info.json should contain version 1.0.0");

        File releaseNotes = new File(releaseFilesDir, "jdeploy-release-notes.md");
        assertTrue(releaseNotes.exists(), "Release notes should be generated");

        // --- Publish ---
        githubDriver.publish(publishingContext, target, null);

        // --- Verify WireMock received expected requests ---
        githubWireMock.verifyRequestMade("POST", "/repos/.*/releases");
        jdeployWireMock.verifyRequestMade("GET", "/register\\.php.*");
    }

    @Test
    @Order(14)
    @DisplayName("jDeploy publish endpoint accepts POST via HttpURLConnection")
    void jdeployPublishEndpointAcceptsPostViaUrlConnection() throws Exception {
        // Ensure the publish.php stub is registered dynamically (belt and suspenders)
        ensurePublishStubRegistered();

        // First test with plain HttpURLConnection (same as register.php uses)
        String url = getJdeployRegistry() + "publish.php";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write("{\"test\": true}".getBytes(StandardCharsets.UTF_8));
        }
        int statusCode = conn.getResponseCode();
        String body = "";
        try (InputStream is = statusCode >= 200 && statusCode < 300 ? conn.getInputStream() : conn.getErrorStream()) {
            if (is != null) {
                byte[] bytes = new byte[4096];
                int len = is.read(bytes);
                if (len > 0) body = new String(bytes, 0, len, StandardCharsets.UTF_8);
            }
        }
        System.out.println("[TEST-DIAG] HttpURLConnection POST to " + url +
                " -> " + statusCode + " " + conn.getResponseMessage() + ": " + body);
        assertEquals(200, statusCode,
                "publish.php via HttpURLConnection should return 200. Got " + statusCode +
                " (" + conn.getResponseMessage() + "): " + body);

        jdeployWireMock.verifyRequestMade("POST", "/publish\\.php.*");
    }

    @Test
    @Order(15)
    @DisplayName("jDeploy publish endpoint accepts POST via Apache HttpClient")
    void jdeployPublishEndpointAcceptsPost() throws Exception {
        // Ensure the publish.php stub is registered dynamically
        ensurePublishStubRegistered();

        // Test with Apache HttpClient (same HTTP client used by ResourceUploader)
        String url = getJdeployRegistry() + "publish.php";
        System.out.println("[TEST-DIAG] POSTing to: " + url);

        // List all current stubs
        HttpURLConnection adminConn = (HttpURLConnection)
                new URL(getJdeployRegistryBaseUrl() + "/__admin/mappings").openConnection();
        adminConn.setConnectTimeout(5000);
        adminConn.setReadTimeout(5000);
        try (InputStream is = adminConn.getInputStream()) {
            byte[] bytes = new byte[8192];
            int len = is.read(bytes);
            if (len > 0) {
                System.out.println("[TEST-DIAG] Current jDeploy WireMock stubs: " +
                        new String(bytes, 0, len, StandardCharsets.UTF_8));
            }
        }

        CloseableHttpClient client = HttpClientBuilder.create().build();
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Content-Type", "application/json; charset='utf-8'");
        httpPost.setEntity(new StringEntity("{\"test\": true}"));

        try (CloseableHttpResponse response = client.execute(httpPost)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity());
            System.out.println("[TEST-DIAG] Apache HttpClient POST -> " + statusCode +
                    " " + response.getStatusLine().getReasonPhrase() + ": " + body);
            assertEquals(200, statusCode,
                    "publish.php should return 200. Got " + statusCode +
                    " (" + response.getStatusLine().getReasonPhrase() + "): " + body);
        }

        jdeployWireMock.verifyRequestMade("POST", "/publish\\.php.*");
    }

    @Test
    @Order(20)
    @DisplayName("GitHub publish: resource upload to mock jDeploy registry")
    void githubPublishResourceUpload() throws Exception {
        // Ensure publish.php stub is registered
        ensurePublishStubRegistered();

        File projectDir = new File(tempDir, "resource-upload-project");
        projectDir.mkdirs();

        createPackageJson(projectDir, "test-resource-app", "1.0.0", "test.jar", null);
        createIcon(projectDir);

        File publishDir = new File(projectDir, "jdeploy" + File.separator + "publish");
        publishDir.mkdirs();
        JSONObject publishPackageJson = new JSONObject();
        publishPackageJson.put("name", "test-resource-app");
        publishPackageJson.put("version", "1.0.0");
        FileUtils.writeStringToFile(
                new File(publishDir, "package.json"),
                publishPackageJson.toString(),
                StandardCharsets.UTF_8
        );

        Config config = createMockConfig();
        PackagingConfig packagingConfig = new PackagingConfig(config);
        ResourceUploader uploader = new ResourceUploader(packagingConfig);

        PublishingContext publishingContext = createPublishingContext(projectDir);

        uploader.uploadResources(publishingContext);

        // Verify the upload hit the mock jDeploy registry
        jdeployWireMock.verifyRequestMade("POST", "/publish\\.php.*");
    }

    // ========================================================================
    // NPM publish flow tests
    // ========================================================================

    @Test
    @Order(30)
    @DisplayName("NPM: version check against Verdaccio")
    void npmVersionCheckAgainstVerdaccio() throws Exception {
        NPM npm = new NPM(System.out, System.err);
        npm.setRegistryUrl(getNpmRegistry());

        assertFalse(npm.isVersionPublished("nonexistent-package", "1.0.0", "npm"),
                "Non-existent package should not be published");
    }

    @Test
    @Order(31)
    @DisplayName("NPM: publish to Verdaccio and verify")
    void npmPublishToVerdaccioAndVerify() throws Exception {
        File publishDir = new File(tempDir, "npm-publish");
        publishDir.mkdirs();

        String packageName = "jdeploy-mock-test-" + System.currentTimeMillis();

        JSONObject packageJson = new JSONObject();
        packageJson.put("name", packageName);
        packageJson.put("version", "1.0.0");
        packageJson.put("description", "Mock network publish test");

        FileUtils.writeStringToFile(
                new File(publishDir, "package.json"),
                packageJson.toString(2),
                StandardCharsets.UTF_8
        );
        FileUtils.writeStringToFile(
                new File(publishDir, "index.js"),
                "module.exports = {};",
                StandardCharsets.UTF_8
        );

        NPM npm = new NPM(System.out, System.err);
        npm.setRegistryUrl(getNpmRegistry());

        npm.publish(publishDir, false, null, null);

        assertTrue(npm.isVersionPublished(packageName, "1.0.0", "npm"),
                "Published package should be retrievable from Verdaccio");
    }

    @Test
    @Order(32)
    @DisplayName("NPM: NPMPublishDriver version check uses configurable registry")
    void npmPublishDriverVersionCheck() throws Exception {
        BasePublishDriver baseDriver = mock(BasePublishDriver.class);
        PlatformBundleGenerator platformBundleGenerator = mock(PlatformBundleGenerator.class);
        DefaultBundleService defaultBundleService = mock(DefaultBundleService.class);
        JDeployProjectFactory projectFactory = mock(JDeployProjectFactory.class);

        NPMPublishDriver npmDriver = new NPMPublishDriver(
                baseDriver, platformBundleGenerator, defaultBundleService, projectFactory
        );
        npmDriver.setRegistryUrl(getNpmRegistry());

        PublishTargetInterface target = new PublishTarget("npm", PublishTargetType.NPM, getNpmRegistry());

        boolean published = npmDriver.isVersionPublished("nonexistent-pkg", "1.0.0", target);
        assertFalse(published);
    }

    // ========================================================================
    // Full end-to-end test
    // ========================================================================

    @Test
    @Order(50)
    @DisplayName("End-to-end: GitHub prepare + publish + jDeploy resource upload")
    void endToEndGithubPublishWithResourceUpload() throws Exception {
        // Ensure publish.php stub is registered
        ensurePublishStubRegistered();
        File projectDir = scaffoldProject(
                "e2e-project", "e2e-test-app", "2.0.0",
                "myapp-2.0.0.jar", "com.example.MyApp"
        );

        GitHubPublishDriver githubDriver = createGitHubPublishDriver();
        PublishingContext publishingContext = createPublishingContext(projectDir);
        PublishTargetInterface target = createGitHubTarget("e2euser", "e2erepo");

        // --- Step 1: Prepare ---
        githubDriver.prepare(publishingContext, target, new BundlerSettings());

        File releaseFilesDir = publishingContext.getGithubReleaseFilesDir();
        File packageInfoFile = new File(releaseFilesDir, "package-info.json");
        assertTrue(packageInfoFile.exists());

        JSONObject packageInfo = new JSONObject(
                FileUtils.readFileToString(packageInfoFile, StandardCharsets.UTF_8)
        );
        assertTrue(packageInfo.getJSONObject("versions").has("2.0.0"),
                "package-info.json should have version 2.0.0");
        assertEquals("2.0.0",
                packageInfo.optJSONObject("dist-tags") != null
                        ? packageInfo.getJSONObject("dist-tags").optString("latest")
                        : "",
                "dist-tags.latest should be 2.0.0");

        // --- Step 2: Publish ---
        githubDriver.publish(publishingContext, target, null);

        // --- Step 3: Upload resources ---
        Config config = createMockConfig();
        PackagingConfig packagingConfig = new PackagingConfig(config);
        ResourceUploader resourceUploader = new ResourceUploader(packagingConfig);
        resourceUploader.uploadResources(publishingContext);

        // --- Verify WireMock received expected requests ---
        githubWireMock.verifyRequestMade("POST", "/repos/e2euser/e2erepo/releases");
        jdeployWireMock.verifyRequestMade("GET", "/register\\.php.*");
        jdeployWireMock.verifyRequestMade("POST", "/publish\\.php.*");
    }
}

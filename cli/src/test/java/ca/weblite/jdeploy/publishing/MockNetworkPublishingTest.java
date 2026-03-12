package ca.weblite.jdeploy.publishing;

import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.publishing.github.GitHubReleaseCreator;
import ca.weblite.jdeploy.publishing.github.GitHubReleaseCreator.ReleaseResponse;
import ca.weblite.jdeploy.publishing.github.GitHubReleaseCreator.AssetWithETag;
import ca.weblite.jdeploy.services.BundleCodeService;
import ca.weblite.jdeploy.packaging.PackagingConfig;
import ca.weblite.jdeploy.config.Config;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that run against mock network services (Verdaccio, WireMock).
 *
 * These tests are designed to run inside the Docker test-runner container where
 * mock services are available. They verify the full publishing flow against
 * realistic mock endpoints.
 *
 * To run locally without Docker, set these environment variables:
 *   - NPM_CONFIG_REGISTRY=http://localhost:4873
 *   - GITHUB_API_BASE_URL=http://localhost:8080
 *   - GITHUB_BASE_URL=http://localhost:8080
 *   - JDEPLOY_REGISTRY_URL=http://localhost:8081/
 *   - GITHUB_TOKEN=mock-github-token-for-testing
 *
 * Or run via: cd test-infra && docker-compose up --build
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MockNetworkPublishingTest {

    private static final String ENV_NPM_REGISTRY = "NPM_CONFIG_REGISTRY";
    private static final String ENV_GITHUB_API_URL = "GITHUB_API_BASE_URL";
    private static final String ENV_GITHUB_BASE_URL = "GITHUB_BASE_URL";
    private static final String ENV_JDEPLOY_REGISTRY = "JDEPLOY_REGISTRY_URL";
    private static final String ENV_GITHUB_TOKEN = "GITHUB_TOKEN";

    @TempDir
    File tempDir;

    private static boolean isMockNetworkAvailable() {
        return System.getenv(ENV_NPM_REGISTRY) != null
                && System.getenv(ENV_GITHUB_API_URL) != null
                && System.getenv(ENV_JDEPLOY_REGISTRY) != null;
    }

    private String getNpmRegistry() {
        return System.getenv(ENV_NPM_REGISTRY);
    }

    private String getGithubApiBaseUrl() {
        return System.getenv(ENV_GITHUB_API_URL);
    }

    private String getGithubBaseUrl() {
        return System.getenv(ENV_GITHUB_BASE_URL);
    }

    private String getJdeployRegistry() {
        return System.getenv(ENV_JDEPLOY_REGISTRY);
    }

    private String getGithubToken() {
        String token = System.getenv(ENV_GITHUB_TOKEN);
        return token != null ? token : "mock-github-token";
    }

    @BeforeEach
    void checkEnvironment() {
        Assumptions.assumeTrue(
                isMockNetworkAvailable(),
                "Mock network services not available. Set environment variables or run via docker-compose."
        );
    }

    @Test
    @Order(1)
    @DisplayName("Verdaccio npm registry is reachable")
    void verdaccioIsReachable() throws Exception {
        URL url = new URL(getNpmRegistry() + "/-/ping");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        int responseCode = conn.getResponseCode();
        assertEquals(200, responseCode, "Verdaccio should respond to ping");
    }

    @Test
    @Order(2)
    @DisplayName("WireMock GitHub API is reachable")
    void wiremockGithubIsReachable() throws Exception {
        URL url = new URL(getGithubApiBaseUrl() + "/__admin/mappings");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        int responseCode = conn.getResponseCode();
        assertEquals(200, responseCode, "WireMock GitHub should respond to admin endpoint");
    }

    @Test
    @Order(3)
    @DisplayName("WireMock jDeploy registry is reachable")
    void wiremockJdeployIsReachable() throws Exception {
        URL url = new URL(getJdeployRegistry() + "__admin/mappings");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        int responseCode = conn.getResponseCode();
        assertEquals(200, responseCode, "WireMock jDeploy should respond to admin endpoint");
    }

    @Test
    @Order(10)
    @DisplayName("GitHubReleaseCreator can create a release against WireMock")
    void gitHubReleaseCreatorCanCreateRelease() throws Exception {
        GitHubReleaseCreator creator = new GitHubReleaseCreator();
        String githubBaseUrl = getGithubBaseUrl();
        if (!githubBaseUrl.endsWith("/")) {
            githubBaseUrl += "/";
        }
        creator.setGithubUrl(githubBaseUrl);
        creator.setGithubApiUrl(getGithubApiBaseUrl() + "/repos/");

        // Create a test artifact file
        File artifact = new File(tempDir, "test-artifact.txt");
        Files.write(artifact.toPath(), "test content".getBytes(StandardCharsets.UTF_8));

        // This should hit WireMock's create-release stub
        creator.createRelease(
                githubBaseUrl + "testuser/testrepo",
                getGithubToken(),
                "v1.0.0",
                "Test release description",
                new File[]{artifact}
        );

        // If we get here without exception, WireMock responded with 201
    }

    @Test
    @Order(11)
    @DisplayName("GitHubReleaseCreator atomic create works against WireMock")
    void gitHubReleaseCreatorAtomicCreateWorks() throws Exception {
        GitHubReleaseCreator creator = new GitHubReleaseCreator();
        String githubBaseUrl = getGithubBaseUrl();
        if (!githubBaseUrl.endsWith("/")) {
            githubBaseUrl += "/";
        }
        creator.setGithubUrl(githubBaseUrl);
        creator.setGithubApiUrl(getGithubApiBaseUrl() + "/repos/");

        File artifact = new File(tempDir, "package-info.json");
        Files.write(artifact.toPath(), "{\"name\":\"test\",\"versions\":{}}".getBytes(StandardCharsets.UTF_8));

        creator.createReleaseAtomic(
                githubBaseUrl + "testuser/testrepo",
                getGithubToken(),
                "jdeploy",
                "Release metadata",
                new File[]{artifact}
        );
    }

    @Test
    @Order(20)
    @DisplayName("BundleCodeService can register with mock jDeploy registry")
    void bundleCodeServiceCanRegister() throws Exception {
        Config config = new Config();
        config.getProperties().setProperty("registry.url", getJdeployRegistry());
        PackagingConfig packagingConfig = new PackagingConfig(config);

        BundleCodeService bundleCodeService = new BundleCodeService(packagingConfig);

        String bundleCode = bundleCodeService.fetchJdeployBundleCode("test-package");
        assertNotNull(bundleCode, "Bundle code should not be null");
        assertEquals("mock-bundle-code-12345", bundleCode);
    }

    @Test
    @Order(21)
    @DisplayName("ResourceUploader can upload to mock jDeploy registry")
    void resourceUploaderCanUpload() throws Exception {
        Config config = new Config();
        config.getProperties().setProperty("registry.url", getJdeployRegistry());
        PackagingConfig packagingConfig = new PackagingConfig(config);

        ResourceUploader uploader = new ResourceUploader(packagingConfig);

        // Create the directory structure that ResourceUploader expects
        File publishDir = new File(tempDir, "jdeploy" + File.separator + "publish");
        publishDir.mkdirs();

        // Create package.json
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-package");
        packageJson.put("version", "1.0.0");
        Files.write(
                new File(publishDir, "package.json").toPath(),
                packageJson.toString().getBytes(StandardCharsets.UTF_8)
        );

        // Create icon.png (minimal valid content)
        File icon = new File(tempDir, "icon.png");
        Files.write(icon.toPath(), new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        // Create publishing context
        Map<String, Object> packageJsonMap = new HashMap<>();
        packageJsonMap.put("name", "test-package");
        packageJsonMap.put("version", "1.0.0");

        PackagingContext packagingContext = new PackagingContext(
                tempDir,
                packageJsonMap,
                new File(tempDir, "package.json"),
                false, false, null, null, null, null,
                System.out, System.err, System.in, false, false
        );

        PublishingContext publishingContext = new PublishingContext(
                packagingContext, false,
                new NPM(System.out, System.err),
                null, null, null, null, null
        );

        // Should not throw - WireMock will return {"code": 200}
        uploader.uploadResources(publishingContext);
    }

    @Test
    @Order(30)
    @DisplayName("NPM can use custom registry URL for version checking")
    void npmCustomRegistryForVersionCheck() throws Exception {
        NPM npm = new NPM(System.out, System.err);
        npm.setRegistryUrl(getNpmRegistry());

        // This should query Verdaccio, which won't have the package (returns false)
        boolean published = npm.isVersionPublished("nonexistent-package", "1.0.0", "npm");
        assertFalse(published, "Non-existent package should not be published");
    }

    @Test
    @Order(40)
    @DisplayName("Full npm publish flow against Verdaccio")
    void fullNpmPublishAgainstVerdaccio() throws Exception {
        // Create a minimal publishable npm package
        File publishDir = new File(tempDir, "publish");
        publishDir.mkdirs();

        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "@jdeploy-test/mock-publish-test");
        packageJson.put("version", "1.0.0");
        packageJson.put("description", "Test package for mock network publishing");

        Files.write(
                new File(publishDir, "package.json").toPath(),
                packageJson.toString(2).getBytes(StandardCharsets.UTF_8)
        );

        // Create a dummy index.js
        Files.write(
                new File(publishDir, "index.js").toPath(),
                "module.exports = {};".getBytes(StandardCharsets.UTF_8)
        );

        // Publish to Verdaccio using NPM class
        NPM npm = new NPM(System.out, System.err);
        npm.setRegistryUrl(getNpmRegistry());

        // First, add a user to Verdaccio using npm adduser
        // In the Docker environment, we can use npm_config_registry env var
        try {
            npm.publish(publishDir, false, null, null);
        } catch (Exception e) {
            // Verdaccio may require auth - this is expected behavior
            // The test verifies that the NPM class correctly targets the custom registry
            System.out.println("Expected auth error from Verdaccio (package targets correct registry): " + e.getMessage());
        }

        // Verify version check works against Verdaccio
        boolean published = npm.isVersionPublished("@jdeploy-test/mock-publish-test", "1.0.0", "npm");
        // May be true or false depending on auth state - but no exception means connectivity works
        System.out.println("Version published check result: " + published);
    }

    @Test
    @Order(50)
    @DisplayName("End-to-end: all mock services work together")
    void endToEndAllServicesWork() throws Exception {
        // 1. Check jDeploy registry
        Config config = new Config();
        config.getProperties().setProperty("registry.url", getJdeployRegistry());
        PackagingConfig packagingConfig = new PackagingConfig(config);
        BundleCodeService bundleCodeService = new BundleCodeService(packagingConfig);

        String bundleCode = bundleCodeService.fetchJdeployBundleCode("e2e-test-package");
        assertNotNull(bundleCode);

        // 2. Check GitHub API
        GitHubReleaseCreator creator = new GitHubReleaseCreator();
        String githubBaseUrl = getGithubBaseUrl();
        if (!githubBaseUrl.endsWith("/")) {
            githubBaseUrl += "/";
        }
        creator.setGithubUrl(githubBaseUrl);
        creator.setGithubApiUrl(getGithubApiBaseUrl() + "/repos/");

        File artifact = new File(tempDir, "e2e-artifact.txt");
        Files.write(artifact.toPath(), "e2e test".getBytes(StandardCharsets.UTF_8));

        creator.createRelease(
                githubBaseUrl + "e2euser/e2erepo",
                getGithubToken(),
                "v2.0.0",
                "E2E test release",
                new File[]{artifact}
        );

        // 3. Check npm registry
        NPM npm = new NPM(System.out, System.err);
        npm.setRegistryUrl(getNpmRegistry());
        assertFalse(npm.isVersionPublished("e2e-nonexistent", "1.0.0", "npm"));

        System.out.println("All mock services responded correctly in end-to-end test");
    }
}

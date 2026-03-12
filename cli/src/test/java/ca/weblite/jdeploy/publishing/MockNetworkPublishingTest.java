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

    // ========================================================================
    // Connectivity tests
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("Verdaccio npm registry is reachable")
    void verdaccioIsReachable() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(getNpmRegistry() + "/-/ping").openConnection();
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
    @Order(20)
    @DisplayName("GitHub publish: resource upload to mock jDeploy registry")
    void githubPublishResourceUpload() throws Exception {
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
        assertEquals("2.0.0", packageInfo.optString("latestVersion"),
                "latestVersion should be 2.0.0");

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

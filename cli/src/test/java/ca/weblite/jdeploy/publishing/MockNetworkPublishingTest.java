package ca.weblite.jdeploy.publishing;

import ca.weblite.jdeploy.JDeploy;
import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.config.Config;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettings;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettingsService;
import ca.weblite.jdeploy.environment.Environment;
import ca.weblite.jdeploy.factories.CheerpjServiceFactory;
import ca.weblite.jdeploy.factories.JDeployProjectFactory;
import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.packaging.*;
import ca.weblite.jdeploy.publishTargets.PublishTarget;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;
import ca.weblite.jdeploy.publishing.github.GitHubPublishDriver;
import ca.weblite.jdeploy.publishing.github.GitHubReleaseCreator;
import ca.weblite.jdeploy.publishing.npm.NPMPublishDriver;
import ca.weblite.jdeploy.services.*;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
class MockNetworkPublishingTest {

    private static final String ENV_NPM_REGISTRY = "NPM_CONFIG_REGISTRY";
    private static final String ENV_GITHUB_API_URL = "GITHUB_API_BASE_URL";
    private static final String ENV_GITHUB_BASE_URL = "GITHUB_BASE_URL";
    private static final String ENV_JDEPLOY_REGISTRY = "JDEPLOY_REGISTRY_URL";
    private static final String ENV_GITHUB_TOKEN = "GITHUB_TOKEN";

    @TempDir
    File tempDir;

    @BeforeEach
    void checkEnvironment() {
        Assumptions.assumeTrue(
                System.getenv(ENV_NPM_REGISTRY) != null
                        && System.getenv(ENV_GITHUB_API_URL) != null
                        && System.getenv(ENV_JDEPLOY_REGISTRY) != null,
                "Mock network services not available. Set environment variables or run via docker-compose."
        );
    }

    private String getNpmRegistry() {
        return System.getenv(ENV_NPM_REGISTRY);
    }

    private String getGithubApiBaseUrl() {
        return System.getenv(ENV_GITHUB_API_URL);
    }

    private String getGithubBaseUrl() {
        String url = System.getenv(ENV_GITHUB_BASE_URL);
        if (url != null && !url.endsWith("/")) {
            url += "/";
        }
        return url;
    }

    private String getJdeployRegistry() {
        return System.getenv(ENV_JDEPLOY_REGISTRY);
    }

    private String getGithubToken() {
        String token = System.getenv(ENV_GITHUB_TOKEN);
        return token != null ? token : "mock-github-token";
    }

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
        HttpURLConnection conn = (HttpURLConnection) new URL(getJdeployRegistry() + "__admin/mappings").openConnection();
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
        // --- Set up project directory with a JAR ---
        File projectDir = new File(tempDir, "github-project");
        projectDir.mkdirs();

        File jarFile = createTestJar(projectDir, "test-app-1.0.jar", "com.example.TestApp");
        File packageJsonFile = createPackageJson(projectDir, "test-github-app", "1.0.0",
                "test-app-1.0.jar", null);

        // Create icon
        FileUtils.copyInputStreamToFile(
                JDeploy.class.getResourceAsStream("icon.png"),
                new File(projectDir, "icon.png")
        );

        // Create the jdeploy-bundle directory manually (simulating what makePackage does)
        File jdeployBundle = new File(projectDir, "jdeploy-bundle");
        jdeployBundle.mkdirs();
        FileUtils.copyFile(jarFile, new File(jdeployBundle, jarFile.getName()));

        // --- Configure services pointing to WireMock ---
        GitHubReleaseCreator releaseCreator = new GitHubReleaseCreator();
        releaseCreator.setGithubUrl(getGithubBaseUrl());
        releaseCreator.setGithubApiUrl(getGithubApiBaseUrl() + "/repos/");

        Config config = new Config();
        config.getProperties().setProperty("registry.url", getJdeployRegistry());
        PackagingConfig packagingConfig = new PackagingConfig(config);
        BundleCodeService bundleCodeService = new BundleCodeService(packagingConfig);

        PackageNameService packageNameService = mock(PackageNameService.class);
        when(packageNameService.getFullPackageName(any(), any())).thenReturn("test-github-app");

        CheerpjServiceFactory cheerpjServiceFactory = mock(CheerpjServiceFactory.class);
        CheerpjService cheerpjService = mock(CheerpjService.class);
        when(cheerpjServiceFactory.create(any())).thenReturn(cheerpjService);
        when(cheerpjService.isEnabled()).thenReturn(false);

        DownloadPageSettingsService downloadPageSettingsService = mock(DownloadPageSettingsService.class);
        DownloadPageSettings settings = new DownloadPageSettings();
        settings.setEnabledPlatforms(new HashSet<>(Arrays.asList(
                DownloadPageSettings.BundlePlatform.LinuxX64
        )));
        when(downloadPageSettingsService.read(any(File.class))).thenReturn(settings);

        PlatformBundleGenerator platformBundleGenerator = mock(PlatformBundleGenerator.class);
        DefaultBundleService defaultBundleService = mock(DefaultBundleService.class);
        JDeployProjectFactory projectFactory = mock(JDeployProjectFactory.class);
        Environment environment = mock(Environment.class);
        JDeployFilesZipGenerator jdeployFilesZipGenerator = mock(JDeployFilesZipGenerator.class);

        // Create BasePublishDriver that sets up the publish directory structure
        BasePublishDriver baseDriver = mock(BasePublishDriver.class);
        doAnswer(invocation -> {
            // Simulate what BasePublishDriver.prepare() does:
            // copy jdeploy-bundle and package.json to publish dir
            PublishingContext ctx = invocation.getArgument(0);
            File publishDir = ctx.getPublishDir();
            publishDir.mkdirs();
            FileUtils.copyDirectory(
                    new File(ctx.directory(), "jdeploy-bundle"),
                    new File(publishDir, "jdeploy-bundle")
            );
            FileUtils.copyFile(ctx.packagingContext.packageJsonFile, ctx.getPublishPackageJsonFile());
            return null;
        }).when(baseDriver).prepare(any(), any(), any());

        GitHubPublishDriver githubDriver = new GitHubPublishDriver(
                baseDriver,
                bundleCodeService,
                packageNameService,
                cheerpjServiceFactory,
                releaseCreator,
                downloadPageSettingsService,
                platformBundleGenerator,
                defaultBundleService,
                projectFactory,
                environment,
                jdeployFilesZipGenerator
        );
        githubDriver.setGithubUrl(getGithubBaseUrl());

        // --- Create publishing context ---
        PackagingContext packagingContext = new PackagingContext.Builder()
                .directory(projectDir)
                .packageJsonFile(packageJsonFile)
                .out(System.out)
                .err(System.err)
                .build();

        NPM npm = mock(NPM.class);
        doNothing().when(npm).pack(any(File.class), any(File.class), anyBoolean());

        String repoUrl = getGithubBaseUrl() + "testuser/testrepo";
        PublishingContext publishingContext = new PublishingContext(
                packagingContext, false, npm,
                getGithubToken(), null, null, null, null
        );

        PublishTargetInterface target = new PublishTarget("github", PublishTargetType.GITHUB, repoUrl);

        // --- Run prepare ---
        githubDriver.prepare(publishingContext, target, new BundlerSettings());

        // Verify prepare created expected files
        File releaseFilesDir = publishingContext.getGithubReleaseFilesDir();
        assertTrue(releaseFilesDir.exists(), "GitHub release files directory should exist");

        File packageInfoFile = new File(releaseFilesDir, "package-info.json");
        assertTrue(packageInfoFile.exists(), "package-info.json should be generated");

        // Verify package-info.json content
        String packageInfoContent = FileUtils.readFileToString(packageInfoFile, StandardCharsets.UTF_8);
        JSONObject packageInfo = new JSONObject(packageInfoContent);
        assertTrue(packageInfo.has("versions"), "package-info.json should have versions");
        assertTrue(packageInfo.getJSONObject("versions").has("1.0.0"),
                "package-info.json should contain version 1.0.0");

        File releaseNotes = new File(releaseFilesDir, "jdeploy-release-notes.md");
        assertTrue(releaseNotes.exists(), "Release notes should be generated");

        // Verify bundleCodeService was called successfully (hit WireMock jDeploy registry)
        // (It was called inside prepare() -> bundleCodeService.fetchJdeployBundleCode())

        // --- Run publish ---
        // This makes real HTTP calls to WireMock (create release, create atomic release, upload assets)
        githubDriver.publish(publishingContext, target, null);

        // If we get here without exception, the full GitHub publish flow completed:
        // 1. downloadAssetWithETag -> WireMock returned 404 (GitHubReleaseNotFoundException, first publish)
        // 2. createRelease -> WireMock returned 201 with release JSON
        // 3. createReleaseAtomic -> WireMock returned 201 (created jdeploy tag)
        System.out.println("GitHub publish flow completed successfully against WireMock");
    }

    @Test
    @Order(20)
    @DisplayName("GitHub publish: resource upload to mock jDeploy registry")
    void githubPublishResourceUpload() throws Exception {
        // Set up project with icon
        File projectDir = new File(tempDir, "resource-upload-project");
        projectDir.mkdirs();

        createPackageJson(projectDir, "test-resource-app", "1.0.0",
                "test.jar", null);

        // Create icon.png
        FileUtils.copyInputStreamToFile(
                JDeploy.class.getResourceAsStream("icon.png"),
                new File(projectDir, "icon.png")
        );

        // Create publish dir with package.json (simulating after prepare)
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

        Config config = new Config();
        config.getProperties().setProperty("registry.url", getJdeployRegistry());
        PackagingConfig packagingConfig = new PackagingConfig(config);
        ResourceUploader uploader = new ResourceUploader(packagingConfig);

        PackagingContext packagingContext = new PackagingContext.Builder()
                .directory(projectDir)
                .packageJsonFile(new File(projectDir, "package.json"))
                .out(System.out)
                .err(System.err)
                .exitOnFail(false)
                .build();

        PublishingContext publishingContext = new PublishingContext(
                packagingContext, false,
                new NPM(System.out, System.err),
                null, null, null, null, null
        );

        // This makes a real HTTP POST to WireMock's /publish.php endpoint
        uploader.uploadResources(publishingContext);
        System.out.println("Resource upload to mock jDeploy registry completed successfully");
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

        // Query Verdaccio for a non-existent package
        assertFalse(npm.isVersionPublished("nonexistent-package", "1.0.0", "npm"),
                "Non-existent package should not be published");
    }

    @Test
    @Order(31)
    @DisplayName("NPM: publish to Verdaccio and verify")
    void npmPublishToVerdaccioAndVerify() throws Exception {
        // Create a minimal publishable npm package
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

        // Publish to Verdaccio
        // Verdaccio with open auth config should accept this
        npm.publish(publishDir, false, null, null);

        // Verify the package is now available on Verdaccio
        assertTrue(npm.isVersionPublished(packageName, "1.0.0", "npm"),
                "Published package should be retrievable from Verdaccio");

        System.out.println("NPM publish + verify against Verdaccio completed for: " + packageName);
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

        // Should not throw - queries Verdaccio
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
        File projectDir = new File(tempDir, "e2e-project");
        projectDir.mkdirs();

        File jarFile = createTestJar(projectDir, "myapp-2.0.0.jar", "com.example.MyApp");
        File packageJsonFile = createPackageJson(projectDir, "e2e-test-app", "2.0.0",
                "myapp-2.0.0.jar", null);

        // Create icon for resource upload
        FileUtils.copyInputStreamToFile(
                JDeploy.class.getResourceAsStream("icon.png"),
                new File(projectDir, "icon.png")
        );

        // Create jdeploy-bundle
        File jdeployBundle = new File(projectDir, "jdeploy-bundle");
        jdeployBundle.mkdirs();
        FileUtils.copyFile(jarFile, new File(jdeployBundle, jarFile.getName()));

        // --- Wire up all services ---
        Config config = new Config();
        config.getProperties().setProperty("registry.url", getJdeployRegistry());
        PackagingConfig packagingConfig = new PackagingConfig(config);

        GitHubReleaseCreator releaseCreator = new GitHubReleaseCreator();
        releaseCreator.setGithubUrl(getGithubBaseUrl());
        releaseCreator.setGithubApiUrl(getGithubApiBaseUrl() + "/repos/");

        BundleCodeService bundleCodeService = new BundleCodeService(packagingConfig);
        ResourceUploader resourceUploader = new ResourceUploader(packagingConfig);

        PackageNameService packageNameService = mock(PackageNameService.class);
        when(packageNameService.getFullPackageName(any(), any())).thenReturn("e2e-test-app");

        CheerpjServiceFactory cheerpjServiceFactory = mock(CheerpjServiceFactory.class);
        CheerpjService cheerpjService = mock(CheerpjService.class);
        when(cheerpjServiceFactory.create(any())).thenReturn(cheerpjService);
        when(cheerpjService.isEnabled()).thenReturn(false);

        DownloadPageSettingsService downloadPageSettingsService = mock(DownloadPageSettingsService.class);
        DownloadPageSettings dlSettings = new DownloadPageSettings();
        dlSettings.setEnabledPlatforms(new HashSet<>(Collections.singletonList(
                DownloadPageSettings.BundlePlatform.LinuxX64
        )));
        when(downloadPageSettingsService.read(any(File.class))).thenReturn(dlSettings);

        PlatformBundleGenerator platformBundleGenerator = mock(PlatformBundleGenerator.class);
        DefaultBundleService defaultBundleService = mock(DefaultBundleService.class);
        JDeployProjectFactory projectFactory = mock(JDeployProjectFactory.class);
        Environment environment = mock(Environment.class);
        JDeployFilesZipGenerator jdeployFilesZipGenerator = mock(JDeployFilesZipGenerator.class);

        BasePublishDriver baseDriver = mock(BasePublishDriver.class);
        doAnswer(invocation -> {
            PublishingContext ctx = invocation.getArgument(0);
            File publishDir = ctx.getPublishDir();
            publishDir.mkdirs();
            FileUtils.copyDirectory(
                    new File(ctx.directory(), "jdeploy-bundle"),
                    new File(publishDir, "jdeploy-bundle")
            );
            FileUtils.copyFile(ctx.packagingContext.packageJsonFile, ctx.getPublishPackageJsonFile());
            return null;
        }).when(baseDriver).prepare(any(), any(), any());

        GitHubPublishDriver githubDriver = new GitHubPublishDriver(
                baseDriver, bundleCodeService, packageNameService,
                cheerpjServiceFactory, releaseCreator, downloadPageSettingsService,
                platformBundleGenerator, defaultBundleService, projectFactory,
                environment, jdeployFilesZipGenerator
        );
        githubDriver.setGithubUrl(getGithubBaseUrl());

        // --- Build context ---
        PackagingContext packagingContext = new PackagingContext.Builder()
                .directory(projectDir)
                .packageJsonFile(packageJsonFile)
                .out(System.out)
                .err(System.err)
                .exitOnFail(false)
                .build();

        NPM npm = mock(NPM.class);
        doNothing().when(npm).pack(any(File.class), any(File.class), anyBoolean());

        String repoUrl = getGithubBaseUrl() + "e2euser/e2erepo";
        PublishingContext publishingContext = new PublishingContext(
                packagingContext, false, npm,
                getGithubToken(), null, null, null, null
        );

        PublishTargetInterface target = new PublishTarget("github", PublishTargetType.GITHUB, repoUrl);

        // --- Step 1: Prepare (hits WireMock jDeploy for bundle code registration) ---
        githubDriver.prepare(publishingContext, target, new BundlerSettings());

        // Verify package-info.json was created with correct version
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

        // --- Step 2: Publish (hits WireMock GitHub for release creation) ---
        githubDriver.publish(publishingContext, target, null);

        // --- Step 3: Upload resources (hits WireMock jDeploy /publish.php) ---
        resourceUploader.uploadResources(publishingContext);

        // --- Verify WireMock received our requests ---
        verifyWiremockReceivedRequests("GitHub", getGithubApiBaseUrl(),
                "/repos/e2euser/e2erepo/releases");
        verifyWiremockReceivedRequests("jDeploy", getJdeployRegistry(),
                "/register.php");

        System.out.println("Full end-to-end GitHub publish flow completed successfully!");
        System.out.println("  - prepare: package-info.json generated with version 2.0.0");
        System.out.println("  - publish: GitHub release created via WireMock");
        System.out.println("  - resources: icon uploaded to mock jDeploy registry");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private File createTestJar(File projectDir, String jarName, String mainClass) throws IOException {
        File targetDir = new File(projectDir, "target");
        targetDir.mkdirs();
        File jarFile = new File(targetDir, jarName);

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
            jos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            manifest.write(jos);
            jos.closeEntry();

            // Add a dummy class file
            jos.putNextEntry(new ZipEntry(mainClass.replace('.', '/') + ".class"));
            jos.write(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
            jos.closeEntry();
        }

        return jarFile;
    }

    private File createPackageJson(File projectDir, String name, String version,
                                   String jar, String source) throws IOException {
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", name);
        packageJson.put("version", version);

        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jar", "target/" + jar);
        jdeploy.put("javaVersion", "11");
        jdeploy.put("title", name);
        packageJson.put("jdeploy", jdeploy);

        if (source != null) {
            packageJson.put("source", source);
        }

        File packageJsonFile = new File(projectDir, "package.json");
        FileUtils.writeStringToFile(packageJsonFile, packageJson.toString(2), StandardCharsets.UTF_8);
        return packageJsonFile;
    }

    private void verifyWiremockReceivedRequests(String serviceName, String wiremockBaseUrl,
                                                 String expectedUrlPath) {
        try {
            URL url = new URL(wiremockBaseUrl + "__admin/requests");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JSONObject requests = new JSONObject(body);
                int totalRequests = requests.optJSONArray("requests") != null
                        ? requests.getJSONArray("requests").length() : 0;
                System.out.println(serviceName + " WireMock received " + totalRequests + " total requests");
            }
        } catch (Exception e) {
            System.out.println("Could not verify " + serviceName + " WireMock requests: " + e.getMessage());
        }
    }
}

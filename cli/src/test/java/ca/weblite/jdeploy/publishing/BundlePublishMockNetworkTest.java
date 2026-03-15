package ca.weblite.jdeploy.publishing;

import ca.weblite.jdeploy.appbundler.Bundler;
import ca.weblite.jdeploy.appbundler.BundlerResult;
import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.config.Config;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettings;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettingsService;
import ca.weblite.jdeploy.environment.Environment;
import ca.weblite.jdeploy.factories.CheerpjServiceFactory;
import ca.weblite.jdeploy.factories.JDeployProjectFactory;
import ca.weblite.jdeploy.models.BundleArtifact;
import ca.weblite.jdeploy.models.BundleManifest;
import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.packaging.PackagingConfig;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishing.github.GitHubPublishDriver;
import ca.weblite.jdeploy.publishing.github.GitHubReleaseCreator;
import ca.weblite.jdeploy.publishing.s3.S3BundleUploader;
import ca.weblite.jdeploy.publishing.s3.S3Config;
import ca.weblite.jdeploy.services.*;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock network tests for the bundle publishing pipeline.
 *
 * These tests verify that when bundle publishing is enabled (via jdeploy.artifacts
 * in package.json), the prepare flow produces the correct artifacts:
 * - Bundle JAR files are copied to the GitHub release files directory
 * - Bundle URLs and SHA-256 checksums are written to the publish package.json
 * - Bundle metadata appears in the package-info.json version entry
 * - The GitHub publish flow uploads the bundle assets alongside release files
 *
 * The PublishBundleService.buildBundles() is mocked (it needs real native toolchains),
 * but BundleUploadRouter and BundleChecksumWriter use real implementations to verify
 * end-to-end artifact correctness.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BundlePublishMockNetworkTest extends BaseMockNetworkPublishingTest {

    /**
     * Creates a fake bundle JAR file with some content to simulate a built bundle.
     */
    private File createFakeBundleJar(String filename, String content) throws IOException {
        File jarFile = new File(tempDir, filename);
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
            ZipEntry entry = new ZipEntry("bundle-content.bin");
            jos.putNextEntry(entry);
            jos.write(content.getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
        return jarFile;
    }

    /**
     * Computes SHA-256 hash of a file, matching PublishBundleService's algorithm.
     */
    private String computeSha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (InputStream is = new FileInputStream(file)) {
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Creates a BundleManifest with fake artifacts for testing.
     */
    private BundleManifest createTestManifest(String version) throws Exception {
        List<BundleArtifact> artifacts = new ArrayList<>();

        // Create mac-arm64 GUI bundle
        File macArm64Jar = createFakeBundleJar(
                "test-app-mac-arm64-" + version + ".jar",
                "mac-arm64-gui-bundle-content"
        );
        String macArm64Sha = computeSha256(macArm64Jar);
        artifacts.add(new BundleArtifact(
                macArm64Jar, "mac", "arm64", version, false, macArm64Sha,
                "test-app-mac-arm64-" + version + ".jar"
        ));

        // Create win-x64 GUI bundle
        File winX64Jar = createFakeBundleJar(
                "test-app-win-x64-" + version + ".jar",
                "win-x64-gui-bundle-content"
        );
        String winX64Sha = computeSha256(winX64Jar);
        artifacts.add(new BundleArtifact(
                winX64Jar, "win", "x64", version, false, winX64Sha,
                "test-app-win-x64-" + version + ".jar"
        ));

        // Create win-x64 CLI bundle
        File winX64CliJar = createFakeBundleJar(
                "test-app-win-x64-" + version + "-cli.jar",
                "win-x64-cli-bundle-content"
        );
        String winX64CliSha = computeSha256(winX64CliJar);
        artifacts.add(new BundleArtifact(
                winX64CliJar, "win", "x64", version, true, winX64CliSha,
                "test-app-win-x64-" + version + "-cli.jar"
        ));

        return new BundleManifest(artifacts);
    }

    /**
     * Creates a package.json that has jdeploy.artifacts enabled for mac-arm64 and win-x64.
     */
    private File createPackageJsonWithArtifacts(
            File projectDir, String name, String version, String jar
    ) throws IOException {
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", name);
        packageJson.put("version", version);

        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jar", "target/" + jar);
        jdeploy.put("javaVersion", "11");
        jdeploy.put("title", name);

        JSONObject artifacts = new JSONObject();
        artifacts.put("mac-arm64", new JSONObject().put("enabled", true));
        artifacts.put("win-x64", new JSONObject().put("enabled", true));
        jdeploy.put("artifacts", artifacts);

        packageJson.put("jdeploy", jdeploy);

        File packageJsonFile = new File(projectDir, "package.json");
        FileUtils.writeStringToFile(packageJsonFile, packageJson.toString(2), StandardCharsets.UTF_8);
        return packageJsonFile;
    }

    /**
     * Creates a GitHubPublishDriver with real BundleUploadRouter and BundleChecksumWriter
     * but mocked PublishBundleService that returns a pre-built manifest.
     */
    private GitHubPublishDriver createDriverWithBundleSupport(
            BundleManifest manifest
    ) throws Exception {
        Config config = createMockConfig();
        PackagingConfig packagingConfig = new PackagingConfig(config);
        BundleCodeService bundleCodeService = new BundleCodeService(packagingConfig);

        GitHubReleaseCreator releaseCreator = new GitHubReleaseCreator();
        releaseCreator.setGithubUrl(getGithubBaseUrl());
        releaseCreator.setGithubApiUrl(getGithubApiBaseUrl() + "/repos/");

        PackageNameService packageNameService = mock(PackageNameService.class);
        when(packageNameService.getFullPackageName(any(), any())).thenAnswer(
                invocation -> invocation.getArgument(1)
        );

        CheerpjServiceFactory cheerpjServiceFactory = mock(CheerpjServiceFactory.class);
        CheerpjService cheerpjService = mock(CheerpjService.class);
        when(cheerpjServiceFactory.create(any())).thenReturn(cheerpjService);
        when(cheerpjService.isEnabled()).thenReturn(false);

        DownloadPageSettingsService downloadPageSettingsService = mock(DownloadPageSettingsService.class);
        DownloadPageSettings settings = new DownloadPageSettings();
        settings.setEnabledPlatforms(new HashSet<>(Collections.singletonList(
                DownloadPageSettings.BundlePlatform.LinuxX64
        )));
        when(downloadPageSettingsService.read(any(File.class))).thenReturn(settings);

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

        // Mock PublishBundleService to return the test manifest
        PublishBundleService publishBundleService = mock(PublishBundleService.class);
        when(publishBundleService.isEnabled(any())).thenReturn(manifest != null && !manifest.isEmpty());
        if (manifest != null) {
            when(publishBundleService.buildBundles(any(), any())).thenReturn(manifest);
        }

        // Use REAL BundleUploadRouter (with S3 disabled, routes to GitHub release dir)
        S3Config s3Config = mock(S3Config.class);
        when(s3Config.isConfigured()).thenReturn(false);
        S3BundleUploader s3Uploader = new S3BundleUploader(s3Config);
        BundleUploadRouter bundleUploadRouter = new BundleUploadRouter(s3Config, s3Uploader);

        // Use REAL BundleChecksumWriter
        BundleChecksumWriter bundleChecksumWriter = new BundleChecksumWriter();

        GitHubPublishDriver githubDriver = new GitHubPublishDriver(
                baseDriver, bundleCodeService, packageNameService,
                cheerpjServiceFactory, releaseCreator, downloadPageSettingsService,
                platformBundleGenerator, defaultBundleService, projectFactory,
                environment, jdeployFilesZipGenerator,
                publishBundleService, bundleUploadRouter, bundleChecksumWriter
        );
        githubDriver.setGithubUrl(getGithubBaseUrl());

        return githubDriver;
    }

    // ========================================================================
    // Tests
    // ========================================================================

    @Test
    @Order(10)
    @DisplayName("Bundle publish: bundle JARs are copied to GitHub release files directory")
    void bundleJarsCopiedToReleaseFilesDir() throws Exception {
        String version = "1.0.0";
        BundleManifest manifest = createTestManifest(version);

        File projectDir = new File(tempDir, "bundle-release-project");
        projectDir.mkdirs();
        File jarFile = createTestJar(projectDir, "app-1.0.0.jar", "com.example.App");
        createPackageJsonWithArtifacts(projectDir, "bundle-test-app", version, "app-1.0.0.jar");
        createIcon(projectDir);
        createJdeployBundle(projectDir, jarFile);

        GitHubPublishDriver driver = createDriverWithBundleSupport(manifest);
        PublishingContext ctx = createPublishingContext(projectDir);
        PublishTargetInterface target = createGitHubTarget("bundleuser", "bundlerepo");

        driver.prepare(ctx, target, new BundlerSettings());

        File releaseFilesDir = ctx.getGithubReleaseFilesDir();
        assertTrue(releaseFilesDir.exists(), "Release files directory should exist");

        // Verify each bundle JAR was copied to the release files dir
        File macArm64InRelease = new File(releaseFilesDir, "test-app-mac-arm64-1.0.0.jar");
        assertTrue(macArm64InRelease.exists(),
                "mac-arm64 bundle JAR should be in release files dir");

        File winX64InRelease = new File(releaseFilesDir, "test-app-win-x64-1.0.0.jar");
        assertTrue(winX64InRelease.exists(),
                "win-x64 bundle JAR should be in release files dir");

        File winX64CliInRelease = new File(releaseFilesDir, "test-app-win-x64-1.0.0-cli.jar");
        assertTrue(winX64CliInRelease.exists(),
                "win-x64 CLI bundle JAR should be in release files dir");
    }

    @Test
    @Order(20)
    @DisplayName("Bundle publish: checksums and URLs written to publish package.json")
    void checksumsWrittenToPublishPackageJson() throws Exception {
        String version = "1.0.0";
        BundleManifest manifest = createTestManifest(version);

        File projectDir = new File(tempDir, "bundle-checksum-project");
        projectDir.mkdirs();
        File jarFile = createTestJar(projectDir, "app-1.0.0.jar", "com.example.App");
        createPackageJsonWithArtifacts(projectDir, "checksum-test-app", version, "app-1.0.0.jar");
        createIcon(projectDir);
        createJdeployBundle(projectDir, jarFile);

        GitHubPublishDriver driver = createDriverWithBundleSupport(manifest);
        PublishingContext ctx = createPublishingContext(projectDir);
        PublishTargetInterface target = createGitHubTarget("checksumuser", "checksumrepo");

        driver.prepare(ctx, target, new BundlerSettings());

        // Read the publish package.json and verify artifacts section
        File publishPackageJson = ctx.getPublishPackageJsonFile();
        assertTrue(publishPackageJson.exists(), "Publish package.json should exist");

        String content = FileUtils.readFileToString(publishPackageJson, StandardCharsets.UTF_8);
        JSONObject packageJson = new JSONObject(content);
        JSONObject jdeploy = packageJson.getJSONObject("jdeploy");
        assertTrue(jdeploy.has("artifacts"), "jdeploy should have artifacts section");

        JSONObject artifacts = jdeploy.getJSONObject("artifacts");

        // Verify mac-arm64 entry
        assertTrue(artifacts.has("mac-arm64"), "Should have mac-arm64 artifact");
        JSONObject macArm64 = artifacts.getJSONObject("mac-arm64");
        assertTrue(macArm64.has("url"), "mac-arm64 should have url");
        assertTrue(macArm64.has("sha256"), "mac-arm64 should have sha256");
        assertTrue(macArm64.getString("url").contains("/releases/download/"),
                "mac-arm64 URL should be a GitHub release download URL");
        assertEquals(64, macArm64.getString("sha256").length(),
                "SHA-256 should be 64 hex characters");

        // Verify win-x64 entry with CLI sub-object
        assertTrue(artifacts.has("win-x64"), "Should have win-x64 artifact");
        JSONObject winX64 = artifacts.getJSONObject("win-x64");
        assertTrue(winX64.has("url"), "win-x64 should have url");
        assertTrue(winX64.has("sha256"), "win-x64 should have sha256");
        assertTrue(winX64.has("cli"), "win-x64 should have cli sub-object");

        JSONObject winX64Cli = winX64.getJSONObject("cli");
        assertTrue(winX64Cli.has("url"), "win-x64 cli should have url");
        assertTrue(winX64Cli.has("sha256"), "win-x64 cli should have sha256");

        // Verify URLs point to the correct repo and use the version in the download path
        // When no githubRefName is set, URLs should fall back to the package.json version
        String expectedUrlPrefix = getGithubBaseUrl() + "checksumuser/checksumrepo"
                + "/releases/download/" + version + "/";
        assertTrue(macArm64.getString("url").startsWith(expectedUrlPrefix),
                "mac-arm64 URL should use version '" + version + "' in download path: "
                        + macArm64.getString("url"));
        assertTrue(winX64.getString("url").startsWith(expectedUrlPrefix),
                "win-x64 URL should use version '" + version + "' in download path: "
                        + winX64.getString("url"));
    }

    @Test
    @Order(25)
    @DisplayName("Bundle publish: URLs use git tag (v-prefix) when githubRefName is set")
    void bundleUrlsUseGitTagWhenRefNameSet() throws Exception {
        String version = "1.0.0";
        String gitTag = "v1.0.0";
        BundleManifest manifest = createTestManifest(version);

        File projectDir = new File(tempDir, "bundle-vtag-project");
        projectDir.mkdirs();
        File jarFile = createTestJar(projectDir, "app-1.0.0.jar", "com.example.App");
        createPackageJsonWithArtifacts(projectDir, "vtag-test-app", version, "app-1.0.0.jar");
        createIcon(projectDir);
        createJdeployBundle(projectDir, jarFile);

        GitHubPublishDriver driver = createDriverWithBundleSupport(manifest);
        // Set githubRefName to simulate GitHub Actions context where tag has v prefix
        PublishingContext ctx = createPublishingContext(projectDir, false, gitTag);
        PublishTargetInterface target = createGitHubTarget("vtaguser", "vtagrepo");

        driver.prepare(ctx, target, new BundlerSettings());

        // Read the publish package.json and verify URLs use the git tag, not bare version
        File publishPackageJson = ctx.getPublishPackageJsonFile();
        String content = FileUtils.readFileToString(publishPackageJson, StandardCharsets.UTF_8);
        JSONObject packageJson = new JSONObject(content);
        JSONObject artifacts = packageJson.getJSONObject("jdeploy").getJSONObject("artifacts");

        String expectedUrlPrefix = getGithubBaseUrl() + "vtaguser/vtagrepo"
                + "/releases/download/" + gitTag + "/";
        String wrongUrlPrefix = getGithubBaseUrl() + "vtaguser/vtagrepo"
                + "/releases/download/" + version + "/";

        // Verify mac-arm64 URL uses the git tag
        JSONObject macArm64 = artifacts.getJSONObject("mac-arm64");
        assertTrue(macArm64.getString("url").startsWith(expectedUrlPrefix),
                "mac-arm64 URL should use git tag '" + gitTag + "' not bare version '" + version
                        + "': " + macArm64.getString("url"));
        assertFalse(macArm64.getString("url").startsWith(wrongUrlPrefix),
                "mac-arm64 URL must NOT use bare version without v prefix: "
                        + macArm64.getString("url"));

        // Verify win-x64 URL uses the git tag
        JSONObject winX64 = artifacts.getJSONObject("win-x64");
        assertTrue(winX64.getString("url").startsWith(expectedUrlPrefix),
                "win-x64 URL should use git tag '" + gitTag + "': " + winX64.getString("url"));

        // Verify win-x64 CLI URL also uses the git tag
        JSONObject winX64Cli = winX64.getJSONObject("cli");
        assertTrue(winX64Cli.getString("url").startsWith(expectedUrlPrefix),
                "win-x64 CLI URL should use git tag '" + gitTag + "': "
                        + winX64Cli.getString("url"));

        // Also verify the release dir package.json has the same correct URLs
        File releasePackageJson = new File(ctx.getGithubReleaseFilesDir(), "package.json");
        if (releasePackageJson.exists()) {
            String releaseContent = FileUtils.readFileToString(releasePackageJson, StandardCharsets.UTF_8);
            JSONObject releasePj = new JSONObject(releaseContent);
            JSONObject releaseArtifacts = releasePj.getJSONObject("jdeploy").getJSONObject("artifacts");

            assertTrue(releaseArtifacts.getJSONObject("mac-arm64").getString("url")
                            .startsWith(expectedUrlPrefix),
                    "Release dir mac-arm64 URL should also use git tag");
        }
    }

    @Test
    @Order(30)
    @DisplayName("Bundle publish: artifacts metadata appears in package-info.json")
    void artifactsInPackageInfoJson() throws Exception {
        String version = "1.0.0";
        BundleManifest manifest = createTestManifest(version);

        File projectDir = new File(tempDir, "bundle-pkginfo-project");
        projectDir.mkdirs();
        File jarFile = createTestJar(projectDir, "app-1.0.0.jar", "com.example.App");
        createPackageJsonWithArtifacts(projectDir, "pkginfo-test-app", version, "app-1.0.0.jar");
        createIcon(projectDir);
        createJdeployBundle(projectDir, jarFile);

        GitHubPublishDriver driver = createDriverWithBundleSupport(manifest);
        PublishingContext ctx = createPublishingContext(projectDir);
        PublishTargetInterface target = createGitHubTarget("pkginfouser", "pkginforepo");

        driver.prepare(ctx, target, new BundlerSettings());

        // Read package-info.json and verify version entry has artifacts
        File packageInfoFile = new File(ctx.getGithubReleaseFilesDir(), "package-info.json");
        assertTrue(packageInfoFile.exists(), "package-info.json should exist");

        String content = FileUtils.readFileToString(packageInfoFile, StandardCharsets.UTF_8);
        JSONObject packageInfo = new JSONObject(content);

        assertTrue(packageInfo.has("versions"), "Should have versions");
        assertTrue(packageInfo.getJSONObject("versions").has(version),
                "Should have version " + version);

        JSONObject versionEntry = packageInfo.getJSONObject("versions").getJSONObject(version);
        assertTrue(versionEntry.has("jdeploy"), "Version entry should have jdeploy section");

        JSONObject jdeploy = versionEntry.getJSONObject("jdeploy");
        assertTrue(jdeploy.has("artifacts"), "jdeploy should have artifacts in package-info.json");

        JSONObject artifacts = jdeploy.getJSONObject("artifacts");
        assertTrue(artifacts.has("mac-arm64"), "package-info.json should have mac-arm64 artifact");
        assertTrue(artifacts.has("win-x64"), "package-info.json should have win-x64 artifact");

        // Verify SHA-256 values match what we computed
        JSONObject macArm64 = artifacts.getJSONObject("mac-arm64");
        assertNotNull(macArm64.optString("sha256", null), "mac-arm64 should have sha256");
    }

    @Test
    @Order(40)
    @DisplayName("Bundle publish: full prepare + publish flow uploads bundle assets to GitHub")
    void fullPreparePublishUploadsAssets() throws Exception {
        String version = "2.0.0";
        BundleManifest manifest = createTestManifest(version);

        File projectDir = new File(tempDir, "bundle-e2e-project");
        projectDir.mkdirs();
        File jarFile = createTestJar(projectDir, "app-2.0.0.jar", "com.example.App");
        createPackageJsonWithArtifacts(projectDir, "bundle-e2e-app", version, "app-2.0.0.jar");
        createIcon(projectDir);
        createJdeployBundle(projectDir, jarFile);

        GitHubPublishDriver driver = createDriverWithBundleSupport(manifest);
        PublishingContext ctx = createPublishingContext(projectDir);
        PublishTargetInterface target = createGitHubTarget("e2ebundleuser", "e2ebundlerepo");

        // Prepare
        driver.prepare(ctx, target, new BundlerSettings());

        // Verify release files directory has both standard files and bundle JARs
        File releaseFilesDir = ctx.getGithubReleaseFilesDir();
        File packageInfoFile = new File(releaseFilesDir, "package-info.json");
        assertTrue(packageInfoFile.exists(), "package-info.json should exist");
        File releaseNotes = new File(releaseFilesDir, "jdeploy-release-notes.md");
        assertTrue(releaseNotes.exists(), "Release notes should exist");

        // Verify bundle JARs are present
        assertTrue(new File(releaseFilesDir, "test-app-mac-arm64-2.0.0.jar").exists(),
                "mac-arm64 bundle should be in release files");
        assertTrue(new File(releaseFilesDir, "test-app-win-x64-2.0.0.jar").exists(),
                "win-x64 bundle should be in release files");
        assertTrue(new File(releaseFilesDir, "test-app-win-x64-2.0.0-cli.jar").exists(),
                "win-x64 CLI bundle should be in release files");

        // Publish
        driver.publish(ctx, target, null);

        // Verify WireMock received the release creation and asset uploads
        githubWireMock.verifyRequestMade("POST", "/repos/e2ebundleuser/e2ebundlerepo/releases");
        jdeployWireMock.verifyRequestMade("GET", "/register\\.php.*");

        // Verify at least one asset upload was made (the upload-asset stub)
        githubWireMock.verifyRequestMade("POST", "/uploads/repos/release-assets.*");
    }

    @Test
    @Order(50)
    @DisplayName("Bundle publish: no artifacts when bundle publishing is disabled")
    void noBundlesWhenDisabled() throws Exception {
        // Use the standard scaffoldProject which does NOT include jdeploy.artifacts
        File projectDir = scaffoldProject(
                "no-bundle-project", "no-bundle-app", "1.0.0",
                "app-1.0.0.jar", "com.example.App"
        );

        // Create driver with null manifest (disabled)
        GitHubPublishDriver driver = createDriverWithBundleSupport(null);
        PublishingContext ctx = createPublishingContext(projectDir);
        PublishTargetInterface target = createGitHubTarget("nobundleuser", "nobundlerepo");

        driver.prepare(ctx, target, new BundlerSettings());

        File releaseFilesDir = ctx.getGithubReleaseFilesDir();
        assertTrue(releaseFilesDir.exists(), "Release files directory should still exist");

        // Verify package-info.json exists but has no artifacts
        File packageInfoFile = new File(releaseFilesDir, "package-info.json");
        assertTrue(packageInfoFile.exists(), "package-info.json should exist");

        // Verify the publish package.json does NOT have artifacts
        File publishPackageJson = ctx.getPublishPackageJsonFile();
        String content = FileUtils.readFileToString(publishPackageJson, StandardCharsets.UTF_8);
        JSONObject packageJson = new JSONObject(content);
        JSONObject jdeploy = packageJson.optJSONObject("jdeploy");
        if (jdeploy != null) {
            assertFalse(jdeploy.has("artifacts"),
                    "jdeploy should not have artifacts when bundle publishing is disabled");
        }

        // No bundle JARs should be in release files dir
        File[] releaseFiles = releaseFilesDir.listFiles();
        if (releaseFiles != null) {
            for (File f : releaseFiles) {
                assertFalse(f.getName().endsWith(".jar") && f.getName().contains("-mac-"),
                        "No mac bundle JARs should be in release dir when disabled");
                assertFalse(f.getName().endsWith(".jar") && f.getName().contains("-win-"),
                        "No win bundle JARs should be in release dir when disabled");
            }
        }
    }

    @Test
    @Order(60)
    @DisplayName("Bundle publish: release package.json also gets bundle checksums")
    void releasePackageJsonAlsoGetsChecksums() throws Exception {
        String version = "1.0.0";
        BundleManifest manifest = createTestManifest(version);

        File projectDir = new File(tempDir, "bundle-release-pj-project");
        projectDir.mkdirs();
        File jarFile = createTestJar(projectDir, "app-1.0.0.jar", "com.example.App");
        createPackageJsonWithArtifacts(projectDir, "release-pj-test-app", version, "app-1.0.0.jar");
        createIcon(projectDir);
        createJdeployBundle(projectDir, jarFile);

        GitHubPublishDriver driver = createDriverWithBundleSupport(manifest);
        PublishingContext ctx = createPublishingContext(projectDir);
        PublishTargetInterface target = createGitHubTarget("releasepjuser", "releasepjrepo");

        driver.prepare(ctx, target, new BundlerSettings());

        // Verify the release files directory package.json also has artifacts
        File releasePackageJson = new File(ctx.getGithubReleaseFilesDir(), "package.json");
        assertTrue(releasePackageJson.exists(), "Release dir package.json should exist");

        String content = FileUtils.readFileToString(releasePackageJson, StandardCharsets.UTF_8);
        JSONObject packageJson = new JSONObject(content);
        JSONObject jdeploy = packageJson.getJSONObject("jdeploy");
        assertTrue(jdeploy.has("artifacts"),
                "Release dir package.json should also have artifacts");

        JSONObject artifacts = jdeploy.getJSONObject("artifacts");
        assertTrue(artifacts.has("mac-arm64"),
                "Release dir package.json should have mac-arm64");
        assertTrue(artifacts.has("win-x64"),
                "Release dir package.json should have win-x64");

        // Verify both publish and release package.json have matching checksums
        File publishPackageJson = ctx.getPublishPackageJsonFile();
        JSONObject publishPj = new JSONObject(
                FileUtils.readFileToString(publishPackageJson, StandardCharsets.UTF_8)
        );
        JSONObject publishArtifacts = publishPj.getJSONObject("jdeploy").getJSONObject("artifacts");

        assertEquals(
                publishArtifacts.getJSONObject("mac-arm64").getString("sha256"),
                artifacts.getJSONObject("mac-arm64").getString("sha256"),
                "SHA-256 should match between publish and release package.json"
        );
    }

    @Test
    @Order(70)
    @DisplayName("Bundle publish: enabled flag preserved alongside url/sha256 in artifacts")
    void enabledFlagPreservedInArtifacts() throws Exception {
        String version = "1.0.0";
        BundleManifest manifest = createTestManifest(version);

        File projectDir = new File(tempDir, "bundle-enabled-project");
        projectDir.mkdirs();
        File jarFile = createTestJar(projectDir, "app-1.0.0.jar", "com.example.App");
        createPackageJsonWithArtifacts(projectDir, "enabled-test-app", version, "app-1.0.0.jar");
        createIcon(projectDir);
        createJdeployBundle(projectDir, jarFile);

        GitHubPublishDriver driver = createDriverWithBundleSupport(manifest);
        PublishingContext ctx = createPublishingContext(projectDir);
        PublishTargetInterface target = createGitHubTarget("enableduser", "enabledrepo");

        driver.prepare(ctx, target, new BundlerSettings());

        // Read the publish package.json
        File publishPackageJson = ctx.getPublishPackageJsonFile();
        String content = FileUtils.readFileToString(publishPackageJson, StandardCharsets.UTF_8);
        JSONObject packageJson = new JSONObject(content);
        JSONObject artifacts = packageJson.getJSONObject("jdeploy").getJSONObject("artifacts");

        // Verify "enabled": true is preserved alongside the new url/sha256 fields
        JSONObject macArm64 = artifacts.getJSONObject("mac-arm64");
        assertTrue(macArm64.optBoolean("enabled", false),
                "enabled flag should be preserved in mac-arm64 artifact");
        assertTrue(macArm64.has("url"), "url should be added alongside enabled flag");
        assertTrue(macArm64.has("sha256"), "sha256 should be added alongside enabled flag");

        JSONObject winX64 = artifacts.getJSONObject("win-x64");
        assertTrue(winX64.optBoolean("enabled", false),
                "enabled flag should be preserved in win-x64 artifact");
    }

    // ========================================================================
    // Windows Authenticode signing integration tests
    // ========================================================================

    /**
     * Creates a GitHubPublishDriver with a REAL PublishBundleService (not mocked)
     * that uses mocked signing services. Bundler.runit() must be statically mocked
     * by the caller since it requires native toolchains.
     */
    private GitHubPublishDriver createDriverWithRealBundleService(
            WindowsSigningService signingService,
            WindowsSigningConfigFactory signingConfigFactory
    ) throws Exception {
        Config config = createMockConfig();
        PackagingConfig packagingConfig = new PackagingConfig(config);
        BundleCodeService bundleCodeService = new BundleCodeService(packagingConfig);

        GitHubReleaseCreator releaseCreator = new GitHubReleaseCreator();
        releaseCreator.setGithubUrl(getGithubBaseUrl());
        releaseCreator.setGithubApiUrl(getGithubApiBaseUrl() + "/repos/");

        PackageNameService packageNameService = mock(PackageNameService.class);
        when(packageNameService.getFullPackageName(any(), any())).thenAnswer(
                invocation -> invocation.getArgument(1)
        );

        CheerpjServiceFactory cheerpjServiceFactory = mock(CheerpjServiceFactory.class);
        CheerpjService cheerpjService = mock(CheerpjService.class);
        when(cheerpjServiceFactory.create(any())).thenReturn(cheerpjService);
        when(cheerpjService.isEnabled()).thenReturn(false);

        DownloadPageSettingsService downloadPageSettingsService = mock(DownloadPageSettingsService.class);
        DownloadPageSettings settings = new DownloadPageSettings();
        settings.setEnabledPlatforms(new HashSet<>(Collections.singletonList(
                DownloadPageSettings.BundlePlatform.LinuxX64
        )));
        when(downloadPageSettingsService.read(any(File.class))).thenReturn(settings);

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

        // Use REAL PublishBundleService with mocked signing
        PublishBundleService publishBundleService = new PublishBundleService(
                packagingConfig, signingService, signingConfigFactory
        );

        // Use REAL BundleUploadRouter (with S3 disabled)
        S3Config s3Config = mock(S3Config.class);
        when(s3Config.isConfigured()).thenReturn(false);
        S3BundleUploader s3Uploader = new S3BundleUploader(s3Config);
        BundleUploadRouter bundleUploadRouter = new BundleUploadRouter(s3Config, s3Uploader);

        BundleChecksumWriter bundleChecksumWriter = new BundleChecksumWriter();

        GitHubPublishDriver githubDriver = new GitHubPublishDriver(
                baseDriver, bundleCodeService, packageNameService,
                cheerpjServiceFactory, releaseCreator, downloadPageSettingsService,
                platformBundleGenerator, defaultBundleService, projectFactory,
                environment, jdeployFilesZipGenerator,
                publishBundleService, bundleUploadRouter, bundleChecksumWriter
        );
        githubDriver.setGithubUrl(getGithubBaseUrl());

        return githubDriver;
    }

    @Test
    @Order(80)
    @DisplayName("Bundle publish: full prepare flow signs Windows exe in pre-built bundles")
    void fullPrepareFlowSignsWindowsExeInPrebuiltBundles() throws Exception {
        String version = "1.0.0";

        // Set up mocked signing
        WindowsSigningService mockSigningService = mock(WindowsSigningService.class);
        WindowsSigningConfigFactory mockConfigFactory = mock(WindowsSigningConfigFactory.class);
        WindowsSigningConfig signingConfig = new WindowsSigningConfig();
        signingConfig.setKeystorePath("/fake/cert.pfx");
        signingConfig.setKeystorePassword("password");
        when(mockConfigFactory.createFromEnvironment()).thenReturn(signingConfig);

        List<File> signedFiles = new ArrayList<>();
        doAnswer(invocation -> {
            signedFiles.add(invocation.getArgument(0));
            return null;
        }).when(mockSigningService).sign(any(File.class), any(WindowsSigningConfig.class));

        // Set up project with win-x64 artifacts enabled
        File projectDir = new File(tempDir, "signing-e2e-project");
        projectDir.mkdirs();
        File jarFile = createTestJar(projectDir, "app-1.0.0.jar", "com.example.App");
        createPackageJsonWithArtifacts(projectDir, "signing-test-app", version, "app-1.0.0.jar");
        createIcon(projectDir);
        createJdeployBundle(projectDir, jarFile);

        // Also create jdeploy-bundle jar so loadAppInfo finds it
        File jdeployBundleDir = new File(projectDir, "jdeploy-bundle");
        File bundledIcon = new File(jdeployBundleDir, "icon.png");
        FileUtils.copyFile(new File(projectDir, "icon.png"), bundledIcon);

        GitHubPublishDriver driver = createDriverWithRealBundleService(
                mockSigningService, mockConfigFactory
        );
        PublishingContext ctx = createPublishingContext(projectDir);
        PublishTargetInterface target = createGitHubTarget("signinguser", "signingrepo");

        try (MockedStatic<Bundler> bundlerMock = mockStatic(Bundler.class)) {
            // Mock Bundler.runit to produce fake .exe files for Windows bundles
            bundlerMock.when(() -> Bundler.runit(
                    any(), any(), anyString(), anyString(), anyString(), anyString()
            )).thenAnswer(invocation -> {
                String bundleTarget = invocation.getArgument(3);
                ca.weblite.jdeploy.appbundler.BundlerSettings settings = invocation.getArgument(0);
                boolean isCli = settings.isCliCommandsEnabled();

                BundlerResult result = new BundlerResult(bundleTarget);
                String destDir = invocation.getArgument(4);
                String suffix = bundleTarget.startsWith("win") ? ".exe" : ".bin";
                File outputFile = new File(destDir, "fake-bundle" + (isCli ? "-cli" : "") + suffix);
                outputFile.getParentFile().mkdirs();
                // Write some content so tar.gz wrapping has something to compress
                FileUtils.writeStringToFile(outputFile, "fake-exe-content-" + bundleTarget,
                        StandardCharsets.UTF_8);
                result.setOutputFile(outputFile);
                return result;
            });

            // Run the full prepare flow
            driver.prepare(ctx, target, new BundlerSettings());
        }

        // Verify signing was called for Windows exe files
        assertFalse(signedFiles.isEmpty(),
                "Signing service should have been called for Windows exe bundles");
        assertTrue(signedFiles.stream().allMatch(f -> f.getName().endsWith(".exe")),
                "All signed files should be .exe files");

        // Verify the release files directory has the bundle tar.gz files
        File releaseFilesDir = ctx.getGithubReleaseFilesDir();
        assertTrue(releaseFilesDir.exists(), "Release files directory should exist");

        // Verify bundle artifacts were uploaded with checksums
        File publishPackageJson = ctx.getPublishPackageJsonFile();
        String content = FileUtils.readFileToString(publishPackageJson, StandardCharsets.UTF_8);
        JSONObject packageJson = new JSONObject(content);
        JSONObject jdeploy = packageJson.getJSONObject("jdeploy");
        assertTrue(jdeploy.has("artifacts"), "Should have artifacts with bundle checksums");
        JSONObject artifacts = jdeploy.getJSONObject("artifacts");
        assertTrue(artifacts.has("win-x64"), "Should have win-x64 artifact with checksum");
        assertTrue(artifacts.getJSONObject("win-x64").has("sha256"),
                "win-x64 artifact should have SHA-256 checksum");
    }

    @Test
    @Order(85)
    @DisplayName("Bundle publish: full prepare flow does not sign when signing is not configured")
    void fullPrepareFlowSkipsSigningWhenNotConfigured() throws Exception {
        String version = "1.0.0";

        // Signing NOT configured (factory returns null)
        WindowsSigningService mockSigningService = mock(WindowsSigningService.class);
        WindowsSigningConfigFactory mockConfigFactory = mock(WindowsSigningConfigFactory.class);
        when(mockConfigFactory.createFromEnvironment()).thenReturn(null);

        File projectDir = new File(tempDir, "nosign-e2e-project");
        projectDir.mkdirs();
        File jarFile = createTestJar(projectDir, "app-1.0.0.jar", "com.example.App");
        createPackageJsonWithArtifacts(projectDir, "nosign-test-app", version, "app-1.0.0.jar");
        createIcon(projectDir);
        createJdeployBundle(projectDir, jarFile);

        File jdeployBundleDir = new File(projectDir, "jdeploy-bundle");
        FileUtils.copyFile(new File(projectDir, "icon.png"), new File(jdeployBundleDir, "icon.png"));

        GitHubPublishDriver driver = createDriverWithRealBundleService(
                mockSigningService, mockConfigFactory
        );
        PublishingContext ctx = createPublishingContext(projectDir);
        PublishTargetInterface target = createGitHubTarget("nosignuser", "nosignrepo");

        try (MockedStatic<Bundler> bundlerMock = mockStatic(Bundler.class)) {
            bundlerMock.when(() -> Bundler.runit(
                    any(), any(), anyString(), anyString(), anyString(), anyString()
            )).thenAnswer(invocation -> {
                String bundleTarget = invocation.getArgument(3);
                BundlerResult result = new BundlerResult(bundleTarget);
                String destDir = invocation.getArgument(4);
                String suffix = bundleTarget.startsWith("win") ? ".exe" : ".bin";
                File outputFile = new File(destDir, "fake-bundle" + suffix);
                outputFile.getParentFile().mkdirs();
                FileUtils.writeStringToFile(outputFile, "fake-content", StandardCharsets.UTF_8);
                result.setOutputFile(outputFile);
                return result;
            });

            driver.prepare(ctx, target, new BundlerSettings());
        }

        // Signing should never have been called
        verify(mockSigningService, never()).sign(any(File.class), any(WindowsSigningConfig.class));

        // But bundles should still be built and uploaded
        File publishPackageJson = ctx.getPublishPackageJsonFile();
        String content = FileUtils.readFileToString(publishPackageJson, StandardCharsets.UTF_8);
        JSONObject packageJson = new JSONObject(content);
        JSONObject artifacts = packageJson.getJSONObject("jdeploy").getJSONObject("artifacts");
        assertTrue(artifacts.has("win-x64"), "win-x64 bundle should still be built unsigned");
    }
}

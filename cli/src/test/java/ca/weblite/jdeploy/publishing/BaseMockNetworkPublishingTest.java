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
import ca.weblite.jdeploy.publishing.BundleChecksumWriter;
import ca.weblite.jdeploy.publishing.BundleUploadRouter;
import ca.weblite.jdeploy.services.*;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Base class for mock network publishing tests.
 *
 * Provides:
 * - Environment variable checks (skips tests if mock services aren't available)
 * - URL accessors for Verdaccio, WireMock GitHub, and WireMock jDeploy
 * - {@link WireMockAdminClient} instances for per-test stub management and request verification
 * - Helper methods for creating test JARs, package.json files, and wired-up service objects
 * - Automatic cleanup of dynamic stubs and request journals between tests
 *
 * Subclasses get a clean WireMock state before each test and can add custom stubs
 * to override the static defaults loaded from test-infra/wiremock.
 */
public abstract class BaseMockNetworkPublishingTest {

    protected static final String ENV_NPM_REGISTRY = "NPM_CONFIG_REGISTRY";
    protected static final String ENV_GITHUB_API_URL = "GITHUB_API_BASE_URL";
    protected static final String ENV_GITHUB_BASE_URL = "GITHUB_BASE_URL";
    protected static final String ENV_JDEPLOY_REGISTRY = "JDEPLOY_REGISTRY_URL";
    protected static final String ENV_GITHUB_TOKEN = "GITHUB_TOKEN";

    @TempDir
    protected File tempDir;

    protected WireMockAdminClient githubWireMock;
    protected WireMockAdminClient jdeployWireMock;

    @BeforeEach
    void baseSetUp() {
        Assumptions.assumeTrue(
                System.getenv(ENV_NPM_REGISTRY) != null
                        && System.getenv(ENV_GITHUB_API_URL) != null
                        && System.getenv(ENV_JDEPLOY_REGISTRY) != null,
                "Mock network services not available. Set environment variables or run via docker-compose."
        );

        githubWireMock = new WireMockAdminClient(getGithubApiBaseUrl());
        jdeployWireMock = new WireMockAdminClient(getJdeployRegistryBaseUrl());

        try {
            githubWireMock.resetRequestJournal();
            jdeployWireMock.resetRequestJournal();
        } catch (IOException e) {
            System.err.println("Warning: could not reset WireMock request journals: " + e.getMessage());
        }
    }

    @AfterEach
    void baseTearDown() {
        if (githubWireMock != null) {
            githubWireMock.removeAllDynamicStubs();
        }
        if (jdeployWireMock != null) {
            jdeployWireMock.removeAllDynamicStubs();
        }
    }

    // ========================================================================
    // URL accessors
    // ========================================================================

    protected String getNpmRegistry() {
        String url = System.getenv(ENV_NPM_REGISTRY);
        if (url != null && !url.endsWith("/")) {
            url += "/";
        }
        return url;
    }

    protected String getGithubApiBaseUrl() {
        return System.getenv(ENV_GITHUB_API_URL);
    }

    protected String getGithubBaseUrl() {
        String url = System.getenv(ENV_GITHUB_BASE_URL);
        if (url != null && !url.endsWith("/")) {
            url += "/";
        }
        return url;
    }

    /**
     * Returns the jDeploy registry URL as-is from the environment (may or may not have trailing slash).
     */
    protected String getJdeployRegistry() {
        return System.getenv(ENV_JDEPLOY_REGISTRY);
    }

    /**
     * Returns the jDeploy WireMock base URL without trailing slash, suitable for admin API calls.
     */
    protected String getJdeployRegistryBaseUrl() {
        String url = System.getenv(ENV_JDEPLOY_REGISTRY);
        if (url != null && url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    protected String getGithubToken() {
        String token = System.getenv(ENV_GITHUB_TOKEN);
        return token != null ? token : "mock-github-token";
    }

    // ========================================================================
    // Project scaffolding helpers
    // ========================================================================

    protected File createTestJar(File projectDir, String jarName, String mainClass) throws IOException {
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

            jos.putNextEntry(new ZipEntry(mainClass.replace('.', '/') + ".class"));
            jos.write(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
            jos.closeEntry();
        }

        return jarFile;
    }

    protected File createPackageJson(File projectDir, String name, String version,
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

    protected void createIcon(File projectDir) throws IOException {
        FileUtils.copyInputStreamToFile(
                JDeploy.class.getResourceAsStream("icon.png"),
                new File(projectDir, "icon.png")
        );
    }

    protected File createJdeployBundle(File projectDir, File jarFile) throws IOException {
        File jdeployBundle = new File(projectDir, "jdeploy-bundle");
        jdeployBundle.mkdirs();
        FileUtils.copyFile(jarFile, new File(jdeployBundle, jarFile.getName()));
        return jdeployBundle;
    }

    // ========================================================================
    // Service wiring helpers
    // ========================================================================

    /**
     * Create a Config with the registry URL pointing to the mock jDeploy WireMock.
     */
    protected Config createMockConfig() {
        Config config = new Config();
        config.getProperties().setProperty("registry.url", getJdeployRegistry());
        return config;
    }

    /**
     * Create a fully wired GitHubPublishDriver pointing at the mock services.
     * The BasePublishDriver is mocked to simulate prepare() by copying jdeploy-bundle.
     */
    protected GitHubPublishDriver createGitHubPublishDriver() throws Exception {
        Config config = createMockConfig();
        PackagingConfig packagingConfig = new PackagingConfig(config);
        BundleCodeService bundleCodeService = new BundleCodeService(packagingConfig);

        GitHubReleaseCreator releaseCreator = new GitHubReleaseCreator();
        releaseCreator.setGithubUrl(getGithubBaseUrl());
        releaseCreator.setGithubApiUrl(getGithubApiBaseUrl() + "/repos/");

        PackageNameService packageNameService = mock(PackageNameService.class);
        when(packageNameService.getFullPackageName(any(), any())).thenAnswer(
                invocation -> {
                    // arg 0 is PublishTargetInterface, arg 1 is the package name string
                    return invocation.getArgument(1);
                }
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

        PublishBundleService publishBundleService = mock(PublishBundleService.class);
        BundleUploadRouter bundleUploadRouter = mock(BundleUploadRouter.class);
        BundleChecksumWriter bundleChecksumWriter = mock(BundleChecksumWriter.class);

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

    /**
     * Create a PublishingContext for a project directory.
     */
    protected PublishingContext createPublishingContext(File projectDir) throws Exception {
        return createPublishingContext(projectDir, false);
    }

    protected PublishingContext createPublishingContext(File projectDir, boolean dryRun) throws Exception {
        PackagingContext packagingContext = new PackagingContext.Builder()
                .directory(projectDir)
                .packageJsonFile(new File(projectDir, "package.json"))
                .out(System.out)
                .err(System.err)
                .exitOnFail(false)
                .build();

        NPM npm = mock(NPM.class);
        doNothing().when(npm).pack(any(File.class), any(File.class), anyBoolean());

        return new PublishingContext(
                packagingContext, dryRun, npm,
                getGithubToken(), null, null, null, null
        );
    }

    /**
     * Create a GitHub publish target for a given user/repo.
     */
    protected PublishTargetInterface createGitHubTarget(String user, String repo) {
        String repoUrl = getGithubBaseUrl() + user + "/" + repo;
        return new PublishTarget("github", PublishTargetType.GITHUB, repoUrl);
    }

    /**
     * Scaffold a complete project directory ready for GitHub publishing.
     * Returns the project directory.
     */
    protected File scaffoldProject(String dirName, String packageName, String version,
                                   String jarName, String mainClass) throws IOException {
        File projectDir = new File(tempDir, dirName);
        projectDir.mkdirs();

        File jarFile = createTestJar(projectDir, jarName, mainClass);
        createPackageJson(projectDir, packageName, version, jarName, null);
        createIcon(projectDir);
        createJdeployBundle(projectDir, jarFile);

        return projectDir;
    }
}

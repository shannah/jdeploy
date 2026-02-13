package ca.weblite.jdeploy.publishing.github;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettingsService;
import ca.weblite.jdeploy.factories.CheerpjServiceFactory;
import ca.weblite.jdeploy.factories.JDeployProjectFactory;
import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.models.Platform;
import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;
import ca.weblite.jdeploy.publishing.BasePublishDriver;
import ca.weblite.jdeploy.publishing.PublishingContext;
import ca.weblite.jdeploy.services.*;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

/**
 * Tests for prebuilt apps functionality in GitHubPublishDriver.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GitHubPublishDriverPrebuiltAppsTest {

    @Mock
    private BasePublishDriver baseDriver;

    @Mock
    private BundleCodeService bundleCodeService;

    @Mock
    private PackageNameService packageNameService;

    @Mock
    private CheerpjServiceFactory cheerpjServiceFactory;

    @Mock
    private GitHubReleaseCreator gitHubReleaseCreator;

    @Mock
    private DownloadPageSettingsService downloadPageSettingsService;

    @Mock
    private PublishTargetInterface target;

    @Mock
    private BundlerSettings bundlerSettings;

    @Mock
    private PlatformBundleGenerator platformBundleGenerator;

    @Mock
    private DefaultBundleService defaultBundleService;

    @Mock
    private JDeployProjectFactory projectFactory;

    @Mock
    private ca.weblite.jdeploy.environment.Environment environment;

    @Mock
    private JDeployFilesZipGenerator jdeployFilesZipGenerator;

    @Mock
    private PrebuiltAppRequirementService prebuiltAppRequirementService;

    @Mock
    private PrebuiltAppPackager prebuiltAppPackager;

    @Mock
    private PrebuiltAppBundlerService prebuiltAppBundlerService;

    @TempDir
    File tempDir;

    private GitHubPublishDriver driver;
    private File packageJsonFile;
    private File releaseFilesDir;
    private File publishDir;

    @BeforeEach
    void setUp() {
        driver = new GitHubPublishDriver(
                baseDriver,
                bundleCodeService,
                packageNameService,
                cheerpjServiceFactory,
                gitHubReleaseCreator,
                downloadPageSettingsService,
                platformBundleGenerator,
                defaultBundleService,
                projectFactory,
                environment,
                jdeployFilesZipGenerator,
                prebuiltAppRequirementService,
                prebuiltAppPackager,
                prebuiltAppBundlerService
        );

        packageJsonFile = new File(tempDir, "package.json");
        releaseFilesDir = new File(tempDir, "jdeploy/github-release-files");
        publishDir = new File(tempDir, "jdeploy/publish");
        releaseFilesDir.mkdirs();
        publishDir.mkdirs();
    }

    @Test
    @DisplayName("Should skip prebuilt apps when not enabled")
    void testPrebuiltAppsSkippedWhenNotEnabled() throws IOException {
        // Arrange
        createPackageJson(false); // No windows signing
        JDeployProject project = createMockProject(false);
        when(projectFactory.createProject(any())).thenReturn(project);
        when(prebuiltAppRequirementService.isPrebuiltAppsEnabled(project)).thenReturn(false);

        PublishingContext context = createPublishingContext();
        setupBasicMocks(context);

        // Act
        driver.prepare(context, target, bundlerSettings);

        // Assert
        // Verify prebuilt app bundler was not called
        verify(prebuiltAppBundlerService, never()).generateNativeBundles(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should call bundler when prebuilt apps enabled")
    void testPrebuiltAppsGeneratedWhenEnabled() throws Exception {
        // Arrange
        createPackageJson(true); // Windows signing enabled
        JDeployProject project = createMockProject(true);
        when(projectFactory.createProject(any())).thenReturn(project);
        when(prebuiltAppRequirementService.isPrebuiltAppsEnabled(project)).thenReturn(true);
        when(prebuiltAppRequirementService.getRequiredPlatforms(project))
                .thenReturn(Arrays.asList(Platform.WIN_X64, Platform.WIN_ARM64));

        // Mock empty bundle results (no actual bundles generated in test)
        when(prebuiltAppBundlerService.generateNativeBundles(any(), any(), any(), any(), any()))
                .thenReturn(new HashMap<>());

        PublishingContext context = createPublishingContext();
        setupBasicMocks(context);

        // Act
        driver.prepare(context, target, bundlerSettings);

        // Assert
        verify(prebuiltAppBundlerService).generateNativeBundles(
                any(), any(), any(),
                argThat(list -> list.contains(Platform.WIN_X64) && list.contains(Platform.WIN_ARM64)),
                any()
        );
    }

    @Test
    @DisplayName("Should embed prebuiltApps list into package.json when bundles succeed")
    void testPrebuiltAppsListEmbeddedInPackageJson() throws Exception {
        // Arrange
        createPackageJson(true);
        JDeployProject project = createMockProject(true);
        when(projectFactory.createProject(any())).thenReturn(project);
        when(prebuiltAppRequirementService.isPrebuiltAppsEnabled(project)).thenReturn(true);
        when(prebuiltAppRequirementService.getRequiredPlatforms(project))
                .thenReturn(Arrays.asList(Platform.WIN_X64, Platform.WIN_ARM64));

        // Mock successful bundle generation
        File mockBundleDir = new File(tempDir, "mock-bundle");
        mockBundleDir.mkdirs();
        Map<Platform, File> bundles = new HashMap<>();
        bundles.put(Platform.WIN_X64, mockBundleDir);
        bundles.put(Platform.WIN_ARM64, mockBundleDir);
        when(prebuiltAppBundlerService.generateNativeBundles(any(), any(), any(), any(), any()))
                .thenReturn(bundles);

        // Mock tarball creation
        File mockTarball = new File(releaseFilesDir, "test-1.0.0-win-x64-bin.tgz");
        mockTarball.createNewFile();
        when(prebuiltAppPackager.packageNativeBundleWithoutNpm(any(), any(), any(), any(), any()))
                .thenReturn(mockTarball);
        when(prebuiltAppPackager.generateChecksum(any())).thenReturn("abc123checksum==");

        PublishingContext context = createPublishingContext();
        setupBasicMocks(context);

        // Create publish package.json
        File publishPackageJson = new File(publishDir, "package.json");
        FileUtils.writeStringToFile(publishPackageJson, "{\"name\":\"test-app\",\"version\":\"1.0.0\",\"jdeploy\":{}}", StandardCharsets.UTF_8);

        // Act
        driver.prepare(context, target, bundlerSettings);

        // Assert - Check that prebuiltApps was added to package.json
        String updatedContent = FileUtils.readFileToString(publishPackageJson, StandardCharsets.UTF_8);
        JSONObject updatedJson = new JSONObject(updatedContent);
        assertTrue(updatedJson.has("jdeploy"));
        assertTrue(updatedJson.getJSONObject("jdeploy").has("prebuiltApps"));

        org.json.JSONArray prebuiltApps = updatedJson.getJSONObject("jdeploy").getJSONArray("prebuiltApps");
        List<String> platforms = new java.util.ArrayList<>();
        for (int i = 0; i < prebuiltApps.length(); i++) {
            platforms.add(prebuiltApps.getString(i));
        }
        assertTrue(platforms.contains("win-x64") || platforms.contains("win-arm64"),
                "Should contain at least one Windows platform");
    }

    // ==================== Helper Methods ====================

    private void createPackageJson(boolean windowsSigningEnabled) throws IOException {
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");

        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jar", "app.jar");

        if (windowsSigningEnabled) {
            JSONObject windowsSigning = new JSONObject();
            windowsSigning.put("enabled", true);
            jdeploy.put("windowsSigning", windowsSigning);
        }

        packageJson.put("jdeploy", jdeploy);

        FileUtils.writeStringToFile(packageJsonFile, packageJson.toString(2), StandardCharsets.UTF_8);
    }

    private JDeployProject createMockProject(boolean windowsSigningEnabled) {
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");

        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jar", "app.jar");

        if (windowsSigningEnabled) {
            JSONObject windowsSigning = new JSONObject();
            windowsSigning.put("enabled", true);
            jdeploy.put("windowsSigning", windowsSigning);
        }

        packageJson.put("jdeploy", jdeploy);

        return new JDeployProject(packageJsonFile.toPath(), packageJson);
    }

    private PublishingContext createPublishingContext() {
        PrintStream out = mock(PrintStream.class);
        PrintStream err = mock(PrintStream.class);
        NPM npm = mock(NPM.class);

        PackagingContext packagingContext = new PackagingContext.Builder()
                .packageJsonFile(packageJsonFile)
                .directory(tempDir)
                .out(out)
                .err(err)
                .build();

        return new PublishingContext(
                packagingContext,
                false,
                npm,
                null,
                null,
                null,
                null,
                null
        );
    }

    private void setupBasicMocks(PublishingContext context) throws IOException {
        // Setup target
        when(target.getUrl()).thenReturn("https://github.com/test/repo");
        when(target.getType()).thenReturn(PublishTargetType.GITHUB);

        // Setup base driver to create directories
        doAnswer(invocation -> {
            PublishingContext ctx = invocation.getArgument(0);
            ctx.getPublishDir().mkdirs();
            // Create publish package.json
            File publishPackageJson = ctx.getPublishPackageJsonFile();
            if (!publishPackageJson.exists()) {
                FileUtils.copyFile(packageJsonFile, publishPackageJson);
            }
            return null;
        }).when(baseDriver).prepare(any(), any(), any());

        // Setup cheerpj
        CheerpjService cheerpjService = mock(CheerpjService.class);
        when(cheerpjServiceFactory.create(any())).thenReturn(cheerpjService);
        when(cheerpjService.isEnabled()).thenReturn(false);

        // Setup npm pack
        NPM npm = context.npm;
        doNothing().when(npm).pack(any(), any(), anyBoolean());
    }
}

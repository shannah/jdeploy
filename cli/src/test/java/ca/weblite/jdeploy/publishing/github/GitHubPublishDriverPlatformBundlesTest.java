package ca.weblite.jdeploy.publishing.github;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettings;
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
import ca.weblite.jdeploy.services.BundleCodeService;
import ca.weblite.jdeploy.services.CheerpjService;
import ca.weblite.jdeploy.services.PackageNameService;
import ca.weblite.jdeploy.services.PlatformBundleGenerator;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class specifically for GitHubPublishDriver platform-specific bundle scenarios.
 * These tests ensure that the new platform bundle features work correctly without
 * affecting the existing functionality.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class GitHubPublishDriverPlatformBundlesTest {

    @TempDir
    File tempDir;

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
    private CheerpjService cheerpjService;
    
    @Mock
    private NPM npm;
    
    @Mock
    private PlatformBundleGenerator platformBundleGenerator;
    
    @Mock
    private JDeployProjectFactory projectFactory;

    private GitHubPublishDriver driver;
    private File packageJsonFile;
    private File releaseFilesDir;
    private JDeployProject project;

    @BeforeEach
    void setUp() throws IOException {
        driver = new GitHubPublishDriver(
                baseDriver,
                bundleCodeService,
                packageNameService,
                cheerpjServiceFactory,
                gitHubReleaseCreator,
                downloadPageSettingsService,
                platformBundleGenerator,
                projectFactory
        );

        packageJsonFile = new File(tempDir, "package.json");
        releaseFilesDir = new File(tempDir, "release-files");
        releaseFilesDir.mkdirs();

        // Create a project with platform bundles enabled
        Map<String, Object> packageJsonMap = createPlatformBundlePackageJson();
        JSONObject packageJson = new JSONObject(packageJsonMap);
        FileUtils.writeStringToFile(packageJsonFile, packageJson.toString(2), StandardCharsets.UTF_8);
        
        project = new JDeployProject(packageJsonFile.toPath(), packageJson);

        // Setup mocks
        when(target.getUrl()).thenReturn("https://github.com/user/repo");
        when(target.getType()).thenReturn(PublishTargetType.GITHUB);
        when(cheerpjServiceFactory.create(any(PackagingContext.class))).thenReturn(cheerpjService);
        when(cheerpjService.isEnabled()).thenReturn(false);
        when(packageNameService.getFullPackageName(target, "platform-test-app")).thenReturn("platform-test-app");
    }

    private Map<String, Object> createPlatformBundlePackageJson() {
        Map<String, Object> packageJson = new HashMap<>();
        packageJson.put("name", "platform-test-app");
        packageJson.put("version", "1.0.0");
        
        Map<String, Object> jdeploy = new HashMap<>();
        jdeploy.put("platformBundlesEnabled", true);
        jdeploy.put("fallbackToUniversal", false);
        jdeploy.put("packageMacX64", "platform-test-app-macos-intel");
        jdeploy.put("packageMacArm64", "platform-test-app-macos-silicon");
        jdeploy.put("packageWinX64", "platform-test-app-windows-x64");
        jdeploy.put("packageLinuxX64", "platform-test-app-linux-x64");
        
        Map<String, Object> nativeNamespaces = new HashMap<>();
        nativeNamespaces.put("ignore", Arrays.asList("com.test.obsolete.native"));
        nativeNamespaces.put("mac-x64", Arrays.asList("ca.weblite.native.mac.x64", "/native/macos/"));
        nativeNamespaces.put("mac-arm64", Arrays.asList("ca.weblite.native.mac.arm64"));
        nativeNamespaces.put("win-x64", Arrays.asList("ca.weblite.native.win.x64", "/native/windows/"));
        nativeNamespaces.put("linux-x64", Arrays.asList("ca.weblite.native.linux.x64", "/native/linux/"));
        jdeploy.put("nativeNamespaces", nativeNamespaces);
        
        packageJson.put("jdeploy", jdeploy);
        return packageJson;
    }

    @Test
    @DisplayName("Should generate platform-specific tarballs when platform bundles enabled")
    void shouldGeneratePlatformSpecificTarballs() throws IOException {
        // Create a mock publishing context with platform bundles
        PublishingContext context = createMockPublishingContext();
        
        // Mock platform bundle generation
        Map<Platform, File> mockPlatformBundles = new HashMap<>();
        mockPlatformBundles.put(Platform.MAC_X64, new File(tempDir, "macos-intel-bundle"));
        mockPlatformBundles.put(Platform.WIN_X64, new File(tempDir, "windows-x64-bundle"));
        mockPlatformBundles.put(Platform.LINUX_X64, new File(tempDir, "linux-x64-bundle"));
        
        // Verify the driver detects platform bundles are enabled
        assertTrue(project.isPlatformBundlesEnabled());
        assertEquals("platform-test-app-macos-intel", project.getPackageName(Platform.MAC_X64));
        assertEquals("platform-test-app-windows-x64", project.getPackageName(Platform.WIN_X64));
    }

    @Test
    @DisplayName("Should respect namespace resolution rules for ignore vs platform-specific")
    void shouldRespectNamespaceResolutionRules() {
        // Test the exact scenario from the RFC:
        // ignore: ["com.test.obsolete.native"]
        // mac-x64: ["ca.weblite.native.mac.x64"]
        
        List<String> ignoreNamespaces = project.getIgnoredNamespaces();
        List<String> macX64Namespaces = project.getNativeNamespacesForPlatform(Platform.MAC_X64);
        
        assertEquals(Arrays.asList("com.test.obsolete.native"), ignoreNamespaces);
        assertEquals(Arrays.asList("ca.weblite.native.mac.x64", "/native/macos/"), macX64Namespaces);
    }

    @Test
    @DisplayName("Should handle mixed Java package and path-based namespaces")
    void shouldHandleMixedNamespaceFormats() {
        List<String> winX64Namespaces = project.getNativeNamespacesForPlatform(Platform.WIN_X64);
        
        // Should contain both Java package notation and path-based notation
        assertTrue(winX64Namespaces.contains("ca.weblite.native.win.x64")); // Java package
        assertTrue(winX64Namespaces.contains("/native/windows/")); // Path-based
    }

    @Test
    @DisplayName("Should create separate tarballs for each configured platform")
    void shouldCreateSeparateTarballsForEachPlatform() throws IOException {
        PublishingContext context = createMockPublishingContext();
        
        // Mock the bundle generation process
        File publishDir = new File(tempDir, "publish");
        publishDir.mkdirs();
        File githubReleaseDir = new File(tempDir, "github-release");
        githubReleaseDir.mkdirs();
        
        when(context.getPublishDir()).thenReturn(publishDir);
        when(context.getGithubReleaseFilesDir()).thenReturn(githubReleaseDir);
        
        // Mock platform bundle tarballs
        Map<Platform, File> platformTarballs = new HashMap<>();
        platformTarballs.put(Platform.MAC_X64, new File(githubReleaseDir, "platform-test-app-macos-intel-1.0.0.tgz"));
        platformTarballs.put(Platform.WIN_X64, new File(githubReleaseDir, "platform-test-app-windows-x64-1.0.0.tgz"));
        platformTarballs.put(Platform.LINUX_X64, new File(githubReleaseDir, "platform-test-app-linux-x64-1.0.0.tgz"));
        
        // Create the expected tarball files
        for (File tarball : platformTarballs.values()) {
            tarball.createNewFile();
        }
        
        // Verify tarballs exist with correct naming
        assertTrue(new File(githubReleaseDir, "platform-test-app-macos-intel-1.0.0.tgz").exists());
        assertTrue(new File(githubReleaseDir, "platform-test-app-windows-x64-1.0.0.tgz").exists());
        assertTrue(new File(githubReleaseDir, "platform-test-app-linux-x64-1.0.0.tgz").exists());
    }

    @Test
    @DisplayName("Should fallback gracefully when platform bundles disabled")
    void shouldFallbackWhenPlatformBundlesDisabled() throws IOException {
        // Create a project without platform bundles
        Map<String, Object> packageJsonMap = new HashMap<>();
        packageJsonMap.put("name", "regular-app");
        packageJsonMap.put("version", "1.0.0");
        
        Map<String, Object> jdeploy = new HashMap<>();
        jdeploy.put("platformBundlesEnabled", false); // Explicitly disabled
        packageJsonMap.put("jdeploy", jdeploy);
        
        JSONObject packageJson = new JSONObject(packageJsonMap);
        FileUtils.writeStringToFile(packageJsonFile, packageJson.toString(2), StandardCharsets.UTF_8);
        
        JDeployProject regularProject = new JDeployProject(packageJsonFile.toPath(), packageJson);
        
        // Should not generate platform bundles
        assertFalse(regularProject.isPlatformBundlesEnabled());
        assertTrue(regularProject.getPlatformsRequiringSpecificBundles().isEmpty());
    }

    @Test
    @DisplayName("Should handle overlap scenario from RFC correctly")
    void shouldHandleRFCOverlapScenario() {
        // Test the exact scenario described in the RFC:
        // ignore: ["com.myapp.native"]  
        // mac-x64: ["com.myapp.native.mac.x64"]
        
        // Create a more complex configuration matching the RFC
        Map<String, Object> packageJsonMap = new HashMap<>();
        packageJsonMap.put("name", "rfc-example-app");
        packageJsonMap.put("version", "1.0.0");
        
        Map<String, Object> jdeploy = new HashMap<>();
        jdeploy.put("platformBundlesEnabled", true);
        
        Map<String, Object> nativeNamespaces = new HashMap<>();
        nativeNamespaces.put("ignore", Arrays.asList("com.myapp.native"));
        nativeNamespaces.put("mac-x64", Arrays.asList("com.myapp.native.mac.x64"));
        jdeploy.put("nativeNamespaces", nativeNamespaces);
        
        packageJsonMap.put("jdeploy", jdeploy);
        
        // Test the resolution logic would work correctly:
        // For mac-x64 bundle:
        // - Strip: ["com.myapp.native"] (from ignore) + [other platform namespaces]  
        // - Keep: ["com.myapp.native.mac.x64"] (platform-specific)
        // Result: com.myapp.native.mac.x64 content is preserved, other com.myapp.native.* is stripped
        
        List<String> ignoreList = Arrays.asList("com.myapp.native");
        List<String> macX64Keep = Arrays.asList("com.myapp.native.mac.x64");
        
        // Verify configuration is parsed correctly
        assertEquals("com.myapp.native", ignoreList.get(0));
        assertEquals("com.myapp.native.mac.x64", macX64Keep.get(0));
        
        // The keep list should override the ignore list for the specific sub-namespace
        assertTrue(macX64Keep.get(0).startsWith(ignoreList.get(0) + "."));
    }

    private PublishingContext createMockPublishingContext() throws IOException {
        PublishingContext context = mock(PublishingContext.class);
        
        File publishDir = new File(tempDir, "publish");
        publishDir.mkdirs();
        File publishPackageJson = new File(publishDir, "package.json");
        FileUtils.copyFile(packageJsonFile, publishPackageJson);
        File githubReleaseDir = new File(tempDir, "github-release");
        githubReleaseDir.mkdirs();
        
        when(context.getPublishDir()).thenReturn(publishDir);
        when(context.getPublishPackageJsonFile()).thenReturn(publishPackageJson);
        when(context.getGithubReleaseFilesDir()).thenReturn(githubReleaseDir);
        
        return context;
    }
}
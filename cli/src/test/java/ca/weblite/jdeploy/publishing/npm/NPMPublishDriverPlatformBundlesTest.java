package ca.weblite.jdeploy.publishing.npm;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.factories.JDeployProjectFactory;
import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.models.Platform;
import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.npm.OneTimePasswordRequestedException;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishing.BasePublishDriver;
import ca.weblite.jdeploy.publishing.OneTimePasswordProviderInterface;
import ca.weblite.jdeploy.publishing.PublishingContext;
import ca.weblite.jdeploy.services.PlatformBundleGenerator;
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
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class for NPMPublishDriver platform-specific bundle functionality.
 * Tests the new platform bundle publishing capabilities while ensuring
 * backward compatibility with existing functionality.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class NPMPublishDriverPlatformBundlesTest {

    @TempDir
    File tempDir;

    @Mock
    private BasePublishDriver baseDriver;

    @Mock
    private PlatformBundleGenerator platformBundleGenerator;

    @Mock
    private JDeployProjectFactory projectFactory;

    @Mock
    private PublishTargetInterface target;

    @Mock
    private OneTimePasswordProviderInterface otpProvider;

    @Mock
    private NPM npm;

    @Mock
    private PrintStream out;

    private NPMPublishDriver driver;
    private File packageJsonFile;
    private File publishDir;
    private PublishingContext publishingContext;
    private PackagingContext packagingContext;
    private JDeployProject project;

    @BeforeEach
    void setUp() throws IOException {
        driver = new NPMPublishDriver(baseDriver, platformBundleGenerator, projectFactory);

        // Set up test directories and files
        packageJsonFile = new File(tempDir, "package.json");
        publishDir = new File(tempDir, "jdeploy/publish");
        publishDir.mkdirs();

        // Create basic package.json
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");
        packageJson.put("description", "Test application");
        FileUtils.writeStringToFile(packageJsonFile, packageJson.toString(2), StandardCharsets.UTF_8);

        // Create real PackagingContext instance with all required parameters
        Map<String, Object> packageJsonMap = new HashMap<>();
        packageJsonMap.put("name", "test-app");
        packageJsonMap.put("version", "1.0.0");
        
        packagingContext = new PackagingContext(
                tempDir,            // directory
                packageJsonMap,     // packageJsonMap
                packageJsonFile,    // packageJsonFile
                false,              // alwaysClean
                false,              // doNotStripJavaFXFiles
                null,               // bundlesOverride
                null,               // installersOverride
                null,               // keyProvider
                null,               // packageSigningService
                out,                // out
                System.err,         // err
                System.in,          // in
                false,              // exitOnFail
                false               // verbose
        );

        // Create real PublishingContext instance
        publishingContext = new PublishingContext(
                packagingContext,
                false,              // alwaysPackageOnPublish
                npm,
                null,               // githubToken
                null,               // githubRepository
                null,               // githubRefName
                null,               // githubRefType
                null                // distTag
        );

        project = mock(JDeployProject.class);
        when(projectFactory.createProject(any(Path.class))).thenReturn(project);
    }

    @Test
    @DisplayName("Should publish only universal bundle when platform bundles disabled")
    void shouldPublishUniversalBundleWhenPlatformBundlesDisabled() throws IOException, OneTimePasswordRequestedException {
        // Given: Platform bundles are disabled
        when(project.isPlatformBundlesEnabled()).thenReturn(false);

        // When: Publishing
        driver.publish(publishingContext, target, otpProvider);

        // Then: Only universal bundle should be published
        verify(npm, times(1)).publish(eq(publishDir), eq(false), eq(null), eq(null));
        verify(platformBundleGenerator, never()).generatePlatformBundles(any(), any(), any());
        verify(out).println("Publishing main package...");
        verify(out).println("Successfully published main package to npm.");
    }

    @Test
    @DisplayName("Should publish universal bundle only when no platform packages configured")
    void shouldPublishUniversalBundleOnlyWhenNoPlatformPackages() throws IOException, OneTimePasswordRequestedException {
        // Given: Platform bundles enabled but no NPM package names configured
        when(project.isPlatformBundlesEnabled()).thenReturn(true);
        when(project.getPlatformsWithNpmPackageNames()).thenReturn(Collections.emptyList());

        // When: Publishing
        driver.publish(publishingContext, target, otpProvider);

        // Then: Only universal bundle should be published
        verify(npm, times(1)).publish(eq(publishDir), eq(false), eq(null), eq(null));
        verify(out).println("Publishing universal bundle...");
        verify(out).println("No platform-specific NPM package names configured, skipping platform bundle publishing");
    }

    @Test
    @DisplayName("Should publish universal bundle and platform bundles when configured")
    void shouldPublishUniversalAndPlatformBundlesWhenConfigured() throws IOException, OneTimePasswordRequestedException {
        // Given: Platform bundles enabled with configured package names
        when(project.isPlatformBundlesEnabled()).thenReturn(true);
        when(project.getPlatformsWithNpmPackageNames()).thenReturn(Arrays.asList(
                Platform.MAC_X64,
                Platform.WIN_X64
        ));
        when(project.getPackageName(Platform.MAC_X64)).thenReturn("test-app-macos-intel");
        when(project.getPackageName(Platform.WIN_X64)).thenReturn("test-app-windows-x64");

        // Set up platform bundle generation
        File macBundle = new File(tempDir, "mac-bundle");
        File winBundle = new File(tempDir, "win-bundle");
        macBundle.mkdirs();
        winBundle.mkdirs();

        // Create package.json files in platform bundles
        createPlatformPackageJson(macBundle, "test-app", "1.0.0", "Test application");
        createPlatformPackageJson(winBundle, "test-app", "1.0.0", "Test application");

        Map<Platform, File> platformBundles = new HashMap<>();
        platformBundles.put(Platform.MAC_X64, macBundle);
        platformBundles.put(Platform.WIN_X64, winBundle);

        when(platformBundleGenerator.generatePlatformBundles(eq(project), eq(publishDir), any(File.class)))
                .thenReturn(platformBundles);

        // When: Publishing
        driver.publish(publishingContext, target, otpProvider);

        // Then: Universal bundle and both platform bundles should be published
        verify(npm, times(3)).publish(any(File.class), eq(false), eq(null), eq(null));

        // Verify universal bundle published first
        verify(npm).publish(eq(publishDir), eq(false), eq(null), eq(null));

        // Verify platform bundles generated
        verify(platformBundleGenerator).generatePlatformBundles(eq(project), eq(publishDir), any(File.class));

        // Verify logging
        verify(out).println("Publishing universal bundle...");
        verify(out).println("Publishing platform-specific bundles to 2 additional NPM packages...");
        verify(out).println(contains("Created platform-specific package.json for test-app-macos-intel"));
        verify(out).println(contains("Created platform-specific package.json for test-app-windows-x64"));
        verify(out).println("All platform bundles published to npm successfully.");
    }

    @Test
    @DisplayName("Should handle 2FA/OTP for platform bundle publishing")
    void shouldHandle2FAForPlatformBundlePublishing() throws IOException, OneTimePasswordRequestedException {
        // Given: Platform bundles enabled and 2FA required
        when(project.isPlatformBundlesEnabled()).thenReturn(true);
        when(project.getPlatformsWithNpmPackageNames()).thenReturn(Arrays.asList(Platform.MAC_X64));
        when(project.getPackageName(Platform.MAC_X64)).thenReturn("test-app-macos-intel");

        File macBundle = new File(tempDir, "mac-bundle");
        macBundle.mkdirs();
        createPlatformPackageJson(macBundle, "test-app", "1.0.0", "Test application");

        Map<Platform, File> platformBundles = new HashMap<>();
        platformBundles.put(Platform.MAC_X64, macBundle);
        when(platformBundleGenerator.generatePlatformBundles(any(), any(), any())).thenReturn(platformBundles);

        // Simulate OTP required for platform bundle publishing
        doNothing().when(npm).publish(eq(publishDir), eq(false), eq(null), eq(null)); // Universal bundle succeeds
        doThrow(new OneTimePasswordRequestedException())
                .when(npm).publish(eq(macBundle), eq(false), eq(null), eq(null));
        doNothing().when(npm).publish(eq(macBundle), eq(false), eq("123456"), eq(null));

        when(otpProvider.promptForOneTimePassword(publishingContext, target)).thenReturn("123456");

        // When: Publishing
        driver.publish(publishingContext, target, otpProvider);

        // Then: Should handle OTP correctly
        verify(npm).publish(eq(publishDir), eq(false), eq(null), eq(null)); // Universal bundle
        verify(npm).publish(eq(macBundle), eq(false), eq(null), eq(null)); // First attempt fails
        verify(npm).publish(eq(macBundle), eq(false), eq("123456"), eq(null)); // Second attempt with OTP
        verify(otpProvider).promptForOneTimePassword(publishingContext, target);
    }

    @Test
    @DisplayName("Should fail gracefully when OTP not provided")
    void shouldFailWhenOTPNotProvided() throws IOException, OneTimePasswordRequestedException {
        // Given: Platform bundles enabled and OTP required but not provided
        when(project.isPlatformBundlesEnabled()).thenReturn(true);
        when(project.getPlatformsWithNpmPackageNames()).thenReturn(Arrays.asList(Platform.MAC_X64));
        when(project.getPackageName(Platform.MAC_X64)).thenReturn("test-app-macos-intel");

        File macBundle = new File(tempDir, "mac-bundle");
        macBundle.mkdirs();
        createPlatformPackageJson(macBundle, "test-app", "1.0.0", "Test application");

        Map<Platform, File> platformBundles = new HashMap<>();
        platformBundles.put(Platform.MAC_X64, macBundle);
        when(platformBundleGenerator.generatePlatformBundles(any(), any(), any())).thenReturn(platformBundles);

        doNothing().when(npm).publish(eq(publishDir), eq(false), eq(null), eq(null)); // Universal bundle succeeds
        doThrow(new OneTimePasswordRequestedException())
                .when(npm).publish(eq(macBundle), eq(false), eq(null), eq(null));

        when(otpProvider.promptForOneTimePassword(publishingContext, target)).thenReturn(null);

        // When/Then: Should throw IOException
        IOException exception = assertThrows(IOException.class, () -> {
            driver.publish(publishingContext, target, otpProvider);
        });

        assertTrue(exception.getMessage().contains("No OTP provided"));
    }

    @Test
    @DisplayName("Should skip publishing when platform bundle generation fails")
    void shouldSkipPublishingWhenPlatformBundleGenerationFails() throws IOException, OneTimePasswordRequestedException {
        // Given: Platform bundles enabled but generation fails for one platform
        when(project.isPlatformBundlesEnabled()).thenReturn(true);
        when(project.getPlatformsWithNpmPackageNames()).thenReturn(Arrays.asList(
                Platform.MAC_X64,
                Platform.WIN_X64
        ));
        when(project.getPackageName(Platform.MAC_X64)).thenReturn("test-app-macos-intel");
        when(project.getPackageName(Platform.WIN_X64)).thenReturn("test-app-windows-x64");

        // Only MAC bundle generated successfully
        File macBundle = new File(tempDir, "mac-bundle");
        macBundle.mkdirs();
        createPlatformPackageJson(macBundle, "test-app", "1.0.0", "Test application");

        Map<Platform, File> platformBundles = new HashMap<>();
        platformBundles.put(Platform.MAC_X64, macBundle);
        // WIN_X64 bundle not generated (null)

        when(platformBundleGenerator.generatePlatformBundles(eq(project), eq(publishDir), any(File.class)))
                .thenReturn(platformBundles);

        // When: Publishing
        driver.publish(publishingContext, target, otpProvider);

        // Then: Should publish universal + MAC bundle, skip Windows
        verify(npm, times(2)).publish(any(File.class), eq(false), eq(null), eq(null));
        verify(out).println("Warning: Platform bundle not generated for win-x64, skipping NPM publishing");
    }

    @Test
    @DisplayName("Should create correct platform-specific package.json")
    void shouldCreateCorrectPlatformSpecificPackageJson() throws IOException, OneTimePasswordRequestedException {
        // Given: Platform bundles enabled
        when(project.isPlatformBundlesEnabled()).thenReturn(true);
        when(project.getPlatformsWithNpmPackageNames()).thenReturn(Arrays.asList(Platform.MAC_X64));
        when(project.getPackageName(Platform.MAC_X64)).thenReturn("test-app-macos-intel");

        File macBundle = new File(tempDir, "mac-bundle");
        macBundle.mkdirs();
        createPlatformPackageJson(macBundle, "test-app", "1.0.0", "Test application");

        Map<Platform, File> platformBundles = new HashMap<>();
        platformBundles.put(Platform.MAC_X64, macBundle);
        when(platformBundleGenerator.generatePlatformBundles(any(), any(), any())).thenReturn(platformBundles);

        // When: Publishing
        driver.publish(publishingContext, target, otpProvider);

        // Then: Platform-specific package.json should be created correctly
        File platformPackageJson = new File(macBundle, "package.json");
        assertTrue(platformPackageJson.exists());

        String content = FileUtils.readFileToString(platformPackageJson, StandardCharsets.UTF_8);
        JSONObject packageJson = new JSONObject(content);

        assertEquals("test-app-macos-intel", packageJson.getString("name"));
        assertTrue(packageJson.getString("description").contains("mac-x64 bundle"));
        assertTrue(packageJson.has("jdeploy"));
        assertEquals("mac-x64", packageJson.getJSONObject("jdeploy").getString("platformBundle"));
    }

    private void createPlatformPackageJson(File bundleDir, String name, String version, String description) throws IOException {
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", name);
        packageJson.put("version", version);
        packageJson.put("description", description);

        JSONObject jdeployConfig = new JSONObject();
        jdeployConfig.put("jar", "test.jar");
        packageJson.put("jdeploy", jdeployConfig);

        File packageJsonFile = new File(bundleDir, "package.json");
        FileUtils.writeStringToFile(packageJsonFile, packageJson.toString(2), StandardCharsets.UTF_8);
    }
}
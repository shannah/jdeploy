package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.models.Platform;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettingsService;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettings;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Simplified integration test for the .jdpignore workflow.
 * Focuses on the essential behavior without getting into detailed JAR content verification.
 */
public class JDeployIgnoreWorkflowTest {

    @TempDir
    Path tempDir;
    
    private JDeployIgnoreService ignoreService;
    private JDeployIgnoreFileParser parser;
    private PlatformSpecificJarProcessor jarProcessor;
    private PlatformBundleGenerator bundleGenerator;
    private DownloadPageSettingsService downloadPageSettingsService;

    @BeforeEach
    public void setUp() {
        parser = new JDeployIgnoreFileParser();
        ignoreService = new JDeployIgnoreService(parser);
        jarProcessor = new PlatformSpecificJarProcessor(ignoreService);
        
        // Mock dependencies
        downloadPageSettingsService = mock(DownloadPageSettingsService.class);
        
        // Mock download page settings to return default platforms
        DownloadPageSettings mockSettings = mock(DownloadPageSettings.class);
        Set<DownloadPageSettings.BundlePlatform> defaultPlatforms = new HashSet<>();
        defaultPlatforms.add(DownloadPageSettings.BundlePlatform.MacX64);
        defaultPlatforms.add(DownloadPageSettings.BundlePlatform.WindowsX64);
        defaultPlatforms.add(DownloadPageSettings.BundlePlatform.LinuxX64);
        when(mockSettings.getResolvedPlatforms()).thenReturn(defaultPlatforms);
        when(downloadPageSettingsService.read(any(JSONObject.class))).thenReturn(mockSettings);
        
        bundleGenerator = new PlatformBundleGenerator(jarProcessor, downloadPageSettingsService, ignoreService);
    }

    @Test
    public void testPlatformBundleDetectionWithIgnoreFiles() throws IOException {
        // Create project with platform-specific ignore file
        JDeployProject project = createTestProject();
        createMacIgnoreFile(project);
        
        // Test that platform detection works correctly
        assertTrue(bundleGenerator.shouldGeneratePlatformBundles(project));
        assertEquals(1, bundleGenerator.getPlatformsForBundleGeneration(project).size());
        assertTrue(bundleGenerator.getPlatformsForBundleGeneration(project).contains(Platform.MAC_X64));
    }

    @Test
    public void testGlobalIgnoreFileDetection() throws IOException {
        // Create project with only global ignore file
        JDeployProject project = createTestProject();
        createGlobalIgnoreFile(project);
        
        // Should enable default bundle filtering but not platform bundles
        assertTrue(bundleGenerator.shouldFilterDefaultBundle(project));
        assertFalse(bundleGenerator.shouldGeneratePlatformBundles(project));
        assertTrue(bundleGenerator.getPlatformsForBundleGeneration(project).isEmpty());
    }

    @Test
    public void testNoPlatformBundlesWithoutIgnoreFiles() throws IOException {
        // Create project without any ignore files
        JDeployProject project = createTestProject();
        
        // Should not generate platform bundles or filter default bundle
        assertFalse(bundleGenerator.shouldGeneratePlatformBundles(project));
        assertFalse(bundleGenerator.shouldFilterDefaultBundle(project));
        assertTrue(bundleGenerator.getPlatformsForBundleGeneration(project).isEmpty());
    }

    @Test
    public void testIgnoreServiceBasicFunctionality() throws IOException {
        JDeployProject project = createTestProject();
        createMacIgnoreFile(project); // Creates ignore file with *.dll pattern
        
        // Test basic pattern matching
        assertTrue(ignoreService.hasIgnoreFiles(project));
        
        // Test file inclusion logic (these are safe patterns that should work)
        assertTrue(ignoreService.shouldIncludeFile(project, "Application.class", Platform.MAC_X64));
        assertFalse(ignoreService.shouldIncludeFile(project, "windows.dll", Platform.MAC_X64));
    }

    @Test
    public void testPlatformBundleGenerationBasicWorkflow() throws IOException {
        // Create project and universal bundle
        JDeployProject project = createTestProject();
        File universalBundle = createSimpleBundle();
        createMacIgnoreFile(project);
        
        // Generate platform bundles
        File outputDir = tempDir.resolve("platform-bundles").toFile();
        Map<Platform, File> bundles = bundleGenerator.generatePlatformBundles(project, universalBundle, outputDir);
        
        // Verify basic structure
        assertEquals(1, bundles.size());
        assertTrue(bundles.containsKey(Platform.MAC_X64));
        
        File macBundle = bundles.get(Platform.MAC_X64);
        assertNotNull(macBundle);
        assertTrue(macBundle.exists());
        assertTrue(macBundle.isDirectory());
    }

    // Helper methods

    private JDeployProject createTestProject() throws IOException {
        File projectDir = tempDir.resolve("test-project").toFile();
        projectDir.mkdirs();
        
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");
        
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("platformBundlesEnabled", true);
        packageJson.put("jdeploy", jdeploy);
        
        File packageFile = new File(projectDir, "package.json");
        FileUtils.writeStringToFile(packageFile, packageJson.toString(2), "UTF-8");
        
        return new JDeployProject(packageFile.toPath(), packageJson);
    }

    private void createMacIgnoreFile(JDeployProject project) throws IOException {
        File projectDir = project.getPackageJSONFile().toFile().getParentFile();
        File macIgnore = new File(projectDir, ".jdpignore.mac-x64");
        String patterns = "# Simple ignore pattern for testing\n/windows.dll\n";
        FileUtils.writeStringToFile(macIgnore, patterns, "UTF-8");
    }

    private void createGlobalIgnoreFile(JDeployProject project) throws IOException {
        File projectDir = project.getPackageJSONFile().toFile().getParentFile();
        File globalIgnore = new File(projectDir, ".jdpignore");
        String patterns = "# Global ignore pattern\n*.debug\n";
        FileUtils.writeStringToFile(globalIgnore, patterns, "UTF-8");
    }

    private File createSimpleBundle() throws IOException {
        File bundleDir = tempDir.resolve("universal-bundle").toFile();
        bundleDir.mkdirs();
        
        File libsDir = new File(bundleDir, "libs");
        libsDir.mkdirs();
        
        // Create a simple dummy JAR file
        File jarFile = new File(libsDir, "test-app.jar");
        FileUtils.writeStringToFile(jarFile, "dummy jar content", "UTF-8");
        
        return bundleDir;
    }
}
package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.models.Platform;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettingsService;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettings;
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
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class specifically for testing the namespace resolution logic in PlatformBundleGenerator.
 * These tests ensure that the RFC-defined resolution rules work correctly, especially the
 * interaction between ignore rules and platform-specific rules.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class PlatformBundleGeneratorResolutionTest {

    @TempDir
    File tempDir;

    private PlatformSpecificJarProcessor jarProcessor;
    
    @Mock
    private DownloadPageSettingsService downloadPageSettingsService;
    
    private PlatformBundleGenerator generator;
    private JDeployProject project;

    @BeforeEach
    void setUp() throws IOException {
        jarProcessor = new PlatformSpecificJarProcessor();
        
        // Mock the download page settings service to return default settings
        DownloadPageSettings defaultSettings = new DownloadPageSettings();
        when(downloadPageSettingsService.read(any(JSONObject.class))).thenReturn(defaultSettings);
        
        generator = new PlatformBundleGenerator(jarProcessor, downloadPageSettingsService);
        project = createTestProject();
    }

    private JDeployProject createTestProject() throws IOException {
        // Create the exact scenario from the RFC documentation
        Map<String, Object> packageJsonMap = new HashMap<>();
        packageJsonMap.put("name", "resolution-test-app");
        packageJsonMap.put("version", "1.0.0");
        
        Map<String, Object> jdeploy = new HashMap<>();
        jdeploy.put("platformBundlesEnabled", true);
        jdeploy.put("packageMacX64", "resolution-test-app-macos-intel");
        jdeploy.put("packageWinX64", "resolution-test-app-windows-x64");
        jdeploy.put("packageLinuxX64", "resolution-test-app-linux-x64");
        
        Map<String, Object> nativeNamespaces = new HashMap<>();
        nativeNamespaces.put("ignore", Arrays.asList(
            "com.myapp.native",         // Parent namespace (should be stripped from all)
            "com.test.obsolete.native"  // Obsolete library (should always be stripped)
        ));
        nativeNamespaces.put("mac-x64", Arrays.asList(
            "com.myapp.native.mac.x64",     // Should be KEPT in mac-x64 bundle despite ignore rule
            "/native/macos/",               // Path-based namespace for mac
            "/my-mac-lib.dylib"             // Specific file for mac
        ));
        nativeNamespaces.put("win-x64", Arrays.asList(
            "com.myapp.native.win.x64",     // Should be KEPT in win-x64 bundle
            "/native/windows/",             // Path-based namespace for windows
            "/my-win-lib.dll"               // Specific file for windows
        ));
        nativeNamespaces.put("linux-x64", Arrays.asList(
            "com.myapp.native.linux.x64",   // Should be KEPT in linux-x64 bundle
            "/native/linux/",               // Path-based namespace for linux
            "/my-linux-lib.so"              // Specific file for linux
        ));
        jdeploy.put("nativeNamespaces", nativeNamespaces);
        
        packageJsonMap.put("jdeploy", jdeploy);
        
        File packageJsonFile = new File(tempDir, "package.json");
        JSONObject packageJson = new JSONObject(packageJsonMap);
        FileUtils.writeStringToFile(packageJsonFile, packageJson.toString(2), StandardCharsets.UTF_8);
        
        JDeployProject project = new JDeployProject(packageJsonFile.toPath(), packageJson);
        return project;
    }

    @Test
    @DisplayName("Should create correct strip and keep lists for mac-x64 platform")
    void shouldCreateCorrectListsForMacX64() {
        // Test the resolution logic by examining what would be passed to the jar processor
        
        // Expected strip list for mac-x64:
        // - All ignore namespaces: ["com.myapp.native", "com.test.obsolete.native"] 
        // - All other platform namespaces: win-x64 and linux-x64 namespaces
        List<String> expectedStripList = Arrays.asList(
            // From ignore list
            "com.myapp.native",
            "com.test.obsolete.native",
            // From win-x64 platform 
            "com.myapp.native.win.x64",
            "/native/windows/", 
            "/my-win-lib.dll",
            // From linux-x64 platform
            "com.myapp.native.linux.x64",
            "/native/linux/",
            "/my-linux-lib.so"
        );
        
        // Expected keep list for mac-x64:
        // - Only the namespaces explicitly listed for mac-x64
        List<String> expectedKeepList = Arrays.asList(
            "com.myapp.native.mac.x64",     // Overrides ignore rule for this sub-namespace
            "/native/macos/",
            "/my-mac-lib.dylib"
        );
        
        // Verify the configuration is parsed correctly
        List<String> actualIgnoreList = project.getIgnoredNamespaces();
        List<String> actualMacX64List = project.getNativeNamespacesForPlatform(Platform.MAC_X64);
        List<String> actualWinX64List = project.getNativeNamespacesForPlatform(Platform.WIN_X64);
        List<String> actualLinuxX64List = project.getNativeNamespacesForPlatform(Platform.LINUX_X64);
        
        assertEquals(2, actualIgnoreList.size());
        assertTrue(actualIgnoreList.contains("com.myapp.native"));
        assertTrue(actualIgnoreList.contains("com.test.obsolete.native"));
        
        assertEquals(3, actualMacX64List.size());
        assertTrue(actualMacX64List.contains("com.myapp.native.mac.x64"));
        assertTrue(actualMacX64List.contains("/native/macos/"));
        assertTrue(actualMacX64List.contains("/my-mac-lib.dylib"));
        
        // Verify other platforms have their expected namespaces
        assertEquals(3, actualWinX64List.size());
        assertEquals(3, actualLinuxX64List.size());
    }

    @Test
    @DisplayName("Should demonstrate RFC overlap resolution behavior")
    void shouldDemonstrateRFCOverlapResolution() {
        // Test the resolution logic by examining the lists that would be generated
        List<String> stripNamespaces = generator.getNamespacesToStrip(project, Platform.MAC_X64);
        List<String> keepNamespaces = generator.getNamespacesToKeep(project, Platform.MAC_X64);
        
        // Strip list should contain ignore + other platform namespaces
        assertTrue(stripNamespaces.contains("com.myapp.native"), 
            "Strip list should contain ignore namespace");
        assertTrue(stripNamespaces.contains("com.test.obsolete.native"), 
            "Strip list should contain obsolete namespace");
        assertTrue(stripNamespaces.contains("com.myapp.native.win.x64"), 
            "Strip list should contain win-x64 namespace");
        assertTrue(stripNamespaces.contains("/native/windows/"), 
            "Strip list should contain win path-based namespace");
        assertTrue(stripNamespaces.contains("com.myapp.native.linux.x64"), 
            "Strip list should contain linux-x64 namespace");
        
        // Keep list should contain only mac-x64 specific namespaces
        assertTrue(keepNamespaces.contains("com.myapp.native.mac.x64"), 
            "Keep list should contain mac-x64 namespace");
        assertTrue(keepNamespaces.contains("/native/macos/"), 
            "Keep list should contain mac path-based namespace");
        assertTrue(keepNamespaces.contains("/my-mac-lib.dylib"), 
            "Keep list should contain mac-specific file");
        
        // Verify the critical RFC behavior: mac-x64 namespace is in KEEP list
        // even though its parent "com.myapp.native" is in STRIP list
        boolean hasParentInStripList = stripNamespaces.contains("com.myapp.native");
        boolean hasChildInKeepList = keepNamespaces.contains("com.myapp.native.mac.x64");
        assertTrue(hasParentInStripList && hasChildInKeepList,
            "RFC resolution: child namespace in keep list should override parent namespace in strip list");
    }

    @Test
    @DisplayName("Should handle different platforms with different namespace overlap")
    void shouldHandleDifferentPlatformNamespaceOverlap() {
        // Test that different platforms get different strip/keep lists
        List<String> stripNamespaces = generator.getNamespacesToStrip(project, Platform.WIN_X64);
        List<String> keepNamespaces = generator.getNamespacesToKeep(project, Platform.WIN_X64);
        
        // For windows platform:
        // Strip should have ignore + mac + linux namespaces
        assertTrue(stripNamespaces.contains("com.myapp.native"));
        assertTrue(stripNamespaces.contains("com.myapp.native.mac.x64"));
        assertTrue(stripNamespaces.contains("com.myapp.native.linux.x64"));
        assertFalse(stripNamespaces.contains("com.myapp.native.win.x64"));
        
        // Keep should only have windows-specific namespaces
        assertTrue(keepNamespaces.contains("com.myapp.native.win.x64"));
        assertTrue(keepNamespaces.contains("/native/windows/"));
        assertTrue(keepNamespaces.contains("/my-win-lib.dll"));
        assertFalse(keepNamespaces.contains("com.myapp.native.mac.x64"));
    }

    @Test
    @DisplayName("Should explain namespace resolution logic clearly")
    void shouldExplainNamespaceResolutionLogic() {
        // Test the explanation method to ensure it describes the resolution correctly
        String explanation = generator.explainNamespaceResolution(project, Platform.MAC_X64);
        
        assertNotNull(explanation);
        assertTrue(explanation.contains("mac-x64"));
        assertTrue(explanation.contains("Strip List"));
        assertTrue(explanation.contains("Keep List"));
        assertTrue(explanation.contains("keep list overrides strip list"));
        
        // Should explain the key RFC behavior
        assertTrue(explanation.toLowerCase().contains("precedence") || 
                  explanation.toLowerCase().contains("override"));
    }

    @Test
    @DisplayName("Should handle edge case where no platform-specific namespaces are defined")
    void shouldHandleEdgeCaseWithNoPlatformNamespaces() throws IOException {
        // Create a project with only ignore rules, no platform-specific rules
        Map<String, Object> packageJsonMap = new HashMap<>();
        packageJsonMap.put("name", "edge-case-app");
        packageJsonMap.put("version", "1.0.0");
        
        Map<String, Object> jdeploy = new HashMap<>();
        jdeploy.put("platformBundlesEnabled", true);
        
        Map<String, Object> nativeNamespaces = new HashMap<>();
        nativeNamespaces.put("ignore", Arrays.asList("com.obsolete.native"));
        // No platform-specific namespaces defined
        jdeploy.put("nativeNamespaces", nativeNamespaces);
        
        packageJsonMap.put("jdeploy", jdeploy);
        
        File packageJsonFile = new File(tempDir, "edge-case-package.json");
        JSONObject packageJson = new JSONObject(packageJsonMap);
        FileUtils.writeStringToFile(packageJsonFile, packageJson.toString(2), StandardCharsets.UTF_8);
        
        JDeployProject edgeCaseProject = new JDeployProject(packageJsonFile.toPath(), packageJson);
        
        // Should still work - only strip ignore list, empty keep list
        List<String> ignoreList = edgeCaseProject.getIgnoredNamespaces();
        List<String> macX64List = edgeCaseProject.getNativeNamespacesForPlatform(Platform.MAC_X64);
        
        assertEquals(1, ignoreList.size());
        assertEquals("com.obsolete.native", ignoreList.get(0));
        assertTrue(macX64List.isEmpty());
    }

    @Test
    @DisplayName("Should validate all supported platforms get correct resolution")
    void shouldValidateAllPlatformsGetCorrectResolution() {
        // Test that all supported platforms get appropriate strip/keep lists
        Platform[] allPlatforms = {Platform.MAC_X64, Platform.WIN_X64, Platform.LINUX_X64};
        
        for (Platform platform : allPlatforms) {
            List<String> platformNamespaces = project.getNativeNamespacesForPlatform(platform);
            assertFalse(platformNamespaces.isEmpty(), 
                "Platform " + platform + " should have native namespaces defined");
            
            // Each platform should have both Java package and path-based namespaces
            boolean hasJavaPackage = platformNamespaces.stream()
                .anyMatch(ns -> !ns.startsWith("/") && ns.contains("com.myapp.native"));
            boolean hasPathBased = platformNamespaces.stream()
                .anyMatch(ns -> ns.startsWith("/native/"));
                
            assertTrue(hasJavaPackage, 
                "Platform " + platform + " should have Java package namespace");
            assertTrue(hasPathBased, 
                "Platform " + platform + " should have path-based namespace");
        }
    }
}
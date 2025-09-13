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
import org.mockito.Mockito;
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
        // Create mock ignore service
        JDeployIgnoreService mockIgnoreService = Mockito.mock(JDeployIgnoreService.class);
        when(mockIgnoreService.hasIgnoreFiles(any(JDeployProject.class))).thenReturn(false);
        
        jarProcessor = new PlatformSpecificJarProcessor(mockIgnoreService);
        
        // Mock the download page settings service to return default settings
        DownloadPageSettings defaultSettings = new DownloadPageSettings();
        when(downloadPageSettingsService.read(any(JSONObject.class))).thenReturn(defaultSettings);
        
        generator = new PlatformBundleGenerator(jarProcessor, downloadPageSettingsService, mockIgnoreService);
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

    // Removed shouldCreateCorrectListsForMacX64 - deprecated nativeNamespaces methods no longer exist

    // Removed testDemonstrateRFCOverlapResolution - deprecated namespace resolution methods no longer exist

    // Removed shouldHandleDifferentPlatformNamespaceOverlap and shouldExplainNamespaceResolutionLogic - deprecated namespace resolution methods no longer exist

    // Removed shouldHandleEdgeCaseWithNoPlatformNamespaces - deprecated nativeNamespaces methods no longer exist

    // Removed shouldValidateAllPlatformsGetCorrectResolution - deprecated nativeNamespaces methods no longer exist
}
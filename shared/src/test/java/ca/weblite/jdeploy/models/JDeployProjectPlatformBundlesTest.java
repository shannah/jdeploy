package ca.weblite.jdeploy.models;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JDeployProjectPlatformBundlesTest {

    private JDeployProject project;
    private JSONObject packageJSON;

    @BeforeEach
    public void setUp() {
        packageJSON = new JSONObject();
    }

    private void createProject() {
        project = new JDeployProject(Paths.get("package.json"), packageJSON);
    }

    @Test
    public void testIsPlatformBundlesEnabled_Default() {
        createProject();
        assertFalse(project.isPlatformBundlesEnabled());
    }

    @Test
    public void testIsPlatformBundlesEnabled_Enabled() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("platformBundlesEnabled", true);
        packageJSON.put("jdeploy", jdeploy);
        
        createProject();
        assertTrue(project.isPlatformBundlesEnabled());
    }

    @Test
    public void testIsPlatformBundlesEnabled_Disabled() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("platformBundlesEnabled", false);
        packageJSON.put("jdeploy", jdeploy);
        
        createProject();
        assertFalse(project.isPlatformBundlesEnabled());
    }

    @Test
    public void testIsFallbackToUniversal_Default() {
        createProject();
        assertTrue(project.isFallbackToUniversal());
    }

    @Test
    public void testIsFallbackToUniversal_Enabled() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("fallbackToUniversal", true);
        packageJSON.put("jdeploy", jdeploy);
        
        createProject();
        assertTrue(project.isFallbackToUniversal());
    }

    @Test
    public void testIsFallbackToUniversal_Disabled() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("fallbackToUniversal", false);
        packageJSON.put("jdeploy", jdeploy);
        
        createProject();
        assertFalse(project.isFallbackToUniversal());
    }

    @Test
    public void testGetPackageName_NotConfigured() {
        createProject();
        assertNull(project.getPackageName(Platform.MAC_X64));
        assertNull(project.getPackageName(Platform.WIN_ARM64));
        assertNull(project.getPackageName(null));
    }

    @Test
    public void testGetPackageName_Configured() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("packageMacX64", "myapp-mac-intel");
        jdeploy.put("packageWinArm64", "myapp-windows-arm");
        packageJSON.put("jdeploy", jdeploy);
        
        createProject();
        assertEquals("myapp-mac-intel", project.getPackageName(Platform.MAC_X64));
        assertEquals("myapp-windows-arm", project.getPackageName(Platform.WIN_ARM64));
        assertNull(project.getPackageName(Platform.LINUX_X64)); // Not configured
    }

    @Test
    public void testGetNativeNamespaces_Empty() {
        createProject();
        Map<Platform, List<String>> namespaces = project.getNativeNamespaces();
        assertTrue(namespaces.isEmpty());
    }

    @Test
    public void testGetNativeNamespaces_Configured() {
        JSONObject jdeploy = new JSONObject();
        JSONObject nativeNamespaces = new JSONObject();
        
        JSONArray macX64Namespaces = new JSONArray();
        macX64Namespaces.put("ca.weblite.native.mac.x64");
        macX64Namespaces.put("com.example.mac.x64");
        nativeNamespaces.put("mac-x64", macX64Namespaces);
        
        JSONArray winArm64Namespaces = new JSONArray();
        winArm64Namespaces.put("ca.weblite.native.win.arm64");
        nativeNamespaces.put("win-arm64", winArm64Namespaces);
        
        jdeploy.put("nativeNamespaces", nativeNamespaces);
        packageJSON.put("jdeploy", jdeploy);
        
        createProject();
        Map<Platform, List<String>> result = project.getNativeNamespaces();
        
        assertEquals(2, result.size());
        assertTrue(result.containsKey(Platform.MAC_X64));
        assertTrue(result.containsKey(Platform.WIN_ARM64));
        
        List<String> macNamespaces = result.get(Platform.MAC_X64);
        assertEquals(2, macNamespaces.size());
        assertTrue(macNamespaces.contains("ca.weblite.native.mac.x64"));
        assertTrue(macNamespaces.contains("com.example.mac.x64"));
        
        List<String> winNamespaces = result.get(Platform.WIN_ARM64);
        assertEquals(1, winNamespaces.size());
        assertTrue(winNamespaces.contains("ca.weblite.native.win.arm64"));
    }

    @Test
    public void testGetNativeNamespacesForPlatform_NotConfigured() {
        createProject();
        List<String> namespaces = project.getNativeNamespacesForPlatform(Platform.MAC_X64);
        assertTrue(namespaces.isEmpty());
        
        assertTrue(project.getNativeNamespacesForPlatform(null).isEmpty());
    }

    @Test
    public void testGetNativeNamespacesForPlatform_Configured() {
        JSONObject jdeploy = new JSONObject();
        JSONObject nativeNamespaces = new JSONObject();
        
        JSONArray macX64Namespaces = new JSONArray();
        macX64Namespaces.put("ca.weblite.native.mac.x64");
        macX64Namespaces.put("com.example.mac.x64");
        nativeNamespaces.put("mac-x64", macX64Namespaces);
        
        jdeploy.put("nativeNamespaces", nativeNamespaces);
        packageJSON.put("jdeploy", jdeploy);
        
        createProject();
        List<String> result = project.getNativeNamespacesForPlatform(Platform.MAC_X64);
        
        assertEquals(2, result.size());
        assertTrue(result.contains("ca.weblite.native.mac.x64"));
        assertTrue(result.contains("com.example.mac.x64"));
        
        // Platform without namespaces should return empty list
        assertTrue(project.getNativeNamespacesForPlatform(Platform.WIN_X64).isEmpty());
    }

    @Test
    public void testGetIgnoredNamespaces_Empty() {
        createProject();
        List<String> ignored = project.getIgnoredNamespaces();
        assertTrue(ignored.isEmpty());
    }

    @Test
    public void testGetIgnoredNamespaces_Configured() {
        JSONObject jdeploy = new JSONObject();
        JSONObject nativeNamespaces = new JSONObject();
        
        JSONArray ignoreArray = new JSONArray();
        ignoreArray.put("com.obsolete.legacy.native");
        ignoreArray.put("com.testing.mocklibs.native");
        nativeNamespaces.put("ignore", ignoreArray);
        
        jdeploy.put("nativeNamespaces", nativeNamespaces);
        packageJSON.put("jdeploy", jdeploy);
        
        createProject();
        List<String> result = project.getIgnoredNamespaces();
        
        assertEquals(2, result.size());
        assertTrue(result.contains("com.obsolete.legacy.native"));
        assertTrue(result.contains("com.testing.mocklibs.native"));
    }

    @Test
    public void testGetAllOtherPlatformNamespaces() {
        JSONObject jdeploy = new JSONObject();
        JSONObject nativeNamespaces = new JSONObject();
        
        // Add ignored namespaces
        JSONArray ignoreArray = new JSONArray();
        ignoreArray.put("com.testing.mocklibs");
        nativeNamespaces.put("ignore", ignoreArray);
        
        // Add platform-specific namespaces
        JSONArray macX64Namespaces = new JSONArray();
        macX64Namespaces.put("ca.weblite.native.mac.x64");
        nativeNamespaces.put("mac-x64", macX64Namespaces);
        
        JSONArray winX64Namespaces = new JSONArray();
        winX64Namespaces.put("ca.weblite.native.win.x64");
        nativeNamespaces.put("win-x64", winX64Namespaces);
        
        JSONArray linuxX64Namespaces = new JSONArray();
        linuxX64Namespaces.put("ca.weblite.native.linux.x64");
        nativeNamespaces.put("linux-x64", linuxX64Namespaces);
        
        jdeploy.put("nativeNamespaces", nativeNamespaces);
        packageJSON.put("jdeploy", jdeploy);
        
        createProject();
        
        // Test for MAC_X64 platform - should get ignored + other platforms' namespaces
        List<String> macResult = project.getAllOtherPlatformNamespaces(Platform.MAC_X64);
        assertEquals(3, macResult.size());
        assertTrue(macResult.contains("com.testing.mocklibs")); // ignored
        assertTrue(macResult.contains("ca.weblite.native.win.x64")); // other platform
        assertTrue(macResult.contains("ca.weblite.native.linux.x64")); // other platform
        assertFalse(macResult.contains("ca.weblite.native.mac.x64")); // own platform excluded
        
        // Test for WIN_X64 platform
        List<String> winResult = project.getAllOtherPlatformNamespaces(Platform.WIN_X64);
        assertEquals(3, winResult.size());
        assertTrue(winResult.contains("com.testing.mocklibs")); // ignored
        assertTrue(winResult.contains("ca.weblite.native.mac.x64")); // other platform
        assertTrue(winResult.contains("ca.weblite.native.linux.x64")); // other platform
        assertFalse(winResult.contains("ca.weblite.native.win.x64")); // own platform excluded
    }

    @Test
    public void testGetPlatformsRequiringSpecificBundles_NoNamespaces() {
        createProject();
        List<Platform> platforms = project.getPlatformsRequiringSpecificBundles();
        assertTrue(platforms.isEmpty());
    }

    @Test
    public void testGetPlatformsRequiringSpecificBundles_WithPlatformNamespaces() {
        JSONObject jdeploy = new JSONObject();
        JSONObject nativeNamespaces = new JSONObject();
        
        // Add platform-specific namespaces - should only return platforms with namespaces defined
        JSONArray macX64Namespaces = new JSONArray();
        macX64Namespaces.put("ca.weblite.native.mac.x64");
        nativeNamespaces.put("mac-x64", macX64Namespaces);
        
        jdeploy.put("nativeNamespaces", nativeNamespaces);
        packageJSON.put("jdeploy", jdeploy);
        
        createProject();
        List<Platform> platforms = project.getPlatformsRequiringSpecificBundles();
        
        assertEquals(1, platforms.size()); // Only mac-x64 has namespaces defined
        assertTrue(platforms.contains(Platform.MAC_X64));
    }

    @Test
    public void testGetPlatformsWithNpmPackageNames_None() {
        createProject();
        List<Platform> platforms = project.getPlatformsWithNpmPackageNames();
        assertTrue(platforms.isEmpty());
    }

    @Test
    public void testGetPlatformsWithNpmPackageNames_Some() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("packageMacX64", "myapp-mac-intel");
        jdeploy.put("packageLinuxArm64", "myapp-linux-arm");
        packageJSON.put("jdeploy", jdeploy);
        
        createProject();
        List<Platform> platforms = project.getPlatformsWithNpmPackageNames();
        
        assertEquals(2, platforms.size());
        assertTrue(platforms.contains(Platform.MAC_X64));
        assertTrue(platforms.contains(Platform.LINUX_ARM64));
        assertFalse(platforms.contains(Platform.WIN_X64));
    }

    @Test
    public void testComplexScenario() {
        // Test a complex scenario with multiple configurations
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("platformBundlesEnabled", true);
        jdeploy.put("fallbackToUniversal", false);
        jdeploy.put("packageMacX64", "myapp-mac-intel");
        jdeploy.put("packageWinX64", "myapp-windows-x64");
        
        JSONObject nativeNamespaces = new JSONObject();
        
        // Ignored namespaces
        JSONArray ignoreArray = new JSONArray();
        ignoreArray.put("com.testing.debug");
        nativeNamespaces.put("ignore", ignoreArray);
        
        // Platform-specific namespaces
        JSONArray macNamespaces = new JSONArray();
        macNamespaces.put("ca.weblite.native.mac.x64");
        macNamespaces.put("com.example.mac");
        nativeNamespaces.put("mac-x64", macNamespaces);
        
        JSONArray winNamespaces = new JSONArray();
        winNamespaces.put("ca.weblite.native.win.x64");
        nativeNamespaces.put("win-x64", winNamespaces);
        
        jdeploy.put("nativeNamespaces", nativeNamespaces);
        packageJSON.put("jdeploy", jdeploy);
        
        createProject();
        
        // Verify all configurations
        assertTrue(project.isPlatformBundlesEnabled());
        assertFalse(project.isFallbackToUniversal());
        
        assertEquals("myapp-mac-intel", project.getPackageName(Platform.MAC_X64));
        assertEquals("myapp-windows-x64", project.getPackageName(Platform.WIN_X64));
        assertNull(project.getPackageName(Platform.LINUX_X64));
        
        List<String> ignored = project.getIgnoredNamespaces();
        assertEquals(1, ignored.size());
        assertTrue(ignored.contains("com.testing.debug"));
        
        List<String> macPlatformNamespaces = project.getNativeNamespacesForPlatform(Platform.MAC_X64);
        assertEquals(2, macPlatformNamespaces.size());
        assertTrue(macPlatformNamespaces.contains("ca.weblite.native.mac.x64"));
        assertTrue(macPlatformNamespaces.contains("com.example.mac"));
        
        // Only platforms with native namespaces should require specific bundles
        List<Platform> platformsRequiringBundles = project.getPlatformsRequiringSpecificBundles();
        assertEquals(2, platformsRequiringBundles.size()); // mac-x64 and win-x64 have namespaces
        assertTrue(platformsRequiringBundles.contains(Platform.MAC_X64));
        assertTrue(platformsRequiringBundles.contains(Platform.WIN_X64));
        
        // Only 2 platforms have NPM package names
        List<Platform> platformsWithNpmNames = project.getPlatformsWithNpmPackageNames();
        assertEquals(2, platformsWithNpmNames.size());
    }
}
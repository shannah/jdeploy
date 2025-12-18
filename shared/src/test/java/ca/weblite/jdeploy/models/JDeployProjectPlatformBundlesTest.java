package ca.weblite.jdeploy.models;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the basic JDeployProject functionality in the shared module.
 * Note: This test file has been updated after removal of the nativeNamespaces implementation.
 * The nativeNamespaces functionality is now handled in the CLI module via .jdpignore files.
 */
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
        // Test a complex scenario with platform configuration (without deprecated nativeNamespaces)
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("platformBundlesEnabled", true);
        jdeploy.put("fallbackToUniversal", false);
        jdeploy.put("packageMacX64", "myapp-mac-intel");
        jdeploy.put("packageWinX64", "myapp-windows-x64");
        packageJSON.put("jdeploy", jdeploy);
        
        createProject();
        
        // Verify basic configurations still work
        assertTrue(project.isPlatformBundlesEnabled());
        assertFalse(project.isFallbackToUniversal());
        
        assertEquals("myapp-mac-intel", project.getPackageName(Platform.MAC_X64));
        assertEquals("myapp-windows-x64", project.getPackageName(Platform.WIN_X64));
        assertNull(project.getPackageName(Platform.LINUX_X64));
        
        // Only 2 platforms have NPM package names
        List<Platform> platformsWithNpmNames = project.getPlatformsWithNpmPackageNames();
        assertEquals(2, platformsWithNpmNames.size());
        assertTrue(platformsWithNpmNames.contains(Platform.MAC_X64));
        assertTrue(platformsWithNpmNames.contains(Platform.WIN_X64));
    }

    // Note: The following functionality has been moved to the CLI module and is now handled via .jdpignore files:
    // - getNativeNamespaces()
    // - getNativeNamespacesForPlatform()
    // - getIgnoredNamespaces() 
    // - getAllOtherPlatformNamespaces()
    // - getPlatformsRequiringSpecificBundles()
    // 
    // The shared module now only contains basic project configuration properties.
}
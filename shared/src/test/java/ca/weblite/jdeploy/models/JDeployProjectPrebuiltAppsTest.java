package ca.weblite.jdeploy.models;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JDeployProject prebuilt apps and Windows signing functionality.
 */
public class JDeployProjectPrebuiltAppsTest {

    private JDeployProject project;
    private JSONObject packageJSON;

    @BeforeEach
    public void setUp() {
        packageJSON = new JSONObject();
    }

    private void createProject() {
        project = new JDeployProject(Paths.get("package.json"), packageJSON);
    }

    // ==================== getPrebuiltApps Tests ====================

    @Test
    public void testGetPrebuiltApps_NotConfigured() {
        createProject();
        List<String> apps = project.getPrebuiltApps();
        assertTrue(apps.isEmpty());
    }

    @Test
    public void testGetPrebuiltApps_EmptyArray() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("prebuiltApps", new JSONArray());
        packageJSON.put("jdeploy", jdeploy);

        createProject();
        List<String> apps = project.getPrebuiltApps();
        assertTrue(apps.isEmpty());
    }

    @Test
    public void testGetPrebuiltApps_WindowsPlatforms() {
        JSONObject jdeploy = new JSONObject();
        JSONArray platforms = new JSONArray();
        platforms.put("win-x64");
        platforms.put("win-arm64");
        jdeploy.put("prebuiltApps", platforms);
        packageJSON.put("jdeploy", jdeploy);

        createProject();
        List<String> apps = project.getPrebuiltApps();

        assertEquals(2, apps.size());
        assertTrue(apps.contains("win-x64"));
        assertTrue(apps.contains("win-arm64"));
    }

    @Test
    public void testGetPrebuiltApps_AllPlatforms() {
        JSONObject jdeploy = new JSONObject();
        JSONArray platforms = new JSONArray();
        platforms.put("win-x64");
        platforms.put("win-arm64");
        platforms.put("mac-x64");
        platforms.put("mac-arm64");
        jdeploy.put("prebuiltApps", platforms);
        packageJSON.put("jdeploy", jdeploy);

        createProject();
        List<String> apps = project.getPrebuiltApps();

        assertEquals(4, apps.size());
        assertTrue(apps.contains("win-x64"));
        assertTrue(apps.contains("win-arm64"));
        assertTrue(apps.contains("mac-x64"));
        assertTrue(apps.contains("mac-arm64"));
    }

    // ==================== setPrebuiltApps Tests ====================

    @Test
    public void testSetPrebuiltApps_AddPlatforms() {
        JSONObject jdeploy = new JSONObject();
        packageJSON.put("jdeploy", jdeploy);

        createProject();
        project.setPrebuiltApps(Arrays.asList("win-x64", "win-arm64"));

        List<String> apps = project.getPrebuiltApps();
        assertEquals(2, apps.size());
        assertTrue(apps.contains("win-x64"));
        assertTrue(apps.contains("win-arm64"));
    }

    @Test
    public void testSetPrebuiltApps_EmptyList_RemovesField() {
        JSONObject jdeploy = new JSONObject();
        JSONArray platforms = new JSONArray();
        platforms.put("win-x64");
        jdeploy.put("prebuiltApps", platforms);
        packageJSON.put("jdeploy", jdeploy);

        createProject();
        project.setPrebuiltApps(Collections.emptyList());

        List<String> apps = project.getPrebuiltApps();
        assertTrue(apps.isEmpty());
    }

    @Test
    public void testSetPrebuiltApps_NullList_RemovesField() {
        JSONObject jdeploy = new JSONObject();
        JSONArray platforms = new JSONArray();
        platforms.put("win-x64");
        jdeploy.put("prebuiltApps", platforms);
        packageJSON.put("jdeploy", jdeploy);

        createProject();
        project.setPrebuiltApps(null);

        List<String> apps = project.getPrebuiltApps();
        assertTrue(apps.isEmpty());
    }

    @Test
    public void testSetPrebuiltApps_ReplacesExisting() {
        JSONObject jdeploy = new JSONObject();
        JSONArray oldPlatforms = new JSONArray();
        oldPlatforms.put("mac-x64");
        jdeploy.put("prebuiltApps", oldPlatforms);
        packageJSON.put("jdeploy", jdeploy);

        createProject();
        project.setPrebuiltApps(Arrays.asList("win-x64", "win-arm64"));

        List<String> apps = project.getPrebuiltApps();
        assertEquals(2, apps.size());
        assertTrue(apps.contains("win-x64"));
        assertTrue(apps.contains("win-arm64"));
        assertFalse(apps.contains("mac-x64")); // Old value should be gone
    }

    // ==================== hasPrebuiltApp Tests ====================

    @Test
    public void testHasPrebuiltApp_Exists() {
        JSONObject jdeploy = new JSONObject();
        JSONArray platforms = new JSONArray();
        platforms.put("win-x64");
        platforms.put("win-arm64");
        jdeploy.put("prebuiltApps", platforms);
        packageJSON.put("jdeploy", jdeploy);

        createProject();

        assertTrue(project.hasPrebuiltApp(Platform.WIN_X64));
        assertTrue(project.hasPrebuiltApp(Platform.WIN_ARM64));
    }

    @Test
    public void testHasPrebuiltApp_DoesNotExist() {
        JSONObject jdeploy = new JSONObject();
        JSONArray platforms = new JSONArray();
        platforms.put("win-x64");
        jdeploy.put("prebuiltApps", platforms);
        packageJSON.put("jdeploy", jdeploy);

        createProject();

        assertFalse(project.hasPrebuiltApp(Platform.MAC_X64));
        assertFalse(project.hasPrebuiltApp(Platform.LINUX_X64));
    }

    @Test
    public void testHasPrebuiltApp_NullPlatform() {
        JSONObject jdeploy = new JSONObject();
        JSONArray platforms = new JSONArray();
        platforms.put("win-x64");
        jdeploy.put("prebuiltApps", platforms);
        packageJSON.put("jdeploy", jdeploy);

        createProject();

        assertFalse(project.hasPrebuiltApp(null));
    }

    @Test
    public void testHasPrebuiltApp_NoConfig() {
        createProject();

        assertFalse(project.hasPrebuiltApp(Platform.WIN_X64));
        assertFalse(project.hasPrebuiltApp(Platform.MAC_X64));
    }

    // ==================== isWindowsSigningEnabled Tests ====================

    @Test
    public void testIsWindowsSigningEnabled_NotConfigured() {
        createProject();
        assertFalse(project.isWindowsSigningEnabled());
    }

    @Test
    public void testIsWindowsSigningEnabled_Enabled() {
        JSONObject jdeploy = new JSONObject();
        JSONObject windowsSigning = new JSONObject();
        windowsSigning.put("enabled", true);
        jdeploy.put("windowsSigning", windowsSigning);
        packageJSON.put("jdeploy", jdeploy);

        createProject();
        assertTrue(project.isWindowsSigningEnabled());
    }

    @Test
    public void testIsWindowsSigningEnabled_Disabled() {
        JSONObject jdeploy = new JSONObject();
        JSONObject windowsSigning = new JSONObject();
        windowsSigning.put("enabled", false);
        jdeploy.put("windowsSigning", windowsSigning);
        packageJSON.put("jdeploy", jdeploy);

        createProject();
        assertFalse(project.isWindowsSigningEnabled());
    }

    @Test
    public void testIsWindowsSigningEnabled_MissingEnabled() {
        // windowsSigning object exists but no "enabled" field
        JSONObject jdeploy = new JSONObject();
        JSONObject windowsSigning = new JSONObject();
        windowsSigning.put("provider", "jsign");
        jdeploy.put("windowsSigning", windowsSigning);
        packageJSON.put("jdeploy", jdeploy);

        createProject();
        assertFalse(project.isWindowsSigningEnabled());
    }

    // ==================== getWindowsSigningConfig Tests ====================

    @Test
    public void testGetWindowsSigningConfig_NotConfigured() {
        createProject();
        assertNull(project.getWindowsSigningConfig());
    }

    @Test
    public void testGetWindowsSigningConfig_Configured() {
        JSONObject jdeploy = new JSONObject();
        JSONObject windowsSigning = new JSONObject();
        windowsSigning.put("enabled", true);
        windowsSigning.put("provider", "jsign");
        jdeploy.put("windowsSigning", windowsSigning);
        packageJSON.put("jdeploy", jdeploy);

        createProject();

        JSONObject config = project.getWindowsSigningConfig();
        assertNotNull(config);
        assertTrue(config.getBoolean("enabled"));
        assertEquals("jsign", config.getString("provider"));
    }

    // ==================== getWindowsSigningProvider Tests ====================

    @Test
    public void testGetWindowsSigningProvider_NotConfigured() {
        createProject();
        assertEquals("jsign", project.getWindowsSigningProvider());
    }

    @Test
    public void testGetWindowsSigningProvider_DefaultWhenMissing() {
        JSONObject jdeploy = new JSONObject();
        JSONObject windowsSigning = new JSONObject();
        windowsSigning.put("enabled", true);
        // No provider specified
        jdeploy.put("windowsSigning", windowsSigning);
        packageJSON.put("jdeploy", jdeploy);

        createProject();
        assertEquals("jsign", project.getWindowsSigningProvider());
    }

    @Test
    public void testGetWindowsSigningProvider_CustomProvider() {
        JSONObject jdeploy = new JSONObject();
        JSONObject windowsSigning = new JSONObject();
        windowsSigning.put("enabled", true);
        windowsSigning.put("provider", "signtool");
        jdeploy.put("windowsSigning", windowsSigning);
        packageJSON.put("jdeploy", jdeploy);

        createProject();
        assertEquals("signtool", project.getWindowsSigningProvider());
    }

    // ==================== setWindowsSigningEnabled Tests ====================

    @Test
    public void testSetWindowsSigningEnabled_Enable() {
        JSONObject jdeploy = new JSONObject();
        packageJSON.put("jdeploy", jdeploy);

        createProject();
        project.setWindowsSigningEnabled(true);

        assertTrue(project.isWindowsSigningEnabled());
    }

    @Test
    public void testSetWindowsSigningEnabled_Disable() {
        JSONObject jdeploy = new JSONObject();
        JSONObject windowsSigning = new JSONObject();
        windowsSigning.put("enabled", true);
        jdeploy.put("windowsSigning", windowsSigning);
        packageJSON.put("jdeploy", jdeploy);

        createProject();
        assertTrue(project.isWindowsSigningEnabled());

        project.setWindowsSigningEnabled(false);
        assertFalse(project.isWindowsSigningEnabled());
    }

    @Test
    public void testSetWindowsSigningEnabled_CreatesConfigIfMissing() {
        JSONObject jdeploy = new JSONObject();
        packageJSON.put("jdeploy", jdeploy);

        createProject();
        assertNull(project.getWindowsSigningConfig());

        project.setWindowsSigningEnabled(true);

        assertNotNull(project.getWindowsSigningConfig());
        assertTrue(project.isWindowsSigningEnabled());
    }

    // ==================== Integration Tests ====================

    @Test
    public void testCompleteSigningScenario() {
        // Simulate a complete Windows signing configuration
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jar", "target/myapp.jar");

        // Windows signing enabled
        JSONObject windowsSigning = new JSONObject();
        windowsSigning.put("enabled", true);
        windowsSigning.put("provider", "jsign");
        jdeploy.put("windowsSigning", windowsSigning);

        // Prebuilt apps for Windows (populated at publish time)
        JSONArray prebuiltApps = new JSONArray();
        prebuiltApps.put("win-x64");
        prebuiltApps.put("win-arm64");
        jdeploy.put("prebuiltApps", prebuiltApps);

        packageJSON.put("jdeploy", jdeploy);
        packageJSON.put("name", "my-signed-app");
        packageJSON.put("version", "1.0.0");

        createProject();

        // Verify all settings
        assertTrue(project.isWindowsSigningEnabled());
        assertEquals(2, project.getPrebuiltApps().size());
        assertTrue(project.hasPrebuiltApp(Platform.WIN_X64));
        assertTrue(project.hasPrebuiltApp(Platform.WIN_ARM64));
        assertFalse(project.hasPrebuiltApp(Platform.MAC_X64)); // Not signed

        JSONObject signingConfig = project.getWindowsSigningConfig();
        assertNotNull(signingConfig);
        assertEquals("jsign", signingConfig.getString("provider"));
    }
}

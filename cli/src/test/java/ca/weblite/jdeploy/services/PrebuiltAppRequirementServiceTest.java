package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.models.Platform;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PrebuiltAppRequirementService to ensure it correctly determines
 * which platforms require prebuilt apps based on project configuration.
 */
public class PrebuiltAppRequirementServiceTest {

    private PrebuiltAppRequirementService service;

    @BeforeEach
    public void setUp() {
        service = new PrebuiltAppRequirementServiceImpl();
    }

    // ==================== getRequiredPlatforms Tests ====================

    @Test
    public void testGetRequiredPlatforms_WindowsSigningEnabled_ReturnsWindowsPlatforms() {
        // Given: A project with Windows signing enabled
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONObject windowsSigning = new JSONObject();
        windowsSigning.put("enabled", true);
        jdeploy.put("windowsSigning", windowsSigning);
        packageJson.put("jdeploy", jdeploy);

        JDeployProject project = createProject(packageJson);

        // When: Getting required platforms
        List<Platform> platforms = service.getRequiredPlatforms(project);

        // Then: Should return Windows platforms
        assertEquals(2, platforms.size());
        assertTrue(platforms.contains(Platform.WIN_X64));
        assertTrue(platforms.contains(Platform.WIN_ARM64));
    }

    @Test
    public void testGetRequiredPlatforms_WindowsSigningDisabled_ReturnsEmpty() {
        // Given: A project with Windows signing explicitly disabled
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONObject windowsSigning = new JSONObject();
        windowsSigning.put("enabled", false);
        jdeploy.put("windowsSigning", windowsSigning);
        packageJson.put("jdeploy", jdeploy);

        JDeployProject project = createProject(packageJson);

        // When: Getting required platforms
        List<Platform> platforms = service.getRequiredPlatforms(project);

        // Then: Should return empty list
        assertTrue(platforms.isEmpty());
    }

    @Test
    public void testGetRequiredPlatforms_NoSigningConfig_ReturnsEmpty() {
        // Given: A project with no signing configuration
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jar", "target/myapp.jar");
        packageJson.put("jdeploy", jdeploy);

        JDeployProject project = createProject(packageJson);

        // When: Getting required platforms
        List<Platform> platforms = service.getRequiredPlatforms(project);

        // Then: Should return empty list
        assertTrue(platforms.isEmpty());
    }

    @Test
    public void testGetRequiredPlatforms_NullProject_ReturnsEmpty() {
        // When: Getting required platforms for null project
        List<Platform> platforms = service.getRequiredPlatforms(null);

        // Then: Should return empty list
        assertTrue(platforms.isEmpty());
    }

    @Test
    public void testGetRequiredPlatforms_EmptyJDeployConfig_ReturnsEmpty() {
        // Given: A project with empty jdeploy config
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-app");

        JDeployProject project = createProject(packageJson);

        // When: Getting required platforms
        List<Platform> platforms = service.getRequiredPlatforms(project);

        // Then: Should return empty list
        assertTrue(platforms.isEmpty());
    }

    // ==================== requiresPrebuiltApp Tests ====================

    @Test
    public void testRequiresPrebuiltApp_WindowsSigningEnabled_WIN_X64_ReturnsTrue() {
        // Given: A project with Windows signing enabled
        JSONObject packageJson = createWindowsSigningEnabledConfig();
        JDeployProject project = createProject(packageJson);

        // When/Then: WIN_X64 should require prebuilt app
        assertTrue(service.requiresPrebuiltApp(project, Platform.WIN_X64));
    }

    @Test
    public void testRequiresPrebuiltApp_WindowsSigningEnabled_WIN_ARM64_ReturnsTrue() {
        // Given: A project with Windows signing enabled
        JSONObject packageJson = createWindowsSigningEnabledConfig();
        JDeployProject project = createProject(packageJson);

        // When/Then: WIN_ARM64 should require prebuilt app
        assertTrue(service.requiresPrebuiltApp(project, Platform.WIN_ARM64));
    }

    @Test
    public void testRequiresPrebuiltApp_WindowsSigningEnabled_MAC_X64_ReturnsFalse() {
        // Given: A project with Windows signing enabled (but not Mac)
        JSONObject packageJson = createWindowsSigningEnabledConfig();
        JDeployProject project = createProject(packageJson);

        // When/Then: MAC_X64 should not require prebuilt app
        assertFalse(service.requiresPrebuiltApp(project, Platform.MAC_X64));
    }

    @Test
    public void testRequiresPrebuiltApp_NullProject_ReturnsFalse() {
        // When/Then: Null project should return false
        assertFalse(service.requiresPrebuiltApp(null, Platform.WIN_X64));
    }

    @Test
    public void testRequiresPrebuiltApp_NullPlatform_ReturnsFalse() {
        // Given: A project with Windows signing enabled
        JSONObject packageJson = createWindowsSigningEnabledConfig();
        JDeployProject project = createProject(packageJson);

        // When/Then: Null platform should return false
        assertFalse(service.requiresPrebuiltApp(project, null));
    }

    // ==================== isPrebuiltAppsEnabled Tests ====================

    @Test
    public void testIsPrebuiltAppsEnabled_WindowsSigningEnabled_ReturnsTrue() {
        // Given: A project with Windows signing enabled
        JSONObject packageJson = createWindowsSigningEnabledConfig();
        JDeployProject project = createProject(packageJson);

        // When/Then: Prebuilt apps should be enabled
        assertTrue(service.isPrebuiltAppsEnabled(project));
    }

    @Test
    public void testIsPrebuiltAppsEnabled_NoSigningConfig_ReturnsFalse() {
        // Given: A project with no signing configuration
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jar", "target/myapp.jar");
        packageJson.put("jdeploy", jdeploy);

        JDeployProject project = createProject(packageJson);

        // When/Then: Prebuilt apps should not be enabled
        assertFalse(service.isPrebuiltAppsEnabled(project));
    }

    @Test
    public void testIsPrebuiltAppsEnabled_NullProject_ReturnsFalse() {
        // When/Then: Null project should return false
        assertFalse(service.isPrebuiltAppsEnabled(null));
    }

    // ==================== Helper Methods ====================

    private JDeployProject createProject(JSONObject packageJson) {
        Path dummyPath = Paths.get("/dummy/package.json");
        return new JDeployProject(dummyPath, packageJson);
    }

    private JSONObject createWindowsSigningEnabledConfig() {
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONObject windowsSigning = new JSONObject();
        windowsSigning.put("enabled", true);
        jdeploy.put("windowsSigning", windowsSigning);
        packageJson.put("jdeploy", jdeploy);
        return packageJson;
    }
}

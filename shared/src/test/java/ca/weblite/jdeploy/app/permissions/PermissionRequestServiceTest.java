package ca.weblite.jdeploy.app.permissions;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PermissionRequestServiceTest {

    private PermissionRequestService service;

    @BeforeEach
    void setUp() {
        service = new PermissionRequestService();
    }

    @Test
    void testGetPermissionRequests_EmptyPackageJson() {
        JSONObject packageJson = new JSONObject();
        
        Map<PermissionRequest, String> result = service.getPermissionRequests(packageJson);
        
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetPermissionRequests_NoJdeploySection() {
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");
        
        Map<PermissionRequest, String> result = service.getPermissionRequests(packageJson);
        
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetPermissionRequests_EmptyJdeploySection() {
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        packageJson.put("jdeploy", jdeploy);
        
        Map<PermissionRequest, String> result = service.getPermissionRequests(packageJson);
        
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetPermissionRequests_NoPermissionsArray() {
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jar", "app.jar");
        packageJson.put("jdeploy", jdeploy);
        
        Map<PermissionRequest, String> result = service.getPermissionRequests(packageJson);
        
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetPermissionRequests_EmptyPermissionsArray() {
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONArray permissions = new JSONArray();
        jdeploy.put("permissions", permissions);
        packageJson.put("jdeploy", jdeploy);
        
        Map<PermissionRequest, String> result = service.getPermissionRequests(packageJson);
        
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetPermissionRequests_SinglePermissionWithDescription() {
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONArray permissions = new JSONArray();
        
        JSONObject cameraPermission = new JSONObject();
        cameraPermission.put("name", "camera");
        cameraPermission.put("description", "Access camera to scan documents");
        permissions.put(cameraPermission);
        
        jdeploy.put("permissions", permissions);
        packageJson.put("jdeploy", jdeploy);
        
        Map<PermissionRequest, String> result = service.getPermissionRequests(packageJson);
        
        assertEquals(1, result.size());
        assertTrue(result.containsKey(PermissionRequest.CAMERA));
        assertEquals("Access camera to scan documents", result.get(PermissionRequest.CAMERA));
    }

    @Test
    void testGetPermissionRequests_SinglePermissionWithoutDescription() {
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONArray permissions = new JSONArray();
        
        JSONObject microphonePermission = new JSONObject();
        microphonePermission.put("name", "microphone");
        permissions.put(microphonePermission);
        
        jdeploy.put("permissions", permissions);
        packageJson.put("jdeploy", jdeploy);
        
        Map<PermissionRequest, String> result = service.getPermissionRequests(packageJson);
        
        assertEquals(1, result.size());
        assertTrue(result.containsKey(PermissionRequest.MICROPHONE));
        assertEquals("The microphone permission is required for functionality of this application.", 
                     result.get(PermissionRequest.MICROPHONE));
    }

    @Test
    void testGetPermissionRequests_MultiplePermissions() {
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONArray permissions = new JSONArray();
        
        JSONObject cameraPermission = new JSONObject();
        cameraPermission.put("name", "camera");
        cameraPermission.put("description", "Access camera to scan documents");
        permissions.put(cameraPermission);
        
        JSONObject locationPermission = new JSONObject();
        locationPermission.put("name", "location_when_in_use");
        locationPermission.put("description", "Location access for weather features");
        permissions.put(locationPermission);
        
        JSONObject microphonePermission = new JSONObject();
        microphonePermission.put("name", "microphone");
        permissions.put(microphonePermission);
        
        jdeploy.put("permissions", permissions);
        packageJson.put("jdeploy", jdeploy);
        
        Map<PermissionRequest, String> result = service.getPermissionRequests(packageJson);
        
        assertEquals(3, result.size());
        
        assertTrue(result.containsKey(PermissionRequest.CAMERA));
        assertEquals("Access camera to scan documents", result.get(PermissionRequest.CAMERA));
        
        assertTrue(result.containsKey(PermissionRequest.LOCATION_WHEN_IN_USE));
        assertEquals("Location access for weather features", result.get(PermissionRequest.LOCATION_WHEN_IN_USE));
        
        assertTrue(result.containsKey(PermissionRequest.MICROPHONE));
        assertEquals("The microphone permission is required for functionality of this application.", 
                     result.get(PermissionRequest.MICROPHONE));
    }

    @Test
    void testGetPermissionRequests_CaseInsensitivePermissionNames() {
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONArray permissions = new JSONArray();
        
        JSONObject cameraPermission = new JSONObject();
        cameraPermission.put("name", "CAMERA");
        cameraPermission.put("description", "Upper case camera permission");
        permissions.put(cameraPermission);
        
        JSONObject microphonePermission = new JSONObject();
        microphonePermission.put("name", "Microphone");
        microphonePermission.put("description", "Mixed case microphone permission");
        permissions.put(microphonePermission);
        
        jdeploy.put("permissions", permissions);
        packageJson.put("jdeploy", jdeploy);
        
        Map<PermissionRequest, String> result = service.getPermissionRequests(packageJson);
        
        assertEquals(2, result.size());
        assertTrue(result.containsKey(PermissionRequest.CAMERA));
        assertTrue(result.containsKey(PermissionRequest.MICROPHONE));
        assertEquals("Upper case camera permission", result.get(PermissionRequest.CAMERA));
        assertEquals("Mixed case microphone permission", result.get(PermissionRequest.MICROPHONE));
    }

    @Test
    void testGetPermissionRequests_InvalidPermissionName() {
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONArray permissions = new JSONArray();
        
        JSONObject invalidPermission = new JSONObject();
        invalidPermission.put("name", "invalid_permission");
        invalidPermission.put("description", "This should be ignored");
        permissions.put(invalidPermission);
        
        JSONObject validPermission = new JSONObject();
        validPermission.put("name", "camera");
        validPermission.put("description", "Valid camera permission");
        permissions.put(validPermission);
        
        jdeploy.put("permissions", permissions);
        packageJson.put("jdeploy", jdeploy);
        
        Map<PermissionRequest, String> result = service.getPermissionRequests(packageJson);
        
        assertEquals(1, result.size());
        assertTrue(result.containsKey(PermissionRequest.CAMERA));
        assertEquals("Valid camera permission", result.get(PermissionRequest.CAMERA));
    }

    @Test
    void testGetPermissionRequests_PermissionMissingName() {
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONArray permissions = new JSONArray();
        
        JSONObject missingNamePermission = new JSONObject();
        missingNamePermission.put("description", "Permission without name should be ignored");
        permissions.put(missingNamePermission);
        
        JSONObject validPermission = new JSONObject();
        validPermission.put("name", "camera");
        validPermission.put("description", "Valid camera permission");
        permissions.put(validPermission);
        
        jdeploy.put("permissions", permissions);
        packageJson.put("jdeploy", jdeploy);
        
        Map<PermissionRequest, String> result = service.getPermissionRequests(packageJson);
        
        assertEquals(1, result.size());
        assertTrue(result.containsKey(PermissionRequest.CAMERA));
        assertEquals("Valid camera permission", result.get(PermissionRequest.CAMERA));
    }

    @Test
    void testGetPermissionRequests_ComplexUnderscorePermissions() {
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONArray permissions = new JSONArray();
        
        JSONObject locationAlways = new JSONObject();
        locationAlways.put("name", "location_always");
        permissions.put(locationAlways);
        
        JSONObject photosAdd = new JSONObject();
        photosAdd.put("name", "photos_add");
        permissions.put(photosAdd);
        
        JSONObject userTracking = new JSONObject();
        userTracking.put("name", "user_tracking");
        userTracking.put("description", "Track user behavior for analytics");
        permissions.put(userTracking);
        
        jdeploy.put("permissions", permissions);
        packageJson.put("jdeploy", jdeploy);
        
        Map<PermissionRequest, String> result = service.getPermissionRequests(packageJson);
        
        assertEquals(3, result.size());
        
        assertTrue(result.containsKey(PermissionRequest.LOCATION_ALWAYS));
        assertEquals("The location always permission is required for functionality of this application.", 
                     result.get(PermissionRequest.LOCATION_ALWAYS));
        
        assertTrue(result.containsKey(PermissionRequest.PHOTOS_ADD));
        assertEquals("The photos add permission is required for functionality of this application.", 
                     result.get(PermissionRequest.PHOTOS_ADD));
        
        assertTrue(result.containsKey(PermissionRequest.USER_TRACKING));
        assertEquals("Track user behavior for analytics", result.get(PermissionRequest.USER_TRACKING));
    }

    @Test
    void testGetPermissionRequests_EmptyDescription() {
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONArray permissions = new JSONArray();
        
        JSONObject cameraPermission = new JSONObject();
        cameraPermission.put("name", "camera");
        cameraPermission.put("description", "");
        permissions.put(cameraPermission);
        
        jdeploy.put("permissions", permissions);
        packageJson.put("jdeploy", jdeploy);
        
        Map<PermissionRequest, String> result = service.getPermissionRequests(packageJson);
        
        assertEquals(1, result.size());
        assertTrue(result.containsKey(PermissionRequest.CAMERA));
        assertEquals("", result.get(PermissionRequest.CAMERA));
    }

    @Test
    void testGetPermissionRequests_NullDescription() {
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONArray permissions = new JSONArray();
        
        JSONObject cameraPermission = new JSONObject();
        cameraPermission.put("name", "camera");
        cameraPermission.put("description", (String) null);
        permissions.put(cameraPermission);
        
        jdeploy.put("permissions", permissions);
        packageJson.put("jdeploy", jdeploy);
        
        Map<PermissionRequest, String> result = service.getPermissionRequests(packageJson);
        
        assertEquals(1, result.size());
        assertTrue(result.containsKey(PermissionRequest.CAMERA));
        assertEquals("The camera permission is required for functionality of this application.", 
                     result.get(PermissionRequest.CAMERA));
    }

    @Test
    void testGetPermissionRequests_AllMacOSPermissions() {
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONArray permissions = new JSONArray();
        
        // Test a few key permissions to verify they all work
        String[] permissionNames = {
            "camera", "microphone", "location", "contacts", "calendars",
            "photos", "bluetooth", "siri", "face_id", "desktop_folder"
        };
        
        for (String permissionName : permissionNames) {
            JSONObject permission = new JSONObject();
            permission.put("name", permissionName);
            permission.put("description", "Test description for " + permissionName);
            permissions.put(permission);
        }
        
        jdeploy.put("permissions", permissions);
        packageJson.put("jdeploy", jdeploy);
        
        Map<PermissionRequest, String> result = service.getPermissionRequests(packageJson);
        
        assertEquals(permissionNames.length, result.size());
        
        for (String permissionName : permissionNames) {
            PermissionRequest expectedEnum = PermissionRequest.valueOf(permissionName.toUpperCase());
            assertTrue(result.containsKey(expectedEnum));
            assertEquals("Test description for " + permissionName, result.get(expectedEnum));
        }
    }
}
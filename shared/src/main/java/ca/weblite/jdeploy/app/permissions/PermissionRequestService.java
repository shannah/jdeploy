package ca.weblite.jdeploy.app.permissions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for loading and processing permission requests from package.json files.
 */
public class PermissionRequestService {
    
    /**
     * Parses permission requests from a package.json JSONObject.
     * 
     * @param packageJson The package.json content as a JSONObject
     * @return A map of PermissionRequest to description strings
     */
    public Map<PermissionRequest, String> getPermissionRequests(JSONObject packageJson) {
        Map<PermissionRequest, String> out = new HashMap<>();
        
        if (!packageJson.has("jdeploy")) {
            return out;
        }
        
        JSONObject jdeploy = packageJson.getJSONObject("jdeploy");
        if (!jdeploy.has("permissions")) {
            return out;
        }
        
        JSONArray permissions = jdeploy.getJSONArray("permissions");
        int len = permissions.length();
        for (int i = 0; i < len; i++) {
            JSONObject permission = permissions.getJSONObject(i);
            if (!permission.has("name")) continue;
            
            String permissionName = permission.getString("name");
            PermissionRequest request = getPermissionRequestByName(permissionName);
            if (request == null) continue;
            
            String description;
            if (permission.has("description")) {
                description = permission.getString("description");
            } else {
                description = generateGenericDescription(permissionName);
            }
            
            out.put(request, description);
        }
        
        return out;
    }
    
    /**
     * Maps a string permission name to a PermissionRequest enum value.
     * 
     * @param name The permission name (case-insensitive)
     * @return The corresponding PermissionRequest, or null if not found
     */
    private static PermissionRequest getPermissionRequestByName(String name) {
        try {
            return PermissionRequest.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    /**
     * Generates a generic description for a permission when none is provided.
     * 
     * @param permissionName The raw permission name from the JSON
     * @return A generic description string
     */
    private static String generateGenericDescription(String permissionName) {
        String friendlyName = permissionName.toLowerCase().replace("_", " ");
        return "The " + friendlyName + " permission is required for functionality of this application.";
    }
}
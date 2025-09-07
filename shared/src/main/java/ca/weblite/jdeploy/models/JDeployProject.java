package ca.weblite.jdeploy.models;

import ca.weblite.jdeploy.io.FileSystemInterface;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public class JDeployProject {
    private Path packageJSONFile;
    private JSONObject packageJSON;

    public JDeployProject(Path packageJSONFile, JSONObject packageJSON) {
        this.packageJSONFile = packageJSONFile;
        this.packageJSON = packageJSON;
    }

    public Path getPackageJSONFile() {
        return packageJSONFile;
    }

    public JSONObject getPackageJSON() {
        return packageJSON;
    }

    /**
     * Gets the jdeploy configuration object from package.json
     * @return the jdeploy configuration object, or empty object if not present
     */
    private JSONObject getJDeployConfig() {
        if (packageJSON.has("jdeploy")) {
            return packageJSON.getJSONObject("jdeploy");
        }
        return new JSONObject();
    }

    /**
     * Checks if platform-specific bundles are enabled
     * @return true if platform bundles are enabled
     */
    public boolean isPlatformBundlesEnabled() {
        JSONObject jdeployConfig = getJDeployConfig();
        return jdeployConfig.optBoolean("platformBundlesEnabled", false);
    }

    /**
     * Checks if fallback to universal bundle is enabled
     * @return true if fallback to universal bundle is enabled
     */
    public boolean isFallbackToUniversal() {
        JSONObject jdeployConfig = getJDeployConfig();
        return jdeployConfig.optBoolean("fallbackToUniversal", true);
    }

    /**
     * Gets the NPM package name for a specific platform
     * @param platform the platform to get package name for
     * @return the package name, or null if not configured
     */
    public String getPackageName(Platform platform) {
        if (platform == null) return null;
        
        JSONObject jdeployConfig = getJDeployConfig();
        String propertyName = platform.getPackagePropertyName();
        
        if (jdeployConfig.has(propertyName)) {
            return jdeployConfig.getString(propertyName);
        }
        return null;
    }

    /**
     * Gets all configured native namespaces organized by platform
     * @return map of platform to list of namespaces
     */
    public Map<Platform, List<String>> getNativeNamespaces() {
        Map<Platform, List<String>> result = new HashMap<>();
        JSONObject jdeployConfig = getJDeployConfig();
        
        if (!jdeployConfig.has("nativeNamespaces")) {
            return result;
        }
        
        JSONObject nativeNamespaces = jdeployConfig.getJSONObject("nativeNamespaces");
        
        for (Platform platform : Platform.values()) {
            String platformId = platform.getIdentifier();
            if (nativeNamespaces.has(platformId)) {
                JSONArray namespaces = nativeNamespaces.getJSONArray(platformId);
                List<String> namespaceList = new ArrayList<>();
                for (int i = 0; i < namespaces.length(); i++) {
                    namespaceList.add(namespaces.getString(i));
                }
                result.put(platform, namespaceList);
            }
        }
        
        return result;
    }

    /**
     * Gets native namespaces for a specific platform
     * @param platform the platform to get namespaces for
     * @return list of namespaces for the platform, empty list if none configured
     */
    public List<String> getNativeNamespacesForPlatform(Platform platform) {
        if (platform == null) return new ArrayList<>();
        
        JSONObject jdeployConfig = getJDeployConfig();
        if (!jdeployConfig.has("nativeNamespaces")) {
            return new ArrayList<>();
        }
        
        JSONObject nativeNamespaces = jdeployConfig.getJSONObject("nativeNamespaces");
        String platformId = platform.getIdentifier();
        
        if (nativeNamespaces.has(platformId)) {
            JSONArray namespaces = nativeNamespaces.getJSONArray(platformId);
            List<String> result = new ArrayList<>();
            for (int i = 0; i < namespaces.length(); i++) {
                result.add(namespaces.getString(i));
            }
            return result;
        }
        
        return new ArrayList<>();
    }

    /**
     * Gets namespaces that should be ignored (stripped from all platform bundles)
     * @return list of namespaces to ignore
     */
    public List<String> getIgnoredNamespaces() {
        JSONObject jdeployConfig = getJDeployConfig();
        if (!jdeployConfig.has("nativeNamespaces")) {
            return new ArrayList<>();
        }
        
        JSONObject nativeNamespaces = jdeployConfig.getJSONObject("nativeNamespaces");
        if (nativeNamespaces.has("ignore")) {
            JSONArray ignoreArray = nativeNamespaces.getJSONArray("ignore");
            List<String> result = new ArrayList<>();
            for (int i = 0; i < ignoreArray.length(); i++) {
                result.add(ignoreArray.getString(i));
            }
            return result;
        }
        
        return new ArrayList<>();
    }

    /**
     * Gets all native namespaces for platforms other than the target platform,
     * plus all ignored namespaces
     * @param targetPlatform the platform to exclude from the result
     * @return list of namespaces to strip from the target platform bundle
     */
    public List<String> getAllOtherPlatformNamespaces(Platform targetPlatform) {
        List<String> result = new ArrayList<>();
        
        // Add ignored namespaces (always stripped)
        result.addAll(getIgnoredNamespaces());
        
        // Add namespaces from all other platforms
        Map<Platform, List<String>> allNamespaces = getNativeNamespaces();
        for (Map.Entry<Platform, List<String>> entry : allNamespaces.entrySet()) {
            Platform platform = entry.getKey();
            if (!platform.equals(targetPlatform)) {
                result.addAll(entry.getValue());
            }
        }
        
        return result;
    }

    /**
     * Gets a list of platforms that require platform-specific bundles.
     * Only platforms that are explicitly defined in nativeNamespaces should get bundles.
     * 
     * @return list of platforms that need platform-specific bundles
     */
    public List<Platform> getPlatformsRequiringSpecificBundles() {
        Map<Platform, List<String>> allNamespaces = getNativeNamespaces();
        
        // Only return platforms that have native namespaces explicitly defined
        // This prevents generating bundles for platforms like win-arm64 when not configured
        return new ArrayList<>(allNamespaces.keySet());
    }

    /**
     * Gets a list of platforms that have NPM package names configured for separate publishing
     * @return list of platforms with configured NPM package names
     */
    public List<Platform> getPlatformsWithNpmPackageNames() {
        List<Platform> result = new ArrayList<>();
        
        for (Platform platform : Platform.values()) {
            if (getPackageName(platform) != null) {
                result.add(platform);
            }
        }
        
        return result;
    }
}

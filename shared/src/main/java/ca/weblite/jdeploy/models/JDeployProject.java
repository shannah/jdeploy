package ca.weblite.jdeploy.models;

import ca.weblite.jdeploy.io.FileSystemInterface;
import org.apache.commons.io.FileUtils;
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

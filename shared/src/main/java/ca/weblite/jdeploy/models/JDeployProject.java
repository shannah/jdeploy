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

/**
 * Project model backed by package.json (JSONObject).
 */
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

    /**
     * Parses and returns the list of CommandSpec objects declared in package.json under jdeploy.commands.
     * The returned list is sorted by command name to ensure deterministic ordering.
     *
     * @return list of CommandSpec (empty list if none configured)
     * @throws IllegalArgumentException if invalid command entries are encountered
     */
    public List<CommandSpec> getCommandSpecs() {
        return CommandSpecParser.parseCommands(getJDeployConfig());
    }

    /**
     * Returns a CommandSpec by name if present.
     * @param name command name
     * @return Optional CommandSpec
     */
    public Optional<CommandSpec> getCommandSpec(String name) {
        if (name == null) return Optional.empty();
        for (CommandSpec cs : getCommandSpecs()) {
            if (name.equals(cs.getName())) return Optional.of(cs);
        }
        return Optional.empty();
    }

    /**
     * Returns whether this application should run in singleton mode.
     * In singleton mode, only one instance of the application can run at a time.
     * Subsequent launches will forward files/URIs to the existing instance.
     *
     * @return true if singleton mode is enabled, false otherwise
     */
    public boolean isSingleton() {
        JSONObject jdeployConfig = getJDeployConfig();
        return jdeployConfig.optBoolean("singleton", false);
    }

    /**
     * Sets whether this application should run in singleton mode.
     *
     * @param singleton true to enable singleton mode, false to disable
     */
    public void setSingleton(boolean singleton) {
        JSONObject jdeployConfig = getJDeployConfig();
        if (singleton) {
            jdeployConfig.put("singleton", true);
        } else {
            jdeployConfig.remove("singleton");
        }
    }

    // ==================== Prebuilt Apps Support ====================

    /**
     * Gets the list of platforms that have prebuilt apps available.
     * This is populated at publish time when prebuilt apps are uploaded.
     *
     * @return list of platform identifiers (e.g., "win-x64", "mac-arm64"), or empty list if none
     */
    public List<String> getPrebuiltApps() {
        JSONObject jdeployConfig = getJDeployConfig();
        JSONArray arr = jdeployConfig.optJSONArray("prebuiltApps");
        if (arr == null) {
            return Collections.emptyList();
        }
        List<String> platforms = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            platforms.add(arr.getString(i));
        }
        return platforms;
    }

    /**
     * Sets the list of platforms that have prebuilt apps available.
     * This should only be called at publish time after successful upload.
     *
     * @param platforms list of platform identifiers
     */
    public void setPrebuiltApps(List<String> platforms) {
        JSONObject jdeployConfig = getJDeployConfig();
        if (platforms == null || platforms.isEmpty()) {
            jdeployConfig.remove("prebuiltApps");
        } else {
            JSONArray arr = new JSONArray();
            for (String p : platforms) {
                arr.put(p);
            }
            jdeployConfig.put("prebuiltApps", arr);
        }
    }

    /**
     * Checks if a prebuilt app is available for the given platform.
     *
     * @param platform the platform to check
     * @return true if a prebuilt app exists for this platform
     */
    public boolean hasPrebuiltApp(Platform platform) {
        if (platform == null) {
            return false;
        }
        return getPrebuiltApps().contains(platform.getIdentifier());
    }

    // ==================== Windows Signing Support ====================

    /**
     * Checks if Windows code signing is enabled for this project.
     *
     * @return true if Windows signing is enabled
     */
    public boolean isWindowsSigningEnabled() {
        JSONObject jdeployConfig = getJDeployConfig();
        JSONObject windowsSigning = jdeployConfig.optJSONObject("windowsSigning");
        return windowsSigning != null && windowsSigning.optBoolean("enabled", false);
    }

    /**
     * Gets the Windows signing configuration object.
     *
     * @return the windowsSigning JSONObject, or null if not configured
     */
    public JSONObject getWindowsSigningConfig() {
        JSONObject jdeployConfig = getJDeployConfig();
        return jdeployConfig.optJSONObject("windowsSigning");
    }
}

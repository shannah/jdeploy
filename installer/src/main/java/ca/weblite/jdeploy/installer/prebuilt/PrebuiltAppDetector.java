package ca.weblite.jdeploy.installer.prebuilt;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Detects whether prebuilt apps are available for a given platform.
 * Reads the prebuiltApps array from the jdeploy section of package.json.
 */
public class PrebuiltAppDetector {

    /**
     * Checks if a prebuilt app is available for the specified platform.
     *
     * @param packageJson the package.json object
     * @param platform the platform identifier (e.g., "win-x64", "mac-arm64")
     * @return true if a prebuilt app is available for the platform
     */
    public boolean hasPrebuiltApp(JSONObject packageJson, String platform) {
        if (packageJson == null || platform == null) {
            return false;
        }

        JSONObject jdeploy = packageJson.optJSONObject("jdeploy");
        if (jdeploy == null) {
            return false;
        }

        JSONArray prebuiltApps = jdeploy.optJSONArray("prebuiltApps");
        if (prebuiltApps == null) {
            return false;
        }

        for (int i = 0; i < prebuiltApps.length(); i++) {
            if (platform.equals(prebuiltApps.optString(i))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the list of platforms that have prebuilt apps available.
     *
     * @param packageJson the package.json object
     * @return list of platform identifiers, or empty list if none
     */
    public List<String> getPrebuiltAppPlatforms(JSONObject packageJson) {
        if (packageJson == null) {
            return Collections.emptyList();
        }

        JSONObject jdeploy = packageJson.optJSONObject("jdeploy");
        if (jdeploy == null) {
            return Collections.emptyList();
        }

        JSONArray prebuiltApps = jdeploy.optJSONArray("prebuiltApps");
        if (prebuiltApps == null) {
            return Collections.emptyList();
        }

        List<String> platforms = new ArrayList<>();
        for (int i = 0; i < prebuiltApps.length(); i++) {
            String platform = prebuiltApps.optString(i);
            if (platform != null && !platform.isEmpty()) {
                platforms.add(platform);
            }
        }

        return platforms;
    }

    /**
     * Constructs the GitHub release download URL for a prebuilt app tarball.
     *
     * @param repositoryUrl the GitHub repository URL (e.g., "https://github.com/owner/repo")
     * @param appName the application name (npm package name)
     * @param version the application version
     * @param platform the platform identifier (e.g., "win-x64")
     * @return the download URL for the prebuilt app tarball
     */
    public String getGitHubDownloadUrl(String repositoryUrl, String appName, String version, String platform) {
        // Remove trailing slash if present
        if (repositoryUrl.endsWith("/")) {
            repositoryUrl = repositoryUrl.substring(0, repositoryUrl.length() - 1);
        }

        // Format: https://github.com/{owner}/{repo}/releases/download/v{version}/{name}-{version}-{platform}-bin.tgz
        String tarballName = getTarballName(appName, version, platform);
        return String.format("%s/releases/download/v%s/%s", repositoryUrl, version, tarballName);
    }

    /**
     * Constructs the GitHub release download URL using the jdeploy tag for latest version.
     *
     * @param repositoryUrl the GitHub repository URL
     * @param appName the application name
     * @param version the application version
     * @param platform the platform identifier
     * @return the download URL for the prebuilt app tarball from jdeploy tag
     */
    public String getGitHubJdeployTagUrl(String repositoryUrl, String appName, String version, String platform) {
        // Remove trailing slash if present
        if (repositoryUrl.endsWith("/")) {
            repositoryUrl = repositoryUrl.substring(0, repositoryUrl.length() - 1);
        }

        // Format: https://github.com/{owner}/{repo}/releases/download/jdeploy/{name}-{version}-{platform}-bin.tgz
        String tarballName = getTarballName(appName, version, platform);
        return String.format("%s/releases/download/jdeploy/%s", repositoryUrl, tarballName);
    }

    /**
     * Gets the tarball filename for a prebuilt app.
     * Format: {appname}-{version}-{platform}-bin.tgz
     *
     * @param appName the application name
     * @param version the application version
     * @param platform the platform identifier
     * @return the tarball filename
     */
    public String getTarballName(String appName, String version, String platform) {
        return String.format("%s-%s-%s-bin.tgz", appName, version, platform);
    }

    /**
     * Detects the current platform identifier based on the operating system and architecture.
     *
     * @return the platform identifier (e.g., "win-x64", "mac-arm64", "linux-x64")
     */
    public String detectCurrentPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();

        String osPrefix;
        if (os.contains("win")) {
            osPrefix = "win";
        } else if (os.contains("mac") || os.contains("darwin")) {
            osPrefix = "mac";
        } else {
            osPrefix = "linux";
        }

        String archSuffix;
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            archSuffix = "arm64";
        } else {
            archSuffix = "x64";
        }

        return osPrefix + "-" + archSuffix;
    }

    /**
     * Checks if the app is published to GitHub (has a GitHub source).
     *
     * @param packageJson the package.json object
     * @return true if the app has a GitHub source configured
     */
    public boolean hasGitHubSource(JSONObject packageJson) {
        if (packageJson == null) {
            return false;
        }

        JSONObject jdeploy = packageJson.optJSONObject("jdeploy");
        if (jdeploy == null) {
            return false;
        }

        String source = jdeploy.optString("source", "");
        return source.contains("github.com");
    }

    /**
     * Extracts the GitHub repository URL from the package.json source.
     *
     * @param packageJson the package.json object
     * @return the GitHub repository URL, or null if not a GitHub source
     */
    public String getGitHubRepositoryUrl(JSONObject packageJson) {
        if (packageJson == null) {
            return null;
        }

        JSONObject jdeploy = packageJson.optJSONObject("jdeploy");
        if (jdeploy == null) {
            return null;
        }

        String source = jdeploy.optString("source", "");
        if (!source.contains("github.com")) {
            return null;
        }

        // Source might have suffix like "#package-name", remove it
        int hashIndex = source.indexOf('#');
        if (hashIndex > 0) {
            source = source.substring(0, hashIndex);
        }

        return source;
    }
}

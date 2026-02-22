package ca.weblite.jdeploy.services.verification;

import ca.weblite.jdeploy.models.CommandSpec;
import ca.weblite.jdeploy.models.JDeployProject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves package information from various input sources:
 * 1. Local or remote package.json path/URL
 * 2. Project code (via jDeploy registry lookup)
 * 3. Package name and optional source URL
 */
@Singleton
public class PackageInfoResolver {

    private static final String JDEPLOY_REGISTRY_URL = "https://www.jdeploy.com/";

    /**
     * Resolves package info from a local file path or URL to package.json.
     */
    public ResolvedPackageInfo resolveFromPackageJson(String pathOrUrl) throws IOException {
        String content;

        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            // Remote URL
            try (InputStream is = new URL(pathOrUrl).openStream()) {
                content = IOUtils.toString(is, StandardCharsets.UTF_8);
            }
        } else {
            // Local file path
            File file = new File(pathOrUrl);
            if (!file.exists()) {
                throw new IOException("package.json not found: " + pathOrUrl);
            }
            content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        }

        return parsePackageJson(content);
    }

    /**
     * Resolves package info from a project code via jDeploy registry lookup.
     */
    public ResolvedPackageInfo resolveFromProjectCode(String projectCode) throws IOException {
        // Query jDeploy registry for project info
        String registryUrl = System.getProperty("jdeploy.registry.url", JDEPLOY_REGISTRY_URL);
        String endpoint = registryUrl + "registry/" + projectCode;

        String registryResponse;
        try (InputStream is = new URL(endpoint).openStream()) {
            registryResponse = IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IOException("Failed to lookup project code '" + projectCode + "' in jDeploy registry: " + e.getMessage(), e);
        }

        JSONObject registryInfo = new JSONObject(registryResponse);
        String source = registryInfo.optString("source", null);
        String packageName = registryInfo.optString("packageName", null);

        if (packageName == null || packageName.isEmpty()) {
            throw new IOException("Registry response missing packageName for project code: " + projectCode);
        }

        // Now resolve the full package info
        return resolveFromPackage(packageName, source);
    }

    /**
     * Resolves package info from package name and optional source.
     * For NPM packages, fetches from npm registry.
     * For GitHub packages, fetches from GitHub releases.
     */
    public ResolvedPackageInfo resolveFromPackage(String packageName, String source) throws IOException {
        if (source != null && !source.isEmpty() && source.contains("github.com")) {
            return resolveFromGitHub(packageName, source);
        } else {
            return resolveFromNpm(packageName);
        }
    }

    private ResolvedPackageInfo resolveFromNpm(String packageName) throws IOException {
        String npmUrl = "https://registry.npmjs.org/" + packageName + "/latest";

        String content;
        try (InputStream is = new URL(npmUrl).openStream()) {
            content = IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IOException("Failed to fetch package from npm: " + packageName, e);
        }

        return parsePackageJson(content);
    }

    private ResolvedPackageInfo resolveFromGitHub(String packageName, String source) throws IOException {
        // Extract owner/repo from source URL
        // Example: https://github.com/owner/repo
        String ownerRepo = extractOwnerRepo(source);
        if (ownerRepo == null) {
            throw new IOException("Invalid GitHub source URL: " + source);
        }

        // Try to fetch package-info.json from jdeploy release tag
        String packageInfoUrl = "https://github.com/" + ownerRepo + "/releases/download/jdeploy/package-info.json";

        String content;
        try (InputStream is = new URL(packageInfoUrl).openStream()) {
            content = IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IOException("Failed to fetch package-info.json from GitHub releases: " + packageInfoUrl, e);
        }

        JSONObject packageInfo = new JSONObject(content);

        // package-info.json has a "versions" object with version details
        JSONObject versions = packageInfo.optJSONObject("versions");
        if (versions == null || versions.length() == 0) {
            throw new IOException("No versions found in package-info.json");
        }

        // Get the latest version
        String latestVersion = null;
        JSONObject latestVersionInfo = null;
        for (String version : versions.keySet()) {
            if (latestVersion == null || compareVersions(version, latestVersion) > 0) {
                latestVersion = version;
                latestVersionInfo = versions.getJSONObject(version);
            }
        }

        if (latestVersionInfo == null) {
            throw new IOException("Could not determine latest version from package-info.json");
        }

        return parseVersionInfo(packageName, source, latestVersion, latestVersionInfo);
    }

    private ResolvedPackageInfo parsePackageJson(String content) throws IOException {
        JSONObject json = new JSONObject(content);

        String packageName = json.optString("name", null);
        if (packageName == null) {
            throw new IOException("package.json missing 'name' field");
        }

        String version = json.optString("version", null);

        JSONObject jdeploy = json.optJSONObject("jdeploy");
        String title = packageName;
        String source = null;
        String winAppDir = null;
        List<CommandSpec> commands = new ArrayList<>();

        if (jdeploy != null) {
            title = jdeploy.optString("title", packageName);
            source = jdeploy.optString("source", null);
            winAppDir = jdeploy.optString("winAppDir", null);

            // Parse commands - can be array or object format
            // Use JDeployProject which handles both formats
            if (jdeploy.has("commands")) {
                Path packageJsonPath = new File("package.json").toPath(); // dummy path
                JDeployProject project = new JDeployProject(packageJsonPath, json);
                commands = project.getCommandSpecs();
            }
        }

        return ResolvedPackageInfo.builder()
                .packageName(packageName)
                .source(source)
                .title(title)
                .version(version)
                .commands(commands)
                .winAppDir(winAppDir)
                .build();
    }

    private ResolvedPackageInfo parseVersionInfo(String packageName, String source,
                                                  String version, JSONObject versionInfo) {
        String title = versionInfo.optString("title", packageName);

        // Commands might be in the jdeploy section
        JSONObject jdeploy = versionInfo.optJSONObject("jdeploy");
        List<CommandSpec> commands = new ArrayList<>();
        String winAppDir = null;

        if (jdeploy != null) {
            winAppDir = jdeploy.optString("winAppDir", null);
            // Parse commands - can be array or object format
            if (jdeploy.has("commands")) {
                Object commandsObj = jdeploy.get("commands");
                if (commandsObj instanceof JSONArray) {
                    JSONArray commandsArray = (JSONArray) commandsObj;
                    for (int i = 0; i < commandsArray.length(); i++) {
                        Object cmd = commandsArray.get(i);
                        if (cmd instanceof JSONObject) {
                            JSONObject cmdObj = (JSONObject) cmd;
                            String name = cmdObj.optString("name", null);
                            if (name != null) {
                                commands.add(new CommandSpec(name, null, null));
                            }
                        } else if (cmd instanceof String) {
                            commands.add(new CommandSpec((String) cmd, null, null));
                        }
                    }
                } else if (commandsObj instanceof JSONObject) {
                    // Object format: {"cmd-name": {...}, ...}
                    JSONObject commandsMap = (JSONObject) commandsObj;
                    for (String cmdName : commandsMap.keySet()) {
                        commands.add(new CommandSpec(cmdName, null, null));
                    }
                }
            }
        }

        return ResolvedPackageInfo.builder()
                .packageName(packageName)
                .source(source)
                .title(title)
                .version(version)
                .commands(commands)
                .winAppDir(winAppDir)
                .build();
    }

    private String extractOwnerRepo(String source) {
        // Handle various GitHub URL formats
        // https://github.com/owner/repo
        // https://github.com/owner/repo.git
        // github.com/owner/repo
        if (source == null) return null;

        String normalized = source
                .replace("https://", "")
                .replace("http://", "")
                .replace("github.com/", "");

        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }

        String[] parts = normalized.split("/");
        if (parts.length >= 2) {
            return parts[0] + "/" + parts[1];
        }
        return null;
    }

    private int compareVersions(String v1, String v2) {
        // Simple version comparison (major.minor.patch)
        String[] parts1 = v1.replace("v", "").split("\\.");
        String[] parts2 = v2.replace("v", "").split("\\.");

        for (int i = 0; i < Math.max(parts1.length, parts2.length); i++) {
            int p1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int p2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            if (p1 != p2) {
                return p1 - p2;
            }
        }
        return 0;
    }

    private int parseVersionPart(String part) {
        try {
            // Handle pre-release versions like "1-beta"
            int dashIndex = part.indexOf('-');
            if (dashIndex > 0) {
                part = part.substring(0, dashIndex);
            }
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

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

    // -------------------- CommandSpec parsing --------------------

    /**
     * Regex for validating command names:
     * Start with an alphanumeric, then allow alphanumeric, dot, underscore, dash.
     */
    private static final String COMMAND_NAME_REGEX = "^[A-Za-z0-9][A-Za-z0-9._-]*$";

    /**
     * Parses and returns the list of CommandSpec objects declared in package.json under jdeploy.commands.
     * The returned list is sorted by command name to ensure deterministic ordering.
     *
     * Validation:
     * - command names must match COMMAND_NAME_REGEX
     * - each command value must be a JSONObject (or absent)
     * - args, if present, must be a JSONArray of strings
     *
     * @return list of CommandSpec (empty list if none configured)
     * @throws IllegalArgumentException if invalid command entries are encountered
     */
    public List<CommandSpec> getCommandSpecs() {
        JSONObject jdeployConfig = getJDeployConfig();
        if (!jdeployConfig.has("commands")) {
            return Collections.emptyList();
        }

        Object commandsObj = jdeployConfig.get("commands");
        if (!(commandsObj instanceof JSONObject)) {
            throw new IllegalArgumentException("'jdeploy.commands' must be an object mapping commandName -> spec");
        }

        JSONObject commands = (JSONObject) commandsObj;
        List<CommandSpec> result = new ArrayList<>();

        for (Iterator<String> it = commands.keys(); it.hasNext(); ) {
            String name = it.next();
            if (name == null || !name.matches(COMMAND_NAME_REGEX)) {
                throw new IllegalArgumentException("Invalid command name: '" + name + "'. Must match " + COMMAND_NAME_REGEX);
            }
            Object value = commands.get(name);
            if (value == JSONObject.NULL) {
                throw new IllegalArgumentException("Command '" + name + "' value must be an object if present");
            }
            if (!(value instanceof JSONObject)) {
                throw new IllegalArgumentException("Command '" + name + "' value must be an object");
            }
            JSONObject specObj = (JSONObject) value;
            List<String> args = new ArrayList<>();
            if (specObj.has("args")) {
                Object argsObj = specObj.get("args");
                if (!(argsObj instanceof JSONArray)) {
                    throw new IllegalArgumentException("Command '" + name + "': 'args' must be an array of strings");
                }
                JSONArray arr = (JSONArray) argsObj;
                for (int i = 0; i < arr.length(); i++) {
                    Object el = arr.get(i);
                    if (!(el instanceof String)) {
                        throw new IllegalArgumentException("Command '" + name + "': all 'args' elements must be strings");
                    }
                    args.add((String) el);
                }
            }
            result.add(new CommandSpec(name, args));
        }

        // sort by name for deterministic order
        result.sort(Comparator.comparing(CommandSpec::getName));
        return Collections.unmodifiableList(result);
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
}

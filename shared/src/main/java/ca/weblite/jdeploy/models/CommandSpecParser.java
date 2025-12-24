package ca.weblite.jdeploy.models;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Utility class for parsing CommandSpec objects from jdeploy configuration.
 * Provides shared validation and parsing logic used by both JDeployProject and NPMPackageVersion.
 */
public class CommandSpecParser {
    /**
     * Regex for validating command names:
     * Start with an alphanumeric, then allow alphanumeric, dot, underscore, dash.
     */
    private static final String COMMAND_NAME_REGEX = "^[A-Za-z0-9][A-Za-z0-9._-]*$";

    /**
     * Parses and returns the list of CommandSpec objects declared in jdeploy config under commands.
     * The returned list is sorted by command name to ensure deterministic ordering.
     *
     * Validation:
     * - command names must match COMMAND_NAME_REGEX
     * - each command value must be a JSONObject (or absent)
     * - args, if present, must be a JSONArray of strings
     *
     * @param jdeployConfig the jdeploy configuration object
     * @return list of CommandSpec (empty list if none configured)
     * @throws IllegalArgumentException if invalid command entries are encountered
     */
    public static List<CommandSpec> parseCommands(JSONObject jdeployConfig) {
        if (jdeployConfig == null || !jdeployConfig.has("commands")) {
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

    private CommandSpecParser() {
        // Utility class, no instantiation
    }
}

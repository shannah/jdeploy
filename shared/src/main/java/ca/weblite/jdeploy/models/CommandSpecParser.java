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
     * Regex pattern to detect dangerous shell metacharacters in args.
     * These characters could enable command injection if args were ever passed through a shell.
     * Matches: ; | & ` $( )
     */
    private static final String DANGEROUS_ARG_CHARS_REGEX = "[;|&`]|\\$\\(";

    /**
     * Valid implementation types for the "implements" property.
     */
    public static final String IMPL_UPDATER = "updater";
    public static final String IMPL_SERVICE_CONTROLLER = "service_controller";
    public static final String IMPL_LAUNCHER = "launcher";

    private static final List<String> VALID_IMPLEMENTATIONS;

    static {
        List<String> impls = new ArrayList<>();
        impls.add(IMPL_UPDATER);
        impls.add(IMPL_SERVICE_CONTROLLER);
        impls.add(IMPL_LAUNCHER);
        VALID_IMPLEMENTATIONS = Collections.unmodifiableList(impls);
    }

    /**
     * Parses and returns the list of CommandSpec objects declared in jdeploy config under commands.
     * The returned list is sorted by command name to ensure deterministic ordering.
     *
     * Validation:
     * - command names must match COMMAND_NAME_REGEX
     * - each command value must be a JSONObject (or absent)
     * - args, if present, must be a JSONArray of strings
     * - args entries must not contain dangerous shell metacharacters (;|&` or $())
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
            
            String description = null;
            if (specObj.has("description")) {
                Object descObj = specObj.get("description");
                if (descObj != JSONObject.NULL && descObj instanceof String) {
                    description = (String) descObj;
                } else if (descObj != JSONObject.NULL) {
                    throw new IllegalArgumentException("Command '" + name + "': 'description' must be a string");
                }
            }
            
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
                    String argValue = (String) el;
                    validateArg(name, argValue, i);
                    args.add(argValue);
                }
            }

            List<String> implementations = new ArrayList<>();
            if (specObj.has("implements")) {
                Object implObj = specObj.get("implements");
                if (!(implObj instanceof JSONArray)) {
                    throw new IllegalArgumentException("Command '" + name + "': 'implements' must be an array of strings");
                }
                JSONArray implArr = (JSONArray) implObj;
                for (int i = 0; i < implArr.length(); i++) {
                    Object el = implArr.get(i);
                    if (!(el instanceof String)) {
                        throw new IllegalArgumentException("Command '" + name + "': all 'implements' elements must be strings");
                    }
                    String implValue = (String) el;
                    validateImplementation(name, implValue, i);
                    implementations.add(implValue);
                }
            }

            Boolean embedPlist = null;
            if (specObj.has("embedPlist")) {
                Object embedObj = specObj.get("embedPlist");
                if (embedObj instanceof Boolean) {
                    embedPlist = (Boolean) embedObj;
                } else if (embedObj != JSONObject.NULL) {
                    throw new IllegalArgumentException("Command '" + name + "': 'embedPlist' must be a boolean");
                }
            }

            result.add(new CommandSpec(name, description, args, implementations, embedPlist));
        }

        // sort by name for deterministic order
        result.sort(Comparator.comparing(CommandSpec::getName));
        return Collections.unmodifiableList(result);
    }

    /**
     * Validates that an arg string does not contain dangerous shell metacharacters.
     *
     * @param commandName the command name (for error messages)
     * @param arg the arg string to validate
     * @param index the index of the arg in the array (for error messages)
     * @throws IllegalArgumentException if the arg contains dangerous characters or is null
     */
    private static void validateArg(String commandName, String arg, int index) {
        if (arg == null) {
            throw new IllegalArgumentException("Command '" + commandName + "': arg at index " + index + " must not be null");
        }
        if (java.util.regex.Pattern.compile(DANGEROUS_ARG_CHARS_REGEX).matcher(arg).find()) {
            throw new IllegalArgumentException(
                "Command '" + commandName + "': arg at index " + index + " contains dangerous shell metacharacters. " +
                "Args must not contain: ; | & ` or $()"
            );
        }
    }

    /**
     * Validates that an implementation string is one of the allowed values.
     *
     * @param commandName the command name (for error messages)
     * @param implementation the implementation string to validate
     * @param index the index of the implementation in the array (for error messages)
     * @throws IllegalArgumentException if the implementation is not a valid value or is null
     */
    private static void validateImplementation(String commandName, String implementation, int index) {
        if (implementation == null) {
            throw new IllegalArgumentException("Command '" + commandName + "': implementation at index " + index + " must not be null");
        }
        if (!VALID_IMPLEMENTATIONS.contains(implementation)) {
            throw new IllegalArgumentException(
                "Command '" + commandName + "': implementation at index " + index + " is invalid: '" + implementation + "'. " +
                "Must be one of: " + String.join(", ", VALID_IMPLEMENTATIONS)
            );
        }
    }

    private CommandSpecParser() {
        // Utility class, no instantiation
    }
}

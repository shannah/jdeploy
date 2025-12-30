package ca.weblite.jdeploy.installer;

/**
 * Constants for CLI installer functionality.
 * Centralizes magic strings used across CLI command installation and management.
 */
public interface CliInstallerConstants {
    /**
     * JSON metadata file stored in app directory to track CLI installation state.
     */
    String CLI_METADATA_FILE = ".jdeploy-cli.json";

    /**
     * JSON key for array of created command wrapper names.
     */
    String CREATED_WRAPPERS_KEY = "createdWrappers";

    /**
     * JSON key for boolean flag indicating if PATH was updated.
     */
    String PATH_UPDATED_KEY = "pathUpdated";

    /**
     * JSON key for the bin directory path where scripts were installed.
     */
    String BIN_DIR_KEY = "binDir";

    /**
     * Command-line argument prefix for specifying which command to invoke.
     * Example: --jdeploy:command=mycommand
     */
    String JDEPLOY_COMMAND_ARG_PREFIX = "--jdeploy:command=";

    /**
     * Name of the CLI-capable launcher executable in the macOS .app bundle.
     * This launcher copy is dedicated to CLI usage (vs. GUI launching).
     */
    String CLI_LAUNCHER_NAME = "Client4JLauncher-cli";

    /**
     * Suffix appended to the application name to create the CLI launcher filename.
     * Example: "MyApp" + CLI_LAUNCHER_SUFFIX + ".exe" → "MyApp-cli.exe"
     */
    String CLI_LAUNCHER_SUFFIX = "-cli";

    /**
     * JSON key for the CLI launcher executable filename (Windows).
     * Stores the name of the CLI exe that was created (e.g., "MyApp-cli.exe")
     * so it can be properly cleaned up during uninstallation.
     */
    String CLI_EXE_KEY = "cliExe";
}

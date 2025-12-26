package ca.weblite.jdeploy.installer.cli;

/**
 * Enum representing the action to take when a CLI command name collision is detected.
 * A collision occurs when a command name is already present from a different application.
 */
public enum CollisionAction {
    /**
     * Skip installation of the conflicting command.
     * The existing command will remain in place.
     */
    SKIP,

    /**
     * Overwrite the existing command with the new one.
     * The new command's launcher will replace the existing one.
     */
    OVERWRITE
}

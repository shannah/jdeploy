package ca.weblite.jdeploy.installer.cli;

/**
 * Interface for handling command name collisions during CLI command installation.
 * A collision occurs when a command name is already present from a different application.
 */
public interface CollisionHandler {
    /**
     * Handles a command name collision and returns the action to take.
     *
     * @param commandName the name of the command that collides
     * @param existingLauncherPath the path to the existing launcher that owns the command
     * @param newLauncherPath the path to the new launcher that wants to install the command
     * @return the action to take: SKIP or OVERWRITE
     */
    CollisionAction handleCollision(String commandName, String existingLauncherPath, String newLauncherPath);
}

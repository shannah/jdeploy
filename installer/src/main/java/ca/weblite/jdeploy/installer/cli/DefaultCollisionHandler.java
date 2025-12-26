package ca.weblite.jdeploy.installer.cli;

import java.util.logging.Logger;

/**
 * Default implementation of CollisionHandler that always returns OVERWRITE.
 * Used for testing and as a simple fallback when no custom collision handler is provided.
 */
public class DefaultCollisionHandler implements CollisionHandler {
    private static final Logger LOGGER = Logger.getLogger(DefaultCollisionHandler.class.getName());

    /**
     * Always returns OVERWRITE for any collision.
     * Logs a warning when invoked to alert operators of the fallback behavior.
     *
     * @param commandName the name of the command that collides
     * @param existingLauncherPath the path to the existing launcher that owns the command
     * @param newLauncherPath the path to the new launcher that wants to install the command
     * @return CollisionAction.OVERWRITE
     */
    @Override
    public CollisionAction handleCollision(String commandName, String existingLauncherPath, String newLauncherPath) {
        LOGGER.warning("CLI command collision detected for '" + commandName + "': " +
            "existing launcher at '" + existingLauncherPath + "' will be overwritten by '" + newLauncherPath + "'. " +
            "Using default OVERWRITE behavior.");
        return CollisionAction.OVERWRITE;
    }
}

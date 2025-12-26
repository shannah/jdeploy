package ca.weblite.jdeploy.installer.cli;

/**
 * Default implementation of CollisionHandler that always returns OVERWRITE.
 * Used for testing and as a simple fallback when no custom collision handler is provided.
 */
public class DefaultCollisionHandler implements CollisionHandler {
    /**
     * Always returns OVERWRITE for any collision.
     *
     * @param commandName the name of the command that collides
     * @param existingLauncherPath the path to the existing launcher that owns the command
     * @param newLauncherPath the path to the new launcher that wants to install the command
     * @return CollisionAction.OVERWRITE
     */
    @Override
    public CollisionAction handleCollision(String commandName, String existingLauncherPath, String newLauncherPath) {
        return CollisionAction.OVERWRITE;
    }
}

package ca.weblite.jdeploy.installer.helpers;

import ca.weblite.tools.platform.Platform;

import java.io.File;

/**
 * Utility class for locating the currently running installer bundle or executable.
 *
 * This class provides methods to find the installer's location on disk, handling
 * platform-specific bundle structures (e.g., macOS .app bundles).
 *
 * @author jDeploy Team
 */
public class InstallerBundleLocator {

    /**
     * System property that contains the path to the launcher executable.
     */
    public static final String LAUNCHER_PATH_PROPERTY = "jdeploy.launcher.path";

    /**
     * Returns the File path to the installer bundle or executable.
     *
     * Platform-specific behavior:
     * <ul>
     *   <li>macOS: If the launcher path points to a binary inside a .app bundle
     *       (e.g., MyApp.app/Contents/MacOS/launcher), this method walks up to find
     *       and return the .app bundle directory.</li>
     *   <li>Windows: Returns the .exe path directly.</li>
     *   <li>Linux: Returns the binary path directly.</li>
     * </ul>
     *
     * @return The File path to the installer bundle (macOS) or executable (Windows/Linux)
     * @throws IllegalStateException if the jdeploy.launcher.path system property is not set,
     *         or if the path doesn't exist, or if on macOS and no .app bundle is found
     */
    public static File getInstallerPath() {
        String launcherPath = System.getProperty(LAUNCHER_PATH_PROPERTY);

        if (launcherPath == null || launcherPath.trim().isEmpty()) {
            throw new IllegalStateException(
                "System property '" + LAUNCHER_PATH_PROPERTY + "' is not set. " +
                "This property should be set by the native launcher."
            );
        }

        File launcherFile = new File(launcherPath);

        if (!launcherFile.exists()) {
            throw new IllegalStateException(
                "Launcher path does not exist: " + launcherPath
            );
        }

        if (Platform.getSystemPlatform().isMac()) {
            // On macOS, resolve to the .app bundle
            File appBundle = resolveAppBundle(launcherFile);
            if (appBundle == null) {
                throw new IllegalStateException(
                    "Could not find .app bundle for launcher path: " + launcherPath
                );
            }
            return appBundle;
        } else {
            // On Windows/Linux, return the path directly
            return launcherFile;
        }
    }

    /**
     * macOS-specific helper that walks up from a path to find the containing .app bundle.
     *
     * This method handles the common case where the launcher executable is located at
     * MyApp.app/Contents/MacOS/launcher and needs to resolve to MyApp.app.
     *
     * @param path The path to start from (typically the launcher executable)
     * @return The .app bundle directory, or null if no .app bundle is found
     */
    public static File resolveAppBundle(File path) {
        if (path == null) {
            return null;
        }

        // If the path itself is a .app directory, return it
        if (path.isDirectory() && path.getName().endsWith(".app")) {
            return path;
        }

        // Walk up parent directories looking for a .app bundle
        File current = path.getParentFile();
        while (current != null) {
            if (current.getName().endsWith(".app")) {
                return current;
            }
            current = current.getParentFile();
        }

        return null;
    }

    /**
     * Checks if the launcher path system property is set.
     *
     * @return true if the jdeploy.launcher.path property is set and non-empty
     */
    public static boolean isLauncherPathSet() {
        String launcherPath = System.getProperty(LAUNCHER_PATH_PROPERTY);
        return launcherPath != null && !launcherPath.trim().isEmpty();
    }
}

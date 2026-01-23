package ca.weblite.jdeploy.installer.helpers;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for orchestrating Helper self-deletion after uninstall.
 *
 * When a Helper application needs to delete itself (e.g., when the main
 * application is uninstalled or services are removed), it cannot directly
 * delete its own files while running. This service coordinates the self-deletion
 * process by:
 * <ol>
 *   <li>Resolving the Helper's file paths</li>
 *   <li>Generating a platform-specific cleanup script</li>
 *   <li>Launching the script as a detached process</li>
 *   <li>Allowing the Helper to exit normally while the script cleans up</li>
 * </ol>
 *
 * The cleanup script waits briefly for the Helper process to exit, then
 * deletes the Helper executable, context directory, and optionally the
 * Helper directory if it becomes empty.
 *
 * @author jDeploy Team
 */
public class HelperSelfDeleteService {

    private static final Logger DEFAULT_LOGGER = Logger.getLogger(HelperSelfDeleteService.class.getName());

    private final HelperCleanupScriptGenerator scriptGenerator;
    private final Logger logger;

    /**
     * Creates a new HelperSelfDeleteService.
     *
     * @param scriptGenerator The script generator for creating cleanup scripts
     * @param logger The logger for recording operations (uses default if null)
     * @throws IllegalArgumentException if scriptGenerator is null
     */
    public HelperSelfDeleteService(HelperCleanupScriptGenerator scriptGenerator, Logger logger) {
        if (scriptGenerator == null) {
            throw new IllegalArgumentException("scriptGenerator cannot be null");
        }
        this.scriptGenerator = scriptGenerator;
        this.logger = logger != null ? logger : DEFAULT_LOGGER;
    }

    /**
     * Creates a new HelperSelfDeleteService with default logger.
     *
     * @param scriptGenerator The script generator for creating cleanup scripts
     * @throws IllegalArgumentException if scriptGenerator is null
     */
    public HelperSelfDeleteService(HelperCleanupScriptGenerator scriptGenerator) {
        this(scriptGenerator, null);
    }

    /**
     * Schedules the Helper for deletion.
     *
     * This method generates a cleanup script and launches it as a detached process.
     * The script will wait briefly for the Helper to exit, then delete:
     * <ul>
     *   <li>The Helper executable or .app bundle</li>
     *   <li>The Helper's .jdeploy-files context directory</li>
     *   <li>The Helper directory (if it becomes empty)</li>
     * </ul>
     *
     * Errors are handled gracefully - if script generation or execution fails,
     * the error is logged and false is returned. This is because the main
     * operation (uninstall) has already succeeded; cleanup failure is not fatal.
     *
     * @param appName The application name (e.g., "My App")
     * @param appDirectory The application installation directory (ignored on macOS)
     * @return true if the cleanup script was launched successfully, false otherwise
     * @throws IllegalArgumentException if appName is null or empty
     */
    public boolean scheduleHelperCleanup(String appName, File appDirectory) {
        if (appName == null || appName.trim().isEmpty()) {
            throw new IllegalArgumentException("appName cannot be null or empty");
        }

        logger.info("Scheduling Helper cleanup for: " + appName);

        // Declare helperPath outside try block so it's available in catch for error messages
        File helperPath = null;

        try {
            // Resolve Helper paths
            helperPath = getHelperPath(appName, appDirectory);
            File helperContextDir = HelperPaths.getHelperContextDirectory(appName, appDirectory);
            File helperDir = HelperPaths.getHelperDirectory(appName, appDirectory);

            // Verify Helper exists before scheduling cleanup
            if (!helperPath.exists()) {
                logger.info("Helper does not exist, skipping cleanup: " + helperPath.getAbsolutePath());
                return true; // Not an error - nothing to clean up
            }

            logger.info("Helper path: " + helperPath.getAbsolutePath());
            logger.info("Context directory: " + helperContextDir.getAbsolutePath());
            logger.info("Helper directory: " + helperDir.getAbsolutePath());

            // Generate cleanup script
            File cleanupScript = scriptGenerator.generateCleanupScript(helperPath, helperContextDir, helperDir);
            logger.info("Generated cleanup script: " + cleanupScript.getAbsolutePath());

            // Execute cleanup script as detached process
            scriptGenerator.executeCleanupScript(cleanupScript);
            logger.info("Cleanup script launched successfully - Helper will be deleted after exit");

            return true;

        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to schedule Helper cleanup for " + appName +
                       " at " + (helperPath != null ? helperPath.getAbsolutePath() : "unknown") +
                       ": " + e.getMessage(), e);
            return false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error scheduling Helper cleanup for " + appName +
                       ": " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Gets the Helper's executable path.
     *
     * This method first checks if the Helper path is available via the
     * jdeploy.launcher.path system property (set when the Helper is running).
     * If not available, it falls back to resolving the path using HelperPaths.
     *
     * @param appName The application name
     * @param appDirectory The application installation directory (ignored on macOS)
     * @return The Helper executable path
     */
    public File getHelperPath(String appName, File appDirectory) {
        // First, check if we're running as the Helper and have the path in system property
        String launcherPath = System.getProperty("jdeploy.launcher.path");
        if (launcherPath != null && !launcherPath.isEmpty()) {
            File launcherFile = new File(launcherPath);
            if (launcherFile.exists()) {
                logger.fine("Using Helper path from jdeploy.launcher.path: " + launcherPath);
                return resolveHelperFromLauncher(launcherFile);
            }
        }

        // Fall back to resolving path using HelperPaths
        return HelperPaths.getHelperExecutablePath(appName, appDirectory);
    }

    /**
     * Resolves the Helper executable/bundle from the launcher path.
     *
     * On macOS, the launcher is inside the .app bundle, so we need to find
     * the bundle root. On Windows/Linux, the launcher is the executable itself.
     *
     * @param launcherFile The launcher file from jdeploy.launcher.path
     * @return The Helper executable or bundle directory
     */
    private File resolveHelperFromLauncher(File launcherFile) {
        // On macOS, the launcher is at: MyApp Helper.app/Contents/MacOS/MyApp Helper
        // We need to return the .app bundle directory
        String path = launcherFile.getAbsolutePath();
        if (path.contains(".app/Contents/MacOS/")) {
            // Find the .app directory
            int appIndex = path.indexOf(".app/");
            if (appIndex > 0) {
                String appBundlePath = path.substring(0, appIndex + 4); // Include ".app"
                return new File(appBundlePath);
            }
        }

        // On Windows/Linux, the launcher is the executable
        return launcherFile;
    }

    /**
     * Checks if the Helper exists for the given application.
     *
     * @param appName The application name
     * @param appDirectory The application installation directory (ignored on macOS)
     * @return true if the Helper exists, false otherwise
     */
    public boolean helperExists(String appName, File appDirectory) {
        if (appName == null || appName.trim().isEmpty()) {
            return false;
        }

        File helperPath = getHelperPath(appName, appDirectory);
        return helperPath.exists();
    }

    /**
     * Schedules cleanup for the currently running Helper.
     *
     * This is a convenience method for when the Helper needs to delete itself.
     * It uses the jdeploy.launcher.path system property to determine the
     * Helper's location.
     *
     * @return true if cleanup was scheduled successfully, false otherwise
     */
    public boolean scheduleCurrentHelperCleanup() {
        String launcherPath = System.getProperty("jdeploy.launcher.path");
        if (launcherPath == null || launcherPath.isEmpty()) {
            logger.warning("Cannot schedule current Helper cleanup: jdeploy.launcher.path not set");
            return false;
        }

        File launcherFile = new File(launcherPath);
        if (!launcherFile.exists()) {
            logger.warning("Cannot schedule current Helper cleanup: launcher file does not exist");
            return false;
        }

        File helperPath = resolveHelperFromLauncher(launcherFile);
        File helperDir = helperPath.getParentFile();
        File helperContextDir;

        // Determine context directory location
        if (helperPath.getName().endsWith(".app")) {
            // macOS: context dir is sibling to .app bundle
            helperContextDir = new File(helperDir, ".jdeploy-files");
        } else {
            // Windows/Linux: context dir is in same directory as executable
            helperContextDir = new File(helperDir, ".jdeploy-files");
        }

        logger.info("Scheduling cleanup for current Helper: " + helperPath.getAbsolutePath());

        try {
            File cleanupScript = scriptGenerator.generateCleanupScript(helperPath, helperContextDir, helperDir);
            logger.info("Generated cleanup script: " + cleanupScript.getAbsolutePath());
            scriptGenerator.executeCleanupScript(cleanupScript);
            logger.info("Cleanup script launched for current Helper - files will be deleted after exit");
            return true;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to schedule current Helper cleanup at " +
                       helperPath.getAbsolutePath() + ": " + e.getMessage(), e);
            return false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error scheduling current Helper cleanup at " +
                       helperPath.getAbsolutePath() + ": " + e.getMessage(), e);
            return false;
        }
    }
}

package ca.weblite.jdeploy.installer.helpers;

import ca.weblite.jdeploy.installer.logging.InstallationLogger;

import java.io.File;

/**
 * Service that orchestrates the complete Helper installation process.
 *
 * The Helper is a background application that provides system tray service management
 * and uninstallation capabilities. This service coordinates copying the installer
 * bundle to the Helper location and setting up the required context files.
 *
 * Installation failures are returned as result objects rather than thrown as exceptions,
 * allowing the main installer to continue even if Helper installation fails.
 *
 * @author jDeploy Team
 */
public class HelperInstallationService {

    private final InstallationLogger logger;
    private final HelperCopyService copyService;

    /**
     * Creates a new HelperInstallationService.
     *
     * @param logger The installation logger for recording operations
     * @param copyService The service for copying files
     */
    public HelperInstallationService(InstallationLogger logger, HelperCopyService copyService) {
        this.logger = logger;
        this.copyService = copyService;
    }

    /**
     * Installs the Helper application.
     *
     * This method performs the following steps:
     * <ol>
     *   <li>Determines the Helper directory path using HelperPaths</li>
     *   <li>Creates the Helper directory if it doesn't exist</li>
     *   <li>Gets the installer bundle path using InstallerBundleLocator</li>
     *   <li>Copies the installer to the Helper location</li>
     *   <li>Copies the .jdeploy-files directory to the Helper directory</li>
     * </ol>
     *
     * @param appName The application name (e.g., "My App")
     * @param appDirectory The application installation directory (used on Windows/Linux)
     * @param jdeployFilesDir The source .jdeploy-files directory to copy
     * @return A HelperInstallationResult indicating success or failure
     */
    public HelperInstallationResult installHelper(String appName, File appDirectory, File jdeployFilesDir) {
        logSection("Installing Helper Application");
        logInfo("App name: " + appName);
        logInfo("App directory: " + (appDirectory != null ? appDirectory.getAbsolutePath() : "null"));
        logInfo("jdeploy-files directory: " + (jdeployFilesDir != null ? jdeployFilesDir.getAbsolutePath() : "null"));

        // Validate inputs
        if (appName == null || appName.trim().isEmpty()) {
            return HelperInstallationResult.failure("App name cannot be null or empty");
        }
        if (jdeployFilesDir == null || !jdeployFilesDir.exists()) {
            return HelperInstallationResult.failure(
                "jdeploy-files directory does not exist: " +
                (jdeployFilesDir != null ? jdeployFilesDir.getAbsolutePath() : "null")
            );
        }
        if (!jdeployFilesDir.isDirectory()) {
            return HelperInstallationResult.failure(
                "jdeploy-files path is not a directory: " + jdeployFilesDir.getAbsolutePath()
            );
        }

        // Declare paths outside try block so they're available in catch
        File helperDir = null;
        File helperExecutable = null;
        File helperContextDir = null;

        try {
            // Step 1: Get Helper paths
            helperDir = HelperPaths.getHelperDirectory(appName, appDirectory);
            helperExecutable = HelperPaths.getHelperExecutablePath(appName, appDirectory);
            helperContextDir = HelperPaths.getHelperContextDirectory(appName, appDirectory);

            logInfo("Helper directory: " + helperDir.getAbsolutePath());
            logInfo("Helper executable: " + helperExecutable.getAbsolutePath());
            logInfo("Helper context directory: " + helperContextDir.getAbsolutePath());

            // Step 2: Create Helper directory if it doesn't exist
            if (!helperDir.exists()) {
                logInfo("Creating Helper directory: " + helperDir.getAbsolutePath());
                if (!helperDir.mkdirs()) {
                    return HelperInstallationResult.failure(
                        "Failed to create Helper directory: " + helperDir.getAbsolutePath(),
                        helperExecutable,
                        helperContextDir
                    );
                }
                logDirectoryCreated(helperDir);
            }

            // Step 3: Get installer bundle path
            if (!InstallerBundleLocator.isLauncherPathSet()) {
                return HelperInstallationResult.failure(
                    "Installer bundle path not available (jdeploy.launcher.path not set)",
                    helperExecutable,
                    helperContextDir
                );
            }

            File installerBundle;
            try {
                installerBundle = InstallerBundleLocator.getInstallerPath();
            } catch (IllegalStateException e) {
                return HelperInstallationResult.failure(
                    "Failed to locate installer bundle: " + e.getMessage(),
                    helperExecutable,
                    helperContextDir
                );
            }

            logInfo("Installer bundle: " + installerBundle.getAbsolutePath());

            // Step 4: Copy installer to Helper location
            logInfo("Copying installer to Helper location...");
            copyService.copyInstaller(installerBundle, helperExecutable);

            // Step 5: Copy .jdeploy-files to Helper directory
            logInfo("Copying context directory...");
            copyService.copyContextDirectory(jdeployFilesDir, helperContextDir);

            // Verify installation
            if (!helperExecutable.exists()) {
                return HelperInstallationResult.failure(
                    "Helper executable was not created at expected location",
                    helperExecutable,
                    helperContextDir
                );
            }
            if (!helperContextDir.exists()) {
                return HelperInstallationResult.failure(
                    "Helper context directory was not created at expected location",
                    helperExecutable,
                    helperContextDir
                );
            }

            logInfo("Helper installation completed successfully");
            return HelperInstallationResult.success(helperExecutable, helperContextDir);

        } catch (Exception e) {
            String errorMessage = "Helper installation failed: " + e.getMessage();
            logError(errorMessage, e);
            // Include paths in failure result if they were calculated
            if (helperExecutable != null || helperContextDir != null) {
                return HelperInstallationResult.failure(errorMessage, helperExecutable, helperContextDir);
            }
            return HelperInstallationResult.failure(errorMessage);
        }
    }

    /**
     * Checks if the Helper is already installed at the expected location.
     *
     * @param appName The application name
     * @param appDirectory The application installation directory (used on Windows/Linux)
     * @return true if the Helper executable exists, false otherwise
     */
    public boolean isHelperInstalled(String appName, File appDirectory) {
        if (appName == null || appName.trim().isEmpty()) {
            return false;
        }

        try {
            File helperExecutable = HelperPaths.getHelperExecutablePath(appName, appDirectory);
            return helperExecutable.exists();
        } catch (IllegalArgumentException e) {
            // Invalid arguments - Helper cannot be installed
            return false;
        }
    }

    // Logging helper methods

    private void logSection(String sectionName) {
        if (logger != null) {
            logger.logSection(sectionName);
        }
    }

    private void logInfo(String message) {
        if (logger != null) {
            logger.logInfo(message);
        }
    }

    private void logError(String message, Throwable e) {
        if (logger != null) {
            logger.logError(message, e);
        }
    }

    private void logDirectoryCreated(File dir) {
        if (logger != null) {
            logger.logDirectoryOperation(
                InstallationLogger.DirectoryOperation.CREATED,
                dir.getAbsolutePath()
            );
        }
    }
}

package ca.weblite.jdeploy.installer.helpers;

import ca.weblite.jdeploy.installer.logging.InstallationLogger;
import ca.weblite.tools.platform.Platform;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * Service for updating Helper installations during application updates.
 *
 * Handles the complete update lifecycle:
 * <ul>
 *   <li>Installing Helper when services are added to an application</li>
 *   <li>Updating Helper when the application is updated</li>
 *   <li>Removing Helper when services are removed from an application</li>
 * </ul>
 *
 * Coordinates between HelperInstallationService and HelperProcessManager
 * to safely update the Helper without leaving stale processes running.
 *
 * @author jDeploy Team
 */
public class HelperUpdateService {

    private static final long TERMINATION_TIMEOUT_MS = 5000;

    private final HelperInstallationService installationService;
    private final HelperProcessManager processManager;
    private final InstallationLogger logger;

    /**
     * Creates a new HelperUpdateService.
     *
     * @param installationService Service for installing Helper applications
     * @param processManager Manager for detecting and terminating Helper processes
     * @param logger Logger for recording operations (may be null)
     */
    public HelperUpdateService(HelperInstallationService installationService,
                               HelperProcessManager processManager,
                               InstallationLogger logger) {
        if (installationService == null) {
            throw new IllegalArgumentException("installationService cannot be null");
        }
        if (processManager == null) {
            throw new IllegalArgumentException("processManager cannot be null");
        }
        this.installationService = installationService;
        this.processManager = processManager;
        this.logger = logger;
    }

    /**
     * Updates the Helper installation.
     *
     * This method handles three scenarios:
     * <ol>
     *   <li>Services exist and Helper exists: Update Helper (terminate, delete, reinstall)</li>
     *   <li>Services exist but Helper doesn't: Install Helper (new services added)</li>
     *   <li>Services removed: Remove Helper if it exists</li>
     * </ol>
     *
     * @param packageName The package name for lock file identification (e.g., "@foo/bar")
     * @param source The package source for lock file identification (e.g., null for npm, GitHub URL)
     * @param appName The application name for paths and process killing (e.g., "My App")
     * @param appDirectory The application installation directory (ignored on macOS)
     * @param jdeployFilesDir The source .jdeploy-files directory
     * @param servicesExist True if the new version has services, false if services were removed
     * @return Result indicating success or failure
     * @throws IllegalArgumentException if packageName or appName is null or empty
     */
    public HelperUpdateResult updateHelper(String packageName, String source, String appName,
                                           File appDirectory, File jdeployFilesDir, boolean servicesExist) {
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("packageName cannot be null or empty");
        }
        if (appName == null || appName.trim().isEmpty()) {
            throw new IllegalArgumentException("appName cannot be null or empty");
        }

        logInfo("Updating Helper for: " + appName + " (package=" + packageName + ", servicesExist=" + servicesExist + ")");

        if (servicesExist) {
            return handleServicesExist(packageName, source, appName, appDirectory, jdeployFilesDir);
        } else {
            return handleServicesRemoved(packageName, source, appName, appDirectory);
        }
    }

    /**
     * Deletes an existing Helper installation.
     *
     * Removes:
     * <ul>
     *   <li>Helper executable/bundle</li>
     *   <li>.jdeploy-files directory</li>
     *   <li>Helpers directory (if empty on Windows/Linux)</li>
     *   <li>Parent Helper directory on macOS</li>
     * </ul>
     *
     * @param appName The application name
     * @param appDirectory The application installation directory (ignored on macOS)
     * @return true if deletion was successful or Helper didn't exist
     * @throws IllegalArgumentException if appName is null or empty
     */
    public boolean deleteHelper(String appName, File appDirectory) {
        if (appName == null || appName.trim().isEmpty()) {
            throw new IllegalArgumentException("appName cannot be null or empty");
        }

        logInfo("Deleting Helper for: " + appName);

        try {
            File helperExecutable = HelperPaths.getHelperExecutablePath(appName, appDirectory);
            File helperContextDir = HelperPaths.getHelperContextDirectory(appName, appDirectory);
            File helperDir = HelperPaths.getHelperDirectory(appName, appDirectory);

            // Delete Helper executable/bundle
            if (helperExecutable.exists()) {
                if (helperExecutable.isDirectory()) {
                    // macOS .app bundle
                    try {
                        FileUtils.deleteDirectory(helperExecutable);
                        logInfo("Deleted Helper bundle: " + helperExecutable.getAbsolutePath());
                    } catch (IOException e) {
                        logWarning("Failed to delete Helper bundle: " + helperExecutable.getAbsolutePath() +
                                   " - " + e.getMessage());
                        return false;
                    }
                } else {
                    // Windows/Linux executable
                    boolean deleted = helperExecutable.delete();
                    if (deleted) {
                        logInfo("Deleted Helper executable: " + helperExecutable.getAbsolutePath());
                    } else {
                        logWarning("Failed to delete Helper executable: " + helperExecutable.getAbsolutePath() +
                                   " - file may be locked or in use");
                        return false;
                    }
                }
            }

            // Delete .jdeploy-files directory
            if (helperContextDir.exists()) {
                try {
                    FileUtils.deleteDirectory(helperContextDir);
                    logInfo("Deleted Helper context directory: " + helperContextDir.getAbsolutePath());
                } catch (IOException e) {
                    logWarning("Failed to delete Helper context directory: " + helperContextDir.getAbsolutePath() +
                               " - " + e.getMessage());
                    // Continue - context directory deletion failure is not critical
                }
            }

            // Delete helper directory if empty (or always on macOS)
            if (helperDir.exists()) {
                if (Platform.getSystemPlatform().isMac()) {
                    // On macOS, the helper directory is "{AppName} Helper" - delete it
                    try {
                        FileUtils.deleteDirectory(helperDir);
                        logInfo("Deleted Helper directory: " + helperDir.getAbsolutePath());
                    } catch (IOException e) {
                        logWarning("Failed to delete Helper directory: " + helperDir.getAbsolutePath() +
                                   " - " + e.getMessage());
                        // Continue - directory deletion failure is not critical
                    }
                } else {
                    // On Windows/Linux, only delete if empty
                    String[] remaining = helperDir.list();
                    if (remaining == null || remaining.length == 0) {
                        boolean deleted = helperDir.delete();
                        if (deleted) {
                            logInfo("Deleted empty helpers directory: " + helperDir.getAbsolutePath());
                        } else {
                            logWarning("Failed to delete empty helpers directory: " + helperDir.getAbsolutePath());
                            // Continue - directory deletion failure is not critical
                        }
                    }
                }
            }

            return true;

        } catch (Exception e) {
            logWarning("Unexpected error deleting Helper: " + e.getMessage());
            return false;
        }
    }

    /**
     * Handles the case where the new version has services.
     * Either updates existing Helper or installs new one.
     */
    private HelperUpdateResult handleServicesExist(String packageName, String source, String appName,
                                                    File appDirectory, File jdeployFilesDir) {
        File helperExecutable = HelperPaths.getHelperExecutablePath(appName, appDirectory);
        boolean helperExists = helperExecutable.exists();

        if (helperExists) {
            logInfo("Existing Helper found, updating...");

            // Step 1: Terminate running Helper
            boolean terminated = terminateExistingHelper(packageName, source, appName);
            if (!terminated) {
                logWarning("Could not terminate existing Helper, attempting to continue anyway");
            }

            // Step 2: Delete existing Helper files
            boolean deleted = deleteHelper(appName, appDirectory);
            if (!deleted) {
                return HelperUpdateResult.failure("Failed to delete existing Helper before update");
            }

            // Step 3: Install new Helper
            HelperInstallationResult installResult = installationService.installHelper(
                    appName, appDirectory, jdeployFilesDir);

            if (installResult.isSuccess()) {
                logInfo("Helper updated successfully");
                return HelperUpdateResult.updated(installResult);
            } else {
                return HelperUpdateResult.failure("Failed to install updated Helper: " + installResult.getErrorMessage());
            }

        } else {
            logInfo("No existing Helper found, installing new Helper...");

            // Install new Helper
            HelperInstallationResult installResult = installationService.installHelper(
                    appName, appDirectory, jdeployFilesDir);

            if (installResult.isSuccess()) {
                logInfo("Helper installed successfully");
                return HelperUpdateResult.installed(installResult);
            } else {
                return HelperUpdateResult.failure("Failed to install Helper: " + installResult.getErrorMessage());
            }
        }
    }

    /**
     * Handles the case where services have been removed from the application.
     * Removes the Helper if it exists.
     */
    private HelperUpdateResult handleServicesRemoved(String packageName, String source, String appName,
                                                      File appDirectory) {
        File helperExecutable = HelperPaths.getHelperExecutablePath(appName, appDirectory);
        boolean helperExists = helperExecutable.exists();

        if (!helperExists) {
            logInfo("No Helper to remove (didn't exist)");
            return HelperUpdateResult.noActionNeeded();
        }

        logInfo("Services removed, removing Helper...");

        // Step 1: Terminate running Helper
        boolean terminated = terminateExistingHelper(packageName, source, appName);
        if (!terminated) {
            logWarning("Could not terminate Helper, attempting to continue anyway");
        }

        // Step 2: Delete Helper files
        boolean deleted = deleteHelper(appName, appDirectory);
        if (deleted) {
            logInfo("Helper removed successfully");
            return HelperUpdateResult.removed();
        } else {
            return HelperUpdateResult.failure("Failed to remove Helper");
        }
    }

    /**
     * Attempts to terminate the existing Helper process.
     *
     * @param packageName The package name for lock file identification
     * @param source The package source for lock file identification
     * @param appName The application name for process killing
     * @return true if terminated or wasn't running
     */
    private boolean terminateExistingHelper(String packageName, String source, String appName) {
        if (!processManager.isHelperRunning(packageName, source)) {
            logInfo("Helper is not running");
            return true;
        }

        logInfo("Terminating running Helper...");
        boolean terminated = processManager.terminateHelper(packageName, source, appName, TERMINATION_TIMEOUT_MS);

        if (terminated) {
            logInfo("Helper terminated successfully");
        } else {
            logWarning("Failed to terminate Helper");
        }

        return terminated;
    }

    private void logInfo(String message) {
        if (logger != null) {
            logger.logInfo(message);
        }
        System.out.println("[HelperUpdateService] " + message);
    }

    private void logWarning(String message) {
        if (logger != null) {
            logger.logWarning(message);
        }
        System.err.println("[HelperUpdateService] WARNING: " + message);
    }

    /**
     * Result of a Helper update operation.
     */
    public static class HelperUpdateResult {

        /**
         * The type of update that occurred.
         */
        public enum UpdateType {
            /** Helper was installed (new services added) */
            INSTALLED,
            /** Helper was updated (replaced existing) */
            UPDATED,
            /** Helper was removed (services removed) */
            REMOVED,
            /** No action was needed (e.g., no Helper to remove) */
            NO_ACTION,
            /** Update failed */
            FAILED
        }

        private final UpdateType type;
        private final boolean success;
        private final String errorMessage;
        private final HelperInstallationResult installationResult;

        private HelperUpdateResult(UpdateType type, boolean success, String errorMessage,
                                   HelperInstallationResult installationResult) {
            this.type = type;
            this.success = success;
            this.errorMessage = errorMessage;
            this.installationResult = installationResult;
        }

        /**
         * Creates a result for a newly installed Helper.
         */
        public static HelperUpdateResult installed(HelperInstallationResult installResult) {
            return new HelperUpdateResult(UpdateType.INSTALLED, true, null, installResult);
        }

        /**
         * Creates a result for an updated Helper.
         */
        public static HelperUpdateResult updated(HelperInstallationResult installResult) {
            return new HelperUpdateResult(UpdateType.UPDATED, true, null, installResult);
        }

        /**
         * Creates a result for a removed Helper.
         */
        public static HelperUpdateResult removed() {
            return new HelperUpdateResult(UpdateType.REMOVED, true, null, null);
        }

        /**
         * Creates a result indicating no action was needed.
         */
        public static HelperUpdateResult noActionNeeded() {
            return new HelperUpdateResult(UpdateType.NO_ACTION, true, null, null);
        }

        /**
         * Creates a result for a failed update.
         */
        public static HelperUpdateResult failure(String errorMessage) {
            return new HelperUpdateResult(UpdateType.FAILED, false, errorMessage, null);
        }

        /**
         * Returns the type of update that occurred.
         */
        public UpdateType getType() {
            return type;
        }

        /**
         * Returns true if the update was successful.
         */
        public boolean isSuccess() {
            return success;
        }

        /**
         * Returns the error message if the update failed.
         */
        public String getErrorMessage() {
            return errorMessage;
        }

        /**
         * Returns the installation result if Helper was installed or updated.
         */
        public HelperInstallationResult getInstallationResult() {
            return installationResult;
        }

        @Override
        public String toString() {
            return "HelperUpdateResult{" +
                    "type=" + type +
                    ", success=" + success +
                    ", errorMessage='" + errorMessage + '\'' +
                    '}';
        }
    }
}

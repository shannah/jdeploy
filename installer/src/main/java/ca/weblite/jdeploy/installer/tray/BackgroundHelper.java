package ca.weblite.jdeploy.installer.tray;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.installer.helpers.HelperCleanupScriptGenerator;
import ca.weblite.jdeploy.installer.helpers.HelperSelfDeleteService;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.models.ServiceRowModel;
import ca.weblite.jdeploy.installer.services.ServiceLogHelper;
import ca.weblite.jdeploy.installer.services.ServiceStatusPoller;
import ca.weblite.jdeploy.installer.uninstall.FileUninstallManifestRepository;
import ca.weblite.jdeploy.installer.uninstall.UninstallService;
import ca.weblite.jdeploy.installer.win.JnaRegistryOperations;
import ca.weblite.jdeploy.installer.win.RegistryOperations;
import ca.weblite.tools.io.MD5;
import ca.weblite.tools.platform.Platform;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Background helper that runs as a system tray application for service management.
 *
 * This class manages the lifecycle of a background helper utility that shows
 * only a system tray icon (no main window) for managing application services.
 * It's intended to be used when the installer is launched in background helper mode.
 *
 * Follows Single Responsibility Principle - only manages background helper functionality.
 *
 * @author Steve Hannah
 */
public class BackgroundHelper {

    private static final Logger logger = Logger.getLogger(BackgroundHelper.class.getName());

    /**
     * Directory where lock and shutdown signal files are stored.
     */
    private static final String LOCK_DIR_PATH = System.getProperty("user.home") +
            File.separator + ".jdeploy" + File.separator + "locks";

    /**
     * Interval in milliseconds between shutdown signal checks.
     */
    private static final long SHUTDOWN_CHECK_INTERVAL_MS = 500;

    private final InstallationSettings settings;
    private ServiceTrayController trayController;

    /**
     * Thread for monitoring shutdown signal file.
     */
    private Thread shutdownMonitorThread;

    /**
     * Flag to control shutdown monitor loop.
     */
    private final AtomicBoolean shutdownMonitorRunning = new AtomicBoolean(false);

    /**
     * Creates a new background helper.
     *
     * @param settings The installation settings (provides package name, source, app info)
     */
    public BackgroundHelper(InstallationSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("settings cannot be null");
        }
        this.settings = settings;
    }

    /**
     * Starts the background helper.
     *
     * Shows the system tray icon with service management menu.
     * This method should be called on the Event Dispatch Thread.
     *
     * If another instance is already running (lock cannot be acquired by ServiceTrayController),
     * this method will silently exit the application.
     *
     * @throws IllegalStateException if SystemTray is not supported
     * @throws RuntimeException if the tray icon cannot be started
     */
    public void start() {
        if (!SystemTray.isSupported()) {
            throw new IllegalStateException("SystemTray is not supported on this platform");
        }

        logger.info("Starting background helper");

        // Create listener for tray menu actions
        ServiceTrayMenuListener listener = createTrayMenuListener();

        // Create and start tray controller
        // ServiceTrayController will handle lock acquisition
        trayController = new ServiceTrayController(settings, listener);

        try {
            trayController.start();

            // Check if tray controller actually started (lock may have been held by another instance)
            if (!trayController.isRunning()) {
                // Another instance is already running - silently exit
                logger.info("Another instance is already running. Exiting silently.");
                System.exit(0);
                return; // Won't reach here, but explicit for clarity
            }

            // Start shutdown signal monitoring
            startShutdownMonitor();

            logger.info("Background helper started successfully");
        } catch (Exception e) {
            logger.severe("Failed to start background helper: " + e.getMessage());
            throw new RuntimeException("Failed to start background helper", e);
        }
    }

    /**
     * Stops the background helper.
     *
     * Stops the shutdown monitor and tray controller, cleans up resources.
     * Lock is automatically released by ServiceTrayController.
     */
    public void stop() {
        // Stop shutdown monitor first
        stopShutdownMonitor();

        if (trayController != null) {
            trayController.stop();
            trayController = null;
        }

        logger.info("Background helper stopped");
    }

    /**
     * Creates the tray menu listener that handles all tray menu actions.
     */
    private ServiceTrayMenuListener createTrayMenuListener() {
        return new ServiceTrayMenuListener() {
            @Override
            public void onStart(ServiceRowModel service) {
                handleServiceStart(service);
            }

            @Override
            public void onStop(ServiceRowModel service) {
                handleServiceStop(service);
            }

            @Override
            public void onViewOutputLog(ServiceRowModel service) {
                handleViewOutputLog(service);
            }

            @Override
            public void onViewErrorLog(ServiceRowModel service) {
                handleViewErrorLog(service);
            }

            @Override
            public void onUninstall() {
                handleUninstall();
            }

            @Override
            public void onQuit() {
                handleQuit();
            }
        };
    }

    /**
     * Handles starting a service from the tray menu.
     */
    private void handleServiceStart(ServiceRowModel service) {
        String packageName = getPackageName();
        String source = getSource();

        new Thread(() -> {
            service.setOperationInProgress(true);

            ServiceStatusPoller poller = new ServiceStatusPoller(null, packageName, source);
            boolean success = poller.startService(service);

            service.setOperationInProgress(false);

            if (success) {
                logger.info("Service started successfully: " + service.getServiceName());
            } else {
                logger.warning("Failed to start service: " + service.getServiceName());
            }

            // Re-poll status after operation
            poller.pollStatus(service);
        }).start();
    }

    /**
     * Handles stopping a service from the tray menu.
     */
    private void handleServiceStop(ServiceRowModel service) {
        String packageName = getPackageName();
        String source = getSource();

        new Thread(() -> {
            service.setOperationInProgress(true);

            ServiceStatusPoller poller = new ServiceStatusPoller(null, packageName, source);
            boolean success = poller.stopService(service);

            service.setOperationInProgress(false);

            if (success) {
                logger.info("Service stopped successfully: " + service.getServiceName());
            } else {
                logger.warning("Failed to stop service: " + service.getServiceName());
            }

            // Re-poll status after operation
            poller.pollStatus(service);
        }).start();
    }

    /**
     * Handles viewing output log from the tray menu.
     */
    private void handleViewOutputLog(ServiceRowModel service) {
        File logFile = ServiceLogHelper.getOutputLogFile(service);
        ServiceLogHelper.openLogFile(logFile, null);
    }

    /**
     * Handles viewing error log from the tray menu.
     */
    private void handleViewErrorLog(ServiceRowModel service) {
        File logFile = ServiceLogHelper.getErrorLogFile(service);
        ServiceLogHelper.openLogFile(logFile, null);
    }

    /**
     * Handles uninstall request from the tray menu.
     */
    private void handleUninstall() {
        logger.info("Uninstall requested from background helper");

        // Step 1: Show confirmation dialog
        String appName = getAppName();

        int result = JOptionPane.showConfirmDialog(
                null,
                "Do you want to uninstall " + appName + "?",
                "Uninstall " + appName,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (result != JOptionPane.YES_OPTION) {
            logger.info("Uninstall cancelled by user");
            return; // User cancelled
        }

        // Step 2: Stop the tray controller (remove tray icon)
        stop();

        // Step 3: Perform uninstallation using UninstallService
        String packageName = getPackageName();
        String source = getSource();
        boolean uninstallSucceeded = false;

        try {
            UninstallService uninstallService = createUninstallService();
            UninstallService.UninstallResult uninstallResult = uninstallService.uninstall(packageName, source);

            if (!uninstallResult.isSuccess()) {
                // Log errors but continue with Helper cleanup
                for (String error : uninstallResult.getErrors()) {
                    logger.warning("Uninstall error: " + error);
                }
            } else {
                uninstallSucceeded = true;
            }
            logger.info("Uninstall completed: " + uninstallResult);
        } catch (Exception e) {
            logger.severe("Failed to perform uninstallation: " + e.getMessage());
            // Show error dialog to user but still attempt Helper cleanup
            JOptionPane.showMessageDialog(
                    null,
                    "An error occurred during uninstallation:\n" + e.getMessage() +
                            "\n\nThe Helper application will still be removed.",
                    "Uninstall Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }

        // Step 4: Schedule Helper self-deletion
        try {
            HelperSelfDeleteService selfDeleteService = createSelfDeleteService();
            boolean scheduled = selfDeleteService.scheduleCurrentHelperCleanup();
            if (scheduled) {
                logger.info("Helper cleanup scheduled successfully");
            } else {
                logger.warning("Failed to schedule Helper cleanup");
            }
        } catch (Exception e) {
            logger.severe("Failed to schedule Helper cleanup: " + e.getMessage());
        }

        // Step 5: Exit
        logger.info("Exiting background helper after uninstall");
        System.exit(0);
    }

    /**
     * Handles quit request from the tray menu.
     */
    private void handleQuit() {
        logger.info("Quit requested from background helper");

        // Stop the tray controller
        stop();

        // Exit gracefully
        System.exit(0);
    }

    // ========== Shutdown Signal Monitoring ==========

    /**
     * Starts the shutdown signal monitor thread.
     *
     * The monitor periodically checks for a shutdown signal file created by
     * HelperProcessManager.tryGracefulTermination(). When detected, the Helper
     * performs a graceful shutdown.
     */
    private void startShutdownMonitor() {
        if (shutdownMonitorRunning.get()) {
            logger.warning("Shutdown monitor is already running");
            return;
        }

        shutdownMonitorRunning.set(true);

        shutdownMonitorThread = new Thread(() -> {
            logger.info("Shutdown monitor started");

            while (shutdownMonitorRunning.get()) {
                try {
                    // Check for shutdown signal file
                    File signalFile = getShutdownSignalFile();
                    if (signalFile != null && signalFile.exists()) {
                        logger.info("Shutdown signal file detected: " + signalFile.getAbsolutePath());
                        handleShutdownSignal(signalFile);
                        return; // Exit monitor thread
                    }

                    // Sleep before next check
                    Thread.sleep(SHUTDOWN_CHECK_INTERVAL_MS);

                } catch (InterruptedException e) {
                    // Thread was interrupted - exit gracefully
                    Thread.currentThread().interrupt();
                    logger.info("Shutdown monitor interrupted");
                    return;
                } catch (Exception e) {
                    // Log unexpected errors but continue monitoring
                    logger.warning("Error in shutdown monitor: " + e.getMessage());
                }
            }

            logger.info("Shutdown monitor stopped");
        }, "BackgroundHelper-ShutdownMonitor");

        shutdownMonitorThread.setDaemon(true);
        shutdownMonitorThread.start();
    }

    /**
     * Stops the shutdown signal monitor thread.
     */
    private void stopShutdownMonitor() {
        if (!shutdownMonitorRunning.get()) {
            return;
        }

        shutdownMonitorRunning.set(false);

        if (shutdownMonitorThread != null) {
            shutdownMonitorThread.interrupt();
            try {
                // Wait briefly for thread to finish
                shutdownMonitorThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            shutdownMonitorThread = null;
        }
    }

    /**
     * Handles detection of the shutdown signal file.
     *
     * Performs graceful shutdown:
     * 1. Logs the shutdown signal
     * 2. Cleans up (removes tray icon, releases locks)
     * 3. Deletes the shutdown signal file
     * 4. Exits gracefully
     *
     * @param signalFile The shutdown signal file that was detected
     */
    private void handleShutdownSignal(File signalFile) {
        logger.info("Shutdown signal received, exiting gracefully");

        // Clean up - this will stop the shutdown monitor and tray controller
        // Do this on the EDT to avoid threading issues with the tray icon
        try {
            SwingUtilities.invokeAndWait(() -> {
                stop();
            });
        } catch (Exception e) {
            logger.warning("Error during shutdown cleanup: " + e.getMessage());
            // Continue with signal file deletion and exit
        }

        // Delete the signal file
        boolean deleted = signalFile.delete();
        if (deleted) {
            logger.info("Shutdown signal file deleted");
        } else {
            logger.warning("Failed to delete shutdown signal file: " + signalFile.getAbsolutePath());
        }

        // Exit gracefully
        logger.info("Exiting due to shutdown signal");
        System.exit(0);
    }

    /**
     * Gets the shutdown signal file for this Helper.
     *
     * The signal file path matches what HelperProcessManager creates:
     * ~/.jdeploy/locks/{fullyQualifiedName}.shutdown
     *
     * @return The shutdown signal file, or null if package name is not available
     */
    File getShutdownSignalFile() {
        String packageName = getPackageName();
        if (packageName == null || packageName.isEmpty()) {
            return null;
        }

        String source = getSource();
        String fullyQualifiedName = createFullyQualifiedName(packageName, source);
        return new File(LOCK_DIR_PATH, fullyQualifiedName + ".shutdown");
    }

    /**
     * Creates a fully qualified package name from package name and source.
     *
     * Format: {sourceHash}.{sanitizedPackageName} or just {sanitizedPackageName} if no source.
     * This must match the logic in HelperProcessManager.createFullyQualifiedName().
     *
     * @param packageName The package name
     * @param source The package source (may be null)
     * @return The fully qualified name
     */
    private String createFullyQualifiedName(String packageName, String source) {
        String sanitized = sanitizeName(packageName);
        if (source != null && !source.isEmpty()) {
            String sourceHash = MD5.getMd5(source);
            return sourceHash + "." + sanitized;
        }
        return sanitized;
    }

    /**
     * Sanitizes a name to make it filesystem-safe.
     *
     * This must match the logic in HelperProcessManager.sanitizeName().
     *
     * @param name The name to sanitize
     * @return The sanitized name
     */
    private String sanitizeName(String name) {
        return name.toLowerCase()
                .replace(" ", "-")
                .replace("@", "")
                .replace("/", "-")
                .replace("\\", "-")
                .replace(":", "-")
                .replace("*", "-")
                .replace("?", "-")
                .replace("\"", "-")
                .replace("<", "-")
                .replace(">", "-")
                .replace("|", "-");
    }

    /**
     * Checks if the shutdown monitor is currently running.
     *
     * @return true if the monitor is running, false otherwise
     */
    boolean isShutdownMonitorRunning() {
        return shutdownMonitorRunning.get();
    }

    /**
     * Gets the package name for service operations, with fallback to AppInfo.
     */
    private String getPackageName() {
        String packageName = settings.getPackageName();
        if (packageName == null || packageName.isEmpty()) {
            AppInfo appInfo = settings.getAppInfo();
            if (appInfo != null) {
                packageName = appInfo.getNpmPackage();
            }
        }
        return packageName;
    }

    /**
     * Gets the source for service operations, with fallback to AppInfo.
     */
    private String getSource() {
        String source = settings.getSource();
        if (source == null) {
            AppInfo appInfo = settings.getAppInfo();
            if (appInfo != null) {
                source = appInfo.getNpmSource();
                // Treat empty string as null
                if (source != null && source.isEmpty()) {
                    source = null;
                }
            }
        }
        return source;
    }

    /**
     * Gets the application name for display purposes.
     */
    private String getAppName() {
        AppInfo appInfo = settings.getAppInfo();
        if (appInfo != null && appInfo.getTitle() != null && !appInfo.getTitle().isEmpty()) {
            return appInfo.getTitle();
        }
        // Fall back to package name if no title available
        String packageName = getPackageName();
        return packageName != null ? packageName : "Application";
    }

    /**
     * Creates an UninstallService instance.
     *
     * Uses JNA-based registry operations on Windows, no-op implementation on other platforms.
     */
    private UninstallService createUninstallService() {
        FileUninstallManifestRepository manifestRepository = new FileUninstallManifestRepository();
        RegistryOperations registryOperations = createRegistryOperations();
        return new UninstallService(manifestRepository, registryOperations);
    }

    /**
     * Creates platform-appropriate RegistryOperations.
     *
     * Returns JNA-based implementation on Windows, no-op implementation on other platforms.
     */
    private RegistryOperations createRegistryOperations() {
        if (Platform.getSystemPlatform().isWindows()) {
            return new JnaRegistryOperations();
        }
        // Return a no-op implementation for non-Windows platforms
        return new NoOpRegistryOperations();
    }

    /**
     * Creates a HelperSelfDeleteService instance.
     */
    private HelperSelfDeleteService createSelfDeleteService() {
        HelperCleanupScriptGenerator scriptGenerator = new HelperCleanupScriptGenerator();
        return new HelperSelfDeleteService(scriptGenerator, logger);
    }

    /**
     * No-op implementation of RegistryOperations for non-Windows platforms.
     */
    private static class NoOpRegistryOperations implements RegistryOperations {
        @Override
        public boolean keyExists(String key) {
            return false;
        }

        @Override
        public boolean valueExists(String key, String valueName) {
            return false;
        }

        @Override
        public String getStringValue(String key, String valueName) {
            return null;
        }

        @Override
        public void setStringValue(String key, String valueName, String value) {
            // No-op
        }

        @Override
        public void setLongValue(String key, long value) {
            // No-op
        }

        @Override
        public void createKey(String key) {
            // No-op
        }

        @Override
        public void deleteKey(String key) {
            // No-op
        }

        @Override
        public void deleteValue(String key, String valueName) {
            // No-op
        }

        @Override
        public java.util.Set<String> getKeys(String key) {
            return java.util.Collections.emptySet();
        }

        @Override
        public java.util.Map<String, Object> getValues(String key) {
            return java.util.Collections.emptyMap();
        }
    }
}

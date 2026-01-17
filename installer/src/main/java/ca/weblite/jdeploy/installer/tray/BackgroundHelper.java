package ca.weblite.jdeploy.installer.tray;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.models.ServiceRowModel;
import ca.weblite.jdeploy.installer.services.ServiceLogHelper;
import ca.weblite.jdeploy.installer.services.ServiceStatusPoller;

import java.awt.*;
import java.io.File;
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

    private final InstallationSettings settings;
    private ServiceTrayController trayController;

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

            logger.info("Background helper started successfully");
        } catch (Exception e) {
            logger.severe("Failed to start background helper: " + e.getMessage());
            throw new RuntimeException("Failed to start background helper", e);
        }
    }

    /**
     * Stops the background helper.
     *
     * Stops the tray controller and cleans up resources.
     * Lock is automatically released by ServiceTrayController.
     */
    public void stop() {
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

        // Stop the tray controller first
        stop();

        // Launch the installer in uninstall mode
        // TODO: Implement launching the uninstaller
        logger.warning("Uninstaller launch not yet implemented");

        // Exit this background helper process
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
}

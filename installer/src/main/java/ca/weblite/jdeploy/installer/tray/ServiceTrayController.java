package ca.weblite.jdeploy.installer.tray;

import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.models.ServiceRowModel;
import ca.weblite.jdeploy.installer.services.ServiceDescriptor;
import ca.weblite.jdeploy.installer.services.ServiceDescriptorService;
import ca.weblite.jdeploy.installer.services.ServiceDescriptorServiceFactory;
import ca.weblite.jdeploy.installer.services.ServiceStatusMonitor;
import ca.weblite.jdeploy.installer.services.ServiceStatusPoller;

import javax.swing.ImageIcon;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for managing the system tray menu lifecycle.
 *
 * Handles creation, polling, and cleanup of the service tray menu.
 * This class encapsulates all tray-related logic, keeping it separate
 * from the installation form UI.
 *
 * Follows Single Responsibility Principle - only manages tray menu.
 *
 * @author Steve Hannah
 */
public class ServiceTrayController {

    private static final Logger logger = Logger.getLogger(ServiceTrayController.class.getName());
    private static final int MONITOR_POLL_INTERVAL_MS = 5000; // 5 seconds

    private final InstallationSettings settings;
    private final ServiceTrayMenuListener listener;

    private ServiceTrayMenu trayMenu;
    private ServiceStatusMonitor statusMonitor;
    private ServiceStatusPoller poller;
    private List<ServiceRowModel> serviceModels;
    private TrayIconLock trayIconLock;
    private boolean running = false;

    /**
     * Creates a new tray controller.
     *
     * @param settings The installation settings (provides package name, source, app info)
     * @param listener The listener for tray menu actions
     */
    public ServiceTrayController(InstallationSettings settings, ServiceTrayMenuListener listener) {
        if (settings == null) {
            throw new IllegalArgumentException("settings cannot be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null");
        }

        this.settings = settings;
        this.listener = listener;
    }

    /**
     * Starts the tray menu.
     *
     * Loads services, creates the tray icon, and begins status polling.
     * This method is idempotent - calling it multiple times has no effect
     * if already running.
     *
     * If another instance is already running (lock cannot be acquired),
     * this method will log a warning and return without showing the tray icon.
     *
     * @throws IllegalStateException if SystemTray is not supported
     * @throws RuntimeException if tray menu creation fails
     */
    public void start() {
        if (running) {
            logger.warning("ServiceTrayController.start() called but already running");
            return;
        }

        if (!SystemTray.isSupported()) {
            throw new IllegalStateException("SystemTray is not supported on this platform");
        }

        try {
            loadServices();

            if (serviceModels.isEmpty()) {
                logger.info("No services found - tray menu not created");
                return;
            }

            // Try to acquire exclusive lock
            String packageName = getPackageNameForLock();
            String source = getSourceForLock();

            trayIconLock = new TrayIconLock(packageName, source);

            if (!trayIconLock.tryAcquire()) {
                // Another instance is already running - skip showing tray icon
                logger.warning("Another tray icon instance is already running. Skipping tray icon creation.");
                return;
            }

            createTrayMenu();
            startMonitoring();
            running = true;

            logger.info("ServiceTrayController started successfully with " + serviceModels.size() + " services");
        } catch (Exception e) {
            // Clean up partial initialization
            cleanup();
            throw new RuntimeException("Failed to start tray controller", e);
        }
    }

    /**
     * Stops the tray menu.
     *
     * Stops polling, disposes the tray icon, and releases resources.
     * This method is idempotent - safe to call multiple times.
     */
    public void stop() {
        if (!running) {
            return;
        }

        cleanup();
        running = false;

        logger.info("ServiceTrayController stopped");
    }

    /**
     * Checks if the tray controller is currently running.
     *
     * @return true if the tray menu is active and polling
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Loads services from the service descriptor service.
     *
     * Creates ServiceRowModel instances for each service found.
     */
    private void loadServices() throws IOException {
        serviceModels = new ArrayList<>();

        // Try to get packageName and source from settings first,
        // fall back to AppInfo if not available
        String packageName = settings.getPackageName();
        String source = settings.getSource();

        if (packageName == null || packageName.isEmpty()) {
            // Fall back to AppInfo
            if (settings.getAppInfo() != null) {
                packageName = settings.getAppInfo().getNpmPackage();
                source = settings.getAppInfo().getNpmSource();
            }
        }

        if (packageName == null || packageName.isEmpty()) {
            logger.warning("No package name available - cannot load services");
            return;
        }

        // Treat empty string as null for source
        if (source != null && source.isEmpty()) {
            source = null;
        }

        ServiceDescriptorService descriptorService = ServiceDescriptorServiceFactory.createDefault();
        List<ServiceDescriptor> descriptors = descriptorService.listServices(packageName, source);

        for (ServiceDescriptor descriptor : descriptors) {
            serviceModels.add(new ServiceRowModel(descriptor));
        }

        // Create poller for status updates
        if (!serviceModels.isEmpty()) {
            poller = new ServiceStatusPoller(null, packageName, source);

            // Initial status poll
            poller.pollAll(serviceModels);
        }
    }

    /**
     * Creates and installs the tray menu.
     */
    private void createTrayMenu() {
        Image appIcon = loadApplicationIcon();
        String appName = getApplicationName();

        trayMenu = new ServiceTrayMenu(appName, appIcon, serviceModels, listener);
    }

    /**
     * Starts the status monitor for event-driven updates.
     *
     * Creates a ServiceStatusMonitor that polls services periodically and
     * notifies the tray menu only when status actually changes. This avoids
     * aggressive menu rebuilding while still keeping the UI up-to-date.
     */
    private void startMonitoring() {
        if (poller == null || serviceModels == null || trayMenu == null) {
            logger.warning("Cannot start monitoring: poller, services, or tray menu is null");
            return;
        }

        // Create status monitor
        statusMonitor = new ServiceStatusMonitor(poller, serviceModels);

        // Register tray menu as listener
        statusMonitor.addListener(trayMenu);

        // Start monitoring with periodic polling
        statusMonitor.start(MONITOR_POLL_INTERVAL_MS);

        logger.info("Service status monitoring started");
    }

    /**
     * Stops monitoring and disposes tray resources.
     */
    private void cleanup() {
        stopMonitoring();
        disposeTrayMenu();
        releaseLock();

        // Clear references
        poller = null;
        serviceModels = null;
    }

    /**
     * Releases the tray icon lock.
     */
    private void releaseLock() {
        if (trayIconLock != null) {
            trayIconLock.release();
            trayIconLock = null;
        }
    }

    /**
     * Stops the status monitor.
     */
    private void stopMonitoring() {
        if (statusMonitor != null) {
            statusMonitor.stop();
            statusMonitor = null;
        }
    }

    /**
     * Disposes the tray menu.
     */
    private void disposeTrayMenu() {
        if (trayMenu != null) {
            trayMenu.dispose();
            trayMenu = null;
        }
    }

    /**
     * Loads the application icon from settings.
     *
     * @return The application icon, or a default placeholder if not available
     */
    private Image loadApplicationIcon() {
        File iconFile = settings.getApplicationIcon();

        if (iconFile != null && iconFile.exists()) {
            try {
                return new ImageIcon(iconFile.toURI().toURL()).getImage();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to load application icon from " + iconFile, e);
            }
        }

        // Return placeholder icon
        return createPlaceholderIcon();
    }

    /**
     * Creates a simple placeholder icon if application icon is not available.
     *
     * @return A 16x16 colored square as a placeholder
     */
    private Image createPlaceholderIcon() {
        java.awt.image.BufferedImage icon = new java.awt.image.BufferedImage(
            16, 16, java.awt.image.BufferedImage.TYPE_INT_RGB
        );
        Graphics2D g = icon.createGraphics();
        g.setColor(new Color(0, 120, 215)); // Blue
        g.fillRect(0, 0, 16, 16);
        g.setColor(Color.WHITE);
        g.fillOval(4, 4, 8, 8);
        g.dispose();
        return icon;
    }

    /**
     * Gets the application name from settings.
     *
     * @return The application title, or package name as fallback
     */
    private String getApplicationName() {
        if (settings.getAppInfo() != null && settings.getAppInfo().getTitle() != null) {
            return settings.getAppInfo().getTitle();
        }

        String packageName = settings.getPackageName();
        return packageName != null ? packageName : "Application";
    }

    /**
     * Gets the package name for lock identification, with fallback to AppInfo.
     *
     * @return The package name
     */
    private String getPackageNameForLock() {
        String packageName = settings.getPackageName();
        if (packageName == null || packageName.isEmpty()) {
            if (settings.getAppInfo() != null) {
                packageName = settings.getAppInfo().getNpmPackage();
            }
        }
        return packageName;
    }

    /**
     * Gets the source for lock identification, with fallback to AppInfo.
     *
     * @return The source
     */
    private String getSourceForLock() {
        String source = settings.getSource();
        if (source == null) {
            if (settings.getAppInfo() != null) {
                source = settings.getAppInfo().getNpmSource();
                // Treat empty string as null
                if (source != null && source.isEmpty()) {
                    source = null;
                }
            }
        }
        return source;
    }
}

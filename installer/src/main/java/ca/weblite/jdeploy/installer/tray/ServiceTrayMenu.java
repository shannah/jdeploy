package ca.weblite.jdeploy.installer.tray;

import ca.weblite.jdeploy.installer.models.ServiceRowModel;
import ca.weblite.jdeploy.installer.models.ServiceStatus;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * System tray menu component for managing application services.
 *
 * Displays a tray icon with a popup menu that allows users to start/stop services,
 * view logs, uninstall the application, and quit. The menu dynamically reflects
 * service status with visual indicators.
 *
 * @author Steve Hannah
 */
public class ServiceTrayMenu {

    private final String appName;
    private final Image appIcon;
    private List<ServiceRowModel> services;
    private final ServiceTrayMenuListener listener;

    private TrayIcon trayIcon;
    private PopupMenu popupMenu;

    // Tracks previous error messages by service command name to detect new errors
    private final Map<String, String> previousErrors;

    /**
     * Creates a new service tray menu.
     *
     * @param appName The application name, used in menu header and uninstall label
     * @param appIcon The icon to display in the system tray
     * @param services The list of service models to display in the menu
     * @param listener Callback listener for menu actions
     * @throws IllegalStateException if SystemTray is not supported on this platform
     */
    public ServiceTrayMenu(String appName, Image appIcon, List<ServiceRowModel> services, ServiceTrayMenuListener listener) {
        if (!SystemTray.isSupported()) {
            throw new IllegalStateException("SystemTray is not supported on this platform");
        }

        this.appName = appName;
        this.appIcon = appIcon;
        this.services = services;
        this.listener = listener;
        this.previousErrors = new HashMap<String, String>();

        // Initialize error tracking state
        for (ServiceRowModel service : services) {
            previousErrors.put(service.getCommandName(), service.getErrorMessage());
        }

        buildMenu();
        installTrayIcon();
    }

    /**
     * Builds the popup menu structure.
     *
     * Creates a menu with header, service submenus, uninstall, and quit options.
     */
    private void buildMenu() {
        popupMenu = new PopupMenu();

        // Header
        MenuItem header = new MenuItem(appName + " Services");
        header.setEnabled(false);
        popupMenu.add(header);

        popupMenu.addSeparator();

        // Service menus
        if (!services.isEmpty()) {
            for (ServiceRowModel service : services) {
                Menu serviceMenu = createServiceMenu(service);
                popupMenu.add(serviceMenu);
            }

            popupMenu.addSeparator();
        }

        // Uninstall
        MenuItem uninstallItem = new MenuItem("Uninstall " + appName + "...");
        uninstallItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                listener.onUninstall();
            }
        });
        popupMenu.add(uninstallItem);

        popupMenu.addSeparator();

        // Quit
        MenuItem quitItem = new MenuItem("Quit");
        quitItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                listener.onQuit();
            }
        });
        popupMenu.add(quitItem);
    }

    /**
     * Creates a submenu for a single service.
     *
     * The submenu includes start, stop, and log viewing options.
     * Menu items are enabled/disabled based on service state.
     *
     * @param service The service model
     * @return The service submenu
     */
    private Menu createServiceMenu(ServiceRowModel service) {
        String statusIndicator = getStatusIndicator(service);
        String serviceName = service.getServiceName();
        Menu serviceMenu = new Menu(statusIndicator + serviceName);

        // Start
        MenuItem startItem = new MenuItem("Start");
        startItem.setEnabled(service.canStart());
        startItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                listener.onStart(service);
            }
        });
        serviceMenu.add(startItem);

        // Stop
        MenuItem stopItem = new MenuItem("Stop");
        stopItem.setEnabled(service.canStop());
        stopItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                listener.onStop(service);
            }
        });
        serviceMenu.add(stopItem);

        // View Output Log
        MenuItem outputLogItem = new MenuItem("View Output Log");
        outputLogItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                listener.onViewOutputLog(service);
            }
        });
        serviceMenu.add(outputLogItem);

        // View Error Log
        MenuItem errorLogItem = new MenuItem("View Error Log");
        errorLogItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                listener.onViewErrorLog(service);
            }
        });
        serviceMenu.add(errorLogItem);

        return serviceMenu;
    }

    /**
     * Gets the status indicator prefix for a service.
     *
     * Returns a visual indicator based on service state:
     * - "... " if operation in progress
     * - "⚠ " if error message present
     * - "● " if running
     * - "○ " if stopped or uninstalled
     * - "? " if unknown
     *
     * @param service The service model
     * @return The status indicator string
     */
    private String getStatusIndicator(ServiceRowModel service) {
        if (service.isOperationInProgress()) {
            return "... ";
        } else if (service.getErrorMessage() != null) {
            return "⚠ ";
        } else {
            ServiceStatus status = service.getStatus();
            switch (status) {
                case RUNNING:
                    return "● ";
                case STOPPED:
                    return "○ ";
                case UNINSTALLED:
                    return "○ ";
                case UNKNOWN:
                default:
                    return "? ";
            }
        }
    }

    /**
     * Installs the tray icon into the system tray.
     *
     * @throws RuntimeException if the tray icon cannot be installed
     */
    private void installTrayIcon() {
        try {
            SystemTray systemTray = SystemTray.getSystemTray();

            trayIcon = new TrayIcon(appIcon, appName + " Services", popupMenu);
            trayIcon.setImageAutoSize(true);

            systemTray.add(trayIcon);
        } catch (AWTException e) {
            throw new RuntimeException("Failed to install tray icon", e);
        }
    }

    /**
     * Updates the menu to reflect current service states.
     *
     * Call this method after service status changes to refresh the menu
     * with updated status indicators and enabled/disabled states.
     */
    public void updateMenu() {
        popupMenu.removeAll();
        buildMenu();
    }

    /**
     * Updates the service list and rebuilds the popup menu.
     *
     * This method is thread-safe and can be called from any thread.
     * It will automatically execute on the Event Dispatch Thread if needed.
     *
     * When a service has a new error (previous state was null, current is non-null),
     * a balloon notification is displayed to alert the user.
     *
     * @param services The updated list of service models
     */
    public void updateServices(final List<ServiceRowModel> services) {
        // Ensure execution on EDT for thread safety
        if (!EventQueue.isDispatchThread()) {
            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    updateServices(services);
                }
            });
            return;
        }

        // Check for new errors and show notifications
        for (ServiceRowModel service : services) {
            String commandName = service.getCommandName();
            String currentError = service.getErrorMessage();
            String previousError = previousErrors.get(commandName);

            // Detect new error: previous was null, current is non-null
            if (previousError == null && currentError != null) {
                trayIcon.displayMessage(
                    service.getServiceName(),
                    currentError,
                    TrayIcon.MessageType.ERROR
                );
            }

            // Update tracked error state
            previousErrors.put(commandName, currentError);
        }

        // Update the service list and rebuild menu
        this.services = services;
        popupMenu.removeAll();
        buildMenu();
    }

    /**
     * Removes the tray icon from the system tray and cleans up resources.
     *
     * Call this method before the application exits to properly dispose of the tray icon.
     */
    public void dispose() {
        SystemTray systemTray = SystemTray.getSystemTray();
        systemTray.remove(trayIcon);
    }

    /**
     * Example main method demonstrating ServiceTrayMenu integration.
     *
     * This example shows how to integrate the tray menu with the existing
     * service management infrastructure used by ServiceManagementPanel.
     */
    public static void main(String[] args) {
        // NOTE: This example uses mock services for demonstration.
        // In a real application, you would load services using:
        //
        //   ServiceDescriptorService descriptorService = ServiceDescriptorServiceFactory.createDefault();
        //   List<ServiceDescriptor> descriptors = descriptorService.listServices(packageName, source);
        //   for (ServiceDescriptor descriptor : descriptors) {
        //       serviceModels.add(new ServiceRowModel(descriptor));
        //   }

        // Configuration
        final String packageName = "example-app";
        final String source = null; // null for NPM packages, GitHub URL for GitHub packages
        final String appName = "Example App";

        // 1. Create ServiceStatusPoller
        //    This is the same poller used by ServiceManagementPanel
        //    In a real app, you would pass the CLI launcher path:
        //    new ServiceStatusPoller(cliLauncherPath, packageName, source)
        final ca.weblite.jdeploy.installer.services.ServiceStatusPoller poller =
            new ca.weblite.jdeploy.installer.services.ServiceStatusPoller(null, packageName, source);

        // 2. Load services into List<ServiceRowModel>
        //    For this example, we'll create mock services
        final List<ServiceRowModel> services = createMockServices();

        // Initial status poll
        System.out.println("Polling initial service status...");
        poller.pollAll(services);

        // 3. Create ServiceTrayMenuListener
        //    This listener handles all tray menu actions
        ServiceTrayMenuListener listener = new ServiceTrayMenuListener() {
            @Override
            public void onStart(ServiceRowModel service) {
                System.out.println("Starting service: " + service.getServiceName());

                // Run in background thread to avoid blocking EDT
                new Thread(() -> {
                    service.setOperationInProgress(true);

                    boolean success = poller.startService(service);

                    service.setOperationInProgress(false);

                    if (success) {
                        System.out.println("Service started successfully: " + service.getServiceName());
                    } else {
                        System.out.println("Failed to start service: " + service.getServiceName());
                    }

                    // Re-poll status after operation
                    poller.pollStatus(service);
                }).start();
            }

            @Override
            public void onStop(ServiceRowModel service) {
                System.out.println("Stopping service: " + service.getServiceName());

                // Run in background thread to avoid blocking EDT
                new Thread(() -> {
                    service.setOperationInProgress(true);

                    boolean success = poller.stopService(service);

                    service.setOperationInProgress(false);

                    if (success) {
                        System.out.println("Service stopped successfully: " + service.getServiceName());
                    } else {
                        System.out.println("Failed to stop service: " + service.getServiceName());
                    }

                    // Re-poll status after operation
                    poller.pollStatus(service);
                }).start();
            }

            @Override
            public void onViewOutputLog(ServiceRowModel service) {
                System.out.println("Opening output log for: " + service.getServiceName());

                // Use ServiceLogHelper to open the log file
                // This is the same utility used by ServiceManagementPanel
                java.io.File logFile = ca.weblite.jdeploy.installer.services.ServiceLogHelper.getOutputLogFile(service);
                ca.weblite.jdeploy.installer.services.ServiceLogHelper.openLogFile(logFile, null);
            }

            @Override
            public void onViewErrorLog(ServiceRowModel service) {
                System.out.println("Opening error log for: " + service.getServiceName());

                // Use ServiceLogHelper to open the log file
                // This is the same utility used by ServiceManagementPanel
                java.io.File logFile = ca.weblite.jdeploy.installer.services.ServiceLogHelper.getErrorLogFile(service);
                ca.weblite.jdeploy.installer.services.ServiceLogHelper.openLogFile(logFile, null);
            }

            @Override
            public void onUninstall() {
                System.out.println("Uninstall requested");
                // In a real application, this would:
                // 1. Stop all running services
                // 2. Launch the uninstaller
                // 3. Exit the application
            }

            @Override
            public void onQuit() {
                System.out.println("Quit requested");
                // In a real application, this would:
                // 1. Stop the refresh timer
                // 2. Clean up resources
                // 3. Exit gracefully
                System.exit(0);
            }
        };

        // 4. Create an icon for the tray
        //    In a real app, you would load your application icon
        Image appIcon = createMockIcon();

        // 5. Instantiate ServiceTrayMenu
        final ServiceTrayMenu trayMenu = new ServiceTrayMenu(appName, appIcon, services, listener);

        System.out.println("System tray menu created successfully");
        System.out.println("Right-click the tray icon to access the menu");

        // 6. Set up a Timer for periodic status polling and menu updates
        //    This is the same polling approach used by ServiceManagementPanel
        javax.swing.Timer refreshTimer = new javax.swing.Timer(5000, e -> {
            System.out.println("Polling service status...");

            // Run poll in background thread (same pattern as ServiceManagementPanel)
            new Thread(() -> {
                // Poll all services for updated status
                poller.pollAll(services);

                // Update the tray menu with new status
                // This will also trigger balloon notifications for new errors
                trayMenu.updateServices(services);

                System.out.println("Service status updated");
            }).start();
        });
        refreshTimer.setRepeats(true);
        refreshTimer.start();

        System.out.println("Refresh timer started (polling every 5 seconds)");

        // Keep the application running
        // In a real application, this would be your main event loop
    }

    /**
     * Creates mock services for demonstration purposes.
     */
    private static List<ServiceRowModel> createMockServices() {
        List<ServiceRowModel> services = new ArrayList<>();

        try {
            // Create mock command specs and service descriptors
            // In a real app, these come from the package.json manifest

            ca.weblite.jdeploy.models.CommandSpec webServerSpec =
                new ca.weblite.jdeploy.models.CommandSpec(
                    "web-server",
                    "Web server service",
                    null,
                    java.util.Arrays.asList("service_controller")
                );

            ca.weblite.jdeploy.installer.services.ServiceDescriptor webServerDescriptor =
                new ca.weblite.jdeploy.installer.services.ServiceDescriptor(
                    webServerSpec,
                    "example-app",
                    "1.0.0",
                    null,
                    null
                );

            services.add(new ServiceRowModel(webServerDescriptor));

            ca.weblite.jdeploy.models.CommandSpec apiServerSpec =
                new ca.weblite.jdeploy.models.CommandSpec(
                    "api-server",
                    "API server service",
                    null,
                    java.util.Arrays.asList("service_controller")
                );

            ca.weblite.jdeploy.installer.services.ServiceDescriptor apiServerDescriptor =
                new ca.weblite.jdeploy.installer.services.ServiceDescriptor(
                    apiServerSpec,
                    "example-app",
                    "1.0.0",
                    null,
                    null
                );

            services.add(new ServiceRowModel(apiServerDescriptor));

        } catch (Exception e) {
            System.err.println("Failed to create mock services: " + e.getMessage());
            e.printStackTrace();
        }

        return services;
    }

    /**
     * Creates a simple mock icon for the tray.
     */
    private static Image createMockIcon() {
        // Create a simple 16x16 colored square as a placeholder icon
        java.awt.image.BufferedImage icon = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = icon.createGraphics();
        g.setColor(new Color(0, 120, 215)); // Blue color
        g.fillRect(0, 0, 16, 16);
        g.setColor(Color.WHITE);
        g.fillOval(4, 4, 8, 8);
        g.dispose();
        return icon;
    }
}

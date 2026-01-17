package ca.weblite.jdeploy.installer.tray;

import ca.weblite.jdeploy.installer.models.ServiceRowModel;
import ca.weblite.jdeploy.installer.models.ServiceStatus;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

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
    private final List<ServiceRowModel> services;
    private final ServiceTrayMenuListener listener;

    private TrayIcon trayIcon;
    private PopupMenu popupMenu;

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
     * Removes the tray icon from the system tray.
     *
     * Call this method before the application exits to clean up the tray icon.
     */
    public void remove() {
        SystemTray systemTray = SystemTray.getSystemTray();
        systemTray.remove(trayIcon);
    }
}

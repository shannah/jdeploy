package ca.weblite.jdeploy.installer.tray;

import ca.weblite.jdeploy.installer.models.ServiceRowModel;

/**
 * Listener interface for system tray menu actions.
 *
 * Implementers of this interface handle user interactions with the
 * service management system tray menu, such as starting/stopping services,
 * viewing logs, and application lifecycle operations.
 *
 * @author Steve Hannah
 */
public interface ServiceTrayMenuListener {

    /**
     * Called when the user requests to start a service from the tray menu.
     *
     * The implementation should start the service (installing first if needed)
     * and update the UI accordingly.
     *
     * @param service The service to start
     */
    void onStart(ServiceRowModel service);

    /**
     * Called when the user requests to stop a service from the tray menu.
     *
     * The implementation should stop the running service and update the UI accordingly.
     *
     * @param service The service to stop
     */
    void onStop(ServiceRowModel service);

    /**
     * Called when the user requests to view the output log for a service.
     *
     * The implementation should open or display the service's output log file.
     *
     * @param service The service whose output log should be viewed
     */
    void onViewOutputLog(ServiceRowModel service);

    /**
     * Called when the user requests to view the error log for a service.
     *
     * The implementation should open or display the service's error log file.
     *
     * @param service The service whose error log should be viewed
     */
    void onViewErrorLog(ServiceRowModel service);

    /**
     * Called when the user requests to uninstall the application from the tray menu.
     *
     * The implementation should initiate the uninstallation process, typically
     * by stopping all running services and launching the uninstaller.
     */
    void onUninstall();

    /**
     * Called when the user requests to quit the application from the tray menu.
     *
     * The implementation should perform cleanup (e.g., save state, stop background tasks)
     * and exit the application gracefully.
     */
    void onQuit();
}

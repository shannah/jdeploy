package ca.weblite.jdeploy.installer.services;

import ca.weblite.jdeploy.installer.models.ServiceRowModel;
import ca.weblite.jdeploy.installer.models.ServiceStatus;

/**
 * Listener interface for service status change events.
 *
 * Implementations receive notifications when service status changes
 * (e.g., RUNNING to STOPPED) or when errors occur. This allows UIs
 * to update only when needed, rather than polling constantly.
 *
 * Follows the Observer pattern for event-driven architecture.
 *
 * @author Steve Hannah
 */
public interface ServiceStatusListener {

    /**
     * Called when a service's status changes.
     *
     * This is only called when the status actually changes (e.g., from
     * STOPPED to RUNNING), not on every poll cycle.
     *
     * @param service The service whose status changed
     * @param oldStatus The previous status
     * @param newStatus The new status
     */
    void onServiceStatusChanged(ServiceRowModel service, ServiceStatus oldStatus, ServiceStatus newStatus);

    /**
     * Called when a service error state changes.
     *
     * This is called when:
     * - A new error occurs (previous error was null, current is non-null)
     * - An error is cleared (previous was non-null, current is null)
     *
     * @param service The service whose error state changed
     * @param errorMessage The current error message (may be null if error cleared)
     */
    void onServiceErrorChanged(ServiceRowModel service, String errorMessage);
}

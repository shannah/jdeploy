package ca.weblite.jdeploy.installer.services;

import ca.weblite.jdeploy.installer.models.ServiceRowModel;
import ca.weblite.jdeploy.installer.models.ServiceStatus;

import javax.swing.Timer;
import java.awt.EventQueue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitors service status and notifies listeners of changes.
 *
 * This class polls services periodically and detects actual status changes,
 * notifying registered listeners only when changes occur. This allows UIs
 * to update reactively rather than polling constantly.
 *
 * Follows the Observer pattern for event-driven architecture.
 *
 * @author Steve Hannah
 */
public class ServiceStatusMonitor {

    private static final Logger logger = Logger.getLogger(ServiceStatusMonitor.class.getName());
    private static final int DEFAULT_POLL_INTERVAL_MS = 5000;

    private final ServiceStatusPoller poller;
    private final List<ServiceRowModel> services;
    private final List<ServiceStatusListener> listeners;

    private Timer pollTimer;
    private boolean running = false;

    // Track previous state to detect changes
    private final Map<String, ServiceStatus> previousStatus;
    private final Map<String, String> previousErrors;

    /**
     * Creates a new service status monitor.
     *
     * @param poller The status poller to use for checking service status
     * @param services The list of services to monitor
     */
    public ServiceStatusMonitor(ServiceStatusPoller poller, List<ServiceRowModel> services) {
        if (poller == null) {
            throw new IllegalArgumentException("poller cannot be null");
        }
        if (services == null) {
            throw new IllegalArgumentException("services cannot be null");
        }

        this.poller = poller;
        this.services = services;
        this.listeners = new CopyOnWriteArrayList<>();
        this.previousStatus = new HashMap<>();
        this.previousErrors = new HashMap<>();

        // Initialize previous state
        for (ServiceRowModel service : services) {
            previousStatus.put(service.getCommandName(), service.getStatus());
            previousErrors.put(service.getCommandName(), service.getErrorMessage());
        }
    }

    /**
     * Adds a listener to be notified of status changes.
     *
     * @param listener The listener to add
     */
    public void addListener(ServiceStatusListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a listener.
     *
     * @param listener The listener to remove
     */
    public void removeListener(ServiceStatusListener listener) {
        listeners.remove(listener);
    }

    /**
     * Starts monitoring services.
     *
     * Begins periodic polling and change detection. This method is idempotent.
     */
    public void start() {
        start(DEFAULT_POLL_INTERVAL_MS);
    }

    /**
     * Starts monitoring services with a custom poll interval.
     *
     * @param pollIntervalMs The polling interval in milliseconds
     */
    public void start(int pollIntervalMs) {
        if (running) {
            logger.warning("ServiceStatusMonitor.start() called but already running");
            return;
        }

        pollTimer = new Timer(pollIntervalMs, e -> pollAndNotify());
        pollTimer.setRepeats(true);
        pollTimer.start();
        running = true;

        logger.info("ServiceStatusMonitor started with " + services.size() + " services");
    }

    /**
     * Stops monitoring services.
     *
     * Stops the polling timer. This method is idempotent.
     */
    public void stop() {
        if (!running) {
            return;
        }

        if (pollTimer != null) {
            pollTimer.stop();
            pollTimer = null;
        }

        running = false;
        logger.info("ServiceStatusMonitor stopped");
    }

    /**
     * Checks if the monitor is currently running.
     *
     * @return true if monitoring is active
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Polls services and notifies listeners of changes.
     *
     * Runs in a background thread to avoid blocking the EDT.
     */
    private void pollAndNotify() {
        // Run poll in background thread
        new Thread(() -> {
            try {
                // Poll all services for current status
                poller.pollAll(services);

                // Check for changes and notify listeners
                detectAndNotifyChanges();

            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to poll services", e);
            }
        }).start();
    }

    /**
     * Detects status and error changes, notifying listeners on the EDT.
     */
    private void detectAndNotifyChanges() {
        for (ServiceRowModel service : services) {
            String commandName = service.getCommandName();
            ServiceStatus currentStatus = service.getStatus();
            String currentError = service.getErrorMessage();

            ServiceStatus prevStatus = previousStatus.get(commandName);
            String prevError = previousErrors.get(commandName);

            // Detect status change
            if (prevStatus != currentStatus) {
                notifyStatusChanged(service, prevStatus, currentStatus);
                previousStatus.put(commandName, currentStatus);
            }

            // Detect error change
            if (!Objects.equals(prevError, currentError)) {
                notifyErrorChanged(service, currentError);
                previousErrors.put(commandName, currentError);
            }
        }
    }

    /**
     * Notifies listeners of status change on the EDT.
     */
    private void notifyStatusChanged(ServiceRowModel service, ServiceStatus oldStatus, ServiceStatus newStatus) {
        EventQueue.invokeLater(() -> {
            for (ServiceStatusListener listener : listeners) {
                try {
                    listener.onServiceStatusChanged(service, oldStatus, newStatus);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Listener threw exception in onServiceStatusChanged", e);
                }
            }
        });
    }

    /**
     * Notifies listeners of error change on the EDT.
     */
    private void notifyErrorChanged(ServiceRowModel service, String errorMessage) {
        EventQueue.invokeLater(() -> {
            for (ServiceStatusListener listener : listeners) {
                try {
                    listener.onServiceErrorChanged(service, errorMessage);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Listener threw exception in onServiceErrorChanged", e);
                }
            }
        });
    }
}

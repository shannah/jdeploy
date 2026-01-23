package ca.weblite.jdeploy.installer.services;

import ca.weblite.jdeploy.installer.models.ServiceRowModel;
import ca.weblite.jdeploy.installer.models.ServiceStatus;

import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Polls service status using ServiceOperationExecutor.
 *
 * This class is designed to be called from a background thread (e.g., SwingWorker)
 * to avoid blocking the UI while checking service status.
 *
 * @author Steve Hannah
 */
public class ServiceStatusPoller {

    private static final Logger logger = Logger.getLogger(ServiceStatusPoller.class.getName());

    private final ServiceOperationExecutor executor;

    /**
     * Creates a new service status poller.
     *
     * @param cliLauncherPath The path to the CLI launcher
     * @param packageName The package name
     * @param source The installation source
     */
    public ServiceStatusPoller(File cliLauncherPath, String packageName, String source) {
        this.executor = new ServiceOperationExecutor(cliLauncherPath, packageName, source);
    }

    /**
     * Creates a new service status poller with an existing executor.
     *
     * @param executor The service operation executor
     */
    public ServiceStatusPoller(ServiceOperationExecutor executor) {
        this.executor = executor;
    }

    /**
     * Polls the status of all services in the list.
     *
     * Updates each ServiceRowModel with the current status.
     * This method should be called from a background thread.
     *
     * @param services The list of service row models to update
     */
    public void pollAll(List<ServiceRowModel> services) {
        for (ServiceRowModel service : services) {
            pollStatus(service);
        }
    }

    /**
     * Polls the status of a single service.
     *
     * Updates the ServiceRowModel with the current status.
     *
     * @param service The service row model to update
     */
    public void pollStatus(ServiceRowModel service) {
        try {
            ServiceOperationResult result = executor.checkStatus(service.getCommandName());
            ServiceStatus status = ServiceStatus.fromExitCode(result.getExitCode());
            service.setStatus(status);

            // Clear error if status check succeeded (even if service is stopped)
            if (result.isSuccess() || result.getExitCode() == 3 || result.getExitCode() == 4) {
                // These are valid status codes, not errors
                // Only keep error message if it was set by a failed operation
            }

            logger.log(Level.FINE, "Service {0} status: {1} (exit code {2})",
                    new Object[]{service.getCommandName(), status, result.getExitCode()});
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to poll status for service " + service.getCommandName(), e);
            service.setStatus(ServiceStatus.UNKNOWN);
            service.setErrorMessage("Failed to check status: " + e.getMessage());
        }
    }

    /**
     * Starts a service.
     *
     * If the service is not installed, it will be installed first.
     *
     * @param service The service to start
     * @return true if the operation succeeded
     */
    public boolean startService(ServiceRowModel service) {
        try {
            // If service is not installed, install it first
            if (service.needsInstall()) {
                ServiceOperationResult installResult = executor.install(service.getCommandName());
                if (!installResult.isSuccess()) {
                    service.setErrorMessage("Install failed: " + installResult.getMessage());
                    return false;
                }
            }

            // Start the service
            ServiceOperationResult startResult = executor.start(service.getCommandName());
            if (!startResult.isSuccess()) {
                service.setErrorMessage("Start failed: " + startResult.getMessage());
                return false;
            }

            service.clearError();
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to start service " + service.getCommandName(), e);
            service.setErrorMessage("Start failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Stops a service.
     *
     * @param service The service to stop
     * @return true if the operation succeeded
     */
    public boolean stopService(ServiceRowModel service) {
        try {
            ServiceOperationResult result = executor.stop(service.getCommandName());
            if (!result.isSuccess()) {
                service.setErrorMessage("Stop failed: " + result.getMessage());
                return false;
            }

            service.clearError();
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to stop service " + service.getCommandName(), e);
            service.setErrorMessage("Stop failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Toggles a service's running state.
     *
     * If running, stops it. If stopped or uninstalled, starts it (installing first if needed).
     *
     * @param service The service to toggle
     * @return true if the operation succeeded
     */
    public boolean toggleService(ServiceRowModel service) {
        if (service.getStatus().canStop()) {
            return stopService(service);
        } else if (service.getStatus().canStart()) {
            return startService(service);
        } else {
            service.setErrorMessage("Cannot toggle service in " + service.getStatus() + " state");
            return false;
        }
    }
}

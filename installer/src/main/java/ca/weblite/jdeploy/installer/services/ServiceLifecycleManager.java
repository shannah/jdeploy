package ca.weblite.jdeploy.installer.services;

import ca.weblite.jdeploy.models.CommandSpec;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrates the complete service lifecycle during application updates.
 *
 * Implements the 6-phase update lifecycle:
 * 1. Service Status Assessment
 * 2. Service Shutdown
 * 3. Installation (handled externally)
 * 4. Application Update
 * 5. Service Installation
 * 6. Service Startup
 *
 * Single Responsibility: Coordinate service lifecycle during updates.
 * Follows Tell, Don't Ask principle.
 *
 * @author Steve Hannah
 */
public class ServiceLifecycleManager {
    private static final Logger LOGGER = Logger.getLogger(ServiceLifecycleManager.class.getName());

    private final ServiceDescriptorService descriptorService;
    private final ServiceOperationExecutor operationExecutor;
    private final ServiceLifecycleProgressCallback progressCallback;

    public ServiceLifecycleManager(
            ServiceDescriptorService descriptorService,
            ServiceOperationExecutor operationExecutor,
            ServiceLifecycleProgressCallback progressCallback) {
        this.descriptorService = descriptorService;
        this.operationExecutor = operationExecutor;
        this.progressCallback = progressCallback;
    }

    /**
     * Executes pre-installation service management (Phases 1-2).
     * Assesses service state and stops running services.
     *
     * @param packageName The package name
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param newCommands The commands from the new version
     * @return Map of service states for post-installation restart
     */
    public Map<String, ServiceState> prepareForUpdate(String packageName, String source, List<CommandSpec> newCommands) {
        progressCallback.updateProgress("Checking service status...");

        // Phase 1: Service Status Assessment
        List<ServiceState> currentServices = assessCurrentServiceStates(packageName, source);
        List<CommandSpec> removedServices = identifyRemovedServices(currentServices, newCommands);

        // Phase 2: Service Shutdown
        stopRunningServices(currentServices);
        uninstallRemovedServices(removedServices, packageName, source);

        // Return state map for post-installation
        return buildServiceStateMap(currentServices);
    }

    /**
     * Executes post-installation service management (Phases 4-6).
     * Runs application update, installs services, and starts them.
     *
     * @param packageName The package name
     * @param newCommands The commands from the new version
     * @param previousStates Service states from pre-installation
     * @param version The version being installed
     */
    public void completeUpdate(
            String packageName,
            List<CommandSpec> newCommands,
            Map<String, ServiceState> previousStates,
            String version) {

        // Phase 4: Application Update
        runApplicationUpdate(version);

        // Phase 5: Service Installation
        installNewAndUpdatedServices(newCommands, previousStates);

        // Phase 6: Service Startup
        startServices(newCommands, previousStates);
    }

    // ========== Phase 1: Service Status Assessment ==========

    private List<ServiceState> assessCurrentServiceStates(String packageName, String source) {
        List<ServiceState> states = new ArrayList<>();

        try {
            List<ServiceDescriptor> descriptors = descriptorService.listServices(packageName, source);

            for (ServiceDescriptor descriptor : descriptors) {
                boolean isRunning = checkIfServiceIsRunning(descriptor.getCommandName());
                states.add(new ServiceState(descriptor, isRunning));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load service descriptors: " + e.getMessage(), e);
            progressCallback.reportWarning("Failed to load service descriptors: " + e.getMessage());
        }

        return states;
    }

    private boolean checkIfServiceIsRunning(String commandName) {
        ServiceOperationResult result = operationExecutor.checkStatus(commandName);
        if (result.isFailure()) {
            LOGGER.log(Level.FINE, "Service {0} status check: {1}", new Object[]{commandName, result.getMessage()});
        }
        return result.isSuccess();
    }

    private List<CommandSpec> identifyRemovedServices(
            List<ServiceState> currentServices,
            List<CommandSpec> newCommands) {

        List<CommandSpec> removed = new ArrayList<>();

        for (ServiceState state : currentServices) {
            String commandName = state.getCommandName();
            boolean existsInNew = false;

            for (CommandSpec newCommand : newCommands) {
                if (newCommand.getName().equals(commandName) &&
                    newCommand.implements_("service_controller")) {
                    existsInNew = true;
                    break;
                }
            }

            if (!existsInNew) {
                removed.add(state.getDescriptor().getCommandSpec());
            }
        }

        return removed;
    }

    // ========== Phase 2: Service Shutdown ==========

    private void stopRunningServices(List<ServiceState> services) {
        List<ServiceState> runningServices = filterRunningServices(services);

        if (runningServices.isEmpty()) {
            return;
        }

        progressCallback.updateProgress("Stopping services...");

        for (ServiceState state : runningServices) {
            String commandName = state.getCommandName();
            ServiceOperationResult result = operationExecutor.stop(commandName);

            if (result.isFailure()) {
                String warning = "Failed to stop service " + commandName + ": " + result.getMessage();
                LOGGER.log(Level.WARNING, warning);
                progressCallback.reportWarning(warning);
            } else {
                LOGGER.log(Level.INFO, "Stopped service: {0}", commandName);
            }
        }

        // Wait for services to actually stop before proceeding
        // This is critical to avoid file locks during installation
        waitForServicesToStop(runningServices);
    }

    /**
     * Waits for services to fully stop by polling their status.
     * Some service managers (launchd, Windows SCM) return immediately from stop commands
     * before the service is fully stopped, which can cause file access issues during installation.
     *
     * @param services The services to wait for
     */
    private void waitForServicesToStop(List<ServiceState> services) {
        if (services.isEmpty()) {
            return;
        }

        progressCallback.updateProgress("Waiting for services to stop...");

        final int MAX_WAIT_SECONDS = 30;
        final int POLL_INTERVAL_MS = 500;

        for (ServiceState state : services) {
            String commandName = state.getCommandName();
            boolean stopped = false;

            for (int elapsed = 0; elapsed < MAX_WAIT_SECONDS * 1000; elapsed += POLL_INTERVAL_MS) {
                ServiceOperationResult status = operationExecutor.checkStatus(commandName);

                // Status check returns success (exit 0) if running, failure if stopped
                if (status.isFailure()) {
                    LOGGER.log(Level.INFO, "Service {0} has stopped", commandName);
                    stopped = true;
                    break;
                }

                // Wait before next poll
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.log(Level.WARNING, "Interrupted while waiting for service to stop: " + commandName);
                    break;
                }
            }

            if (!stopped) {
                String warning = "Service " + commandName + " did not stop after " + MAX_WAIT_SECONDS +
                    " seconds. Installation may encounter file access issues.";
                LOGGER.log(Level.WARNING, warning);
                progressCallback.reportWarning(warning);
            }
        }
    }

    private void uninstallRemovedServices(List<CommandSpec> removedServices, String packageName, String source) {
        if (removedServices.isEmpty()) {
            return;
        }

        progressCallback.updateProgress("Uninstalling removed services...");

        for (CommandSpec command : removedServices) {
            String commandName = command.getName();
            ServiceOperationResult result = operationExecutor.uninstall(commandName);

            if (result.isFailure()) {
                String warning = "Failed to uninstall service " + commandName + ": " + result.getMessage();
                LOGGER.log(Level.WARNING, warning);
                progressCallback.reportWarning(warning);
            } else {
                LOGGER.log(Level.INFO, "Uninstalled service: {0}", commandName);
            }

            // Delete service descriptor
            try {
                descriptorService.unregisterService(packageName, source, commandName, null);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to delete descriptor for " + commandName, e);
            }
        }
    }

    // ========== Phase 4: Application Update ==========

    private void runApplicationUpdate(String version) {
        progressCallback.updateProgress("Updating application...");

        ServiceOperationResult result = operationExecutor.runApplicationUpdate(version);

        if (result.isFailure()) {
            String warning = "Application update failed: " + result.getMessage();
            LOGGER.log(Level.WARNING, warning);
            progressCallback.reportWarning(warning);
        } else {
            LOGGER.log(Level.INFO, "Application update completed successfully");
        }
    }

    // ========== Phase 5: Service Installation ==========

    private void installNewAndUpdatedServices(
            List<CommandSpec> newCommands,
            Map<String, ServiceState> previousStates) {

        List<CommandSpec> servicesToInstall = extractServiceCommands(newCommands);

        // Filter out services that were already installed in the previous version
        List<CommandSpec> newServices = new ArrayList<>();
        for (CommandSpec command : servicesToInstall) {
            if (!previousStates.containsKey(command.getName())) {
                newServices.add(command);
            }
        }

        if (newServices.isEmpty()) {
            LOGGER.log(Level.INFO, "No new services to install");
            return;
        }

        progressCallback.updateProgress("Installing new services...");

        for (CommandSpec command : newServices) {
            String commandName = command.getName();
            ServiceOperationResult result = operationExecutor.install(commandName);

            if (result.isFailure()) {
                String warning = "Failed to install service " + commandName + ": " + result.getMessage();
                LOGGER.log(Level.WARNING, warning);
                progressCallback.reportWarning(warning);
            } else {
                LOGGER.log(Level.INFO, "Installed new service: {0}", commandName);
            }
        }
    }

    // ========== Phase 6: Service Startup ==========

    private void startServices(
            List<CommandSpec> newCommands,
            Map<String, ServiceState> previousStates) {

        List<String> servicesToStart = determineServicesToStart(newCommands, previousStates);

        if (servicesToStart.isEmpty()) {
            return;
        }

        progressCallback.updateProgress("Starting services...");

        for (String commandName : servicesToStart) {
            ServiceOperationResult result = operationExecutor.start(commandName);

            if (result.isFailure()) {
                String warning = "Failed to start service " + commandName + ": " + result.getMessage();
                LOGGER.log(Level.WARNING, warning);
                progressCallback.reportWarning(warning);
            } else {
                LOGGER.log(Level.INFO, "Started service: {0}", commandName);
            }
        }
    }

    // ========== Helper Methods ==========

    private List<ServiceState> filterRunningServices(List<ServiceState> services) {
        List<ServiceState> running = new ArrayList<>();
        for (ServiceState state : services) {
            if (state.wasRunning()) {
                running.add(state);
            }
        }
        return running;
    }

    private Map<String, ServiceState> buildServiceStateMap(List<ServiceState> states) {
        Map<String, ServiceState> map = new HashMap<>();
        for (ServiceState state : states) {
            map.put(state.getCommandName(), state);
        }
        return map;
    }

    private List<CommandSpec> extractServiceCommands(List<CommandSpec> commands) {
        List<CommandSpec> services = new ArrayList<>();
        for (CommandSpec command : commands) {
            if (command.implements_("service_controller")) {
                services.add(command);
            }
        }
        return services;
    }

    private List<String> determineServicesToStart(
            List<CommandSpec> newCommands,
            Map<String, ServiceState> previousStates) {

        List<String> toStart = new ArrayList<>();

        for (CommandSpec command : newCommands) {
            if (!command.implements_("service_controller")) {
                continue;
            }

            String commandName = command.getName();
            ServiceState previousState = previousStates.get(commandName);

            if (previousState == null) {
                // New service - start it
                toStart.add(commandName);
            } else if (previousState.wasRunning()) {
                // Previously running service - restart it
                toStart.add(commandName);
            }
            // If previousState exists but was not running, don't start
        }

        return toStart;
    }
}

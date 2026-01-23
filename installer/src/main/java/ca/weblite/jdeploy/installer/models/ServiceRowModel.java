package ca.weblite.jdeploy.installer.models;

import ca.weblite.jdeploy.installer.services.ServiceDescriptor;

/**
 * Mutable model representing a service row in the ServiceManagementPanel.
 *
 * Tracks the service's current status, any error messages, and whether
 * an operation is currently in progress.
 *
 * @author Steve Hannah
 */
public class ServiceRowModel {

    private final ServiceDescriptor descriptor;
    private ServiceStatus status;
    private String errorMessage;
    private boolean operationInProgress;

    /**
     * Creates a new service row model.
     *
     * @param descriptor The service descriptor
     */
    public ServiceRowModel(ServiceDescriptor descriptor) {
        this.descriptor = descriptor;
        this.status = ServiceStatus.UNKNOWN;
        this.errorMessage = null;
        this.operationInProgress = false;
    }

    /**
     * @return The service descriptor
     */
    public ServiceDescriptor getDescriptor() {
        return descriptor;
    }

    /**
     * @return The service/command name for display
     */
    public String getServiceName() {
        return descriptor.getCommandName();
    }

    /**
     * @return The service description, or null if not available
     */
    public String getDescription() {
        return descriptor.getCommandSpec().getDescription();
    }

    /**
     * @return The command name used to execute service operations
     */
    public String getCommandName() {
        return descriptor.getCommandName();
    }

    /**
     * @return The current service status
     */
    public ServiceStatus getStatus() {
        return status;
    }

    /**
     * Sets the service status.
     *
     * @param status The new status
     */
    public void setStatus(ServiceStatus status) {
        this.status = status;
    }

    /**
     * @return The current error message, or null if no error
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sets the error message.
     *
     * @param errorMessage The error message, or null to clear
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Clears the error message.
     */
    public void clearError() {
        this.errorMessage = null;
    }

    /**
     * @return true if an operation is currently in progress
     */
    public boolean isOperationInProgress() {
        return operationInProgress;
    }

    /**
     * Sets whether an operation is in progress.
     *
     * @param operationInProgress true if an operation is in progress
     */
    public void setOperationInProgress(boolean operationInProgress) {
        this.operationInProgress = operationInProgress;
    }

    /**
     * @return true if the service can be started
     */
    public boolean canStart() {
        return !operationInProgress && status.canStart();
    }

    /**
     * @return true if the service can be stopped
     */
    public boolean canStop() {
        return !operationInProgress && status.canStop();
    }

    /**
     * @return true if the service needs to be installed before starting
     */
    public boolean needsInstall() {
        return status.needsInstall();
    }

    @Override
    public String toString() {
        return "ServiceRowModel{" +
                "serviceName='" + getServiceName() + '\'' +
                ", status=" + status +
                ", errorMessage='" + errorMessage + '\'' +
                ", operationInProgress=" + operationInProgress +
                '}';
    }
}

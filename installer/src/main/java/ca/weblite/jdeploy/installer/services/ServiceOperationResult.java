package ca.weblite.jdeploy.installer.services;

/**
 * Result of a service operation (status, stop, start, install, uninstall).
 *
 * Uses Railway Oriented Programming pattern - operations either succeed or fail,
 * but failures don't throw exceptions, allowing graceful degradation.
 *
 * @author Steve Hannah
 */
public final class ServiceOperationResult {
    private final boolean success;
    private final String message;
    private final int exitCode;

    private ServiceOperationResult(boolean success, String message, int exitCode) {
        this.success = success;
        this.message = message;
        this.exitCode = exitCode;
    }

    public static ServiceOperationResult success() {
        return new ServiceOperationResult(true, "", 0);
    }

    public static ServiceOperationResult success(String message) {
        return new ServiceOperationResult(true, message, 0);
    }

    public static ServiceOperationResult failure(String message) {
        return new ServiceOperationResult(false, message, -1);
    }

    public static ServiceOperationResult failure(String message, int exitCode) {
        return new ServiceOperationResult(false, message, exitCode);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isFailure() {
        return !success;
    }

    public String getMessage() {
        return message;
    }

    public int getExitCode() {
        return exitCode;
    }

    @Override
    public String toString() {
        return success ? "Success: " + message : "Failure: " + message + " (exit " + exitCode + ")";
    }
}

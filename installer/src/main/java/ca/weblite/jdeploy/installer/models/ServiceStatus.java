package ca.weblite.jdeploy.installer.models;

/**
 * Represents the status of a service as determined by LSB-compliant exit codes.
 *
 * Exit codes from {@code service status} command:
 * <ul>
 *   <li>0 = RUNNING - Service is active and running</li>
 *   <li>3 = STOPPED - Service is installed but not running</li>
 *   <li>4 = UNINSTALLED - Service is not registered with the service manager</li>
 *   <li>Other = UNKNOWN - Unable to determine status</li>
 * </ul>
 *
 * @author Steve Hannah
 */
public enum ServiceStatus {

    RUNNING("Running", 0),
    STOPPED("Stopped", 3),
    UNINSTALLED("Uninstalled", 4),
    UNKNOWN("Unknown", -1);

    private final String displayName;
    private final int exitCode;

    ServiceStatus(String displayName, int exitCode) {
        this.displayName = displayName;
        this.exitCode = exitCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getExitCode() {
        return exitCode;
    }

    /**
     * Determines the service status from an exit code.
     *
     * @param exitCode The exit code from the service status command
     * @return The corresponding ServiceStatus, or UNKNOWN if not recognized
     */
    public static ServiceStatus fromExitCode(int exitCode) {
        switch (exitCode) {
            case 0:
                return RUNNING;
            case 3:
                return STOPPED;
            case 4:
                return UNINSTALLED;
            default:
                return UNKNOWN;
        }
    }

    /**
     * Checks if the service can be started (i.e., it's not already running).
     *
     * @return true if the service can be started
     */
    public boolean canStart() {
        return this == STOPPED || this == UNINSTALLED;
    }

    /**
     * Checks if the service can be stopped (i.e., it's currently running).
     *
     * @return true if the service can be stopped
     */
    public boolean canStop() {
        return this == RUNNING;
    }

    /**
     * Checks if the service needs to be installed before it can be started.
     *
     * @return true if the service is not installed
     */
    public boolean needsInstall() {
        return this == UNINSTALLED;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

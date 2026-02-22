package ca.weblite.jdeploy.services;

/**
 * Configuration for Windows Docker testing.
 *
 * Supports two modes:
 * - HEADLESS: Automated installation and verification
 * - RDP: Interactive mode for manual testing via Remote Desktop
 */
public class WindowsDockerConfig {

    /**
     * Testing mode.
     */
    public enum Mode {
        /**
         * Automated headless testing - installs app and runs verification checks.
         */
        HEADLESS,

        /**
         * Interactive RDP mode - starts container and waits for user to connect via RDP.
         */
        RDP
    }

    /**
     * Docker image to use for Windows testing.
     */
    public static final String DOCKER_IMAGE = "dockurr/windows";

    private Mode mode = Mode.HEADLESS;
    private String windowsVersion = "win11";
    private int rdpPort = 3389;
    private int timeoutMinutes = 15;

    public WindowsDockerConfig() {
    }

    public Mode getMode() {
        return mode;
    }

    public WindowsDockerConfig setMode(Mode mode) {
        this.mode = mode;
        return this;
    }

    public String getWindowsVersion() {
        return windowsVersion;
    }

    public WindowsDockerConfig setWindowsVersion(String windowsVersion) {
        this.windowsVersion = windowsVersion;
        return this;
    }

    public int getRdpPort() {
        return rdpPort;
    }

    public WindowsDockerConfig setRdpPort(int rdpPort) {
        this.rdpPort = rdpPort;
        return this;
    }

    public int getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public WindowsDockerConfig setTimeoutMinutes(int timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
        return this;
    }

    /**
     * Parses the --windows option value.
     *
     * @param value The option value (null, "headless", or "rdp")
     * @return Configured WindowsDockerConfig
     */
    public static WindowsDockerConfig fromOptionValue(String value) {
        WindowsDockerConfig config = new WindowsDockerConfig();

        if (value == null || value.isEmpty() || "headless".equalsIgnoreCase(value)) {
            config.setMode(Mode.HEADLESS);
        } else if ("rdp".equalsIgnoreCase(value)) {
            config.setMode(Mode.RDP);
        } else {
            throw new IllegalArgumentException(
                    "Invalid --windows value: " + value + ". Use 'headless' or 'rdp'");
        }

        return config;
    }
}

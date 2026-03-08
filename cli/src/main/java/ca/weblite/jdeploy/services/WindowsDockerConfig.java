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
    private int webPort = 8006;  // dockurr/windows provides web-based viewer on port 8006
    private int timeoutMinutes = 15;
    private boolean clean = false;  // If true, start from clean golden snapshot

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

    public int getWebPort() {
        return webPort;
    }

    public WindowsDockerConfig setWebPort(int webPort) {
        this.webPort = webPort;
        return this;
    }

    public int getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public WindowsDockerConfig setTimeoutMinutes(int timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
        return this;
    }

    public boolean isClean() {
        return clean;
    }

    public WindowsDockerConfig setClean(boolean clean) {
        this.clean = clean;
        return this;
    }

    /**
     * Parses the --windows option value.
     *
     * Supported formats:
     * - null, "", "headless" → headless mode
     * - "rdp" → interactive RDP/web mode
     * - "clean" → headless mode with clean state
     * - "rdp,clean" or "clean,rdp" → RDP mode with clean state
     *
     * @param value The option value
     * @return Configured WindowsDockerConfig
     */
    public static WindowsDockerConfig fromOptionValue(String value) {
        WindowsDockerConfig config = new WindowsDockerConfig();

        if (value == null || value.isEmpty()) {
            config.setMode(Mode.HEADLESS);
            return config;
        }

        // Parse comma-separated options
        String[] parts = value.toLowerCase().split(",");
        for (String part : parts) {
            part = part.trim();
            switch (part) {
                case "headless":
                    config.setMode(Mode.HEADLESS);
                    break;
                case "rdp":
                    config.setMode(Mode.RDP);
                    break;
                case "clean":
                    config.setClean(true);
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Invalid --windows value: " + part + ". Use 'headless', 'rdp', and/or 'clean'");
            }
        }

        return config;
    }
}

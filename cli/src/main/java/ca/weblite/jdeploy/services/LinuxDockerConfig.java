package ca.weblite.jdeploy.services;

/**
 * Configuration for Linux Docker testing.
 *
 * Supports two modes:
 * - HEADLESS: Automated installation and verification
 * - VNC: Interactive mode for manual testing via VNC/browser
 */
public class LinuxDockerConfig {

    /**
     * Testing mode.
     */
    public enum Mode {
        /**
         * Automated headless testing - installs app and runs verification checks.
         */
        HEADLESS,

        /**
         * Interactive VNC mode - starts container and waits for user to connect via browser/VNC.
         */
        VNC
    }

    /**
     * Docker image to use for Linux desktop testing.
     * linuxserver/webtop provides various desktops with web access and ARM64 support.
     */
    public static final String DOCKER_IMAGE = "linuxserver/webtop:ubuntu-xfce";

    private Mode mode = Mode.HEADLESS;
    private int vncPort = 5901;  // Avoid 5900 which conflicts with macOS Screen Sharing
    private int noVncPort = 3000;  // linuxserver/webtop uses port 3000
    private int timeoutMinutes = 10;
    private String resolution = "1280x720";
    private boolean clean = false;  // If true, start from fresh container

    // Dev mode fields
    private java.io.File jdeployHome;  // jDeploy project directory (for dev mode)
    private String[] runArgs;  // Original run arguments to pass through

    public LinuxDockerConfig() {
    }

    /**
     * Returns true if running in dev mode (jdeploy built from source).
     */
    public boolean isDevMode() {
        return jdeployHome != null && jdeployHome.exists();
    }

    public java.io.File getJdeployHome() {
        return jdeployHome;
    }

    public LinuxDockerConfig setJdeployHome(java.io.File jdeployHome) {
        this.jdeployHome = jdeployHome;
        return this;
    }

    public String[] getRunArgs() {
        return runArgs;
    }

    public LinuxDockerConfig setRunArgs(String[] runArgs) {
        this.runArgs = runArgs;
        return this;
    }

    public Mode getMode() {
        return mode;
    }

    public LinuxDockerConfig setMode(Mode mode) {
        this.mode = mode;
        return this;
    }

    public int getVncPort() {
        return vncPort;
    }

    public LinuxDockerConfig setVncPort(int vncPort) {
        this.vncPort = vncPort;
        return this;
    }

    public int getNoVncPort() {
        return noVncPort;
    }

    public LinuxDockerConfig setNoVncPort(int noVncPort) {
        this.noVncPort = noVncPort;
        return this;
    }

    public int getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public LinuxDockerConfig setTimeoutMinutes(int timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
        return this;
    }

    public String getResolution() {
        return resolution;
    }

    public LinuxDockerConfig setResolution(String resolution) {
        this.resolution = resolution;
        return this;
    }

    public boolean isClean() {
        return clean;
    }

    public LinuxDockerConfig setClean(boolean clean) {
        this.clean = clean;
        return this;
    }

    /**
     * Parses the --linux option value.
     *
     * Supported formats:
     * - null, "", "headless" → headless mode
     * - "vnc" → interactive VNC/web mode
     * - "clean" → headless mode with clean state
     * - "vnc,clean" or "clean,vnc" → VNC mode with clean state
     *
     * @param value The option value
     * @return Configured LinuxDockerConfig
     */
    public static LinuxDockerConfig fromOptionValue(String value) {
        LinuxDockerConfig config = new LinuxDockerConfig();

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
                case "vnc":
                    config.setMode(Mode.VNC);
                    break;
                case "clean":
                    config.setClean(true);
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Invalid --linux value: " + part + ". Use 'headless', 'vnc', and/or 'clean'");
            }
        }

        return config;
    }
}

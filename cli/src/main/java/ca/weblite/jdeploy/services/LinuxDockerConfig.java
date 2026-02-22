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
     * dorowu/ubuntu-desktop-lxde-vnc provides Ubuntu + LXDE + VNC + noVNC
     */
    public static final String DOCKER_IMAGE = "dorowu/ubuntu-desktop-lxde-vnc";

    private Mode mode = Mode.HEADLESS;
    private int vncPort = 5900;
    private int noVncPort = 6080;
    private int timeoutMinutes = 10;
    private String resolution = "1280x720";

    public LinuxDockerConfig() {
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

    /**
     * Parses the --linux option value.
     *
     * @param value The option value (null, "headless", or "vnc")
     * @return Configured LinuxDockerConfig
     */
    public static LinuxDockerConfig fromOptionValue(String value) {
        LinuxDockerConfig config = new LinuxDockerConfig();

        if (value == null || value.isEmpty() || "headless".equalsIgnoreCase(value)) {
            config.setMode(Mode.HEADLESS);
        } else if ("vnc".equalsIgnoreCase(value)) {
            config.setMode(Mode.VNC);
        } else {
            throw new IllegalArgumentException(
                    "Invalid --linux value: " + value + ". Use 'headless' or 'vnc'");
        }

        return config;
    }
}

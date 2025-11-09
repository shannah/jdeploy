package ca.weblite.jdeploy.installer.util;

/**
 * Debug logging utility for the installer.
 * Controlled by the JDEPLOY_DEBUG environment variable.
 * When JDEPLOY_DEBUG is set to "1" or "true", debug messages are printed to stdout.
 */
public class DebugLogger {
    private static final boolean DEBUG_ENABLED = isDebugEnabled();

    private static boolean isDebugEnabled() {
        String debug = System.getenv("JDEPLOY_DEBUG");
        return "1".equals(debug) || "true".equalsIgnoreCase(debug);
    }

    /**
     * Log a debug message if debug mode is enabled.
     * @param message The message to log
     */
    public static void log(String message) {
        if (DEBUG_ENABLED) {
            System.out.println("[JDEPLOY_DEBUG] " + message);
        }
    }

    /**
     * Log a network request if debug mode is enabled.
     * @param method HTTP method (GET, POST, etc.)
     * @param url The URL being requested
     */
    public static void logNetworkRequest(String method, String url) {
        if (DEBUG_ENABLED) {
            System.out.println("[JDEPLOY_DEBUG] HTTP " + method + " " + url);
        }
    }

    /**
     * Log a network response if debug mode is enabled.
     * @param url The URL that was requested
     * @param statusCode HTTP status code
     * @param message Additional message (optional)
     */
    public static void logNetworkResponse(String url, int statusCode, String message) {
        if (DEBUG_ENABLED) {
            System.out.println("[JDEPLOY_DEBUG] HTTP Response " + statusCode + " for " + url +
                (message != null && !message.isEmpty() ? " - " + message : ""));
        }
    }

    /**
     * Check if debug mode is enabled.
     * @return true if JDEPLOY_DEBUG is set to "1" or "true"
     */
    public static boolean isEnabled() {
        return DEBUG_ENABLED;
    }
}

package ca.weblite.jdeploy.installer.services;

import ca.weblite.jdeploy.models.HelperAction;
import ca.weblite.tools.platform.Platform;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for executing helper actions.
 *
 * Handles opening URLs, custom protocol handlers, and file paths based on the action's url field.
 *
 * @author Steve Hannah
 */
public class HelperActionExecutor {

    private static final Logger logger = Logger.getLogger(HelperActionExecutor.class.getName());

    /**
     * Executes a helper action by opening its URL.
     *
     * URL types supported:
     * - Regular URLs (http://, https://) - opened in default browser
     * - Custom protocol handlers (myapp://) - opened via system handler
     * - File paths (absolute paths) - opened with system default application
     *
     * @param action the helper action to execute
     */
    public void execute(HelperAction action) {
        if (action == null) {
            logger.warning("Cannot execute null helper action");
            return;
        }

        String url = action.getUrl();
        if (url == null || url.isEmpty()) {
            logger.warning("Cannot execute helper action with null or empty URL");
            return;
        }

        try {
            if (isProtocolUrl(url)) {
                // URL or custom protocol - use browse
                openUrl(url);
            } else if (isFilePath(url)) {
                // File path - use open
                openFile(url);
            } else {
                // Treat as URL by default
                openUrl(url);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to execute helper action: " + action.getLabel(), e);
        }
    }

    /**
     * Checks if the given string is a protocol URL (contains "://").
     *
     * @param url the URL to check
     * @return true if it contains "://"
     */
    private boolean isProtocolUrl(String url) {
        return url.contains("://");
    }

    /**
     * Checks if the given string appears to be a file path.
     *
     * @param url the string to check
     * @return true if it looks like a file path
     */
    private boolean isFilePath(String url) {
        // Unix absolute path
        if (url.startsWith("/")) {
            return true;
        }
        // Windows absolute path (e.g., C:\path or C:/path)
        if (url.length() >= 2 && Character.isLetter(url.charAt(0)) && url.charAt(1) == ':') {
            return true;
        }
        return false;
    }

    /**
     * Opens a URL or custom protocol in the default handler.
     *
     * @param url the URL to open
     * @throws Exception if opening fails
     */
    private void openUrl(String url) throws Exception {
        if (!Desktop.isDesktopSupported()) {
            logger.warning("Desktop API not supported, cannot open URL: " + url);
            return;
        }

        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            logger.warning("Browse action not supported, cannot open URL: " + url);
            return;
        }

        logger.info("Opening URL: " + url);
        desktop.browse(new URI(url));
    }

    /**
     * Opens a file with the system default application.
     *
     * On macOS, uses the "open" command for better compatibility.
     * On other platforms, uses the Desktop API.
     *
     * @param path the file path to open
     * @throws Exception if opening fails
     */
    private void openFile(String path) throws Exception {
        File file = new File(path);

        if (Platform.getSystemPlatform().isMac()) {
            // Use "open" command on macOS for better handling
            logger.info("Opening file with 'open' command: " + path);
            Runtime.getRuntime().exec(new String[]{"open", file.getAbsolutePath()});
        } else {
            if (!Desktop.isDesktopSupported()) {
                logger.warning("Desktop API not supported, cannot open file: " + path);
                return;
            }

            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.OPEN)) {
                logger.warning("Open action not supported, cannot open file: " + path);
                return;
            }

            logger.info("Opening file: " + path);
            desktop.open(file);
        }
    }
}

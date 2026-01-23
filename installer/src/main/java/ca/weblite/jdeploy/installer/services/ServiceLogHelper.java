package ca.weblite.jdeploy.installer.services;

import ca.weblite.jdeploy.installer.models.ServiceRowModel;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for managing service log files.
 *
 * Provides methods to locate and open log files for services, handling
 * platform-specific paths and different package sources (NPM vs GitHub).
 *
 * @author Steve Hannah
 */
public class ServiceLogHelper {

    private static final Logger logger = Logger.getLogger(ServiceLogHelper.class.getName());

    /**
     * Private constructor to prevent instantiation.
     */
    private ServiceLogHelper() {
        // Utility class - no instances
    }

    /**
     * Gets the stdout log file for the given service.
     *
     * @param service The service model
     * @return The output log file
     */
    public static File getOutputLogFile(ServiceRowModel service) {
        ServiceDescriptor descriptor = service.getDescriptor();
        return getLogFile(
            descriptor.getPackageName(),
            service.getCommandName(),
            descriptor.getSource(),
            "out"
        );
    }

    /**
     * Gets the stderr log file for the given service.
     *
     * @param service The service model
     * @return The error log file
     */
    public static File getErrorLogFile(ServiceRowModel service) {
        ServiceDescriptor descriptor = service.getDescriptor();
        return getLogFile(
            descriptor.getPackageName(),
            service.getCommandName(),
            descriptor.getSource(),
            "err"
        );
    }

    /**
     * Computes the log file path based on platform, source, and log type.
     *
     * Log files are stored in:
     * - NPM packages: ~/.jdeploy/log/{packageName}.{commandName}.{logType}.log
     * - GitHub packages: ~/.jdeploy/log/github.com/{owner}/{repo}/{packageName}.{commandName}.{logType}.log
     *
     * @param packageName The package name
     * @param commandName The command name
     * @param source The source (null for NPM, GitHub URL for GitHub packages)
     * @param logType The log type ("out" for stdout, "err" for stderr)
     * @return The log file
     */
    private static File getLogFile(String packageName, String commandName, String source, String logType) {
        String logFileName = packageName + "." + commandName + "." + logType + ".log";
        String os = System.getProperty("os.name", "").toLowerCase();
        String userHome = System.getProperty("user.home");

        if (source != null && !source.isEmpty() && source.contains("github.com")) {
            // GitHub-sourced package
            String githubPath = extractGitHubPath(source);
            if (os.contains("windows")) {
                return new File(userHome, ".jdeploy\\log\\github.com\\" + githubPath + "\\" + logFileName);
            } else {
                return new File(userHome, ".jdeploy/log/github.com/" + githubPath + "/" + logFileName);
            }
        } else {
            // NPM package
            if (os.contains("windows")) {
                return new File(userHome, ".jdeploy\\log\\" + logFileName);
            } else {
                return new File(userHome, ".jdeploy/log/" + logFileName);
            }
        }
    }

    /**
     * Extracts owner/repo from a GitHub URL.
     *
     * Examples:
     * - https://github.com/owner/repo -> owner/repo
     * - https://github.com/owner/repo.git -> owner/repo
     * - https://github.com/owner/repo/ -> owner/repo
     *
     * @param source The GitHub URL
     * @return The owner/repo path
     */
    private static String extractGitHubPath(String source) {
        String path = source;
        if (path.contains("github.com/")) {
            path = path.substring(path.indexOf("github.com/") + "github.com/".length());
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - 4);
        }
        return path;
    }

    /**
     * Opens the log file using the system's default application.
     *
     * If the file doesn't exist, shows an informational dialog.
     * If the Desktop API is not supported or opening fails, shows an error dialog.
     *
     * @param logFile The log file to open
     * @param parentComponent The parent component for dialogs (may be null)
     */
    public static void openLogFile(File logFile, Component parentComponent) {
        // Determine log type from file name for display purposes
        String fileName = logFile.getName();
        String logTypeDisplay = fileName.contains(".out.log") ? "stdout" : "stderr";

        if (!logFile.exists()) {
            JOptionPane.showMessageDialog(parentComponent,
                    "Log file does not exist yet.\nThe " + logTypeDisplay + " log file will be created when the service runs.\n\nExpected location:\n" + logFile.getAbsolutePath(),
                    "Log Not Found",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (!Desktop.isDesktopSupported()) {
            JOptionPane.showMessageDialog(parentComponent,
                    "Cannot open log file: Desktop API not supported.\n\nLog file location:\n" + logFile.getAbsolutePath(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            Desktop.getDesktop().open(logFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to open log file", e);
            JOptionPane.showMessageDialog(parentComponent,
                    "Failed to open log file: " + e.getMessage() + "\n\nLog file location:\n" + logFile.getAbsolutePath(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}

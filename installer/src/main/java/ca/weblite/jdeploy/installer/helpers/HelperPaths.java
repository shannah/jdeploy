package ca.weblite.jdeploy.installer.helpers;

import ca.weblite.tools.platform.Platform;

import java.io.File;

/**
 * Utility class for resolving platform-specific paths for the Helper application.
 *
 * The Helper is a background application that provides system tray service management
 * and uninstallation capabilities. This class provides consistent path resolution
 * across macOS, Windows, and Linux platforms.
 *
 * @author jDeploy Team
 */
public class HelperPaths {

    /**
     * Returns the directory where the Helper should be installed.
     *
     * Platform-specific behavior:
     * <ul>
     *   <li>macOS: ~/Applications/{appName} Helper/</li>
     *   <li>Windows: {appDirectory}/helpers/</li>
     *   <li>Linux: {appDirectory}/helpers/</li>
     * </ul>
     *
     * @param appName The application name (e.g., "My App")
     * @param appDirectory The application installation directory (ignored on macOS)
     * @return The directory where the Helper should be installed
     * @throws IllegalArgumentException if appName is null or empty
     */
    public static File getHelperDirectory(String appName, File appDirectory) {
        if (appName == null || appName.trim().isEmpty()) {
            throw new IllegalArgumentException("appName cannot be null or empty");
        }

        if (Platform.getSystemPlatform().isMac()) {
            // macOS: ~/Applications/{AppName} Helper/
            String userHome = System.getProperty("user.home");
            return new File(userHome + File.separator + "Applications", appName + " Helper");
        } else {
            // Windows and Linux: {appDirectory}/helpers/
            if (appDirectory == null) {
                throw new IllegalArgumentException("appDirectory cannot be null for Windows/Linux");
            }
            return new File(appDirectory, "helpers");
        }
    }

    /**
     * Returns the full path to the Helper executable.
     *
     * Platform-specific behavior:
     * <ul>
     *   <li>macOS: ~/Applications/{appName} Helper/{appName} Helper.app</li>
     *   <li>Windows: {appDirectory}/helpers/{appname}-helper.exe</li>
     *   <li>Linux: {appDirectory}/helpers/{appname}-helper</li>
     * </ul>
     *
     * @param appName The application name (e.g., "My App")
     * @param appDirectory The application installation directory (ignored on macOS)
     * @return The full path to the Helper executable
     * @throws IllegalArgumentException if appName is null or empty, or if appDirectory is null on Windows/Linux
     */
    public static File getHelperExecutablePath(String appName, File appDirectory) {
        File helperDir = getHelperDirectory(appName, appDirectory);

        if (Platform.getSystemPlatform().isMac()) {
            // macOS: {appName} Helper.app
            return new File(helperDir, appName + " Helper.app");
        } else if (Platform.getSystemPlatform().isWindows()) {
            // Windows: {appname}-helper.exe (lowercase with hyphens)
            String helperName = deriveHelperName(appName);
            return new File(helperDir, helperName + ".exe");
        } else {
            // Linux: {appname}-helper (lowercase with hyphens)
            String helperName = deriveHelperName(appName);
            return new File(helperDir, helperName);
        }
    }

    /**
     * Returns the path to the .jdeploy-files directory within the Helper directory.
     *
     * This directory contains the application context (app.xml, icon.png, etc.) that
     * the Helper uses to know which application it's helping.
     *
     * @param appName The application name (e.g., "My App")
     * @param appDirectory The application installation directory (ignored on macOS)
     * @return The path to the .jdeploy-files directory
     * @throws IllegalArgumentException if appName is null or empty, or if appDirectory is null on Windows/Linux
     */
    public static File getHelperContextDirectory(String appName, File appDirectory) {
        File helperDir = getHelperDirectory(appName, appDirectory);
        return new File(helperDir, ".jdeploy-files");
    }

    /**
     * Converts an application title to a helper executable name following platform conventions.
     *
     * Platform-specific behavior:
     * <ul>
     *   <li>macOS: Preserves case and spaces: "My App" -> "My App Helper"</li>
     *   <li>Windows/Linux: Lowercase with hyphens: "My App" -> "my-app-helper"</li>
     * </ul>
     *
     * The Windows/Linux naming follows the same pattern as deriveLinuxBinaryNameFromTitle():
     * converts to lowercase, replaces spaces with hyphens, removes all non-alphanumeric
     * characters except hyphens, and appends "-helper".
     *
     * @param appTitle The application title (e.g., "My App")
     * @return The helper executable name
     * @throws IllegalArgumentException if appTitle is null or empty
     */
    public static String deriveHelperName(String appTitle) {
        if (appTitle == null || appTitle.trim().isEmpty()) {
            throw new IllegalArgumentException("appTitle cannot be null or empty");
        }

        if (Platform.getSystemPlatform().isMac()) {
            // macOS: Preserve case, just add " Helper"
            return appTitle + " Helper";
        } else {
            // Windows/Linux: lowercase with hyphens, add "-helper"
            // Pattern: title.toLowerCase().replace(" ", "-").replaceAll("[^a-z0-9\\-]", "")
            String normalized = appTitle.toLowerCase()
                .replace(" ", "-")
                .replaceAll("[^a-z0-9\\-]", "");
            return normalized + "-helper";
        }
    }
}

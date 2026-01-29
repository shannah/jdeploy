package ca.weblite.jdeploy.installer.util;

import java.io.File;

/**
 * Centralized utility for resolving the Windows app installation directory.
 *
 * The install directory is controlled by the {@code jdeploy.winAppDir} property in package.json,
 * which specifies a path relative to the user's home directory (e.g., {@code "AppData\\Local\\Programs"}).
 *
 * If not set, falls back to the legacy {@code .jdeploy/apps} directory.
 */
public class WindowsAppDirResolver {

    public static final String DEFAULT_WIN_APP_DIR = ".jdeploy" + File.separator + "apps";

    /**
     * Resolves the app directory for a given fully qualified package name.
     *
     * @param winAppDir The winAppDir value from package.json jdeploy config, or null for default
     * @param fullyQualifiedPackageName The FQPN of the app
     * @return The app directory
     */
    public static File resolveAppDir(String winAppDir, String fullyQualifiedPackageName) {
        File appsBaseDir = resolveAppsBaseDir(winAppDir);
        return new File(appsBaseDir, fullyQualifiedPackageName);
    }

    /**
     * Returns the apps base directory (without the FQPN suffix).
     *
     * @param winAppDir The winAppDir value from package.json jdeploy config, or null for default
     * @return The base directory where apps are installed
     */
    public static File resolveAppsBaseDir(String winAppDir) {
        File userHome = new File(System.getProperty("user.home"));
        String basePath = (winAppDir != null && !winAppDir.isEmpty()) ? winAppDir : DEFAULT_WIN_APP_DIR;
        return new File(userHome, basePath);
    }

    /**
     * Returns the legacy app directory (always ~/.jdeploy/apps/{fqpn}).
     * Used for backward compatibility checks when searching for existing installations.
     *
     * @param fullyQualifiedPackageName The FQPN of the app
     * @return The legacy app directory
     */
    public static File getLegacyAppDir(String fullyQualifiedPackageName) {
        File userHome = new File(System.getProperty("user.home"));
        File jdeployHome = new File(userHome, ".jdeploy");
        File appsDir = new File(jdeployHome, "apps");
        return new File(appsDir, fullyQualifiedPackageName);
    }
}

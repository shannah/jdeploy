package ca.weblite.jdeploy.installer.util;

import ca.weblite.tools.io.MD5;

import java.io.File;

/**
 * Utility class for resolving the directory where CLI command wrappers/scripts
 * should be installed for jDeploy packages.
 *
 * Supports both NPM-released packages and GitHub-released packages with
 * distinct fully-qualified package names to avoid collision.
 */
public class CliCommandBinDirResolver {

    private static final String BIN_DIR_NAME = ".jdeploy/bin";

    /**
     * Computes the fully-qualified package name for CLI command installation.
     *
     * For NPM packages (source == null), returns the packageName as-is.
     * For GitHub packages, returns "{MD5(source)}.{packageName}" to avoid collisions
     * between packages with the same name from different GitHub repositories.
     *
     * @param packageName the package name (e.g., "my-app")
     * @param source      the GitHub source URL (null for NPM packages)
     * @return the fully-qualified package name
     * @throws IllegalArgumentException if packageName is null or empty
     */
    public static String computeFullyQualifiedPackageName(String packageName, String source) {
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("packageName cannot be null or empty");
        }

        if (source == null) {
            // NPM package: use packageName as-is
            return packageName;
        }

        // GitHub package: use MD5(source).packageName format
        String hash = MD5.getMd5(source);
        return hash + "." + packageName;
    }

    /**
     * Convenience method to get the CLI command bin directory for a package.
     *
     * Uses the default user home directory as the base path.
     *
     * @param packageName the package name
     * @param source      the GitHub source URL (null for NPM packages)
     * @return the File object representing the bin directory
     * @throws IllegalArgumentException if packageName is null or empty
     */
    public static File getCliCommandBinDir(String packageName, String source) {
        String userHome = System.getProperty("user.home");
        return getCliCommandBinDir(packageName, source, new File(userHome));
    }

    /**
     * Testable method to get the CLI command bin directory with injected base directory.
     *
     * Allows tests to inject a custom base directory instead of relying on system properties.
     *
     * @param packageName the package name
     * @param source      the GitHub source URL (null for NPM packages)
     * @param userHome    the base directory (typically user home or test directory)
     * @return the File object representing the bin directory
     * @throws IllegalArgumentException if packageName is null or empty
     */
    public static File getCliCommandBinDir(String packageName, String source, File userHome) {
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("packageName cannot be null or empty");
        }

        return new File(userHome, BIN_DIR_NAME);
    }
}

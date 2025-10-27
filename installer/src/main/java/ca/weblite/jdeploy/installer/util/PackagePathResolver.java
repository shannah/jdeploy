package ca.weblite.jdeploy.installer.util;

import ca.weblite.tools.io.MD5;

import java.io.File;

/**
 * Utility class for resolving package directory paths with architecture-specific support.
 *
 * This utility implements the architecture-specific package locations feature (GitHub Issue #310).
 * It provides methods to resolve package paths that support both legacy (non-architecture-specific)
 * and new (architecture-specific) directory structures.
 *
 * Path Resolution Strategy:
 * - When installing: Always use architecture-specific paths (e.g., ~/.jdeploy/packages-arm64)
 * - When reading/uninstalling: Check architecture-specific path first, fall back to legacy path
 * - This allows backward compatibility with existing installations while supporting multiple
 *   architecture versions on systems that can run both (e.g., macOS with Rosetta 2)
 */
public class PackagePathResolver {

    /**
     * Gets the base .jdeploy directory in the user's home.
     *
     * @return The .jdeploy directory
     */
    private static File getJDeployHome() {
        return new File(System.getProperty("user.home") + File.separator + ".jdeploy");
    }

    /**
     * Resolves the package directory path for reading/uninstalling.
     * Checks architecture-specific path first, then falls back to legacy path.
     *
     * @param packageName The NPM package name
     * @param version The version (can be null for latest)
     * @param source The source URL (null for NPM registry packages)
     * @return The resolved package directory path (may not exist)
     */
    public static File resolvePackagePath(String packageName, String version, String source) {
        if (source == null || source.isEmpty()) {
            return resolveNpmPackagePath(packageName, version);
        } else {
            return resolveGhPackagePath(packageName, version, source);
        }
    }

    /**
     * Gets the package directory path to use for new installations.
     * Always returns architecture-specific path.
     *
     * @param packageName The NPM package name
     * @param version The version (can be null for latest)
     * @param source The source URL (null for NPM registry packages)
     * @return The package directory path for installation
     */
    public static File getInstallPackagePath(String packageName, String version, String source) {
        if (source == null || source.isEmpty()) {
            return getNpmPackagePathForInstall(packageName, version);
        } else {
            return getGhPackagePathForInstall(packageName, version, source);
        }
    }

    /**
     * Resolves NPM package path (from npm registry).
     * Checks architecture-specific path first, falls back to legacy.
     *
     * @param packageName The NPM package name
     * @param version The version (can be null)
     * @return The resolved path
     */
    private static File resolveNpmPackagePath(String packageName, String version) {
        // Try architecture-specific path first
        File archSpecificPath = getNpmPackagePathForInstall(packageName, version);
        if (archSpecificPath.exists()) {
            return archSpecificPath;
        }

        // Fall back to legacy path
        return getLegacyNpmPackagePath(packageName, version);
    }

    /**
     * Resolves GitHub package path (from custom source).
     * Checks architecture-specific path first, falls back to legacy.
     *
     * @param packageName The NPM package name
     * @param version The version (can be null)
     * @param source The GitHub source URL
     * @return The resolved path
     */
    private static File resolveGhPackagePath(String packageName, String version, String source) {
        // Try architecture-specific path first
        File archSpecificPath = getGhPackagePathForInstall(packageName, version, source);
        if (archSpecificPath.exists()) {
            return archSpecificPath;
        }

        // Fall back to legacy path
        return getLegacyGhPackagePath(packageName, version, source);
    }

    /**
     * Gets the architecture-specific NPM package path for installation.
     *
     * @param packageName The NPM package name
     * @param version The version (can be null)
     * @return The architecture-specific path
     */
    private static File getNpmPackagePathForInstall(String packageName, String version) {
        String archSuffix = ArchitectureUtil.getArchitectureSuffix();
        File packagesDir = new File(getJDeployHome(), "packages" + archSuffix);

        if (version == null) {
            return new File(packagesDir, new File(packageName).getName());
        } else {
            return new File(
                    packagesDir,
                    new File(packageName).getName() + File.separator + new File(version).getName()
            );
        }
    }

    /**
     * Gets the architecture-specific GitHub package path for installation.
     *
     * @param packageName The NPM package name
     * @param version The version (can be null)
     * @param source The GitHub source URL
     * @return The architecture-specific path
     */
    private static File getGhPackagePathForInstall(String packageName, String version, String source) {
        String archSuffix = ArchitectureUtil.getArchitectureSuffix();
        String sourceHash = MD5.getMd5(source);
        String qualifiedName = sourceHash + "." + packageName;

        if (version == null) {
            return new File(getJDeployHome(), "gh-packages" + archSuffix + File.separator + qualifiedName);
        } else {
            return new File(
                    getJDeployHome(),
                    "gh-packages" + archSuffix + File.separator + qualifiedName + File.separator + new File(version).getName()
            );
        }
    }

    /**
     * Gets the legacy (non-architecture-specific) NPM package path.
     *
     * @param packageName The NPM package name
     * @param version The version (can be null)
     * @return The legacy path
     */
    private static File getLegacyNpmPackagePath(String packageName, String version) {
        if (version == null) {
            return new File(getJDeployHome(), "packages" + File.separator + new File(packageName).getName());
        } else {
            return new File(
                    getJDeployHome(),
                    "packages" + File.separator +
                            new File(packageName).getName() + File.separator +
                            new File(version).getName()
            );
        }
    }

    /**
     * Gets the legacy (non-architecture-specific) GitHub package path.
     *
     * @param packageName The NPM package name
     * @param version The version (can be null)
     * @param source The GitHub source URL
     * @return The legacy path
     */
    private static File getLegacyGhPackagePath(String packageName, String version, String source) {
        String sourceHash = MD5.getMd5(source);
        String qualifiedName = sourceHash + "." + packageName;

        if (version == null) {
            return new File(getJDeployHome(), "gh-packages" + File.separator + qualifiedName);
        } else {
            return new File(
                    getJDeployHome(),
                    "gh-packages" + File.separator + qualifiedName + File.separator + new File(version).getName()
            );
        }
    }

    /**
     * Gets all possible package paths for a given package (for cleanup/uninstall operations).
     * Returns both architecture-specific and legacy paths.
     *
     * @param packageName The NPM package name
     * @param version The version (can be null)
     * @param source The source URL (null for NPM registry packages)
     * @return Array of possible paths [architecture-specific, legacy]
     */
    public static File[] getAllPossiblePackagePaths(String packageName, String version, String source) {
        File archSpecificPath = getInstallPackagePath(packageName, version, source);
        File legacyPath;

        if (source == null || source.isEmpty()) {
            legacyPath = getLegacyNpmPackagePath(packageName, version);
        } else {
            legacyPath = getLegacyGhPackagePath(packageName, version, source);
        }

        return new File[]{archSpecificPath, legacyPath};
    }
}

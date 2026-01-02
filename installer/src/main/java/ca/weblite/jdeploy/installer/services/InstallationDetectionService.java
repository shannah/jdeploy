package ca.weblite.jdeploy.installer.services;

import ca.weblite.jdeploy.installer.uninstall.FileUninstallManifestRepository;
import ca.weblite.jdeploy.installer.uninstall.UninstallManifestRepository;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import ca.weblite.jdeploy.installer.util.PackagePathResolver;

import java.io.File;
import java.util.Optional;

/**
 * Service for detecting if a jDeploy application is already installed on the system.
 *
 * An application is considered installed if either:
 * 1. The package directory exists at one of the expected locations:
 *    - ~/.jdeploy/packages{-arch}/{packageName} (for NPM packages)
 *    - ~/.jdeploy/gh-packages{-arch}/{fullyQualifiedPackageName} (for GitHub packages)
 * 2. An uninstall manifest exists for the application at:
 *    - ~/.jdeploy/manifests/{arch}/{fqpn}/uninstall-manifest.xml
 *
 * This service handles both architecture-specific and legacy package locations.
 */
public class InstallationDetectionService {

    private final UninstallManifestRepository manifestRepository;

    /**
     * Creates a new InstallationDetectionService with the default FileUninstallManifestRepository.
     */
    public InstallationDetectionService() {
        this(new FileUninstallManifestRepository());
    }

    /**
     * Creates a new InstallationDetectionService with a custom UninstallManifestRepository.
     * This constructor is useful for testing with mock repositories.
     *
     * @param manifestRepository the repository for accessing uninstall manifests
     */
    public InstallationDetectionService(UninstallManifestRepository manifestRepository) {
        this.manifestRepository = manifestRepository;
    }

    /**
     * Checks if an application is already installed on the system.
     *
     * @param packageName the package name (e.g., "my-app")
     * @param source the source URL (null or empty for NPM packages, GitHub URL for GitHub packages)
     * @return true if the application is installed, false otherwise
     */
    public boolean isInstalled(String packageName, String source) {
        return isInstalled(packageName, null, source);
    }

    /**
     * Checks if a specific version of an application is already installed on the system.
     *
     * @param packageName the package name (e.g., "my-app")
     * @param version the version string (can be null to check any version)
     * @param source the source URL (null or empty for NPM packages, GitHub URL for GitHub packages)
     * @return true if the application is installed, false otherwise
     */
    public boolean isInstalled(String packageName, String version, String source) {
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("packageName cannot be null or empty");
        }

        // Normalize empty source to null
        String normalizedSource = (source != null && source.trim().isEmpty()) ? null : source;

        // Check if package directory exists
        if (packageDirectoryExists(packageName, version, normalizedSource)) {
            return true;
        }

        // Check if uninstall manifest exists
        return uninstallManifestExists(packageName, normalizedSource);
    }

    /**
     * Checks if the package directory exists for the given package.
     * Uses PackagePathResolver which handles both architecture-specific and legacy paths.
     *
     * @param packageName the package name
     * @param version the version (can be null)
     * @param source the source URL (null for NPM packages)
     * @return true if the package directory exists, false otherwise
     */
    private boolean packageDirectoryExists(String packageName, String version, String source) {
        try {
            File packagePath = PackagePathResolver.resolvePackagePath(packageName, version, source);
            return packagePath != null && packagePath.exists() && packagePath.isDirectory();
        } catch (Exception e) {
            // If there's an error resolving the path, consider it as not installed
            return false;
        }
    }

    /**
     * Checks if an uninstall manifest exists for the given package.
     *
     * @param packageName the package name
     * @param source the source URL (null for NPM packages)
     * @return true if the uninstall manifest exists, false otherwise
     */
    private boolean uninstallManifestExists(String packageName, String source) {
        try {
            Optional<UninstallManifest> manifest = manifestRepository.load(packageName, source);
            return manifest.isPresent();
        } catch (Exception e) {
            // If there's an error loading the manifest, consider it as not existing
            return false;
        }
    }
}

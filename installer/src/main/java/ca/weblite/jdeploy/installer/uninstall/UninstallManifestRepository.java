package ca.weblite.jdeploy.installer.uninstall;

import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;

import java.util.Optional;

/**
 * Repository interface for managing uninstall manifests.
 * 
 * This interface defines the contract for persisting and retrieving uninstall manifest
 * metadata. Implementations may store manifests in various formats (e.g., XML files, databases).
 */
public interface UninstallManifestRepository {

    /**
     * Saves an uninstall manifest.
     *
     * @param manifest the manifest to save
     * @throws Exception if an error occurs during save
     */
    void save(UninstallManifest manifest) throws Exception;

    /**
     * Loads an uninstall manifest for the given package.
     *
     * @param packageName the package name
     * @param source the GitHub source URL (null for NPM packages)
     * @return an Optional containing the manifest if found, empty otherwise
     * @throws Exception if an error occurs during load
     */
    Optional<UninstallManifest> load(String packageName, String source) throws Exception;

    /**
     * Deletes the uninstall manifest for the given package.
     *
     * @param packageName the package name
     * @param source the GitHub source URL (null for NPM packages)
     * @throws Exception if an error occurs during delete
     */
    void delete(String packageName, String source) throws Exception;
}

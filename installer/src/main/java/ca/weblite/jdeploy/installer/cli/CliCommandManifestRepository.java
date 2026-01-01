package ca.weblite.jdeploy.installer.cli;

import java.util.Optional;

/**
 * Repository interface for managing CLI command manifests.
 * 
 * This interface defines the contract for persisting and retrieving CLI command
 * installation metadata. Implementations may store manifests in various formats
 * (e.g., JSON files, databases).
 */
public interface CliCommandManifestRepository {

    /**
     * Saves a CLI command manifest.
     *
     * @param manifest the manifest to save
     * @throws Exception if an error occurs during save
     */
    void save(CliCommandManifest manifest) throws Exception;

    /**
     * Loads a CLI command manifest for the given package.
     *
     * @param packageName the package name
     * @param source the GitHub source URL (null for NPM packages)
     * @return an Optional containing the manifest if found, empty otherwise
     * @throws Exception if an error occurs during load
     */
    Optional<CliCommandManifest> load(String packageName, String source) throws Exception;

    /**
     * Deletes the CLI command manifest for the given package.
     *
     * @param packageName the package name
     * @param source the GitHub source URL (null for NPM packages)
     * @throws Exception if an error occurs during delete
     */
    void delete(String packageName, String source) throws Exception;
}

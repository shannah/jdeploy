package ca.weblite.jdeploy.installer.services;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing service descriptors.
 *
 * Service descriptors are stored at:
 * - Non-branch: ~/.jdeploy/services/{arch}/{fqpn}/{commandName}.json
 * - Branch: ~/.jdeploy/services/{arch}/{fqpn}/{branchName}/{commandName}.json
 *
 * @author Steve Hannah
 */
public interface ServiceDescriptorRepository {

    /**
     * Saves a service descriptor.
     *
     * @param descriptor The descriptor to save
     * @throws IOException if an I/O error occurs
     */
    void save(ServiceDescriptor descriptor) throws IOException;

    /**
     * Loads a service descriptor.
     *
     * @param packageName The package name
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param commandName The command name
     * @param branchName The branch name, or null for non-branch installations
     * @return An Optional containing the descriptor if found, empty otherwise
     * @throws IOException if an I/O error occurs
     */
    Optional<ServiceDescriptor> load(String packageName, String source, String commandName, String branchName)
        throws IOException;

    /**
     * Deletes a service descriptor.
     *
     * @param packageName The package name
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param commandName The command name
     * @param branchName The branch name, or null for non-branch installations
     * @return true if the descriptor was deleted, false if it didn't exist
     * @throws IOException if an I/O error occurs
     */
    boolean delete(String packageName, String source, String commandName, String branchName)
        throws IOException;

    /**
     * Lists all service descriptors for a package with source.
     *
     * @param packageName The package name
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @return A list of all service descriptors for the package (empty if none found)
     * @throws IOException if an I/O error occurs
     */
    List<ServiceDescriptor> listByPackage(String packageName, String source) throws IOException;

    /**
     * Lists all service descriptors for a package and branch.
     *
     * @param packageName The package name
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param branchName The branch name, or null for non-branch installations
     * @return A list of service descriptors (empty if none found)
     * @throws IOException if an I/O error occurs
     */
    List<ServiceDescriptor> listByPackageAndBranch(String packageName, String source, String branchName)
        throws IOException;

    /**
     * Checks if a service descriptor exists.
     *
     * @param packageName The package name
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param commandName The command name
     * @param branchName The branch name, or null for non-branch installations
     * @return true if the descriptor exists
     */
    boolean exists(String packageName, String source, String commandName, String branchName);

    /**
     * Deletes all service descriptors for a package.
     *
     * @param packageName The package name
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @return The number of descriptors deleted
     * @throws IOException if an I/O error occurs
     */
    int deleteAllByPackage(String packageName, String source) throws IOException;

    /**
     * Deletes all service descriptors for a package and branch.
     *
     * @param packageName The package name
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param branchName The branch name, or null for non-branch installations
     * @return The number of descriptors deleted
     * @throws IOException if an I/O error occurs
     */
    int deleteAllByPackageAndBranch(String packageName, String source, String branchName) throws IOException;
}

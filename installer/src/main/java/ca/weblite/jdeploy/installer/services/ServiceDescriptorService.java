package ca.weblite.jdeploy.installer.services;

import ca.weblite.jdeploy.models.CommandSpec;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service layer for managing service descriptors.
 *
 * Provides high-level operations for service lifecycle management during
 * installation, updates, and uninstallation.
 *
 * @author Steve Hannah
 */
@Singleton
public class ServiceDescriptorService {
    private static final Logger LOGGER = Logger.getLogger(ServiceDescriptorService.class.getName());

    private final ServiceDescriptorRepository repository;

    @Inject
    public ServiceDescriptorService(ServiceDescriptorRepository repository) {
        this.repository = repository;
    }

    /**
     * Registers a new service by creating and saving a service descriptor.
     *
     * @param commandSpec The command spec that implements service_controller
     * @param packageName The package name
     * @param version The package version
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param branchName The branch name, or null for non-branch installations
     * @return The created ServiceDescriptor
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if commandSpec doesn't implement service_controller
     */
    public ServiceDescriptor registerService(
            CommandSpec commandSpec,
            String packageName,
            String version,
            String source,
            String branchName) throws IOException {

        ServiceDescriptor descriptor = new ServiceDescriptor(
            commandSpec,
            packageName,
            version,
            source,
            branchName
        );

        repository.save(descriptor);
        LOGGER.log(Level.INFO, "Registered service: {0} for package {1} (version {2})",
                  new Object[]{commandSpec.getName(), packageName, version});

        return descriptor;
    }

    /**
     * Unregisters a service by deleting its descriptor.
     *
     * @param packageName The package name
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param commandName The command name
     * @param branchName The branch name, or null for non-branch installations
     * @return true if the service was unregistered, false if it didn't exist
     * @throws IOException if an I/O error occurs
     */
    public boolean unregisterService(String packageName, String source, String commandName, String branchName)
            throws IOException {
        boolean deleted = repository.delete(packageName, source, commandName, branchName);

        if (deleted) {
            LOGGER.log(Level.INFO, "Unregistered service: {0} for package {1}",
                      new Object[]{commandName, packageName});
        }

        return deleted;
    }

    /**
     * Gets a service descriptor if it exists.
     *
     * @param packageName The package name
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param commandName The command name
     * @param branchName The branch name, or null for non-branch installations
     * @return An Optional containing the descriptor if found
     * @throws IOException if an I/O error occurs
     */
    public Optional<ServiceDescriptor> getService(String packageName, String source, String commandName, String branchName)
            throws IOException {
        return repository.load(packageName, source, commandName, branchName);
    }

    /**
     * Checks if a service is registered.
     *
     * @param packageName The package name
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param commandName The command name
     * @param branchName The branch name, or null for non-branch installations
     * @return true if the service is registered
     */
    public boolean isServiceRegistered(String packageName, String source, String commandName, String branchName) {
        return repository.exists(packageName, source, commandName, branchName);
    }

    /**
     * Lists all services for a package.
     *
     * This method correctly handles both NPM packages (source = null) and
     * GitHub packages (source = GitHub URL) by computing the fully qualified
     * package name used for service descriptor storage.
     *
     * @param packageName The package name
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @return A list of service descriptors (empty if none found)
     * @throws IOException if an I/O error occurs
     */
    public List<ServiceDescriptor> listServices(String packageName, String source) throws IOException {
        return repository.listByPackage(packageName, source);
    }

    /**
     * Lists services for a specific package and branch.
     *
     * @param packageName The package name
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param branchName The branch name, or null for non-branch installations
     * @return A list of service descriptors (empty if none found)
     * @throws IOException if an I/O error occurs
     */
    public List<ServiceDescriptor> listServices(String packageName, String source, String branchName) throws IOException {
        return repository.listByPackageAndBranch(packageName, source, branchName);
    }

    /**
     * Unregisters all services for a package.
     *
     * @param packageName The package name
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @return The number of services unregistered
     * @throws IOException if an I/O error occurs
     */
    public int unregisterAllServices(String packageName, String source) throws IOException {
        int count = repository.deleteAllByPackage(packageName, source);

        if (count > 0) {
            LOGGER.log(Level.INFO, "Unregistered {0} service(s) for package {1}",
                      new Object[]{count, packageName});
        }

        return count;
    }

    /**
     * Unregisters all services for a package and branch.
     *
     * @param packageName The package name
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param branchName The branch name, or null for non-branch installations
     * @return The number of services unregistered
     * @throws IOException if an I/O error occurs
     */
    public int unregisterAllServices(String packageName, String source, String branchName) throws IOException {
        int count = repository.deleteAllByPackageAndBranch(packageName, source, branchName);

        if (count > 0) {
            LOGGER.log(Level.INFO, "Unregistered {0} service(s) for package {1}, branch {2}",
                      new Object[]{count, packageName, branchName});
        }

        return count;
    }

    /**
     * Finds services that exist in the current installation but not in the new command specs.
     * These services should be uninstalled before updating.
     *
     * @param packageName The package name
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param branchName The branch name, or null for non-branch installations
     * @param newCommandSpecs The command specs from the new version
     * @return A list of service descriptors that should be uninstalled
     * @throws IOException if an I/O error occurs
     */
    public List<ServiceDescriptor> findServicesToUninstall(
            String packageName,
            String source,
            String branchName,
            List<CommandSpec> newCommandSpecs) throws IOException {

        List<ServiceDescriptor> currentServices = listServices(packageName, source, branchName);
        List<ServiceDescriptor> toUninstall = new ArrayList<>();

        for (ServiceDescriptor currentService : currentServices) {
            boolean existsInNewVersion = false;

            for (CommandSpec newSpec : newCommandSpecs) {
                if (newSpec.getName().equals(currentService.getCommandName()) &&
                    newSpec.implements_("service_controller")) {
                    existsInNewVersion = true;
                    break;
                }
            }

            if (!existsInNewVersion) {
                toUninstall.add(currentService);
                LOGGER.log(Level.INFO, "Service {0} should be uninstalled (not in new version)",
                          currentService.getCommandName());
            }
        }

        return toUninstall;
    }

    /**
     * Finds all services that need to be stopped before an update.
     * This includes all currently registered services for the package and branch.
     *
     * @param packageName The package name
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param branchName The branch name, or null for non-branch installations
     * @return A list of service descriptors that should be stopped
     * @throws IOException if an I/O error occurs
     */
    public List<ServiceDescriptor> findServicesToStop(String packageName, String source, String branchName)
            throws IOException {
        return listServices(packageName, source, branchName);
    }

    /**
     * Updates a service descriptor with new information.
     * This creates a new descriptor with updated timestamp.
     *
     * @param packageName The package name
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param commandName The command name
     * @param branchName The branch name, or null for non-branch installations
     * @param newCommandSpec The new command spec
     * @param newVersion The new version
     * @return The updated ServiceDescriptor, or empty if the service wasn't registered
     * @throws IOException if an I/O error occurs
     */
    public Optional<ServiceDescriptor> updateService(
            String packageName,
            String source,
            String commandName,
            String branchName,
            CommandSpec newCommandSpec,
            String newVersion) throws IOException {

        Optional<ServiceDescriptor> existing = repository.load(packageName, source, commandName, branchName);

        if (!existing.isPresent()) {
            return Optional.empty();
        }

        // Preserve source from existing descriptor (should match passed source)
        ServiceDescriptor updated = new ServiceDescriptor(
            newCommandSpec,
            packageName,
            newVersion,
            source,
            branchName,
            existing.get().getInstalledTimestamp(),
            System.currentTimeMillis()
        );

        repository.save(updated);
        LOGGER.log(Level.INFO, "Updated service: {0} for package {1} to version {2}",
                  new Object[]{commandName, packageName, newVersion});

        return Optional.of(updated);
    }
}

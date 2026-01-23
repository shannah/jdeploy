package ca.weblite.jdeploy.installer.services;

/**
 * Factory for creating ServiceDescriptorService instances.
 *
 * Provides a centralized way to create and configure the service descriptor service.
 *
 * @author Steve Hannah
 */
public class ServiceDescriptorServiceFactory {

    /**
     * Creates a new ServiceDescriptorService instance with default configuration.
     *
     * @return A configured ServiceDescriptorService instance
     */
    public static ServiceDescriptorService createDefault() {
        ServiceDescriptorRepository repository = new FileServiceDescriptorRepository();
        return new ServiceDescriptorService(repository);
    }

    /**
     * Creates a new ServiceDescriptorService instance with a custom repository.
     *
     * @param repository The repository to use
     * @return A configured ServiceDescriptorService instance
     */
    public static ServiceDescriptorService create(ServiceDescriptorRepository repository) {
        return new ServiceDescriptorService(repository);
    }

    private ServiceDescriptorServiceFactory() {
        // Utility class, no instantiation
    }
}

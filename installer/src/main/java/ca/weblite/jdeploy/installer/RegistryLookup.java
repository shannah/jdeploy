package ca.weblite.jdeploy.installer;

import java.io.IOException;

/**
 * Functional interface for querying the jDeploy registry for bundle information.
 * Package-private for testing purposes.
 */
interface RegistryLookup {
    /**
     * Query the registry for bundle information.
     *
     * @param code The bundle code
     * @return BundleInfo if found, null if not found or on error
     * @throws IOException If an I/O error occurs
     */
    BundleInfo queryBundle(String code) throws IOException;
}
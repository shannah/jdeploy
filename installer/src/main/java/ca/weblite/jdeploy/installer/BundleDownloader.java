package ca.weblite.jdeploy.installer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Functional interface for downloading jDeploy bundle files.
 * Package-private for testing purposes.
 */
interface BundleDownloader {
    /**
     * Open an input stream to download the bundle files.
     *
     * @param code The bundle code
     * @param version The version string
     * @param appBundle The app bundle file
     * @return InputStream for reading the bundle zip file
     * @throws IOException If an I/O error occurs
     */
    InputStream openBundleStream(String code, String version, File appBundle) throws IOException;
}
package ca.weblite.jdeploy.installer;

import java.io.IOException;
import java.io.InputStream;

/**
 * Functional interface for downloading files from GitHub releases.
 * Allows mocking GitHub HTTP requests in tests.
 */
@FunctionalInterface
public interface GitHubDownloader {
    /**
     * Download a file from a GitHub release.
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @param tag Release tag (e.g., "v1.2.3" or "jdeploy")
     * @param filename File to download from the release
     * @return InputStream of the downloaded file
     * @throws IOException if download fails (404, network error, etc.)
     */
    InputStream downloadFromRelease(String owner, String repo, String tag, String filename) throws IOException;
}

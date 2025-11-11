package ca.weblite.jdeploy.publishing.github;

import java.io.IOException;

/**
 * Thrown when the jdeploy release exists but the package-info.json asset doesn't exist
 * (404 on asset lookup). This indicates a corrupted state and requires manual cleanup
 * (delete jdeploy release).
 */
public class GitHubAssetNotFoundException extends IOException {
    public GitHubAssetNotFoundException(String message) {
        super(message);
    }

    public GitHubAssetNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

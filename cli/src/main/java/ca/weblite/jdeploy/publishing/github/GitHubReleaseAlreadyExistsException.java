package ca.weblite.jdeploy.publishing.github;

import java.io.IOException;

/**
 * Thrown when attempting to atomically create a release that already exists
 * (422 Unprocessable Entity from GitHub). This indicates a concurrent first publish -
 * another process created the jdeploy release while this publish was running.
 */
public class GitHubReleaseAlreadyExistsException extends IOException {
    public GitHubReleaseAlreadyExistsException(String message) {
        super(message);
    }

    public GitHubReleaseAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}

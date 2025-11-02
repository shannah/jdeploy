package ca.weblite.jdeploy.publishing.github;

import java.io.IOException;

/**
 * Thrown when the jdeploy tag/release doesn't exist (404 on release lookup).
 * This is expected on first publish.
 */
public class GitHubReleaseNotFoundException extends IOException {
    public GitHubReleaseNotFoundException(String message) {
        super(message);
    }

    public GitHubReleaseNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

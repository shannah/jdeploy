package ca.weblite.jdeploy.services;

import java.io.IOException;

/**
 * Thrown when a GitHub API request fails because of invalid, expired or missing
 * credentials (HTTP 401). It extends {@link IOException} so existing callers
 * continue to work, while allowing callers that care about authentication to
 * distinguish a bad token from other I/O errors and prompt the user to
 * re-authenticate.
 */
public class GitHubAuthenticationException extends IOException {

    public GitHubAuthenticationException(String message) {
        super(message);
    }

    public GitHubAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}

package ca.weblite.jdeploy.publishing.github;

import java.io.IOException;

/**
 * Thrown when the optimistic lock fails (412 response), indicating that another publish process
 * modified the jdeploy tag during this publish.
 */
public class ConcurrentPublishException extends IOException {
    public ConcurrentPublishException(String message) {
        super(message);
    }

    public ConcurrentPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}

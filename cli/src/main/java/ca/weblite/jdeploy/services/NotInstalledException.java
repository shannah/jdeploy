package ca.weblite.jdeploy.services;

/**
 * Exception thrown when attempting to run an application that is not installed locally.
 */
public class NotInstalledException extends Exception {

    public NotInstalledException(String message) {
        super(message);
    }

    public NotInstalledException(String message, Throwable cause) {
        super(message, cause);
    }
}

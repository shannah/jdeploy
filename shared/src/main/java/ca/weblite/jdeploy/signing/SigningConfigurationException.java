package ca.weblite.jdeploy.signing;

/**
 * Exception thrown when signing configuration is invalid.
 */
public class SigningConfigurationException extends Exception {

    public SigningConfigurationException(String message) {
        super(message);
    }

    public SigningConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}

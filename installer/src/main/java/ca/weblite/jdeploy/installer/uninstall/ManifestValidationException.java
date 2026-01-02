package ca.weblite.jdeploy.installer.uninstall;

/**
 * Checked exception thrown when manifest validation against the XSD schema fails.
 * Includes detailed error messages from the schema validator.
 */
public class ManifestValidationException extends Exception {
    private final String detailedMessage;

    /**
     * Constructs a ManifestValidationException with a simple message.
     *
     * @param message the error message
     */
    public ManifestValidationException(String message) {
        super(message);
        this.detailedMessage = message;
    }

    /**
     * Constructs a ManifestValidationException with a message and detailed validation errors.
     *
     * @param message the error message
     * @param detailedMessage the detailed validation error messages
     */
    public ManifestValidationException(String message, String detailedMessage) {
        super(message);
        this.detailedMessage = detailedMessage;
    }

    /**
     * Constructs a ManifestValidationException with a message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public ManifestValidationException(String message, Throwable cause) {
        super(message, cause);
        this.detailedMessage = message;
    }

    /**
     * Constructs a ManifestValidationException with a message, detailed message, and cause.
     *
     * @param message the error message
     * @param detailedMessage the detailed validation error messages
     * @param cause the underlying cause
     */
    public ManifestValidationException(String message, String detailedMessage, Throwable cause) {
        super(message, cause);
        this.detailedMessage = detailedMessage;
    }

    /**
     * Returns the detailed validation error messages.
     *
     * @return detailed error messages from the schema validator
     */
    public String getDetailedMessage() {
        return detailedMessage;
    }
}

package ca.weblite.jdeploy.installer;

/**
 * A RuntimeException that carries both a technical message for logging and
 * a user-friendly HTML message for display in dialogs.
 *
 * This allows us to show helpful, formatted error messages to users while
 * still logging the technical details for debugging.
 */
public class UserLangRuntimeException extends RuntimeException {
    private final String userFriendlyMessage;

    /**
     * Creates a new UserLangRuntimeException with both technical and user-friendly messages.
     *
     * @param technicalMessage The technical error message for logging
     * @param userFriendlyMessage The user-friendly HTML message for display
     */
    public UserLangRuntimeException(String technicalMessage, String userFriendlyMessage) {
        super(technicalMessage);
        this.userFriendlyMessage = userFriendlyMessage;
    }

    /**
     * Creates a new UserLangRuntimeException with both technical and user-friendly messages,
     * and a cause.
     *
     * @param technicalMessage The technical error message for logging
     * @param userFriendlyMessage The user-friendly HTML message for display
     * @param cause The underlying cause of the exception
     */
    public UserLangRuntimeException(String technicalMessage, String userFriendlyMessage, Throwable cause) {
        super(technicalMessage, cause);
        this.userFriendlyMessage = userFriendlyMessage;
    }

    /**
     * Gets the user-friendly HTML message suitable for display in a dialog.
     *
     * @return The user-friendly message, or null if not set
     */
    public String getUserFriendlyMessage() {
        return userFriendlyMessage;
    }

    /**
     * Checks if this exception has a user-friendly message.
     *
     * @return true if a user-friendly message is available
     */
    public boolean hasUserFriendlyMessage() {
        return userFriendlyMessage != null && !userFriendlyMessage.isEmpty();
    }
}
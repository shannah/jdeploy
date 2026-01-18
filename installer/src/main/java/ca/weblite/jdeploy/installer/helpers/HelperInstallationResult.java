package ca.weblite.jdeploy.installer.helpers;

import java.io.File;

/**
 * Result of a Helper installation operation.
 *
 * This class encapsulates the outcome of installing the Helper application,
 * including paths to installed files and any error information.
 *
 * @author jDeploy Team
 */
public class HelperInstallationResult {

    private final File helperExecutable;
    private final File helperContextDirectory;
    private final boolean success;
    private final String errorMessage;

    private HelperInstallationResult(File helperExecutable, File helperContextDirectory,
                                     boolean success, String errorMessage) {
        this.helperExecutable = helperExecutable;
        this.helperContextDirectory = helperContextDirectory;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a successful installation result.
     *
     * @param helperExecutable The path to the installed Helper executable
     * @param helperContextDirectory The path to the installed .jdeploy-files directory
     * @return A successful HelperInstallationResult
     */
    public static HelperInstallationResult success(File helperExecutable, File helperContextDirectory) {
        return new HelperInstallationResult(helperExecutable, helperContextDirectory, true, null);
    }

    /**
     * Creates a failed installation result.
     *
     * @param errorMessage A description of what went wrong
     * @return A failed HelperInstallationResult
     */
    public static HelperInstallationResult failure(String errorMessage) {
        return new HelperInstallationResult(null, null, false, errorMessage);
    }

    /**
     * Creates a failed installation result with partial path information.
     *
     * @param errorMessage A description of what went wrong
     * @param helperExecutable The expected path to the Helper executable (may not exist)
     * @param helperContextDirectory The expected path to the context directory (may not exist)
     * @return A failed HelperInstallationResult
     */
    public static HelperInstallationResult failure(String errorMessage,
                                                   File helperExecutable,
                                                   File helperContextDirectory) {
        return new HelperInstallationResult(helperExecutable, helperContextDirectory, false, errorMessage);
    }

    /**
     * Returns the path to the installed Helper executable.
     *
     * @return The Helper executable path, or null if installation failed
     */
    public File getHelperExecutable() {
        return helperExecutable;
    }

    /**
     * Returns the path to the installed .jdeploy-files directory.
     *
     * @return The context directory path, or null if installation failed
     */
    public File getHelperContextDirectory() {
        return helperContextDirectory;
    }

    /**
     * Returns whether the installation was successful.
     *
     * @return true if installation succeeded, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the error message if installation failed.
     *
     * @return The error message, or null if installation succeeded
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        if (success) {
            return "HelperInstallationResult{success=true, " +
                   "helperExecutable=" + helperExecutable + ", " +
                   "helperContextDirectory=" + helperContextDirectory + "}";
        } else {
            return "HelperInstallationResult{success=false, " +
                   "errorMessage='" + errorMessage + "'}";
        }
    }
}

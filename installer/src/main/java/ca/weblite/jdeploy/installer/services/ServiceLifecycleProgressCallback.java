package ca.weblite.jdeploy.installer.services;

/**
 * Callback interface for reporting progress during service lifecycle operations.
 *
 * Allows UI to display meaningful feedback to users during potentially long-running
 * service management operations.
 *
 * @author Steve Hannah
 */
public interface ServiceLifecycleProgressCallback {

    /**
     * Updates the progress message displayed to the user.
     *
     * @param message The progress message (e.g., "Stopping services...")
     */
    void updateProgress(String message);

    /**
     * Reports a warning that occurred during service operations.
     * Installation continues despite warnings.
     *
     * @param message The warning message
     */
    void reportWarning(String message);

    /**
     * No-op implementation for when progress feedback isn't needed.
     */
    ServiceLifecycleProgressCallback SILENT = new ServiceLifecycleProgressCallback() {
        @Override
        public void updateProgress(String message) {
            // No-op
        }

        @Override
        public void reportWarning(String message) {
            // No-op
        }
    };
}

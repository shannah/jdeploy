package ca.weblite.jdeploy.installer.services;

/**
 * Console-based implementation of ServiceLifecycleProgressCallback for headless installations.
 * Prints progress messages to stdout in a clean format.
 *
 * @author Steve Hannah
 */
public class ConsoleServiceLifecycleProgressCallback implements ServiceLifecycleProgressCallback {

    @Override
    public void updateProgress(String message) {
        System.out.println(message);
    }

    @Override
    public void reportWarning(String message) {
        System.err.println("Warning: " + message);
    }
}

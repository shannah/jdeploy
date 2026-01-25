package ca.weblite.jdeploy.installer;

import org.apache.commons.io.FileUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

public class MainDebug {

    // Static fields for headless output suppression
    private static PrintStream originalOut;
    private static PrintStream originalErr;
    private static File logFile;
    private static PrintStream logStream;
    private static boolean headlessMode = false;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: MainDebug <code> [version] [install]");
            System.err.println("  code: The jDeploy bundle code");
            System.err.println("  version: The version (defaults to 'latest' if not specified)");
            System.err.println("  install: Pass 'install' as third arg for headless mode");
            System.exit(1);
        }

        String code = args[0];
        String version = args.length > 1 ? args[1] : "latest";

        // Check if headless mode is requested (install argument in args)
        for (int i = 2; i < args.length; i++) {
            if ("install".equals(args[i])) {
                headlessMode = true;
                break;
            }
        }

        // Set up output suppression for headless mode before any verbose operations
        if (headlessMode) {
            setupHeadlessOutputSuppression();
        }

        File tempDir = File.createTempFile("jdeploy-debug2-", "");
        tempDir.delete();
        tempDir.mkdirs();

        // If client4j.launcher.path is set and exists, copy it to temp directory
        // so that findInstallFilesDir() will find the downloaded .jdeploy-files
        // instead of any stale test fixtures near the original launcher path
        String originalLauncherPath = System.getProperty("client4j.launcher.path");
        if (originalLauncherPath != null) {
            File originalLauncher = new File(originalLauncherPath);
            if (originalLauncher.exists()) {
                File newLauncher = new File(tempDir, originalLauncher.getName());
                FileUtils.copyFile(originalLauncher, newLauncher);
                System.setProperty("client4j.launcher.path", newLauncher.getAbsolutePath());
                System.out.println("Copied launcher to: " + newLauncher.getAbsolutePath());
            }
        }

        File originalDir = new File(System.getProperty("user.dir"));
        System.setProperty("user.dir", tempDir.getAbsolutePath());

        try {
            File jdeployFilesDir = DefaultInstallationContext.downloadJDeployBundleForCode(code, version, null);
            File targetJDeployFilesDir = new File(tempDir, ".jdeploy-files");
            if (jdeployFilesDir != null && jdeployFilesDir.exists()) {
                FileUtils.copyDirectory(jdeployFilesDir, targetJDeployFilesDir);
                System.out.println("Downloaded .jdeploy-files to: " + targetJDeployFilesDir.getAbsolutePath());
            } else {
                System.err.println("Failed to download .jdeploy-files for code: " + code + ", version: " + version);
                System.exit(1);
            }
            String[] newArgs;
            if (args.length > 2) {
                newArgs = new String[args.length - 2];
                System.arraycopy(args, 2, newArgs, 0, newArgs.length);
            } else {
                newArgs = new String[0];
            }
            // Pass original streams to Main so it can output user-facing messages
            if (headlessMode) {
                Main.setOriginalStreams(originalOut, originalErr, logFile);
            }
            Main.main(newArgs);
        } finally {
            System.setProperty("user.dir", originalDir.getAbsolutePath());
        }
    }

    /**
     * Sets up output suppression for headless mode.
     * Redirects stdout and stderr to a log file and suppresses java.util.logging.
     */
    private static void setupHeadlessOutputSuppression() {
        // Store original streams
        originalOut = System.out;
        originalErr = System.err;

        // Suppress java.util.logging output
        java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
        java.util.logging.Handler[] handlers = rootLogger.getHandlers();
        for (java.util.logging.Handler handler : handlers) {
            handler.setLevel(java.util.logging.Level.OFF);
        }
        rootLogger.setLevel(java.util.logging.Level.OFF);

        // Redirect stdout and stderr to log file
        logFile = new File(
            System.getProperty("user.home") + File.separator + ".jdeploy" +
            File.separator + "log" + File.separator + "jdeploy-headless-install.log"
        );
        logFile.getParentFile().mkdirs();

        try {
            logStream = new PrintStream(new FileOutputStream(logFile));
            System.setOut(logStream);
            System.setErr(logStream);
        } catch (FileNotFoundException e) {
            originalErr.println("Warning: Could not create log file: " + e.getMessage());
        }
    }
}
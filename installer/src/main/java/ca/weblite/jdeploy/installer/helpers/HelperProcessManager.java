package ca.weblite.jdeploy.installer.helpers;

import ca.weblite.tools.io.MD5;
import ca.weblite.tools.platform.Platform;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages running Helper processes.
 *
 * Provides functionality to:
 * <ul>
 *   <li>Check if a Helper is currently running</li>
 *   <li>Attempt graceful termination of a Helper</li>
 *   <li>Force kill a Helper process</li>
 * </ul>
 *
 * Uses the same file locking mechanism as TrayIconLock to detect running instances.
 * Lock files are stored in ~/.jdeploy/locks/ and use the fully qualified package name
 * format: {sourceHash}.{packageName} (or just {packageName} if no source).
 *
 * @author jDeploy Team
 */
public class HelperProcessManager {

    private static final Logger logger = Logger.getLogger(HelperProcessManager.class.getName());
    private static final String LOCK_DIR_PATH = System.getProperty("user.home") +
            File.separator + ".jdeploy" + File.separator + "locks";

    private static final int PROCESS_KILL_TIMEOUT_SECONDS = 10;

    /**
     * Checks if a Helper is currently running for the given package.
     *
     * Uses file locking to detect if another process holds the Helper lock.
     * This is the same mechanism used by ServiceTrayController/TrayIconLock.
     *
     * @param packageName The package name (e.g., "@foo/bar")
     * @param source The package source (e.g., null for npm, GitHub URL for github)
     * @return true if the Helper appears to be running, false otherwise
     * @throws IllegalArgumentException if packageName is null or empty
     */
    public boolean isHelperRunning(String packageName, String source) {
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("packageName cannot be null or empty");
        }

        File lockFile = getLockFile(packageName, source);

        // If lock file doesn't exist, Helper is definitely not running
        if (!lockFile.exists()) {
            return false;
        }

        // Try to acquire the lock - if we can't, another process holds it
        RandomAccessFile raf = null;
        FileChannel channel = null;
        FileLock lock = null;

        try {
            raf = new RandomAccessFile(lockFile, "rw");
            channel = raf.getChannel();

            // Try non-blocking lock acquisition
            lock = channel.tryLock();

            if (lock != null) {
                // We acquired the lock - Helper is NOT running
                // Release immediately since we're just checking
                lock.release();
                return false;
            } else {
                // Could not acquire lock - Helper IS running
                return true;
            }

        } catch (OverlappingFileLockException e) {
            // Lock held by another thread in this JVM - Helper is running
            return true;

        } catch (IOException e) {
            // Error accessing lock file - assume not running
            logger.log(Level.WARNING, "Error checking Helper lock status", e);
            return false;

        } finally {
            // Clean up resources
            try {
                if (lock != null && lock.isValid()) {
                    lock.release();
                }
            } catch (IOException e) {
                logger.log(Level.FINE, "Error releasing lock during check", e);
            }

            try {
                if (channel != null && channel.isOpen()) {
                    channel.close();
                }
            } catch (IOException e) {
                logger.log(Level.FINE, "Error closing channel", e);
            }

            try {
                if (raf != null) {
                    raf.close();
                }
            } catch (IOException e) {
                logger.log(Level.FINE, "Error closing file", e);
            }
        }
    }

    /**
     * Attempts to terminate a running Helper.
     *
     * This method first attempts graceful termination by creating a shutdown signal file.
     * If the Helper doesn't exit within the timeout, it falls back to force kill.
     *
     * @param packageName The package name (e.g., "@foo/bar")
     * @param source The package source (e.g., null for npm, GitHub URL for github)
     * @param appName The application name for process killing (e.g., "My App")
     * @param timeoutMs Maximum time to wait for graceful termination in milliseconds
     * @return true if the Helper was terminated or wasn't running, false if termination failed
     * @throws IllegalArgumentException if packageName or appName is null or empty
     */
    public boolean terminateHelper(String packageName, String source, String appName, long timeoutMs) {
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("packageName cannot be null or empty");
        }
        if (appName == null || appName.trim().isEmpty()) {
            throw new IllegalArgumentException("appName cannot be null or empty");
        }

        if (!isHelperRunning(packageName, source)) {
            logger.info("Helper is not running for: " + packageName);
            return true;
        }

        logger.info("Attempting to terminate Helper for: " + packageName);

        // Try graceful termination first via shutdown signal file
        boolean gracefulSuccess = tryGracefulTermination(packageName, source, timeoutMs);

        if (gracefulSuccess) {
            logger.info("Helper terminated gracefully for: " + packageName);
            return true;
        }

        // Graceful termination failed or timed out - try force kill
        logger.info("Graceful termination failed, attempting force kill for: " + packageName);
        boolean forceKillSuccess = forceKillHelper(packageName, source, appName);

        if (forceKillSuccess) {
            logger.info("Helper force killed successfully for: " + packageName);
        } else {
            logger.warning("Failed to terminate Helper for: " + packageName);
        }

        return forceKillSuccess;
    }

    /**
     * Force kills the Helper process.
     *
     * Uses platform-specific commands:
     * <ul>
     *   <li>macOS: pkill -f "{appName} Helper"</li>
     *   <li>Linux: pkill -f "{appname}-helper"</li>
     *   <li>Windows: taskkill /F /IM {appname}-helper.exe</li>
     * </ul>
     *
     * @param packageName The package name for lock checking (e.g., "@foo/bar")
     * @param source The package source (e.g., null for npm, GitHub URL for github)
     * @param appName The application name for process killing (e.g., "My App")
     * @return true if the Helper was killed or wasn't running, false if kill failed
     * @throws IllegalArgumentException if packageName or appName is null or empty
     */
    public boolean forceKillHelper(String packageName, String source, String appName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("packageName cannot be null or empty");
        }
        if (appName == null || appName.trim().isEmpty()) {
            throw new IllegalArgumentException("appName cannot be null or empty");
        }

        // Check if running before attempting kill
        if (!isHelperRunning(packageName, source)) {
            return true;
        }

        try {
            if (Platform.getSystemPlatform().isMac()) {
                return forceKillMac(appName);
            } else if (Platform.getSystemPlatform().isWindows()) {
                return forceKillWindows(appName);
            } else {
                return forceKillLinux(appName);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error force killing Helper", e);
            return false;
        }
    }

    /**
     * Gets the lock file for a given package.
     *
     * Uses the fully qualified package name format: {sourceHash}.{sanitizedPackageName}.lock
     * or just {sanitizedPackageName}.lock if no source.
     *
     * @param packageName The package name
     * @param source The package source (may be null)
     * @return The lock file
     */
    File getLockFile(String packageName, String source) {
        String lockFileName = createLockFileName(packageName, source);
        return new File(LOCK_DIR_PATH, lockFileName);
    }

    /**
     * Creates a filesystem-safe lock file name from the package name and source.
     *
     * Format: {fullyQualifiedPackageName}.lock
     * Where fullyQualifiedPackageName is: {sourceHash}.{sanitizedPackageName} or just {sanitizedPackageName}
     *
     * @param packageName The package name
     * @param source The package source (may be null)
     * @return The sanitized lock file name
     */
    private String createLockFileName(String packageName, String source) {
        String fullyQualifiedName = createFullyQualifiedName(packageName, source);
        return fullyQualifiedName + ".lock";
    }

    /**
     * Creates a fully qualified package name from package name and source.
     *
     * Format: {sourceHash}.{sanitizedPackageName} or just {sanitizedPackageName} if no source.
     *
     * @param packageName The package name
     * @param source The package source (may be null)
     * @return The fully qualified name
     */
    private String createFullyQualifiedName(String packageName, String source) {
        String sanitized = sanitizeName(packageName);
        if (source != null && !source.isEmpty()) {
            String sourceHash = MD5.getMd5(source);
            return sourceHash + "." + sanitized;
        }
        return sanitized;
    }

    /**
     * Sanitizes a name to make it filesystem-safe.
     *
     * @param name The name to sanitize
     * @return The sanitized name
     */
    private String sanitizeName(String name) {
        return name.toLowerCase()
            .replace(" ", "-")
            .replace("@", "")
            .replace("/", "-")
            .replace("\\", "-")
            .replace(":", "-")
            .replace("*", "-")
            .replace("?", "-")
            .replace("\"", "-")
            .replace("<", "-")
            .replace(">", "-")
            .replace("|", "-");
    }

    /**
     * Attempts graceful termination by creating a shutdown signal file.
     *
     * The Helper should periodically check for this file and exit gracefully.
     * Note: This requires the Helper to implement shutdown signal checking.
     *
     * @param packageName The package name
     * @param source The package source (may be null)
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if Helper exited within timeout, false otherwise
     */
    private boolean tryGracefulTermination(String packageName, String source, long timeoutMs) {
        // Create shutdown signal file
        File shutdownSignal = getShutdownSignalFile(packageName, source);

        try {
            // Ensure parent directory exists
            File parentDir = shutdownSignal.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }

            // Create the shutdown signal file
            shutdownSignal.createNewFile();

            // Wait for Helper to exit
            long startTime = System.currentTimeMillis();
            long elapsed = 0;

            while (elapsed < timeoutMs) {
                if (!isHelperRunning(packageName, source)) {
                    // Helper has exited
                    shutdownSignal.delete();
                    return true;
                }

                // Sleep a bit before checking again
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                elapsed = System.currentTimeMillis() - startTime;
            }

            // Timeout reached - clean up signal file
            shutdownSignal.delete();
            return false;

        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to create shutdown signal file", e);
            return false;
        }
    }

    /**
     * Gets the shutdown signal file path for a package.
     *
     * @param packageName The package name
     * @param source The package source (may be null)
     * @return The shutdown signal file
     */
    File getShutdownSignalFile(String packageName, String source) {
        String fullyQualifiedName = createFullyQualifiedName(packageName, source);
        return new File(LOCK_DIR_PATH, fullyQualifiedName + ".shutdown");
    }

    /**
     * Force kills the Helper on macOS using pkill.
     *
     * @param appName The application name
     * @return true if killed successfully
     */
    private boolean forceKillMac(String appName) throws IOException, InterruptedException {
        // On macOS, the Helper is "{AppName} Helper.app"
        // The process name would contain the app name
        String helperName = appName + " Helper";

        // Use pkill with pattern matching
        ProcessBuilder pb = new ProcessBuilder("pkill", "-f", helperName);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        boolean completed = process.waitFor(PROCESS_KILL_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!completed) {
            process.destroyForcibly();
            return false;
        }

        // pkill returns 0 if processes were killed, 1 if no processes matched
        // Both are acceptable outcomes
        int exitCode = process.exitValue();
        return exitCode == 0 || exitCode == 1;
    }

    /**
     * Force kills the Helper on Windows using taskkill.
     *
     * @param appName The application name
     * @return true if killed successfully
     */
    private boolean forceKillWindows(String appName) throws IOException, InterruptedException {
        // On Windows, the Helper is "{appname}-helper.exe"
        String helperExeName = HelperPaths.deriveHelperName(appName) + ".exe";

        // Use taskkill with force flag
        ProcessBuilder pb = new ProcessBuilder("taskkill", "/F", "/IM", helperExeName);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        boolean completed = process.waitFor(PROCESS_KILL_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!completed) {
            process.destroyForcibly();
            return false;
        }

        // taskkill returns 0 if killed, 128 if process not found
        int exitCode = process.exitValue();
        return exitCode == 0 || exitCode == 128;
    }

    /**
     * Force kills the Helper on Linux using pkill.
     *
     * @param appName The application name
     * @return true if killed successfully
     */
    private boolean forceKillLinux(String appName) throws IOException, InterruptedException {
        // On Linux, the Helper is "{appname}-helper"
        String helperName = HelperPaths.deriveHelperName(appName);

        // Use pkill with pattern matching
        ProcessBuilder pb = new ProcessBuilder("pkill", "-f", helperName);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        boolean completed = process.waitFor(PROCESS_KILL_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!completed) {
            process.destroyForcibly();
            return false;
        }

        // pkill returns 0 if processes were killed, 1 if no processes matched
        int exitCode = process.exitValue();
        return exitCode == 0 || exitCode == 1;
    }
}

package ca.weblite.jdeploy.installer.tray;

import ca.weblite.tools.io.MD5;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages exclusive lock for a single tray icon instance per application.
 *
 * Uses Java NIO FileLock to ensure only one tray icon is displayed for a given
 * application (identified by package name + source). The lock is automatically
 * released when the JVM exits, even on crash.
 *
 * Lock files are stored in ~/.jdeploy/locks/ and use the fully qualified package
 * name format: {sourceHash}.{sanitizedPackageName}.lock (or just {sanitizedPackageName}.lock
 * if no source).
 *
 * @author Steve Hannah
 */
public class TrayIconLock {

    private static final Logger logger = Logger.getLogger(TrayIconLock.class.getName());
    private static final String LOCK_DIR_PATH = System.getProperty("user.home") +
            File.separator + ".jdeploy" + File.separator + "locks";

    private final String packageName;
    private final String source;
    private final File lockFile;

    private RandomAccessFile randomAccessFile;
    private FileChannel fileChannel;
    private FileLock fileLock;

    /**
     * Creates a new tray icon lock for the given package.
     *
     * @param packageName The package name (e.g., "@foo/bar")
     * @param source The package source (e.g., null for npm, GitHub URL for github)
     */
    public TrayIconLock(String packageName, String source) {
        if (packageName == null || packageName.isEmpty()) {
            throw new IllegalArgumentException("packageName cannot be null or empty");
        }

        this.packageName = packageName;
        this.source = source;
        this.lockFile = createLockFile();
    }

    /**
     * Attempts to acquire the exclusive lock.
     *
     * @return true if the lock was acquired, false if another instance already holds it
     */
    public boolean tryAcquire() {
        // Already locked by this instance
        if (isLocked()) {
            logger.warning("Lock already acquired by this instance");
            return true;
        }

        try {
            // Create lock directory if it doesn't exist
            File lockDir = lockFile.getParentFile();
            if (!lockDir.exists()) {
                lockDir.mkdirs();
            }

            // Open file for writing
            randomAccessFile = new RandomAccessFile(lockFile, "rw");
            fileChannel = randomAccessFile.getChannel();

            // Try to acquire exclusive lock (non-blocking)
            fileLock = fileChannel.tryLock();

            if (fileLock != null) {
                logger.info("Successfully acquired tray icon lock: " + lockFile.getName());
                return true;
            } else {
                logger.info("Failed to acquire tray icon lock (another instance running): " + lockFile.getName());
                cleanup();
                return false;
            }

        } catch (OverlappingFileLockException e) {
            // Lock already held by another thread in this JVM
            logger.info("Tray icon lock already held by another thread");
            cleanup();
            return false;

        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to acquire tray icon lock due to I/O error", e);
            cleanup();
            return false;
        }
    }

    /**
     * Releases the lock and deletes the lock file.
     *
     * Safe to call multiple times or if lock was never acquired.
     */
    public void release() {
        if (!isLocked()) {
            return;
        }

        logger.info("Releasing tray icon lock: " + lockFile.getName());

        try {
            if (fileLock != null) {
                fileLock.release();
                fileLock = null;
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error releasing file lock", e);
        }

        cleanup();

        // Delete lock file on clean shutdown
        if (lockFile.exists()) {
            boolean deleted = lockFile.delete();
            if (!deleted) {
                logger.warning("Failed to delete lock file: " + lockFile.getAbsolutePath());
            }
        }
    }

    /**
     * Checks if this instance currently holds the lock.
     *
     * @return true if the lock is held by this instance
     */
    public boolean isLocked() {
        return fileLock != null && fileLock.isValid();
    }

    /**
     * Cleans up file channel and random access file without deleting the lock file.
     */
    private void cleanup() {
        try {
            if (fileChannel != null && fileChannel.isOpen()) {
                fileChannel.close();
                fileChannel = null;
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error closing file channel", e);
        }

        try {
            if (randomAccessFile != null) {
                randomAccessFile.close();
                randomAccessFile = null;
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error closing random access file", e);
        }
    }

    /**
     * Creates the lock file for this package.
     *
     * @return The lock file
     */
    private File createLockFile() {
        String lockFileName = createLockFileName();
        return new File(LOCK_DIR_PATH, lockFileName);
    }

    /**
     * Creates a filesystem-safe lock file name from package name and source.
     *
     * Uses the fully qualified package name format:
     * - {sourceHash}.{sanitizedPackageName}.lock if source is provided
     * - {sanitizedPackageName}.lock if no source (npm package)
     *
     * Examples:
     * - npm package "@foo/bar" -> "foo-bar.lock"
     * - github package "@foo/bar" from "https://github.com/user/repo" -> "{md5hash}.foo-bar.lock"
     *
     * @return The sanitized lock file name
     */
    private String createLockFileName() {
        String fullyQualifiedName = createFullyQualifiedName();
        return fullyQualifiedName + ".lock";
    }

    /**
     * Creates a fully qualified package name from package name and source.
     *
     * Format: {sourceHash}.{sanitizedPackageName} or just {sanitizedPackageName} if no source.
     *
     * @return The fully qualified name
     */
    private String createFullyQualifiedName() {
        String sanitized = sanitizePackageName(packageName);
        if (source != null && !source.isEmpty()) {
            String sourceHash = MD5.getMd5(source);
            return sourceHash + "." + sanitized;
        }
        return sanitized;
    }

    /**
     * Sanitizes package name to make it filesystem-safe.
     *
     * Removes or replaces characters that are invalid in filenames:
     * - @ -> (removed)
     * - / -> -
     * - \ -> -
     * - : -> -
     * - * -> -
     * - ? -> -
     * - " -> -
     * - < -> -
     * - > -> -
     * - | -> -
     * - space -> -
     *
     * Also converts to lowercase for consistency.
     *
     * @param name The package name to sanitize
     * @return The sanitized name
     */
    private String sanitizePackageName(String name) {
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
     * Gets the lock file path for debugging purposes.
     *
     * @return The absolute path to the lock file
     */
    public String getLockFilePath() {
        return lockFile.getAbsolutePath();
    }
}

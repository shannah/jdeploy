package ca.weblite.jdeploy.installer.helpers;

import ca.weblite.jdeploy.installer.logging.InstallationLogger;
import ca.weblite.tools.platform.Platform;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/**
 * Service class that handles copying the installer bundle/executable to the Helper location.
 *
 * This class uses platform-appropriate methods:
 * <ul>
 *   <li>macOS: Uses the {@code ditto} command to preserve symlinks, resource forks, and code signing</li>
 *   <li>Windows: Uses Java file copy with recursive directory traversal</li>
 *   <li>Linux: Uses Java file copy with recursive directory traversal</li>
 * </ul>
 *
 * @author jDeploy Team
 */
public class HelperCopyService {

    private static final int COPY_TIMEOUT_SECONDS = 120;
    private static final int BUFFER_SIZE = 8192;

    private final InstallationLogger logger;

    /**
     * Creates a new HelperCopyService.
     *
     * @param logger The installation logger for recording operations
     */
    public HelperCopyService(InstallationLogger logger) {
        this.logger = logger;
    }

    /**
     * Copies the installer to the Helper location using platform-appropriate methods.
     *
     * Platform-specific behavior:
     * <ul>
     *   <li>macOS: Uses {@code ditto} command to preserve symlinks, resource forks, and code signing.
     *       This is essential for maintaining the integrity of .app bundles.</li>
     *   <li>Windows: Uses recursive Java file copy</li>
     *   <li>Linux: Uses recursive Java file copy</li>
     * </ul>
     *
     * @param source The source file or directory to copy (installer bundle or executable)
     * @param destination The destination path for the Helper
     * @throws IOException if the copy operation fails
     * @throws IllegalArgumentException if source is null, doesn't exist, or destination is null
     */
    public void copyInstaller(File source, File destination) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("Source cannot be null");
        }
        if (!source.exists()) {
            throw new IllegalArgumentException("Source does not exist: " + source.getAbsolutePath());
        }
        if (destination == null) {
            throw new IllegalArgumentException("Destination cannot be null");
        }

        logSection("Copying Installer to Helper Location");
        logInfo("Source: " + source.getAbsolutePath());
        logInfo("Destination: " + destination.getAbsolutePath());

        // Ensure parent directory exists
        File parentDir = destination.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Failed to create destination directory: " + parentDir.getAbsolutePath());
            }
            logDirectoryCreated(parentDir);
        }

        // Remove existing destination if present
        if (destination.exists()) {
            logInfo("Removing existing destination: " + destination.getAbsolutePath());
            deleteRecursively(destination);
        }

        if (Platform.getSystemPlatform().isMac()) {
            copyWithDitto(source, destination);
        } else {
            copyRecursively(source, destination);
        }

        logInfo("Copy completed successfully");
    }

    /**
     * Copies using the macOS {@code ditto} command.
     *
     * The ditto command preserves:
     * <ul>
     *   <li>Symbolic links</li>
     *   <li>Resource forks and HFS metadata</li>
     *   <li>Code signing information</li>
     *   <li>Extended attributes</li>
     * </ul>
     *
     * @param source The source .app bundle or file
     * @param destination The destination path
     * @throws IOException if the ditto command fails
     */
    private void copyWithDitto(File source, File destination) throws IOException {
        logInfo("Using ditto for macOS bundle copy");

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "ditto",
                source.getAbsolutePath(),
                destination.getAbsolutePath()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Consume output to prevent blocking
            StringBuilder output = new StringBuilder();
            Thread outputConsumer = new Thread(() -> {
                try {
                    InputStream is = process.getInputStream();
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        output.append(new String(buffer, 0, bytesRead));
                    }
                } catch (IOException e) {
                    // Ignore - process may have been destroyed
                }
            });
            outputConsumer.setDaemon(true);
            outputConsumer.start();

            boolean completed = process.waitFor(COPY_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                throw new IOException("ditto command timed out after " + COPY_TIMEOUT_SECONDS + " seconds");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errorMsg = output.toString().trim();
                if (errorMsg.isEmpty()) {
                    errorMsg = "Unknown error";
                }
                throw new IOException("ditto command failed with exit code " + exitCode + ": " + errorMsg);
            }

            logFileCopied(source, destination);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ditto command was interrupted", e);
        }
    }

    /**
     * Recursively copies a file or directory using Java I/O.
     *
     * @param source The source file or directory
     * @param destination The destination path
     * @throws IOException if any copy operation fails
     */
    private void copyRecursively(File source, File destination) throws IOException {
        if (source.isDirectory()) {
            if (!destination.exists() && !destination.mkdirs()) {
                throw new IOException("Failed to create directory: " + destination.getAbsolutePath());
            }
            logDirectoryCreated(destination);

            String[] children = source.list();
            if (children != null) {
                for (String child : children) {
                    copyRecursively(new File(source, child), new File(destination, child));
                }
            }
        } else {
            copyFile(source, destination);
        }
    }

    /**
     * Copies a single file.
     *
     * @param source The source file
     * @param destination The destination file
     * @throws IOException if the copy fails
     */
    private void copyFile(File source, File destination) throws IOException {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        // Preserve executable permission on Unix-like systems
        if (!Platform.getSystemPlatform().isWindows() && source.canExecute()) {
            destination.setExecutable(true);
        }

        logFileCopied(source, destination);
    }

    /**
     * Recursively deletes a file or directory.
     *
     * @param file The file or directory to delete
     * @throws IOException if deletion fails
     */
    private void deleteRecursively(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            throw new IOException("Failed to delete: " + file.getAbsolutePath());
        }
    }

    // Logging helper methods

    private void logSection(String sectionName) {
        if (logger != null) {
            logger.logSection(sectionName);
        }
    }

    private void logInfo(String message) {
        if (logger != null) {
            logger.logInfo(message);
        }
    }

    private void logDirectoryCreated(File dir) {
        if (logger != null) {
            logger.logDirectoryOperation(
                InstallationLogger.DirectoryOperation.CREATED,
                dir.getAbsolutePath()
            );
        }
    }

    private void logFileCopied(File source, File destination) {
        if (logger != null) {
            logger.logFileOperation(
                InstallationLogger.FileOperation.COPIED,
                destination.getAbsolutePath(),
                "from " + source.getAbsolutePath()
            );
        }
    }
}

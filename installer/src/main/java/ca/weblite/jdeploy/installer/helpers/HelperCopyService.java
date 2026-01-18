package ca.weblite.jdeploy.installer.helpers;

import ca.weblite.jdeploy.installer.logging.InstallationLogger;
import ca.weblite.tools.platform.Platform;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;

/**
 * Service class that handles copying the installer bundle/executable to the Helper location.
 *
 * This class uses platform-appropriate methods:
 * <ul>
 *   <li>macOS: Uses the {@code ditto} command to preserve symlinks, resource forks, and code signing</li>
 *   <li>Windows: Uses {@code Files.copy()} with {@code REPLACE_EXISTING}</li>
 *   <li>Linux: Uses {@code Files.copy()} with {@code REPLACE_EXISTING} and {@code COPY_ATTRIBUTES},
 *       then sets executable permission</li>
 * </ul>
 *
 * @author jDeploy Team
 */
public class HelperCopyService {

    private static final int DITTO_TIMEOUT_SECONDS = 120;

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
     *   <li>Windows: Uses {@code Files.copy()} with {@code REPLACE_EXISTING}</li>
     *   <li>Linux: Uses {@code Files.copy()} with {@code REPLACE_EXISTING} and {@code COPY_ATTRIBUTES},
     *       then sets executable permission</li>
     * </ul>
     *
     * @param source The source file or directory to copy (installer bundle or executable)
     * @param destination The destination path for the Helper
     * @throws IOException if the copy operation fails
     * @throws IllegalArgumentException if source is null, doesn't exist, or destination is null
     */
    public void copyInstaller(File source, File destination) throws IOException {
        validateCopyArguments(source, destination);

        logSection("Copying Installer to Helper Location");
        logInfo("Source: " + source.getAbsolutePath());
        logInfo("Destination: " + destination.getAbsolutePath());

        // Ensure parent directory exists
        ensureParentDirectoryExists(destination);

        // Remove existing destination if present
        if (destination.exists()) {
            logInfo("Removing existing destination: " + destination.getAbsolutePath());
            deleteRecursively(destination);
        }

        Platform platform = Platform.getSystemPlatform();
        if (platform.isMac()) {
            executeDitto(source, destination);
        } else if (platform.isWindows()) {
            copyForWindows(source, destination);
        } else {
            copyForLinux(source, destination);
        }

        logInfo("Installer copy completed successfully");
    }

    /**
     * Copies the .jdeploy-files context directory to the destination.
     *
     * This method performs a recursive directory copy and works the same on all platforms.
     * It copies all files and subdirectories while preserving the directory structure.
     *
     * @param source The source .jdeploy-files directory
     * @param destination The destination directory
     * @throws IOException if the copy operation fails
     * @throws IllegalArgumentException if source is null, doesn't exist, is not a directory, or destination is null
     */
    public void copyContextDirectory(File source, File destination) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("Source cannot be null");
        }
        if (!source.exists()) {
            throw new IllegalArgumentException("Source does not exist: " + source.getAbsolutePath());
        }
        if (!source.isDirectory()) {
            throw new IllegalArgumentException("Source must be a directory: " + source.getAbsolutePath());
        }
        if (destination == null) {
            throw new IllegalArgumentException("Destination cannot be null");
        }

        logSection("Copying Context Directory");
        logInfo("Source: " + source.getAbsolutePath());
        logInfo("Destination: " + destination.getAbsolutePath());

        // Remove existing destination if present
        if (destination.exists()) {
            logInfo("Removing existing destination: " + destination.getAbsolutePath());
            deleteRecursively(destination);
        }

        copyDirectoryRecursively(source, destination);

        logInfo("Context directory copy completed successfully");
    }

    /**
     * Executes the macOS {@code ditto} command to copy files while preserving
     * symlinks, resource forks, and code signing.
     *
     * @param source The source file or directory
     * @param destination The destination path
     * @throws IOException if the ditto command fails or times out
     */
    private void executeDitto(File source, File destination) throws IOException {
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

            boolean completed = process.waitFor(DITTO_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                String errorMessage = "ditto command timed out after " + DITTO_TIMEOUT_SECONDS + " seconds";
                logError(errorMessage, null);
                throw new IOException(errorMessage);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errorMsg = output.toString().trim();
                if (errorMsg.isEmpty()) {
                    errorMsg = "Unknown error";
                }
                String errorMessage = "ditto command failed with exit code " + exitCode + ": " + errorMsg;
                logError(errorMessage, null);
                throw new IOException(errorMessage);
            }

            logFileCopied(source, destination);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String errorMessage = "ditto command was interrupted";
            logError(errorMessage, e);
            throw new IOException(errorMessage, e);
        }
    }

    /**
     * Copies a file or directory for Windows using Files.copy() with REPLACE_EXISTING.
     *
     * @param source The source file or directory
     * @param destination The destination path
     * @throws IOException if the copy fails
     */
    private void copyForWindows(File source, File destination) throws IOException {
        logInfo("Using Files.copy for Windows");

        if (source.isDirectory()) {
            copyDirectoryRecursively(source, destination);
        } else {
            try {
                Files.copy(
                    source.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                );
                logFileCopied(source, destination);
            } catch (IOException e) {
                String errorMessage = "Failed to copy file from " + source.getAbsolutePath() +
                                      " to " + destination.getAbsolutePath();
                logError(errorMessage, e);
                throw new IOException(errorMessage + ": " + e.getMessage(), e);
            }
        }
    }

    /**
     * Copies a file or directory for Linux using Files.copy() with REPLACE_EXISTING
     * and COPY_ATTRIBUTES, then sets executable permission if needed.
     *
     * @param source The source file or directory
     * @param destination The destination path
     * @throws IOException if the copy fails
     */
    private void copyForLinux(File source, File destination) throws IOException {
        logInfo("Using Files.copy for Linux");

        if (source.isDirectory()) {
            copyDirectoryRecursively(source, destination);
        } else {
            try {
                Files.copy(
                    source.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES
                );

                // Ensure executable permission is set if source was executable
                if (source.canExecute()) {
                    if (!destination.setExecutable(true)) {
                        logInfo("Warning: Could not set executable permission on " + destination.getAbsolutePath());
                    }
                }

                logFileCopied(source, destination);
            } catch (IOException e) {
                String errorMessage = "Failed to copy file from " + source.getAbsolutePath() +
                                      " to " + destination.getAbsolutePath();
                logError(errorMessage, e);
                throw new IOException(errorMessage + ": " + e.getMessage(), e);
            }
        }
    }

    /**
     * Recursively copies a directory and all its contents.
     *
     * @param source The source directory
     * @param destination The destination directory
     * @throws IOException if the copy fails
     */
    private void copyDirectoryRecursively(File source, File destination) throws IOException {
        Path sourcePath = source.toPath();
        Path destPath = destination.toPath();

        Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = destPath.resolve(sourcePath.relativize(dir));
                try {
                    if (!Files.exists(targetDir)) {
                        Files.createDirectories(targetDir);
                        logDirectoryCreated(targetDir.toFile());
                    }
                } catch (IOException e) {
                    String errorMessage = "Failed to create directory: " + targetDir.toAbsolutePath();
                    logError(errorMessage, e);
                    throw new IOException(errorMessage + ": " + e.getMessage(), e);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = destPath.resolve(sourcePath.relativize(file));

                CopyOption[] options;
                if (Platform.getSystemPlatform().isWindows()) {
                    options = new CopyOption[] { StandardCopyOption.REPLACE_EXISTING };
                } else {
                    options = new CopyOption[] {
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES
                    };
                }

                try {
                    Files.copy(file, targetFile, options);

                    // Set executable permission on Linux if source was executable
                    if (Platform.getSystemPlatform().isLinux() && Files.isExecutable(file)) {
                        targetFile.toFile().setExecutable(true);
                    }

                    logFileCopied(file.toFile(), targetFile.toFile());
                } catch (IOException e) {
                    String errorMessage = "Failed to copy file from " + file.toAbsolutePath() +
                                          " to " + targetFile.toAbsolutePath();
                    logError(errorMessage, e);
                    throw new IOException(errorMessage + ": " + e.getMessage(), e);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                String errorMessage = "Failed to access file during copy: " + file.toAbsolutePath();
                logError(errorMessage, exc);
                throw new IOException(errorMessage + ": " + exc.getMessage(), exc);
            }
        });
    }

    /**
     * Validates copy arguments.
     */
    private void validateCopyArguments(File source, File destination) {
        if (source == null) {
            throw new IllegalArgumentException("Source cannot be null");
        }
        if (!source.exists()) {
            throw new IllegalArgumentException("Source does not exist: " + source.getAbsolutePath());
        }
        if (destination == null) {
            throw new IllegalArgumentException("Destination cannot be null");
        }
    }

    /**
     * Ensures the parent directory of the destination exists.
     */
    private void ensureParentDirectoryExists(File destination) throws IOException {
        File parentDir = destination.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                String errorMessage = "Failed to create destination directory: " + parentDir.getAbsolutePath();
                logError(errorMessage, null);
                throw new IOException(errorMessage);
            }
            logDirectoryCreated(parentDir);
        }
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
            String errorMessage = "Failed to delete: " + file.getAbsolutePath();
            logError(errorMessage, null);
            throw new IOException(errorMessage);
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

    private void logError(String message, Throwable e) {
        if (logger != null) {
            logger.logError(message, e);
        }
    }
}

package ca.weblite.jdeploy.installer.logging;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Comprehensive logger for installation and uninstallation operations.
 * Records all file operations, registry changes, and other installation activities
 * to a dedicated log file for debugging and auditing purposes.
 *
 * Log files are stored at:
 * - Installs: ~/.jdeploy/log/jdeploy-installer/installs/{packageName}-{timestamp}.log
 * - Uninstalls: ~/.jdeploy/log/jdeploy-installer/uninstalls/{packageName}-{timestamp}.log
 */
public class InstallationLogger implements Closeable {

    public enum OperationType {
        INSTALL,
        UNINSTALL
    }

    public enum FileOperation {
        CREATED,
        DELETED,
        OVERWRITTEN,
        SKIPPED_EXISTS,
        SKIPPED_COLLISION,
        COPIED,
        MOVED,
        MODIFIED,
        FAILED
    }

    public enum RegistryOperation {
        KEY_CREATED,
        KEY_DELETED,
        VALUE_SET,
        VALUE_DELETED,
        PATH_ADDED,
        PATH_REMOVED,
        FAILED
    }

    public enum DirectoryOperation {
        CREATED,
        DELETED,
        SKIPPED_EXISTS,
        SKIPPED_NOT_EMPTY,
        FAILED
    }

    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private static final SimpleDateFormat FILE_TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMdd-HHmmss");

    private final PrintWriter writer;
    private final File logFile;
    private final String packageName;
    private final OperationType operationType;
    private final long startTime;

    private int filesCreated = 0;
    private int filesDeleted = 0;
    private int filesOverwritten = 0;
    private int filesSkipped = 0;
    private int filesFailed = 0;
    private int registryOperations = 0;
    private int directoriesCreated = 0;
    private int directoriesDeleted = 0;

    /**
     * Creates a new installation logger.
     *
     * @param packageName the fully qualified package name
     * @param operationType whether this is an install or uninstall operation
     * @throws IOException if the log file cannot be created
     */
    public InstallationLogger(String packageName, OperationType operationType) throws IOException {
        this.packageName = packageName;
        this.operationType = operationType;
        this.startTime = System.currentTimeMillis();

        String userHome = System.getProperty("user.home");
        String subDir = operationType == OperationType.INSTALL ? "installs" : "uninstalls";
        File logDir = new File(userHome, ".jdeploy" + File.separator + "log" +
                File.separator + "jdeploy-installer" + File.separator + subDir);

        if (!logDir.exists() && !logDir.mkdirs()) {
            throw new IOException("Failed to create log directory: " + logDir.getAbsolutePath());
        }

        String timestamp = FILE_TIMESTAMP_FORMAT.format(new Date());
        String safePackageName = packageName.replaceAll("[^a-zA-Z0-9.-]", "_");
        this.logFile = new File(logDir, safePackageName + "-" + timestamp + ".log");

        this.writer = new PrintWriter(new BufferedWriter(new FileWriter(logFile)), true);

        writeHeader();
    }

    private void writeHeader() {
        writeLine("================================================================================");
        writeLine(operationType == OperationType.INSTALL ? "INSTALLATION LOG" : "UNINSTALLATION LOG");
        writeLine("================================================================================");
        writeLine("Package: " + packageName);
        writeLine("Started: " + TIMESTAMP_FORMAT.format(new Date(startTime)));
        writeLine("Log File: " + logFile.getAbsolutePath());
        writeLine("--------------------------------------------------------------------------------");
        writeLine("");
    }

    private synchronized void writeLine(String message) {
        writer.println(message);
    }

    private synchronized void writeTimestampedLine(String message) {
        String timestamp = TIMESTAMP_FORMAT.format(new Date());
        writer.println("[" + timestamp + "] " + message);
    }

    /**
     * Logs the start of a major section.
     */
    public void logSection(String sectionName) {
        writeLine("");
        writeLine("--- " + sectionName + " ---");
    }

    /**
     * Logs a file operation.
     */
    public void logFileOperation(FileOperation operation, String filePath, String details) {
        String prefix = getFileOperationPrefix(operation);
        String message = prefix + " " + filePath;
        if (details != null && !details.isEmpty()) {
            message += " (" + details + ")";
        }
        writeTimestampedLine(message);

        // Update counters
        switch (operation) {
            case CREATED:
            case COPIED:
                filesCreated++;
                break;
            case DELETED:
                filesDeleted++;
                break;
            case OVERWRITTEN:
            case MODIFIED:
                filesOverwritten++;
                break;
            case SKIPPED_EXISTS:
            case SKIPPED_COLLISION:
                filesSkipped++;
                break;
            case FAILED:
                filesFailed++;
                break;
        }
    }

    /**
     * Logs a file operation without additional details.
     */
    public void logFileOperation(FileOperation operation, String filePath) {
        logFileOperation(operation, filePath, null);
    }

    private String getFileOperationPrefix(FileOperation operation) {
        switch (operation) {
            case CREATED: return "[FILE CREATED]";
            case DELETED: return "[FILE DELETED]";
            case OVERWRITTEN: return "[FILE OVERWRITTEN]";
            case SKIPPED_EXISTS: return "[FILE SKIPPED - EXISTS]";
            case SKIPPED_COLLISION: return "[FILE SKIPPED - COLLISION]";
            case COPIED: return "[FILE COPIED]";
            case MOVED: return "[FILE MOVED]";
            case MODIFIED: return "[FILE MODIFIED]";
            case FAILED: return "[FILE FAILED]";
            default: return "[FILE]";
        }
    }

    /**
     * Logs a directory operation.
     */
    public void logDirectoryOperation(DirectoryOperation operation, String dirPath, String details) {
        String prefix = getDirectoryOperationPrefix(operation);
        String message = prefix + " " + dirPath;
        if (details != null && !details.isEmpty()) {
            message += " (" + details + ")";
        }
        writeTimestampedLine(message);

        // Update counters
        switch (operation) {
            case CREATED:
                directoriesCreated++;
                break;
            case DELETED:
                directoriesDeleted++;
                break;
        }
    }

    /**
     * Logs a directory operation without additional details.
     */
    public void logDirectoryOperation(DirectoryOperation operation, String dirPath) {
        logDirectoryOperation(operation, dirPath, null);
    }

    private String getDirectoryOperationPrefix(DirectoryOperation operation) {
        switch (operation) {
            case CREATED: return "[DIR CREATED]";
            case DELETED: return "[DIR DELETED]";
            case SKIPPED_EXISTS: return "[DIR SKIPPED - EXISTS]";
            case SKIPPED_NOT_EMPTY: return "[DIR SKIPPED - NOT EMPTY]";
            case FAILED: return "[DIR FAILED]";
            default: return "[DIR]";
        }
    }

    /**
     * Logs a registry operation.
     */
    public void logRegistryOperation(RegistryOperation operation, String registryPath, String details) {
        String prefix = getRegistryOperationPrefix(operation);
        String message = prefix + " " + registryPath;
        if (details != null && !details.isEmpty()) {
            message += " (" + details + ")";
        }
        writeTimestampedLine(message);

        if (operation != RegistryOperation.FAILED) {
            registryOperations++;
        }
    }

    /**
     * Logs a registry operation without additional details.
     */
    public void logRegistryOperation(RegistryOperation operation, String registryPath) {
        logRegistryOperation(operation, registryPath, null);
    }

    private String getRegistryOperationPrefix(RegistryOperation operation) {
        switch (operation) {
            case KEY_CREATED: return "[REGISTRY KEY CREATED]";
            case KEY_DELETED: return "[REGISTRY KEY DELETED]";
            case VALUE_SET: return "[REGISTRY VALUE SET]";
            case VALUE_DELETED: return "[REGISTRY VALUE DELETED]";
            case PATH_ADDED: return "[REGISTRY PATH ADDED]";
            case PATH_REMOVED: return "[REGISTRY PATH REMOVED]";
            case FAILED: return "[REGISTRY FAILED]";
            default: return "[REGISTRY]";
        }
    }

    /**
     * Logs a CLI command wrapper operation.
     */
    public void logCliCommand(String commandName, FileOperation operation, String wrapperPath, String details) {
        String prefix = "[CLI COMMAND " + operation.name() + "]";
        String message = prefix + " " + commandName + " -> " + wrapperPath;
        if (details != null && !details.isEmpty()) {
            message += " (" + details + ")";
        }
        writeTimestampedLine(message);
    }

    /**
     * Logs a shortcut operation.
     */
    public void logShortcut(FileOperation operation, String shortcutPath, String targetPath) {
        String prefix = "[SHORTCUT " + operation.name() + "]";
        String message = prefix + " " + shortcutPath;
        if (targetPath != null) {
            message += " -> " + targetPath;
        }
        writeTimestampedLine(message);
    }

    /**
     * Logs an informational message.
     */
    public void logInfo(String message) {
        writeTimestampedLine("[INFO] " + message);
    }

    /**
     * Logs a warning message.
     */
    public void logWarning(String message) {
        writeTimestampedLine("[WARNING] " + message);
    }

    /**
     * Logs an error message.
     */
    public void logError(String message) {
        writeTimestampedLine("[ERROR] " + message);
    }

    /**
     * Logs an error with exception details.
     */
    public void logError(String message, Throwable ex) {
        writeTimestampedLine("[ERROR] " + message + ": " + ex.getMessage());
        ex.printStackTrace(writer);
    }

    /**
     * Logs PATH environment variable changes.
     */
    public void logPathChange(boolean added, String pathEntry, String context) {
        String action = added ? "ADDED TO" : "REMOVED FROM";
        String message = "[PATH " + action + "] " + pathEntry;
        if (context != null) {
            message += " (" + context + ")";
        }
        writeTimestampedLine(message);
    }

    /**
     * Gets the log file path.
     */
    public File getLogFile() {
        return logFile;
    }

    /**
     * Writes a summary and closes the logger.
     */
    @Override
    public void close() {
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        writeLine("");
        writeLine("--------------------------------------------------------------------------------");
        writeLine("SUMMARY");
        writeLine("--------------------------------------------------------------------------------");
        writeLine("Completed: " + TIMESTAMP_FORMAT.format(new Date(endTime)));
        writeLine("Duration: " + formatDuration(duration));
        writeLine("");
        writeLine("Files created: " + filesCreated);
        writeLine("Files deleted: " + filesDeleted);
        writeLine("Files overwritten: " + filesOverwritten);
        writeLine("Files skipped: " + filesSkipped);
        writeLine("Files failed: " + filesFailed);
        writeLine("Directories created: " + directoriesCreated);
        writeLine("Directories deleted: " + directoriesDeleted);
        writeLine("Registry operations: " + registryOperations);
        writeLine("");
        writeLine("================================================================================");
        writeLine("END OF LOG");
        writeLine("================================================================================");

        writer.close();

        // Also print log file location to stdout for visibility
        System.out.println("Installation log written to: " + logFile.getAbsolutePath());
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long ms = millis % 1000;
        if (seconds < 60) {
            return seconds + "." + String.format("%03d", ms) + " seconds";
        }
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return minutes + " minutes, " + seconds + " seconds";
    }
}

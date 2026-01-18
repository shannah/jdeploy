package ca.weblite.jdeploy.installer.helpers;

import ca.weblite.tools.platform.Platform;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generates cleanup scripts for Helper self-deletion.
 *
 * When a Helper needs to delete itself (e.g., when services are removed from
 * an application), it cannot directly delete its own files while running.
 * This class generates platform-specific scripts that:
 * <ol>
 *   <li>Wait briefly for the Helper process to exit</li>
 *   <li>Delete the Helper executable/bundle</li>
 *   <li>Delete the Helper context directory (.jdeploy-files)</li>
 *   <li>Attempt to delete the Helper directory (if empty)</li>
 *   <li>Delete the cleanup script itself</li>
 * </ol>
 *
 * @author jDeploy Team
 */
public class HelperCleanupScriptGenerator {

    private static final Logger logger = Logger.getLogger(HelperCleanupScriptGenerator.class.getName());

    private static final int CLEANUP_DELAY_SECONDS = 2;

    /**
     * Generates a cleanup script for Helper self-deletion.
     *
     * Creates a temporary platform-specific script that will delete the Helper
     * files after a brief delay. The script is designed to be run as a detached
     * process so it can outlive the Helper process.
     *
     * @param helperPath The Helper executable or .app bundle to delete
     * @param helperContextDir The Helper's .jdeploy-files directory to delete
     * @param helperDir The Helper directory to remove if empty (may be null)
     * @return The generated cleanup script file
     * @throws IOException if the script cannot be created
     * @throws IllegalArgumentException if helperPath or helperContextDir is null
     */
    public File generateCleanupScript(File helperPath, File helperContextDir, File helperDir) throws IOException {
        if (helperPath == null) {
            throw new IllegalArgumentException("helperPath cannot be null");
        }
        if (helperContextDir == null) {
            throw new IllegalArgumentException("helperContextDir cannot be null");
        }

        if (Platform.getSystemPlatform().isWindows()) {
            return generateWindowsScript(helperPath, helperContextDir, helperDir);
        } else {
            return generateUnixScript(helperPath, helperContextDir, helperDir);
        }
    }

    /**
     * Executes a cleanup script as a detached process.
     *
     * The script is launched in a way that allows it to continue running
     * after the current process exits. This is necessary for self-deletion
     * since the Helper cannot delete itself while running.
     *
     * @param script The cleanup script to execute
     * @throws IOException if the script cannot be executed
     * @throws IllegalArgumentException if script is null or doesn't exist
     */
    public void executeCleanupScript(File script) throws IOException {
        if (script == null) {
            throw new IllegalArgumentException("script cannot be null");
        }
        if (!script.exists()) {
            throw new IllegalArgumentException("script does not exist: " + script.getAbsolutePath());
        }

        logger.info("Executing cleanup script: " + script.getAbsolutePath());

        if (Platform.getSystemPlatform().isWindows()) {
            executeWindowsScript(script);
        } else {
            executeUnixScript(script);
        }
    }

    /**
     * Generates a bash cleanup script for macOS/Linux.
     *
     * The generated script:
     * 1. Waits for the Helper process to exit (sleep)
     * 2. Forcefully removes the Helper executable/bundle (rm -rf)
     * 3. Forcefully removes the context directory (rm -rf)
     * 4. Attempts to remove the parent directory if empty (rmdir, errors suppressed)
     * 5. Deletes itself ($0)
     *
     * @param helperPath The Helper executable or .app bundle
     * @param helperContextDir The .jdeploy-files directory
     * @param helperDir The parent Helper directory (may be null)
     * @return The generated script file
     * @throws IOException if script creation fails
     */
    private File generateUnixScript(File helperPath, File helperContextDir, File helperDir) throws IOException {
        File scriptFile = File.createTempFile("helper-cleanup-", ".sh");

        try (PrintWriter writer = new PrintWriter(new FileWriter(scriptFile))) {
            // Shebang - use bash for reliable behavior
            writer.println("#!/bin/bash");

            // Wait for Helper to exit before attempting deletion
            writer.println("sleep " + CLEANUP_DELAY_SECONDS);

            // Delete Helper executable/bundle (-rf handles both files and directories)
            writer.println("rm -rf \"" + escapeForBash(helperPath.getAbsolutePath()) + "\"");

            // Delete context directory containing app.xml, etc.
            writer.println("rm -rf \"" + escapeForBash(helperContextDir.getAbsolutePath()) + "\"");

            // Try to remove parent directory if empty (suppress errors if not empty)
            if (helperDir != null) {
                writer.println("rmdir \"" + escapeForBash(helperDir.getAbsolutePath()) + "\" 2>/dev/null");
            }

            // Delete this script itself (-- prevents issues if filename starts with -)
            writer.println("rm -- \"$0\"");
        }

        // Make the script executable so it can be run
        boolean executableSet = scriptFile.setExecutable(true);
        if (!executableSet) {
            logger.warning("Failed to set executable permission on cleanup script");
        }

        logger.info("Generated Unix cleanup script: " + scriptFile.getAbsolutePath());
        return scriptFile;
    }

    /**
     * Generates a batch cleanup script for Windows.
     *
     * The generated script:
     * 1. Waits for the Helper process to exit (timeout)
     * 2. Deletes the Helper executable (del) or directory (rmdir /s /q)
     * 3. Removes the context directory recursively (rmdir /s /q)
     * 4. Attempts to remove the parent directory if empty (rmdir, errors suppressed)
     * 5. Deletes itself (%~f0 is the full path to this script)
     *
     * @param helperPath The Helper executable file
     * @param helperContextDir The .jdeploy-files directory
     * @param helperDir The parent helpers directory (may be null)
     * @return The generated script file
     * @throws IOException if script creation fails
     */
    private File generateWindowsScript(File helperPath, File helperContextDir, File helperDir) throws IOException {
        File scriptFile = File.createTempFile("helper-cleanup-", ".bat");

        try (PrintWriter writer = new PrintWriter(new FileWriter(scriptFile))) {
            // Suppress command echoing for cleaner output
            writer.println("@echo off");

            // Wait for Helper to exit (/nobreak prevents user interruption, > nul hides output)
            writer.println("timeout /t " + CLEANUP_DELAY_SECONDS + " /nobreak > nul");

            // Delete Helper executable - use rmdir for directories, del for files
            // /s = recursive, /q = quiet (no confirmation), /f = force
            if (helperPath.isDirectory()) {
                writer.println("rmdir /s /q \"" + escapeForBatch(helperPath.getAbsolutePath()) + "\"");
            } else {
                writer.println("del /f /q \"" + escapeForBatch(helperPath.getAbsolutePath()) + "\"");
            }

            // Delete context directory recursively
            writer.println("rmdir /s /q \"" + escapeForBatch(helperContextDir.getAbsolutePath()) + "\"");

            // Try to remove parent directory if empty (2>nul suppresses "not empty" errors)
            if (helperDir != null) {
                writer.println("rmdir \"" + escapeForBatch(helperDir.getAbsolutePath()) + "\" 2>nul");
            }

            // Delete this script itself (%~f0 expands to full path of batch file)
            writer.println("del \"%~f0\"");
        }

        logger.info("Generated Windows cleanup script: " + scriptFile.getAbsolutePath());
        return scriptFile;
    }

    /**
     * Executes a cleanup script on macOS/Linux using nohup.
     *
     * Uses nohup to run the script in a way that survives the parent process exit.
     * Output is redirected to /dev/null since the script runs after we exit.
     *
     * @param script The cleanup script to execute
     * @throws IOException if the process cannot be started
     */
    private void executeUnixScript(File script) throws IOException {
        // nohup allows the script to continue running after this process exits
        ProcessBuilder pb = new ProcessBuilder("nohup", script.getAbsolutePath());

        // Discard output since we won't be around to read it
        pb.redirectOutput(new File("/dev/null"));
        pb.redirectError(new File("/dev/null"));

        // Run from script's directory for relative path safety
        pb.directory(script.getParentFile());
        pb.start();

        logger.info("Launched Unix cleanup script as detached process");
    }

    /**
     * Executes a cleanup script on Windows using start /min.
     *
     * Uses "start /min" to launch the script in a minimized window that
     * survives the parent process exit. The nested cmd /c ensures the
     * window closes after the script completes.
     *
     * @param script The cleanup script to execute
     * @throws IOException if the process cannot be started
     */
    private void executeWindowsScript(File script) throws IOException {
        // start /min launches in minimized window; nested cmd /c runs script and closes
        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", "/min", "cmd", "/c", script.getAbsolutePath());
        pb.start();

        logger.info("Launched Windows cleanup script as detached process");
    }

    /**
     * Escapes a string for safe inclusion in a bash script.
     *
     * Handles paths with special characters by escaping them properly.
     *
     * @param value The string to escape
     * @return The escaped string
     */
    private String escapeForBash(String value) {
        // Inside double quotes, we need to escape: $ ` \ " !
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("$", "\\$")
                .replace("`", "\\`")
                .replace("!", "\\!");
    }

    /**
     * Escapes a string for safe inclusion in a Windows batch script.
     *
     * Handles paths with special characters.
     *
     * @param value The string to escape
     * @return The escaped string
     */
    private String escapeForBatch(String value) {
        // Inside double quotes in batch, we mainly need to handle % and ^
        // but most paths don't need escaping inside quotes
        return value.replace("%", "%%");
    }

    /**
     * Generates the content of a Unix cleanup script without writing to a file.
     *
     * This is useful for testing the script content generation.
     *
     * @param helperPath The Helper executable or .app bundle path
     * @param helperContextDir The Helper's .jdeploy-files directory path
     * @param helperDir The Helper directory path (may be null)
     * @return The script content as a string
     */
    public String generateUnixScriptContent(File helperPath, File helperContextDir, File helperDir) {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/bash\n");
        sb.append("sleep ").append(CLEANUP_DELAY_SECONDS).append("\n");
        sb.append("rm -rf \"").append(escapeForBash(helperPath.getAbsolutePath())).append("\"\n");
        sb.append("rm -rf \"").append(escapeForBash(helperContextDir.getAbsolutePath())).append("\"\n");
        if (helperDir != null) {
            sb.append("rmdir \"").append(escapeForBash(helperDir.getAbsolutePath())).append("\" 2>/dev/null\n");
        }
        sb.append("rm -- \"$0\"\n");
        return sb.toString();
    }

    /**
     * Generates the content of a Windows cleanup script without writing to a file.
     *
     * This is useful for testing the script content generation.
     *
     * @param helperPath The Helper executable path
     * @param helperContextDir The Helper's .jdeploy-files directory path
     * @param helperDir The Helper directory path (may be null)
     * @param isHelperDirectory Whether the helperPath is a directory (for testing)
     * @return The script content as a string
     */
    public String generateWindowsScriptContent(File helperPath, File helperContextDir, File helperDir, boolean isHelperDirectory) {
        StringBuilder sb = new StringBuilder();
        sb.append("@echo off\n");
        sb.append("timeout /t ").append(CLEANUP_DELAY_SECONDS).append(" /nobreak > nul\n");

        if (isHelperDirectory) {
            sb.append("rmdir /s /q \"").append(escapeForBatch(helperPath.getAbsolutePath())).append("\"\n");
        } else {
            sb.append("del /f /q \"").append(escapeForBatch(helperPath.getAbsolutePath())).append("\"\n");
        }

        sb.append("rmdir /s /q \"").append(escapeForBatch(helperContextDir.getAbsolutePath())).append("\"\n");

        if (helperDir != null) {
            sb.append("rmdir \"").append(escapeForBatch(helperDir.getAbsolutePath())).append("\" 2>nul\n");
        }

        sb.append("del \"%~f0\"\n");
        return sb.toString();
    }
}

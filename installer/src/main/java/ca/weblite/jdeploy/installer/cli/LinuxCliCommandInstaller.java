package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.models.CommandSpec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Linux-specific implementation of CLI command installer.
 * Handles installation and uninstallation of CLI commands on Linux systems,
 * including script creation, symlink management, and PATH updates.
 */
public class LinuxCliCommandInstaller extends AbstractUnixCliCommandInstaller {

    /**
     * Installs CLI commands for the given launcher executable.
     *
     * @param launcherPath the path to the main launcher executable
     * @param commands list of command specifications to install
     * @param settings installation settings containing platform-specific configuration
     * @return list of files created during the installation process
     */
    @Override
    public List<File> installCommands(File launcherPath, List<CommandSpec> commands, InstallationSettings settings) {
        List<File> createdFiles = new ArrayList<>();

        if (launcherPath == null || !launcherPath.exists()) {
            System.err.println("Warning: Launcher path does not exist: " + launcherPath);
            return createdFiles;
        }

        // Return empty if no commands provided
        if (commands == null || commands.isEmpty()) {
            return createdFiles;
        }

        File localBinDir = getBinDir(settings);

        // Create ~/.local/bin if it doesn't exist
        if (!ensureBinDirExists(localBinDir)) {
            return createdFiles;
        }

        // Create command scripts using common helper
        createdFiles.addAll(installCommandScripts(launcherPath, commands, localBinDir));

        // Update PATH and save metadata if any files were created
        if (!createdFiles.isEmpty()) {
            boolean pathUpdated = addToPath(localBinDir);
            File appDir = launcherPath.getParentFile();
            // Save metadata to launcher's parent directory if it differs from bin, otherwise use bin
            File metadataDir = (appDir != null && !appDir.equals(localBinDir)) ? appDir : localBinDir;
            saveMetadata(metadataDir, createdFiles, pathUpdated, localBinDir);
        }

        return createdFiles;
    }

    /**
     * Installs a single CLI launcher symlink (no individual command scripts).
     * Used when installCliLauncher is true but installCliCommands is false.
     *
     * @param launcherPath the path to the main launcher executable
     * @param commandName the name to use for the symlink
     * @param settings installation settings containing platform-specific configuration
     * @return the symlink File if created, or null if creation was skipped
     */
    public File installLauncher(File launcherPath, String commandName, InstallationSettings settings) {
        if (launcherPath == null || commandName == null || commandName.isEmpty()) {
            return null;
        }

        File localBinDir = getBinDir(settings);

        // Create ~/.local/bin if it doesn't exist
        if (!ensureBinDirExists(localBinDir)) {
            return null;
        }

        File symlinkPath = new File(localBinDir, commandName);

        // Remove existing symlink/file if present
        if (symlinkPath.exists() || Files.isSymbolicLink(symlinkPath.toPath())) {
            try {
                Files.delete(symlinkPath.toPath());
            } catch (IOException e) {
                System.err.println("Warning: Failed to delete existing launcher symlink: " + e.getMessage());
                return null;
            }
        }

        try {
            // Create symlink to the launcher
            Files.createSymbolicLink(symlinkPath.toPath(), launcherPath.toPath());
            System.out.println("Created launcher symlink: " + symlinkPath.getAbsolutePath());

            // Update PATH and save metadata
            boolean pathUpdated = addToPath(localBinDir);
            File appDir = launcherPath.getParentFile();
            // Save metadata to launcher's parent directory if it differs from bin, otherwise use bin
            File metadataDir = (appDir != null && !appDir.equals(localBinDir)) ? appDir : localBinDir;
            saveMetadata(metadataDir, java.util.Collections.singletonList(symlinkPath), pathUpdated, localBinDir);

            return symlinkPath;
        } catch (IOException ioe) {
            System.err.println("Warning: Failed to create launcher symlink: " + ioe.getMessage());
            return null;
        }
    }

    @Override
    protected void writeCommandScript(File scriptPath, String launcherPath, String commandName, List<String> args) throws IOException {
        String content = generateContent(launcherPath, commandName);
        try (FileOutputStream fos = new FileOutputStream(scriptPath)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        // make executable for owner/group/other (non-strict)
        scriptPath.setExecutable(true, false);
    }

    /**
     * Generate the content of a POSIX shell script that exec's the given launcher with
     * the configured command name and forwards user-supplied args.
     *
     * @param launcherPath Absolute path to the CLI-capable launcher binary.
     * @param commandName  The command name to pass as --jdeploy:command=<name>.
     * @return Script content (including shebang and trailing newline).
     */
    private static String generateContent(String launcherPath, String commandName) {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/sh\n");
        sb.append("exec \"").append(escapeDoubleQuotes(launcherPath)).append("\" ").append(CliInstallerConstants.JDEPLOY_COMMAND_ARG_PREFIX).append(commandName).append(" -- \"$@\"\n");
        return sb.toString();
    }

    /**
     * Escapes special characters in a string for use within double quotes in a shell script.
     * Order matters: escape backslashes first to avoid double-escaping.
     *
     * @param s the string to escape
     * @return the escaped string safe for use in double quotes
     */
    private static String escapeDoubleQuotes(String s) {
        if (s == null) return "";
        // Order matters: escape backslashes first to avoid double-escaping
        // Then escape other special chars that have meaning inside double quotes
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("`", "\\`")
                .replace("$", "\\$");
    }
}

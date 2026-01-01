package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.util.DebugLogger;
import ca.weblite.jdeploy.models.CommandSpec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * macOS implementation of CLI command installation.
 *
 * Handles installation of CLI commands to ~/.jdeploy/bin-{arch}/{fqpn}, creation of symlinks
 * to the CLI launcher, PATH management, and persistence of command metadata.
 */
public class MacCliCommandInstaller extends AbstractUnixCliCommandInstaller {

    /**
     * Sets the collision handler for detecting and resolving command name conflicts.
     * 
     * @param collisionHandler the handler to use for collision resolution
     */
    public void setCollisionHandler(CollisionHandler collisionHandler) {
        super.setCollisionHandler(collisionHandler);
    }

    @Override
    protected File getBinDir(InstallationSettings settings) {
        return super.getBinDir(settings);
    }

    @Override
    public List<File> installCommands(File launcherPath, List<CommandSpec> commands, InstallationSettings settings) {
        List<File> createdFiles = new ArrayList<>();

        // Log entry with key parameters
        if (DebugLogger.isEnabled()) {
            int commandCount = (commands != null) ? commands.size() : 0;
            DebugLogger.log("MacCliCommandInstaller.installCommands() entry - launcherPath: " + launcherPath +
                            ", commands count: " + commandCount +
                            ", isInstallCliCommands: " + settings.isInstallCliCommands() +
                            ", isInstallCliLauncher: " + settings.isInstallCliLauncher());
        }

        if (launcherPath == null || !launcherPath.exists()) {
            System.err.println("Warning: Launcher path does not exist: " + launcherPath);
            return createdFiles;
        }

        boolean anyCreated = false;
        File commandsBinDir = null;
        File launcherBinDir = null;

        // Create per-command scripts in per-app directory if requested
        if (settings.isInstallCliCommands() && commands != null && !commands.isEmpty()) {
            commandsBinDir = getBinDir(settings);  // Returns ~/.jdeploy/bin-{arch}/{fqpn}
            DebugLogger.log("Determined commands binDir: " + commandsBinDir);

            DebugLogger.log("Calling ensureBinDirExists() for: " + commandsBinDir);
            if (!ensureBinDirExists(commandsBinDir)) {
                DebugLogger.log("ensureBinDirExists() failed for commands bin dir");
                System.err.println("Warning: Failed to create CLI commands bin directory: " + commandsBinDir);
            } else {
                DebugLogger.log("ensureBinDirExists() succeeded");
                DebugLogger.log("Starting installCommandScripts() for " + commands.size() + " commands");
                createdFiles.addAll(installCommandScripts(launcherPath, commands, commandsBinDir));
                DebugLogger.log("installCommandScripts() completed, created " + createdFiles.size() + " files");
                anyCreated = !createdFiles.isEmpty();

                if (anyCreated) {
                    // Add per-app commands directory to PATH
                    DebugLogger.log("Calling addToPath() for: " + commandsBinDir);
                    addToPath(commandsBinDir);
                }
            }
        } else {
            DebugLogger.log("Skipping installCommandScripts() - isInstallCliCommands: " + settings.isInstallCliCommands() +
                            ", commands: " + (commands != null ? commands.size() : "null"));
        }

        // Create CLI launcher symlink in the same per-app directory if requested
        // Note: On macOS, CLI Launcher uses the same directory as CLI Commands (unlike Linux)
        if (settings.isInstallCliLauncher()) {
            // Use commands bin dir if available, otherwise create a new per-app bin dir for the launcher
            launcherBinDir = commandsBinDir != null ? commandsBinDir : getBinDir(settings);
            DebugLogger.log("Determined launcher binDir: " + launcherBinDir);

            if (!ensureBinDirExists(launcherBinDir)) {
                DebugLogger.log("ensureBinDirExists() failed for launcher bin dir");
                System.err.println("Warning: Failed to create CLI launcher bin directory: " + launcherBinDir);
            } else {
                String commandName = deriveCommandName(settings);
                File symlinkPath = new File(launcherBinDir, commandName);
                DebugLogger.log("Creating symlink for primary command: " + commandName + " at " + symlinkPath);

                if (symlinkPath.exists()) {
                    symlinkPath.delete();
                }

                try {
                    Files.createSymbolicLink(symlinkPath.toPath(), launcherPath.toPath());
                    System.out.println("Created command-line symlink: " + symlinkPath.getAbsolutePath());
                    DebugLogger.log("Symlink created successfully: " + symlinkPath);
                    settings.setCommandLineSymlinkCreated(true);
                    createdFiles.add(symlinkPath);
                    anyCreated = true;

                    // Add to PATH if not already added for commands
                    if (commandsBinDir == null) {
                        DebugLogger.log("Calling addToPath() for: " + launcherBinDir);
                        addToPath(launcherBinDir);
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to create command-line symlink: " + e.getMessage());
                    DebugLogger.log("Failed to create symlink: " + e.getMessage());
                }
            }
        } else {
            DebugLogger.log("Skipping symlink creation - isInstallCliLauncher is false");
        }

        // Save metadata if any files were created
        if (anyCreated) {
            File appDir = launcherPath.getParentFile();
            // Use commands bin dir for metadata if available, otherwise launcher bin dir
            File binDirForMetadata = commandsBinDir != null ? commandsBinDir : launcherBinDir;
            File metadataDir = (appDir != null && !appDir.equals(binDirForMetadata)) ? appDir : binDirForMetadata;
            boolean pathUpdated = commandsBinDir != null || launcherBinDir != null;
            DebugLogger.log("Saving metadata to: " + metadataDir + " with " + createdFiles.size() + " files");
            saveMetadata(metadataDir, createdFiles, pathUpdated, binDirForMetadata, settings.getPackageName(), settings.getSource());
            settings.setAddedToPath(pathUpdated);
            DebugLogger.log("Updated settings - addedToPath: " + pathUpdated);
        } else {
            DebugLogger.log("No files were created, skipping PATH update and metadata save");
        }

        DebugLogger.log("MacCliCommandInstaller.installCommands() exit - returning " + createdFiles.size() + " files");
        return createdFiles;
    }

    @Override
    protected void writeCommandScript(File scriptPath, String launcherPath, String commandName, List<String> args) throws IOException {
        StringBuilder script = new StringBuilder();
        script.append("#!/bin/bash\n");
        script.append("\"").append(escapeDoubleQuotes(launcherPath)).append("\" ");
        script.append(CliInstallerConstants.JDEPLOY_COMMAND_ARG_PREFIX).append(commandName);
        script.append(" -- \"$@\"\n");

        try (FileOutputStream fos = new FileOutputStream(scriptPath)) {
            fos.write(script.toString().getBytes(StandardCharsets.UTF_8));
        }

        scriptPath.setExecutable(true, false);
    }

    /**
     * Escapes double quotes in a string for shell script usage.
     * 
     * @param s the string to escape
     * @return the escaped string
     */
    private static String escapeDoubleQuotes(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\"", "\\\"");
    }
}

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
 * Handles installation of CLI commands to ~/.local/bin, creation of symlinks
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
        if (settings != null && settings.getCommandLinePath() != null && !settings.getCommandLinePath().isEmpty()) {
            return new File(settings.getCommandLinePath()).getParentFile();
        }
        return new File(System.getProperty("user.home"), "bin");
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

        File localBinDir = getBinDir(settings);
        DebugLogger.log("Determined binDir: " + localBinDir);

        // Create ~/.local/bin if it doesn't exist
        DebugLogger.log("Calling ensureBinDirExists() for: " + localBinDir);
        if (!ensureBinDirExists(localBinDir)) {
            DebugLogger.log("ensureBinDirExists() failed, returning empty list");
            return createdFiles;
        }
        DebugLogger.log("ensureBinDirExists() succeeded");

        boolean anyCreated = false;

        // Create per-command scripts if requested and commands are present
        if (settings.isInstallCliCommands() && commands != null && !commands.isEmpty()) {
            DebugLogger.log("Starting installCommandScripts() for " + commands.size() + " commands");
            createdFiles.addAll(installCommandScripts(launcherPath, commands, localBinDir));
            DebugLogger.log("installCommandScripts() completed, created " + createdFiles.size() + " files");
            anyCreated = !createdFiles.isEmpty();
        } else {
            DebugLogger.log("Skipping installCommandScripts() - isInstallCliCommands: " + settings.isInstallCliCommands() + 
                            ", commands: " + (commands != null ? commands.size() : "null"));
        }

        // Create traditional single symlink for primary command if requested
        if (settings.isInstallCliLauncher()) {
            String commandName = deriveCommandName(settings);
            File symlinkPath = new File(localBinDir, commandName);
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
            } catch (Exception e) {
                System.err.println("Warning: Failed to create command-line symlink: " + e.getMessage());
                DebugLogger.log("Failed to create symlink: " + e.getMessage());
            }
        } else {
            DebugLogger.log("Skipping symlink creation - isInstallCliLauncher is false");
        }

        // Add to PATH and save metadata if any files were created
        if (anyCreated) {
            DebugLogger.log("Calling addToPath() for: " + localBinDir);
            boolean pathUpdated = addToPath(localBinDir);
            DebugLogger.log("addToPath() completed, pathUpdated: " + pathUpdated);

            // Save metadata to launcher's parent directory (appDir) if it differs from binDir
            File appDir = launcherPath.getParentFile();
            File metadataDir = (appDir != null && !appDir.equals(localBinDir)) ? appDir : localBinDir;
            DebugLogger.log("Saving metadata to: " + metadataDir + " with " + createdFiles.size() + " files");
            saveMetadata(metadataDir, createdFiles, pathUpdated, localBinDir);

            // Update settings
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

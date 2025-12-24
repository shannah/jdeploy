package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.linux.LinuxCliScriptWriter;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.models.CommandSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

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

        // Create command scripts
        for (CommandSpec command : commands) {
            String cmdName = command.getName();
            File scriptPath = new File(localBinDir, cmdName);

            // Remove existing script if present
            if (scriptPath.exists()) {
                try {
                    scriptPath.delete();
                } catch (Exception e) {
                    System.err.println("Warning: Failed to delete existing script for " + cmdName);
                }
            }

            try {
                writeCommandScript(scriptPath, launcherPath.getAbsolutePath(), cmdName, command.getArgs());
                System.out.println("Created command-line script: " + scriptPath.getAbsolutePath());
                createdFiles.add(scriptPath);
            } catch (IOException ioe) {
                System.err.println("Warning: Failed to create command script for " + cmdName + ": " + ioe.getMessage());
            }
        }

        // Update PATH and save metadata if any files were created
        // IMPORTANT: Save metadata to launcherPath's parent directory (the app directory), NOT binDir
        if (!createdFiles.isEmpty()) {
            boolean pathUpdated = addToPath(localBinDir);
            File appDir = launcherPath.getParentFile();
            saveMetadata(appDir, createdFiles, pathUpdated, localBinDir);
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
            saveMetadata(localBinDir, java.util.Collections.singletonList(symlinkPath), pathUpdated, localBinDir);

            return symlinkPath;
        } catch (IOException ioe) {
            System.err.println("Warning: Failed to create launcher symlink: " + ioe.getMessage());
            return null;
        }
    }

    @Override
    protected void writeCommandScript(File scriptPath, String launcherPath, String commandName, List<String> args) throws IOException {
        LinuxCliScriptWriter.writeExecutableScript(scriptPath, launcherPath, commandName);
    }
}

package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.linux.LinuxCliScriptWriter;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.models.CommandSpec;
import ca.weblite.tools.io.IOUtil;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Linux-specific implementation of CLI command installer.
 * Handles installation and uninstallation of CLI commands on Linux systems,
 * including script creation, symlink management, and PATH updates.
 */
public class LinuxCliCommandInstaller implements CliCommandInstaller {

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

        if (commands == null || commands.isEmpty()) {
            return createdFiles;
        }

        File localBinDir;
        if (settings != null && settings.getCommandLinePath() != null && !settings.getCommandLinePath().isEmpty()) {
            localBinDir = new File(settings.getCommandLinePath());
        } else {
            localBinDir = new File(System.getProperty("user.home"), ".local" + File.separator + "bin");
        }

        // Create ~/.local/bin if it doesn't exist
        if (!localBinDir.exists()) {
            if (!localBinDir.mkdirs()) {
                System.err.println("Warning: Failed to create ~/.local/bin directory");
                return createdFiles;
            }
            System.out.println("Created ~/.local/bin directory");
        }

        // Create per-command scripts
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
                LinuxCliScriptWriter.writeExecutableScript(scriptPath, launcherPath.getAbsolutePath(), cmdName);
                System.out.println("Created command-line script: " + scriptPath.getAbsolutePath());
                createdFiles.add(scriptPath);
            } catch (IOException ioe) {
                System.err.println("Warning: Failed to create command script for " + cmdName + ": " + ioe.getMessage());
            }
        }

        // Update PATH if any commands were created
        if (!createdFiles.isEmpty()) {
            boolean pathUpdated = addToPath(localBinDir);
            if (pathUpdated) {
                saveMetadata(launcherPath.getParentFile(), createdFiles, true, localBinDir);
            }
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

        File localBinDir;
        if (settings != null && settings.getCommandLinePath() != null && !settings.getCommandLinePath().isEmpty()) {
            localBinDir = new File(settings.getCommandLinePath()).getParentFile();
        } else {
            localBinDir = new File(System.getProperty("user.home"), ".local" + File.separator + "bin");
        }

        // Create ~/.local/bin if it doesn't exist
        if (!localBinDir.exists()) {
            if (!localBinDir.mkdirs()) {
                System.err.println("Warning: Failed to create ~/.local/bin directory");
                return null;
            }
            System.out.println("Created ~/.local/bin directory");
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
            if (pathUpdated) {
                saveMetadata(launcherPath.getParentFile(), java.util.Collections.singletonList(symlinkPath), true, localBinDir);
            }

            return symlinkPath;
        } catch (IOException ioe) {
            System.err.println("Warning: Failed to create launcher symlink: " + ioe.getMessage());
            return null;
        }
    }

    /**
     * Uninstalls CLI commands that were previously installed.
     *
     * @param appDir the application directory containing installed command files
     */
    @Override
    public void uninstallCommands(File appDir) {
        if (appDir == null || !appDir.exists()) {
            return;
        }

        // Load metadata to find installed commands
        JSONObject metadata = loadMetadata(appDir);
        if (metadata == null) {
            return;
        }

        if (metadata.has(CliInstallerConstants.CREATED_WRAPPERS_KEY)) {
            JSONArray installedCommands = metadata.getJSONArray(CliInstallerConstants.CREATED_WRAPPERS_KEY);
            File localBinDir;
            if (metadata.has("binDir")) {
                localBinDir = new File(metadata.getString("binDir"));
            } else {
                localBinDir = new File(System.getProperty("user.home"), ".local" + File.separator + "bin");
            }

            for (int i = 0; i < installedCommands.length(); i++) {
                String cmdName = installedCommands.getString(i);
                File scriptPath = new File(localBinDir, cmdName);

                if (scriptPath.exists()) {
                    try {
                        scriptPath.delete();
                        System.out.println("Removed command-line script: " + scriptPath.getAbsolutePath());
                    } catch (Exception e) {
                        System.err.println("Warning: Failed to remove command script for " + cmdName + ": " + e.getMessage());
                    }
                }
            }
        }

        // Remove metadata file
        File metadataFile = new File(appDir, CliInstallerConstants.CLI_METADATA_FILE);
        if (metadataFile.exists()) {
            metadataFile.delete();
        }
    }

    /**
     * Adds a directory to the system PATH environment variable.
     * Updates shell configuration files (.bashrc, .zshrc, etc.) to persist the change.
     *
     * @param binDir the directory to add to PATH
     * @return true if the PATH was successfully updated, false otherwise
     */
    @Override
    public boolean addToPath(File binDir) {
        String shell = System.getenv("SHELL");
        String pathEnv = System.getenv("PATH");
        File homeDir = new File(System.getProperty("user.home"));
        return addToPath(binDir, shell, pathEnv, homeDir);
    }

    /**
     * Testable overload for addToPath with explicit environment parameters.
     *
     * @param binDir   directory to add to PATH
     * @param shell    shell path from environment (e.g., /bin/bash)
     * @param pathEnv  PATH environment variable
     * @param homeDir  user's home directory to update config files under
     * @return true if PATH was updated or already contained the directory, false otherwise
     */
    static boolean addToPath(File binDir, String shell, String pathEnv, File homeDir) {
        try {
            // Detect the user's shell; default to bash when unknown
            if (shell == null || shell.isEmpty()) {
                shell = "/bin/bash";
            }

            File configFile = null;
            String shellName = new File(shell).getName();

            switch (shellName) {
                case "bash": {
                    File bashrc = new File(homeDir, ".bashrc");
                    File bashProfile = new File(homeDir, ".bash_profile");
                    configFile = bashrc.exists() ? bashrc : bashProfile;
                    break;
                }
                case "zsh":
                    configFile = new File(homeDir, ".zshrc");
                    break;
                case "fish":
                    // Fish uses a different syntax; do not attempt to auto-update
                    System.out.println("Note: Fish shell detected. Please manually add ~/.local/bin to your PATH:");
                    System.out.println("  set -U fish_user_paths ~/.local/bin $fish_user_paths");
                    return false;
                default:
                    configFile = new File(homeDir, ".profile");
                    break;
            }

            // If PATH already contains binDir, nothing to do
            if (pathEnv != null && pathEnv.contains(binDir.getAbsolutePath())) {
                System.out.println("~/.local/bin is already in PATH");
                return true;
            }

            // Ensure configFile exists (create if necessary)
            if (!configFile.exists()) {
                // create parent directories if necessary
                File parent = configFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                // create empty file so we can append to it
                try {
                    configFile.createNewFile();
                } catch (Exception ignored) { }
            } else {
                // Check file contents to avoid duplicate entries
                String content = IOUtil.readToString(new FileInputStream(configFile));
                if (content.contains("$HOME/.local/bin") || content.contains(binDir.getAbsolutePath())) {
                    System.out.println("~/.local/bin is already in PATH configuration");
                    return true;
                }
            }

            // Append PATH export to the config file
            String pathExport = "\n# Added by jDeploy installer\nexport PATH=\"$HOME/.local/bin:$PATH\"\n";
            try (FileOutputStream fos = new FileOutputStream(configFile, true)) {
                fos.write(pathExport.getBytes(StandardCharsets.UTF_8));
            }

            System.out.println("Added ~/.local/bin to PATH in " + configFile.getName());
            System.out.println("Please restart your terminal or run: source " + configFile.getAbsolutePath());
            return true;

        } catch (Exception e) {
            System.err.println("Warning: Failed to add ~/.local/bin to PATH: " + e.getMessage());
            System.out.println("You may need to manually add ~/.local/bin to your PATH");
            return false;
        }
    }

    /**
     * Saves metadata about installed CLI commands to a JSON file for later retrieval.
     *
     * @param appDir       the application directory where metadata will be stored
     * @param createdFiles list of files created during installation
     * @param pathUpdated  whether the PATH was updated
     * @param binDir       the bin directory where commands were installed
     */
    private void saveMetadata(File appDir, List<File> createdFiles, boolean pathUpdated, File binDir) {
        try {
            File metadataFile = new File(appDir, CliInstallerConstants.CLI_METADATA_FILE);
            JSONObject metadata = new JSONObject();

            // Store installed command names (just the filenames, not full paths)
            JSONArray commandNames = new JSONArray();
            for (File file : createdFiles) {
                commandNames.put(file.getName());
            }
            metadata.put(CliInstallerConstants.CREATED_WRAPPERS_KEY, commandNames);
            metadata.put(CliInstallerConstants.PATH_UPDATED_KEY, pathUpdated);
            metadata.put("binDir", binDir.getAbsolutePath());

            // Write metadata to file
            try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(metadataFile.toPath(), StandardCharsets.UTF_8)) {
                writer.write(metadata.toString(2)); // Pretty-print with 2-space indent
            }

            System.out.println("Saved CLI metadata to " + metadataFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Warning: Failed to save CLI metadata: " + e.getMessage());
        }
    }

    /**
     * Loads metadata about installed CLI commands from a JSON file.
     *
     * @param appDir the application directory where metadata is stored
     * @return the metadata JSONObject, or null if the metadata file does not exist
     */
    private JSONObject loadMetadata(File appDir) {
        try {
            File metadataFile = new File(appDir, CliInstallerConstants.CLI_METADATA_FILE);
            if (!metadataFile.exists()) {
                return null;
            }

            String content = new String(Files.readAllBytes(Paths.get(metadataFile.getAbsolutePath())), StandardCharsets.UTF_8);
            return new JSONObject(content);
        } catch (Exception e) {
            System.err.println("Warning: Failed to load CLI metadata: " + e.getMessage());
            return null;
        }
    }
}

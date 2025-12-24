package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.models.CommandSpec;
import ca.weblite.tools.io.IOUtil;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * macOS implementation of CLI command installation.
 * 
 * Handles installation of CLI commands to ~/.local/bin, creation of symlinks
 * to the CLI launcher, PATH management, and persistence of command metadata.
 */
public class MacCliCommandInstaller implements CliCommandInstaller {

    private static final String CLI_LAUNCHER_NAME = "Client4JLauncher-cli";

    @Override
    public List<File> installCommands(File launcherPath, List<CommandSpec> commands, InstallationSettings settings) {
        List<File> createdFiles = new ArrayList<>();

        if (launcherPath == null || !launcherPath.exists()) {
            System.err.println("Warning: Launcher path does not exist: " + launcherPath);
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

        boolean anyCreated = false;

        // Create per-command scripts if requested and commands are present
        if (settings.isInstallCliCommands() && commands != null && !commands.isEmpty()) {
            for (CommandSpec cs : commands) {
                String cmdName = cs.getName();
                File scriptPath = new File(localBinDir, cmdName);

                if (scriptPath.exists()) {
                    scriptPath.delete();
                }

                try {
                    createCommandScript(scriptPath, launcherPath.getAbsolutePath(), cmdName, cs.getArgs());
                    System.out.println("Created command-line script: " + scriptPath.getAbsolutePath());
                    createdFiles.add(scriptPath);
                    anyCreated = true;
                } catch (IOException ioe) {
                    System.err.println("Warning: Failed to create command script for " + cmdName + ": " + ioe.getMessage());
                }
            }

            // Persist command metadata
            try {
                boolean pathUpdated = false;
                String path = System.getenv("PATH");
                String localBinPath = localBinDir.getAbsolutePath();
                if (path == null || !path.contains(localBinPath)) {
                    pathUpdated = addToPath(localBinDir);
                } else {
                    pathUpdated = true;
                }
                persistCommandMetadata(localBinDir, commands, pathUpdated);
            } catch (IOException ioe) {
                System.err.println("Warning: Failed to persist command metadata: " + ioe.getMessage());
            }
        }

        // Create traditional single symlink for primary command if requested
        if (settings.isInstallCliLauncher()) {
            String commandName = deriveCommandName(settings);
            File symlinkPath = new File(localBinDir, commandName);

            if (symlinkPath.exists()) {
                symlinkPath.delete();
            }

            try {
                Process p = Runtime.getRuntime().exec(new String[]{"ln", "-s", launcherPath.getAbsolutePath(), symlinkPath.getAbsolutePath()});
                int result = p.waitFor();
                if (result == 0) {
                    System.out.println("Created command-line symlink: " + symlinkPath.getAbsolutePath());
                    settings.setCommandLineSymlinkCreated(true);
                    createdFiles.add(symlinkPath);
                    anyCreated = true;
                } else {
                    System.err.println("Warning: Failed to create command-line symlink. Exit code " + result);
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to create command-line symlink: " + e.getMessage());
            }
        }

        // Add to PATH if any files were created
        if (anyCreated) {
            String path = System.getenv("PATH");
            String localBinPath = localBinDir.getAbsolutePath();
            if (path == null || !path.contains(localBinPath)) {
                boolean pathUpdated = addToPath(localBinDir);
                settings.setAddedToPath(pathUpdated);
            } else {
                settings.setAddedToPath(true);
            }
        }

        return createdFiles;
    }

    @Override
    public void uninstallCommands(File appDir) {
        if (appDir == null || !appDir.exists()) {
            System.err.println("Warning: App directory does not exist: " + appDir);
            return;
        }

        File localBinDir = new File(System.getProperty("user.home"), ".local" + File.separator + "bin");
        
        // Try to load metadata from the default binDir location first
        File metadataFileInBinDir = new File(localBinDir, CliInstallerConstants.CLI_METADATA_FILE);
        if (metadataFileInBinDir.exists()) {
            try {
                String content = IOUtil.readToString(new FileInputStream(metadataFileInBinDir));
                JSONObject metadata = new JSONObject(content);
                if (metadata.has("binDir")) {
                    localBinDir = new File(metadata.getString("binDir"));
                }
            } catch (IOException e) {
                // Fall back to default localBinDir
            }
        }
        
        // Also try to load metadata from appDir if it exists there
        File metadataFile = new File(appDir, CliInstallerConstants.CLI_METADATA_FILE);
        if (metadataFile.exists()) {
            try {
                String content = IOUtil.readToString(new FileInputStream(metadataFile));
                JSONObject metadata = new JSONObject(content);
                if (metadata.has("binDir")) {
                    localBinDir = new File(metadata.getString("binDir"));
                }
            } catch (IOException e) {
                // Fall back to determined localBinDir
            }
        }

        try {
            // Load metadata to find installed commands
            List<CommandSpec> commands = loadCommandMetadata(localBinDir);
            if (commands != null && !commands.isEmpty()) {
                for (CommandSpec cs : commands) {
                    File scriptPath = new File(localBinDir, cs.getName());
                    if (scriptPath.exists()) {
                        scriptPath.delete();
                        System.out.println("Removed command script: " + scriptPath.getAbsolutePath());
                    }
                }
            }

            // Remove metadata files from both locations
            metadataFileInBinDir = new File(localBinDir, CliInstallerConstants.CLI_METADATA_FILE);
            if (metadataFileInBinDir.exists()) {
                metadataFileInBinDir.delete();
                System.out.println("Removed command metadata file");
            }
            if (metadataFile.exists()) {
                metadataFile.delete();
            }
        } catch (IOException ioe) {
            System.err.println("Warning: Failed to uninstall commands: " + ioe.getMessage());
        }
    }

    @Override
    public boolean addToPath(File localBinDir) {
        String shell = System.getenv("SHELL");
        String pathEnv = System.getenv("PATH");
        File homeDir = new File(System.getProperty("user.home"));
        return addToPath(localBinDir, shell, pathEnv, homeDir);
    }

    /**
     * Testable overload for addToPath with injectable dependencies.
     * 
     * @param localBinDir directory to add to PATH
     * @param shell shell path from environment (e.g., /bin/bash)
     * @param pathEnv PATH environment variable
     * @param homeDir user's home directory
     * @return true if PATH was updated or already contains the directory
     */
    static boolean addToPath(File localBinDir, String shell, String pathEnv, File homeDir) {
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

            // If PATH already contains localBinDir, nothing to do
            if (pathEnv != null && pathEnv.contains(localBinDir.getAbsolutePath())) {
                System.out.println("~/.local/bin is already in PATH");
                return true;
            }

            // Ensure configFile exists (create if necessary)
            if (!configFile.exists()) {
                File parent = configFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try {
                    configFile.createNewFile();
                } catch (Exception ignored) { }
            } else {
                // Check file contents to avoid duplicate entries
                String content = IOUtil.readToString(new FileInputStream(configFile));
                if (content.contains("$HOME/.local/bin") || content.contains(localBinDir.getAbsolutePath())) {
                    System.out.println("~/.local/bin is already in PATH configuration");
                    return true;
                }
            }

            // Append PATH export to the config file
            String pathExport = "\n# Added by jDeploy installer\nexport PATH=\"$HOME/.local/bin:$PATH\"\n";
            try (FileOutputStream fos = new FileOutputStream(configFile, true)) {
                fos.write(pathExport.getBytes());
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
     * Creates a shell script that invokes the launcher with the specified command name.
     * 
     * @param scriptPath the path where the script should be created
     * @param launcherPath the path to the launcher executable
     * @param commandName the command name to invoke
     * @param args additional command-line arguments (if any)
     * @throws IOException if the script cannot be created
     */
    private void createCommandScript(File scriptPath, String launcherPath, String commandName, List<String> args) throws IOException {
        StringBuilder script = new StringBuilder();
        script.append("#!/bin/bash\n");
        script.append("\"").append(escapeDoubleQuotes(launcherPath)).append("\" ");
        script.append(escapeDoubleQuotes(commandName));
        
        if (args != null && !args.isEmpty()) {
            for (String arg : args) {
                script.append(" \"").append(escapeDoubleQuotes(arg)).append("\"");
            }
        }
        
        script.append(" \"$@\"\n");

        try (FileOutputStream fos = new FileOutputStream(scriptPath)) {
            fos.write(script.toString().getBytes());
        }

        scriptPath.setExecutable(true, false);
    }

    /**
     * Persists the list of installed commands to a metadata file for later uninstallation.
     * 
     * @param localBinDir the ~/.local/bin directory
     * @param commands the list of commands to persist
     * @param pathUpdated whether the PATH was updated
     * @throws IOException if the metadata file cannot be written
     */
    private void persistCommandMetadata(File localBinDir, List<CommandSpec> commands, boolean pathUpdated) throws IOException {
        JSONObject metadata = new JSONObject();
        JSONArray commandsArray = new JSONArray();

        for (CommandSpec cmd : commands) {
            commandsArray.put(cmd.getName());
        }

        metadata.put(CliInstallerConstants.CREATED_WRAPPERS_KEY, commandsArray);
        metadata.put(CliInstallerConstants.PATH_UPDATED_KEY, pathUpdated);
        metadata.put("installedAt", System.currentTimeMillis());
        metadata.put("binDir", localBinDir.getAbsolutePath());

        File metadataFile = new File(localBinDir, CliInstallerConstants.CLI_METADATA_FILE);
        try (FileOutputStream fos = new FileOutputStream(metadataFile)) {
            fos.write(metadata.toString(2).getBytes());
        }
    }

    /**
     * Loads command metadata from the persistence file.
     * 
     * @param localBinDir the ~/.local/bin directory
     * @return list of CommandSpec objects, or empty list if metadata not found
     * @throws IOException if the metadata file cannot be read
     */
    private List<CommandSpec> loadCommandMetadata(File localBinDir) throws IOException {
        File metadataFile = new File(localBinDir, CliInstallerConstants.CLI_METADATA_FILE);
        if (!metadataFile.exists()) {
            return Collections.emptyList();
        }

        String content = IOUtil.readToString(new FileInputStream(metadataFile));
        JSONObject metadata = new JSONObject(content);
        JSONArray commandsArray = metadata.optJSONArray(CliInstallerConstants.CREATED_WRAPPERS_KEY);

        List<CommandSpec> commands = new ArrayList<>();
        if (commandsArray != null) {
            for (int i = 0; i < commandsArray.length(); i++) {
                String name = commandsArray.getString(i);
                commands.add(new CommandSpec(name, Collections.emptyList()));
            }
        }

        return commands;
    }

    /**
     * Derives the primary command name from installation settings.
     * 
     * @param settings the installation settings
     * @return the command name to use
     */
    private String deriveCommandName(InstallationSettings settings) {
        if (settings.getAppInfo() != null && settings.getAppInfo().getTitle() != null) {
            return settings.getAppInfo().getTitle()
                    .toLowerCase()
                    .replace(" ", "-")
                    .replaceAll("[^a-z0-9\\-]", "");
        }
        return "app";
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

package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.models.CommandSpec;
import ca.weblite.tools.io.IOUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for Unix-like (macOS and Linux) CLI command installation.
 * Encapsulates shared logic for command script creation, metadata persistence,
 * PATH management, and uninstallation.
 * 
 * Subclasses must implement {@link #writeCommandScript} to provide platform-specific
 * script generation.
 */
public abstract class AbstractUnixCliCommandInstaller implements CliCommandInstaller {

    /**
     * Determines the binary directory where CLI commands will be installed.
     * Uses the path from settings if provided, otherwise defaults to ~/.local/bin.
     *
     * @param settings installation settings containing optional custom command line path
     * @return the resolved binary directory
     */
    protected File getBinDir(InstallationSettings settings) {
        if (settings != null && settings.getCommandLinePath() != null && !settings.getCommandLinePath().isEmpty()) {
            return new File(settings.getCommandLinePath());
        }
        return new File(System.getProperty("user.home"), ".local" + File.separator + "bin");
    }

    /**
     * Saves metadata about installed CLI commands to a JSON file for later retrieval.
     * Stores command names, PATH update status, and the bin directory location.
     *
     * @param appDir       the application directory where metadata will be stored
     * @param createdFiles list of files created during installation
     * @param pathUpdated  whether the PATH was updated
     * @param binDir       the bin directory where commands were installed
     */
    protected void saveMetadata(File appDir, List<File> createdFiles, boolean pathUpdated, File binDir) {
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
            metadata.put("installedAt", System.currentTimeMillis());
            metadata.put("binDir", binDir.getAbsolutePath());

            // Write metadata to file
            try (FileOutputStream fos = new FileOutputStream(metadataFile)) {
                fos.write(metadata.toString(2).getBytes(StandardCharsets.UTF_8));
            }

            System.out.println("Saved CLI metadata to " + metadataFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Warning: Failed to save CLI metadata: " + e.getMessage());
        }
    }

    /**
     * Loads metadata about installed CLI commands from a JSON file.
     * First checks the appDir for metadata, then falls back to the default ~/.local/bin location.
     *
     * @param appDir the application directory where metadata is stored
     * @return the metadata JSONObject, or null if the metadata file does not exist
     */
    protected JSONObject loadMetadata(File appDir) {
        try {
            File metadataFile = new File(appDir, CliInstallerConstants.CLI_METADATA_FILE);
            if (!metadataFile.exists()) {
                return null;
            }

            String content = IOUtil.readToString(new FileInputStream(metadataFile));
            return new JSONObject(content);
        } catch (IOException e) {
            System.err.println("Warning: Failed to load CLI metadata: " + e.getMessage());
            return null;
        }
    }

    /**
     * Uninstalls CLI commands that were previously installed.
     * Loads metadata to find installed commands and removes them from the bin directory.
     *
     * @param appDir the application directory containing installed command files
     */
    @Override
    public void uninstallCommands(File appDir) {
        if (appDir == null || !appDir.exists()) {
            System.err.println("Warning: App directory does not exist: " + appDir);
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
            try {
                metadataFile.delete();
            } catch (Exception e) {
                System.err.println("Warning: Failed to remove metadata file: " + e.getMessage());
            }
        }
    }

    /**
     * Adds a directory to the system PATH environment variable.
     * Updates shell configuration files (.bashrc, .zshrc, etc.) to persist the change.
     * Delegates to UnixPathManager for the actual implementation.
     *
     * @param binDir the directory to add to PATH
     * @return true if the PATH was successfully updated, false otherwise
     */
    @Override
    public boolean addToPath(File binDir) {
        String shell = System.getenv("SHELL");
        String pathEnv = System.getenv("PATH");
        File homeDir = new File(System.getProperty("user.home"));
        return UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);
    }

    /**
     * Testable overload for addToPath with explicit environment parameters.
     * Delegates to UnixPathManager for the actual implementation.
     *
     * @param binDir   directory to add to PATH
     * @param shell    shell path from environment (e.g., /bin/bash)
     * @param pathEnv  PATH environment variable
     * @param homeDir  user's home directory to update config files under
     * @return true if PATH was updated or already contained the directory, false otherwise
     */
    public static boolean addToPath(File binDir, String shell, String pathEnv, File homeDir) {
        return UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);
    }

    /**
     * Writes a command script file for the given command.
     * Subclasses must implement this method with platform-specific script generation.
     *
     * @param scriptPath   the path where the script should be created
     * @param launcherPath the path to the launcher executable
     * @param commandName  the command name to invoke
     * @param args         additional command-line arguments (if any)
     * @throws IOException if the script cannot be created
     */
    protected abstract void writeCommandScript(File scriptPath, String launcherPath, String commandName, List<String> args) throws IOException;

    /**
     * Creates the bin directory if it doesn't exist.
     * Ensures the directory structure is ready for installing scripts.
     *
     * @param binDir the directory to create
     * @return true if the directory exists or was successfully created, false otherwise
     */
    protected boolean ensureBinDirExists(File binDir) {
        if (!binDir.exists()) {
            if (!binDir.mkdirs()) {
                System.err.println("Warning: Failed to create ~/.local/bin directory");
                return false;
            }
            System.out.println("Created ~/.local/bin directory");
        }
        return true;
    }

    /**
     * Loads installed command names from metadata.
     *
     * @param binDir the bin directory containing the metadata file
     * @return list of CommandSpec objects with command names, or empty list if metadata not found
     * @throws IOException if the metadata file cannot be read
     */
    protected List<CommandSpec> loadCommandMetadata(File binDir) throws IOException {
        File metadataFile = new File(binDir, CliInstallerConstants.CLI_METADATA_FILE);
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
     * Uses the app title if available, otherwise defaults to "app".
     *
     * @param settings the installation settings
     * @return the command name to use
     */
    protected String deriveCommandName(InstallationSettings settings) {
        if (settings != null && settings.getAppInfo() != null && settings.getAppInfo().getTitle() != null) {
            return settings.getAppInfo().getTitle()
                    .toLowerCase()
                    .replace(" ", "-")
                    .replaceAll("[^a-z0-9\\-]", "");
        }
        return "app";
    }
}

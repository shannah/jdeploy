package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.util.DebugLogger;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstract base class for Unix-like (macOS and Linux) CLI command installation.
 * Encapsulates shared logic for command script creation, metadata persistence,
 * PATH management, and uninstallation.
 * 
 * Subclasses must implement {@link #writeCommandScript} to provide platform-specific
 * script generation.
 */
public abstract class AbstractUnixCliCommandInstaller implements CliCommandInstaller {

    private CollisionHandler collisionHandler = new DefaultCollisionHandler();

    /**
     * Sets the collision handler for detecting and resolving command name conflicts.
     * 
     * @param collisionHandler the handler to use for collision resolution
     */
    public void setCollisionHandler(CollisionHandler collisionHandler) {
        this.collisionHandler = collisionHandler != null ? collisionHandler : new DefaultCollisionHandler();
    }

    /**
     * Determines the binary directory where CLI commands will be installed.
     * Uses the path from settings if provided, otherwise defaults to ~/.local/bin.
     *
     * @param settings installation settings containing optional custom command line path
     * @return the resolved binary directory
     */
    protected File getBinDir(InstallationSettings settings) {
        if (settings != null && settings.getCommandLinePath() != null && !settings.getCommandLinePath().isEmpty()) {
            return new File(settings.getCommandLinePath()).getParentFile();
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
     * Loads metadata from appDir to find installed commands and removes them from the bin directory.
     * Falls back to checking the default ~/.local/bin location for backwards compatibility.
     *
     * @param appDir the application directory that may contain the metadata file
     */
    @Override
    public void uninstallCommands(File appDir) {
        if (appDir == null || !appDir.exists()) {
            System.err.println("Warning: App directory does not exist: " + appDir);
            return;
        }

        // Try to load metadata from appDir first
        JSONObject metadata = loadMetadata(appDir);
        File metadataSearchDir = appDir;

        // Fall back to checking ~/.local/bin (for backwards compatibility)
        if (metadata == null) {
            File defaultBinDir = new File(System.getProperty("user.home"), ".local" + File.separator + "bin");
            metadata = loadMetadata(defaultBinDir);
            if (metadata != null) {
                metadataSearchDir = defaultBinDir;
            }
        }

        if (metadata == null) {
            return;
        }

        // Get binDir from metadata (where scripts were actually installed)
        File binDir;
        if (metadata.has("binDir")) {
            binDir = new File(metadata.getString("binDir"));
        } else {
            // Fallback to default
            binDir = new File(System.getProperty("user.home"), ".local" + File.separator + "bin");
        }

        // Remove installed commands
        if (metadata.has(CliInstallerConstants.CREATED_WRAPPERS_KEY)) {
            JSONArray installedCommands = metadata.getJSONArray(CliInstallerConstants.CREATED_WRAPPERS_KEY);

            for (int i = 0; i < installedCommands.length(); i++) {
                String cmdName = installedCommands.getString(i);
                File scriptPath = new File(binDir, cmdName);

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

        // Remove metadata file from where it was found
        File metadataFile = new File(metadataSearchDir, CliInstallerConstants.CLI_METADATA_FILE);
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
     * Protected helper method for installing command scripts.
     * Encapsulates the common logic of creating scripts and updating metadata.
     * Called by subclasses to implement their specific installation workflows.
     *
     * @param launcherPath the path to the main launcher executable
     * @param commands list of command specifications to install
     * @param binDir the directory where scripts will be installed
     * @return list of files created during script installation
     */
    protected List<File> installCommandScripts(File launcherPath, List<CommandSpec> commands, File binDir) {
        List<File> createdFiles = new ArrayList<>();

        if (commands == null || commands.isEmpty()) {
            return createdFiles;
        }

        // Create command scripts
        for (CommandSpec command : commands) {
            String cmdName = command.getName();
            File scriptPath = new File(binDir, cmdName);

            // Check for collision with existing script
            if (scriptPath.exists()) {
                String existingLauncherPath = extractLauncherPathFromScript(scriptPath);
                
                if (existingLauncherPath != null && !existingLauncherPath.equals(launcherPath.getAbsolutePath())) {
                    // Different app owns this command - invoke collision handler
                    CollisionAction action = collisionHandler.handleCollision(
                        cmdName, 
                        existingLauncherPath, 
                        launcherPath.getAbsolutePath()
                    );
                    
                    if (action == CollisionAction.SKIP) {
                        System.out.println("Skipping command '" + cmdName + "' - already owned by another app");
                        continue;
                    }
                    // OVERWRITE - fall through to delete and recreate
                    System.out.println("Overwriting command '" + cmdName + "' from another app");
                }
                // Same app or couldn't parse - silently overwrite
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

        return createdFiles;
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
     * Computes a user-friendly display path (using ~ for home directory).
     *
     * @param binDir  the binary directory
     * @param homeDir the user's home directory
     * @return the display path string
     */
    private String computeDisplayPath(File binDir, File homeDir) {
        String homePath = homeDir.getAbsolutePath();
        String binPath = binDir.getAbsolutePath();

        if (binPath.startsWith(homePath)) {
            String relativePath = binPath.substring(homePath.length());
            if (relativePath.startsWith(File.separator)) {
                relativePath = relativePath.substring(1);
            }
            return "~/" + relativePath.replace(File.separatorChar, '/');
        }
        return binPath;
    }

    /**
     * Creates the bin directory if it doesn't exist.
     * Ensures the directory structure is ready for installing scripts.
     *
     * @param binDir the directory to create
     * @return true if the directory exists or was successfully created, false otherwise
     */
    protected boolean ensureBinDirExists(File binDir) {
        DebugLogger.log("ensureBinDirExists() called with: " + (binDir != null ? binDir.getAbsolutePath() : "null"));
        
        if (binDir == null) {
            DebugLogger.log("ensureBinDirExists() failed: binDir is null");
            System.err.println("Warning: Failed to create bin directory - path is null");
            return false;
        }
        
        String displayPath = computeDisplayPath(binDir, new File(System.getProperty("user.home")));
        
        if (!binDir.exists()) {
            DebugLogger.log("binDir does not exist, attempting to create: " + binDir.getAbsolutePath());
            
            // Log parent directory status for debugging
            File parent = binDir.getParentFile();
            if (parent != null) {
                DebugLogger.log("  Parent directory: " + parent.getAbsolutePath());
                DebugLogger.log("  Parent exists: " + parent.exists());
                DebugLogger.log("  Parent isDirectory: " + parent.isDirectory());
                DebugLogger.log("  Parent canWrite: " + parent.canWrite());
            } else {
                DebugLogger.log("  Parent directory is null");
            }
            
            boolean created = binDir.mkdirs();
            DebugLogger.log("mkdirs() returned: " + created);
            
            if (!created) {
                // Check if it was created by another process (race condition)
                if (binDir.exists() && binDir.isDirectory()) {
                    DebugLogger.log("Directory now exists (created by another process)");
                    System.out.println("Created " + displayPath + " directory");
                    return true;
                }
                
                // Log additional diagnostics on failure
                DebugLogger.log("Failed to create directory. Diagnostics:");
                DebugLogger.log("  binDir.exists(): " + binDir.exists());
                DebugLogger.log("  binDir.isDirectory(): " + binDir.isDirectory());
                if (parent != null) {
                    DebugLogger.log("  Parent now exists: " + parent.exists());
                    DebugLogger.log("  Parent now canWrite: " + parent.canWrite());
                }
                
                System.err.println("Warning: Failed to create " + displayPath + " directory");
                return false;
            }
            DebugLogger.log("Successfully created directory: " + binDir.getAbsolutePath());
            System.out.println("Created " + displayPath + " directory");
        } else {
            DebugLogger.log("binDir already exists: " + binDir.getAbsolutePath());
            DebugLogger.log("  isDirectory: " + binDir.isDirectory());
            DebugLogger.log("  canWrite: " + binDir.canWrite());
            
            if (!binDir.isDirectory()) {
                DebugLogger.log("ensureBinDirExists() failed: path exists but is not a directory");
                System.err.println("Warning: " + displayPath + " exists but is not a directory");
                return false;
            }
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

    /**
     * Extracts the launcher path from an existing shell script.
     * Parses the script looking for the exec line with --jdeploy:command pattern.
     * 
     * @param scriptPath the path to the existing script
     * @return the launcher path if found, or null if parsing fails
     */
    protected String extractLauncherPathFromScript(File scriptPath) {
        try {
            String content = new String(Files.readAllBytes(scriptPath.toPath()), StandardCharsets.UTF_8);
            // Pattern matches: exec "path/to/launcher" --jdeploy:command=...
            // or: "path/to/launcher" --jdeploy:command=...
            Pattern pattern = Pattern.compile(
                "(?:exec\\s+)?\"([^\"]+)\"\\s+--jdeploy:command="
            );
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to read existing script " + scriptPath + ": " + e.getMessage());
        }
        return null;
    }
}

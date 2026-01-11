package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.logging.InstallationLogger;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.services.ServiceDescriptorService;
import ca.weblite.jdeploy.installer.services.ServiceDescriptor;
import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
import ca.weblite.jdeploy.installer.util.DebugLogger;
import ca.weblite.jdeploy.installer.util.ArchitectureUtil;
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
import java.util.Optional;
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
    protected InstallationLogger installationLogger;
    private ServiceDescriptorService serviceDescriptorService;

    /**
     * Sets the collision handler for detecting and resolving command name conflicts.
     *
     * @param collisionHandler the handler to use for collision resolution
     */
    public void setCollisionHandler(CollisionHandler collisionHandler) {
        this.collisionHandler = collisionHandler != null ? collisionHandler : new DefaultCollisionHandler();
    }

    /**
     * Sets the installation logger for recording detailed operation logs.
     *
     * @param logger the installation logger to use
     */
    public void setInstallationLogger(InstallationLogger logger) {
        this.installationLogger = logger;
    }

    /**
     * Sets the service descriptor service for managing service lifecycle.
     *
     * @param service the service descriptor service to use
     */
    public void setServiceDescriptorService(ServiceDescriptorService service) {
        this.serviceDescriptorService = service;
    }

    /**
     * Determines the binary directory where CLI commands will be installed.
     * Uses CliCommandBinDirResolver to compute the per-app bin directory (~/.jdeploy/bin-{arch}/{fqpn}/).
     * This is ONLY for CLI Commands (jdeploy.commands), NOT for CLI Launcher.
     *
     * @param settings installation settings containing package name and source
     * @return the resolved binary directory (per-app in ~/.jdeploy/bin-{arch}/{fqpn}/)
     * @throws IllegalArgumentException if packageName is null or empty
     */
    protected File getBinDir(InstallationSettings settings) {
        if (settings == null || settings.getPackageName() == null || settings.getPackageName().trim().isEmpty()) {
            throw new IllegalArgumentException("InstallationSettings must contain a non-empty packageName for CLI commands installation");
        }
        File homeDir = getHomeDir();
        return CliCommandBinDirResolver.getPerAppBinDir(settings.getPackageName(), settings.getSource(), homeDir);
    }

    /**
     * Returns the directory for CLI Launcher (single symlink) installation.
     * This is ONLY for CLI Launcher (jdeploy.command), NOT for CLI Commands.
     * On Linux, this returns ~/.local/bin for backwards compatibility.
     *
     * @return the CLI Launcher bin directory (~/.local/bin on Linux)
     */
    protected File getCliLauncherBinDir() {
        File homeDir = getHomeDir();
        return new File(homeDir, ".local" + File.separator + "bin");
    }

    /**
     * Saves metadata about installed CLI commands to a JSON file for later retrieval.
     * Stores command names, PATH update status, the bin directory location, and package information.
     *
     * @param appDir       the application directory where metadata will be stored
     * @param createdFiles list of files created during installation
     * @param pathUpdated  whether the PATH was updated
     * @param binDir       the bin directory where commands were installed
     * @param packageName  the package name (for manifest-based cleanup)
     * @param source       the source URL (null for NPM packages, or GitHub URL for GitHub packages)
     */
    protected void saveMetadata(File appDir, List<File> createdFiles, boolean pathUpdated, File binDir, String packageName, String source) {
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
            metadata.put("packageName", packageName);
            if (source != null) {
                metadata.put("source", source);
            }

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
     * Uses manifest-based cleanup when available, falls back to legacy metadata for backwards compatibility.
     * Removes command scripts, per-app directories, and manifest files.
     *
     * @param appDir the application directory that may contain the metadata file
     */
    @Override
    public void uninstallCommands(File appDir) {
        if (appDir == null || !appDir.exists()) {
            System.err.println("Warning: App directory does not exist: " + appDir);
            return;
        }

        // First, try to load legacy metadata to get packageName and source
        JSONObject metadata = loadMetadata(appDir);
        if (metadata == null) {
            // Fall back to checking ~/.local/bin (for backwards compatibility)
            File defaultBinDir = new File(System.getProperty("user.home"), ".local" + File.separator + "bin");
            metadata = loadMetadata(defaultBinDir);
        }
        
        if (metadata == null) {
            return;
        }

        // Extract packageName and source from metadata
        String packageName = metadata.optString("packageName", null);
        String source = metadata.optString("source", null);
        if (source != null && source.isEmpty()) {
            source = null;  // Treat empty string as null
        }

        // Try to load manifest from repository
        CliCommandManifestRepository manifestRepository = createManifestRepository();
        Optional<CliCommandManifest> manifestOpt = Optional.empty();
        
        if (packageName != null && !packageName.isEmpty()) {
            try {
                manifestOpt = manifestRepository.load(packageName, source);
            } catch (Exception e) {
                System.err.println("Warning: Failed to load manifest: " + e.getMessage());
            }
        }

        File binDir;
        List<String> commandNames = new ArrayList<>();
        boolean pathUpdated = false;
        
        if (manifestOpt.isPresent()) {
            // Use manifest data
            CliCommandManifest manifest = manifestOpt.get();
            binDir = manifest.getBinDir();
            commandNames = manifest.getCommandNames();
            pathUpdated = manifest.isPathUpdated();
        } else {
            // Fall back to legacy metadata
            if (metadata.has("binDir")) {
                binDir = new File(metadata.getString("binDir"));
            } else {
                binDir = new File(System.getProperty("user.home"), ".local" + File.separator + "bin");
            }
            
            if (metadata.has(CliInstallerConstants.CREATED_WRAPPERS_KEY)) {
                JSONArray installedCommands = metadata.getJSONArray(CliInstallerConstants.CREATED_WRAPPERS_KEY);
                for (int i = 0; i < installedCommands.length(); i++) {
                    commandNames.add(installedCommands.getString(i));
                }
            }
            pathUpdated = metadata.optBoolean(CliInstallerConstants.PATH_UPDATED_KEY, false);
        }

        // Stop and unregister services before removing command scripts
        if (packageName != null && !packageName.isEmpty()) {
            // Extract branch name from source if present (simplified - may need refinement)
            String branchName = extractBranchName(source);
            // Find launcher path from appDir
            File launcherPath = findLauncherPath(appDir);
            stopAndUnregisterServices(packageName, source, branchName, launcherPath);
        }

        // Remove installed command scripts
        for (String cmdName : commandNames) {
            File scriptPath = new File(binDir, cmdName);
            if (scriptPath.exists()) {
                try {
                    scriptPath.delete();
                    System.out.println("Removed command-line script: " + scriptPath.getAbsolutePath());
                    if (installationLogger != null) {
                        installationLogger.logCliCommand(cmdName, InstallationLogger.FileOperation.DELETED,
                                scriptPath.getAbsolutePath(), null);
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to remove command script for " + cmdName + ": " + e.getMessage());
                    if (installationLogger != null) {
                        installationLogger.logCliCommand(cmdName, InstallationLogger.FileOperation.FAILED,
                                scriptPath.getAbsolutePath(), e.getMessage());
                    }
                }
            }
        }

        // Remove the per-app bin directory (~/.jdeploy/bin-{arch}/{fqpn}/)
        // This is the binDir from manifest, NOT the shared bin directory
        if (binDir != null && binDir.exists() && binDir.isDirectory()) {
            // Only remove if it's a per-app directory (contains arch suffix pattern)
            String binDirPath = binDir.getAbsolutePath();
            if (binDirPath.contains(".jdeploy" + File.separator + "bin-")) {
                // Remove the per-app bin directory but NOT parent directories
                try {
                    // Delete all remaining files in the directory
                    File[] remainingFiles = binDir.listFiles();
                    if (remainingFiles != null) {
                        for (File f : remainingFiles) {
                            f.delete();
                            if (installationLogger != null) {
                                installationLogger.logFileOperation(InstallationLogger.FileOperation.DELETED,
                                        f.getAbsolutePath(), "Remaining file in bin directory");
                            }
                        }
                    }
                    binDir.delete();
                    System.out.println("Removed per-app bin directory: " + binDir.getAbsolutePath());
                    if (installationLogger != null) {
                        installationLogger.logDirectoryOperation(InstallationLogger.DirectoryOperation.DELETED,
                                binDir.getAbsolutePath(), "Per-app CLI bin directory");
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to remove per-app bin directory: " + e.getMessage());
                    if (installationLogger != null) {
                        installationLogger.logDirectoryOperation(InstallationLogger.DirectoryOperation.FAILED,
                                binDir.getAbsolutePath(), e.getMessage());
                    }
                }
            }
        }

        // Remove PATH entry if it was added (handled by subclasses or platform-specific logic)
        // For Unix, we don't automatically remove PATH entries as they're in shell config files
        // which users may have customized

        // Delete manifest file via repository
        if (packageName != null && !packageName.isEmpty()) {
            try {
                manifestRepository.delete(packageName, source);
                System.out.println("Deleted manifest for package: " + packageName);
                if (installationLogger != null) {
                    installationLogger.logInfo("Deleted CLI command manifest for package: " + packageName);
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to delete manifest: " + e.getMessage());
                if (installationLogger != null) {
                    installationLogger.logError("Failed to delete manifest: " + e.getMessage());
                }
            }
        }

        // Remove legacy metadata file from appDir
        File metadataFile = new File(appDir, CliInstallerConstants.CLI_METADATA_FILE);
        if (metadataFile.exists()) {
            try {
                metadataFile.delete();
                if (installationLogger != null) {
                    installationLogger.logFileOperation(InstallationLogger.FileOperation.DELETED,
                            metadataFile.getAbsolutePath(), "CLI metadata file");
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to remove metadata file: " + e.getMessage());
                if (installationLogger != null) {
                    installationLogger.logFileOperation(InstallationLogger.FileOperation.FAILED,
                            metadataFile.getAbsolutePath(), e.getMessage());
                }
            }
        }
    }

    /**
     * Returns the user's home directory. Protected to allow test overrides.
     *
     * @return the home directory
     */
    protected File getHomeDir() {
        return new File(System.getProperty("user.home"));
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
        File homeDir = getHomeDir();
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
                        if (installationLogger != null) {
                            installationLogger.logCliCommand(cmdName, InstallationLogger.FileOperation.SKIPPED_COLLISION,
                                    scriptPath.getAbsolutePath(), "Owned by another app: " + existingLauncherPath);
                        }
                        continue;
                    }
                    // OVERWRITE - fall through to delete and recreate
                    System.out.println("Overwriting command '" + cmdName + "' from another app");
                    if (installationLogger != null) {
                        installationLogger.logCliCommand(cmdName, InstallationLogger.FileOperation.OVERWRITTEN,
                                scriptPath.getAbsolutePath(), "Overwriting from another app: " + existingLauncherPath);
                    }
                }
                // Same app or couldn't parse - silently overwrite
                try {
                    scriptPath.delete();
                } catch (Exception e) {
                    System.err.println("Warning: Failed to delete existing script for " + cmdName);
                }
            }

            try {
                writeCommandScript(scriptPath, launcherPath.getAbsolutePath(), command);
                System.out.println("Created command-line script: " + scriptPath.getAbsolutePath());
                if (installationLogger != null) {
                    installationLogger.logCliCommand(cmdName, InstallationLogger.FileOperation.CREATED,
                            scriptPath.getAbsolutePath(), null);
                }
                createdFiles.add(scriptPath);
            } catch (IOException ioe) {
                System.err.println("Warning: Failed to create command script for " + cmdName + ": " + ioe.getMessage());
                if (installationLogger != null) {
                    installationLogger.logCliCommand(cmdName, InstallationLogger.FileOperation.FAILED,
                            scriptPath.getAbsolutePath(), ioe.getMessage());
                }
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
     * @param command      the command specification including name, args, and implementations
     * @throws IOException if the script cannot be created
     */
    protected abstract void writeCommandScript(File scriptPath, String launcherPath, CommandSpec command) throws IOException;

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
                    if (installationLogger != null) {
                        installationLogger.logDirectoryOperation(InstallationLogger.DirectoryOperation.CREATED,
                                binDir.getAbsolutePath(), "CLI bin directory (race condition)");
                    }
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
                if (installationLogger != null) {
                    installationLogger.logDirectoryOperation(InstallationLogger.DirectoryOperation.FAILED,
                            binDir.getAbsolutePath(), "Failed to create CLI bin directory");
                }
                return false;
            }
            DebugLogger.log("Successfully created directory: " + binDir.getAbsolutePath());
            System.out.println("Created " + displayPath + " directory");
            if (installationLogger != null) {
                installationLogger.logDirectoryOperation(InstallationLogger.DirectoryOperation.CREATED,
                        binDir.getAbsolutePath(), "CLI bin directory");
            }
        } else {
            DebugLogger.log("binDir already exists: " + binDir.getAbsolutePath());
            DebugLogger.log("  isDirectory: " + binDir.isDirectory());
            DebugLogger.log("  canWrite: " + binDir.canWrite());

            if (!binDir.isDirectory()) {
                DebugLogger.log("ensureBinDirExists() failed: path exists but is not a directory");
                System.err.println("Warning: " + displayPath + " exists but is not a directory");
                if (installationLogger != null) {
                    installationLogger.logDirectoryOperation(InstallationLogger.DirectoryOperation.FAILED,
                            binDir.getAbsolutePath(), "Path exists but is not a directory");
                }
                return false;
            }
            if (installationLogger != null) {
                installationLogger.logDirectoryOperation(InstallationLogger.DirectoryOperation.SKIPPED_EXISTS,
                        binDir.getAbsolutePath(), "CLI bin directory already exists");
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
                commands.add(new CommandSpec(name, null, Collections.emptyList()));
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

    /**
     * Extracts the branch name from a source URL if present.
     * For now, returns null (branch installations are handled separately).
     * TODO: Implement branch name extraction logic when needed.
     *
     * @param source The source URL
     * @return The branch name, or null
     */
    private String extractBranchName(String source) {
        // Branch installations are typically handled with explicit branch names
        // For now, return null for non-branch installations
        return null;
    }

    /**
     * Finds the launcher executable in the app directory.
     * Looks for common launcher file names in the app directory.
     *
     * @param appDir The application directory
     * @return The launcher file, or null if not found
     */
    private File findLauncherPath(File appDir) {
        if (appDir == null || !appDir.exists()) {
            return null;
        }

        // Look for common launcher names
        String[] launcherNames = {"launcher", "Client4JLauncher", "jdeploy-launcher"};
        for (String name : launcherNames) {
            File launcher = new File(appDir, name);
            if (launcher.exists() && launcher.canExecute()) {
                return launcher;
            }
        }

        // Look for any executable file
        File[] files = appDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.canExecute()) {
                    return file;
                }
            }
        }

        return null;
    }

    /**
     * Registers services for commands that implement service_controller.
     * Called after successful command installation.
     *
     * @param commands The list of installed commands
     * @param packageName The package name
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param version The package version
     * @param branchName The branch name (always null since branches don't support CLI commands)
     */
    protected void registerServices(List<CommandSpec> commands, String packageName, String source, String version, String branchName) {
        if (serviceDescriptorService == null) {
            // Service management not configured, skip registration
            return;
        }

        if (commands == null || commands.isEmpty()) {
            return;
        }

        // Branch installations should never reach here since CLI commands aren't installed for branches
        // But check defensively anyway
        if (branchName != null && !branchName.trim().isEmpty()) {
            System.out.println("Skipping service registration for branch installation");
            if (installationLogger != null) {
                installationLogger.logInfo("Branch installation detected - skipping service registration");
            }
            return;
        }

        for (CommandSpec command : commands) {
            if (command.implements_("service_controller")) {
                try {
                    serviceDescriptorService.registerService(command, packageName, version, source, branchName);
                    System.out.println("Registered service: " + command.getName());
                    if (installationLogger != null) {
                        installationLogger.logInfo("Registered service: " + command.getName());
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to register service " + command.getName() + ": " + e.getMessage());
                    if (installationLogger != null) {
                        installationLogger.logError("Failed to register service " + command.getName() + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Stops and unregisters all services for a package.
     * Called before uninstalling commands.
     *
     * @param packageName The package name
     * @param source The source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param branchName The branch name (null for non-branch installations)
     * @param launcherPath The launcher executable (for stopping services)
     */
    protected void stopAndUnregisterServices(String packageName, String source, String branchName, File launcherPath) {
        if (serviceDescriptorService == null) {
            // Service management not configured, skip
            return;
        }

        try {
            List<ServiceDescriptor> services = serviceDescriptorService.listServices(packageName, source, branchName);

            for (ServiceDescriptor service : services) {
                // Stop the service if launcher is available
                if (launcherPath != null && launcherPath.exists()) {
                    try {
                        String commandName = service.getCommandName();
                        System.out.println("Stopping service: " + commandName);

                        // Execute: <command> service stop
                        ProcessBuilder pb = new ProcessBuilder(commandName, "service", "stop");
                        Process process = pb.start();
                        int exitCode = process.waitFor();

                        if (exitCode == 0) {
                            System.out.println("Successfully stopped service: " + commandName);
                            if (installationLogger != null) {
                                installationLogger.logInfo("Stopped service: " + commandName);
                            }
                        } else {
                            System.err.println("Warning: Service stop returned exit code " + exitCode + " for " + commandName);
                        }
                    } catch (Exception e) {
                        System.err.println("Warning: Failed to stop service " + service.getCommandName() + ": " + e.getMessage());
                        if (installationLogger != null) {
                            installationLogger.logError("Failed to stop service " + service.getCommandName() + ": " + e.getMessage());
                        }
                    }
                }

                // Unregister the service
                try {
                    serviceDescriptorService.unregisterService(packageName, source, service.getCommandName(), branchName);
                    System.out.println("Unregistered service: " + service.getCommandName());
                    if (installationLogger != null) {
                        installationLogger.logInfo("Unregistered service: " + service.getCommandName());
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to unregister service " + service.getCommandName() + ": " + e.getMessage());
                    if (installationLogger != null) {
                        installationLogger.logError("Failed to unregister service " + service.getCommandName() + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to stop/unregister services: " + e.getMessage());
            if (installationLogger != null) {
                installationLogger.logError("Failed to stop/unregister services: " + e.getMessage());
            }
        }
    }

    /**
     * Creates a manifest repository instance. Protected to allow test overrides.
     *
     * @return a CliCommandManifestRepository instance
     */
    protected CliCommandManifestRepository createManifestRepository() {
        return new FileCliCommandManifestRepository();
    }
}

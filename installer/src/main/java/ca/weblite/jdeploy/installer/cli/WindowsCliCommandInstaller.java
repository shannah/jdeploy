package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.logging.InstallationLogger;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
import ca.weblite.jdeploy.installer.util.ArchitectureUtil;
import ca.weblite.jdeploy.installer.win.InstallWindowsRegistry;
import ca.weblite.jdeploy.installer.win.RegistryOperations;
import ca.weblite.jdeploy.models.CommandSpec;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Windows implementation of CLI command installer.
 * 
 * Handles creation of .cmd wrapper scripts, PATH registry updates,
 * and metadata persistence for CLI command installation on Windows.
 */
public class WindowsCliCommandInstaller implements CliCommandInstaller {

    private CollisionHandler collisionHandler = new DefaultCollisionHandler();
    private RegistryOperations registryOperations;
    private InstallationLogger installationLogger;

    /**
     * Sets the collision handler for detecting and resolving command name conflicts.
     * 
     * @param collisionHandler the handler to use for collision resolution
     */
    public void setCollisionHandler(CollisionHandler collisionHandler) {
        this.collisionHandler = collisionHandler != null ? collisionHandler : new DefaultCollisionHandler();
    }

    /**
     * Sets the registry operations implementation for testing or custom scenarios.
     * If not set, JnaRegistryOperations will be used by default.
     *
     * @param registryOperations the RegistryOperations implementation to use
     */
    public void setRegistryOperations(RegistryOperations registryOperations) {
        this.registryOperations = registryOperations;
    }

    /**
     * Sets the installation logger for detailed operation logging.
     *
     * @param logger the InstallationLogger to use (may be null)
     */
    public void setInstallationLogger(InstallationLogger logger) {
        this.installationLogger = logger;
    }

    @Override
    public List<File> installCommands(File launcherPath, List<CommandSpec> commands, InstallationSettings settings) {
        List<File> createdFiles = new ArrayList<>();

        if (commands == null || commands.isEmpty()) {
            return createdFiles;
        }

        // Determine per-app bin directory location for both wrappers and PATH registry
        File userBinDir = CliCommandBinDirResolver.getPerAppBinDir(
            settings.getPackageName(),
            settings.getSource()
        );

        // Use the same per-app bin directory for PATH registry operations
        File perAppPathDir = userBinDir;

        try {
            // Write .cmd wrappers for each command
            List<File> wrapperFiles = writeCommandWrappersForTest(userBinDir, launcherPath, commands);
            createdFiles.addAll(wrapperFiles);

            // Update user PATH via registry using per-app directory
            boolean pathUpdated = addToPath(perAppPathDir);

            // Update Git Bash path if applicable using per-app directory
            boolean gitBashPathUpdated = addToGitBashPath(perAppPathDir);

            // Determine if the launcher is the CLI variant
            File cliExePath = null;
            if (launcherPath.getName().contains(CliInstallerConstants.CLI_LAUNCHER_SUFFIX)) {
                cliExePath = launcherPath;
            }

            // Persist metadata for uninstall in the app directory
            File appDir = launcherPath.getParentFile();
            persistMetadata(appDir, wrapperFiles, pathUpdated, gitBashPathUpdated, cliExePath, userBinDir, perAppPathDir, settings.getPackageName(), settings.getSource());

        } catch (IOException e) {
            System.err.println("Warning: Failed to install CLI commands: " + e.getMessage());
        }

        return createdFiles;
    }

    @Override
    public void uninstallCommands(File appDir) {
        if (appDir == null || !appDir.exists()) {
            return;
        }

        // Read metadata file
        File metadataFile = new File(appDir, CliInstallerConstants.CLI_METADATA_FILE);
        if (!metadataFile.exists()) {
            return;
        }

        try {
            String metadataContent = FileUtils.readFileToString(metadataFile, "UTF-8");
            JSONObject metadata = new JSONObject(metadataContent);

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

            // Clean up CLI exe if it was installed
            String cliExeName = metadata.optString(CliInstallerConstants.CLI_EXE_KEY, null);
            if (cliExeName != null && !cliExeName.isEmpty()) {
                File cliExeFile = new File(appDir, cliExeName);
                if (cliExeFile.exists()) {
                    if (cliExeFile.delete()) {
                        if (installationLogger != null) {
                            installationLogger.logFileOperation(InstallationLogger.FileOperation.DELETED,
                                cliExeFile.getAbsolutePath(), "CLI executable");
                        }
                    } else {
                        System.err.println("Warning: Failed to delete CLI exe: " + cliExeFile.getAbsolutePath());
                        if (installationLogger != null) {
                            installationLogger.logFileOperation(InstallationLogger.FileOperation.FAILED,
                                cliExeFile.getAbsolutePath(), "Failed to delete CLI executable");
                        }
                    }
                }
            }

            // Determine per-app bin directory for wrapper cleanup
            File userBinDir;
            if (metadata.has("sharedBinDir")) {
                userBinDir = new File(metadata.getString("sharedBinDir"));
            } else if (metadata.has("perAppPathDir")) {
                userBinDir = new File(metadata.getString("perAppPathDir"));
            } else {
                // Fallback: compute the per-app directory from package name and source
                String packageNameMeta = metadata.optString("packageName", null);
                String sourceMeta = metadata.optString("source", null);
                if (sourceMeta != null && sourceMeta.isEmpty()) {
                    sourceMeta = null;
                }
                if (packageNameMeta != null && !packageNameMeta.isEmpty()) {
                    userBinDir = CliCommandBinDirResolver.getPerAppBinDir(packageNameMeta, sourceMeta);
                } else {
                    // Last resort fallback for very old metadata without package info
                    userBinDir = new File(System.getProperty("user.home") + File.separator + ".jdeploy" + File.separator + "bin");
                }
            }

            // Clean up wrapper files
            JSONArray wrappersArray = metadata.optJSONArray(CliInstallerConstants.CREATED_WRAPPERS_KEY);
            if (wrappersArray != null) {
                for (int i = 0; i < wrappersArray.length(); i++) {
                    String wrapperName = wrappersArray.getString(i);
                    File wrapperFile = new File(userBinDir, wrapperName);
                    if (wrapperFile.exists()) {
                        if (wrapperFile.delete()) {
                            if (installationLogger != null) {
                                installationLogger.logCliCommand(wrapperName, InstallationLogger.FileOperation.DELETED,
                                    wrapperFile.getAbsolutePath(), null);
                            }
                        } else {
                            System.err.println("Warning: Failed to delete wrapper file: " + wrapperFile.getAbsolutePath());
                            if (installationLogger != null) {
                                installationLogger.logFileOperation(InstallationLogger.FileOperation.FAILED,
                                    wrapperFile.getAbsolutePath(), "Failed to delete wrapper");
                            }
                        }
                    }

                    // Also attempt to delete the extensionless version (for Git Bash)
                    String shWrapperName = wrapperName.endsWith(".cmd")
                            ? wrapperName.substring(0, wrapperName.length() - 4)
                            : wrapperName;

                    File shWrapperFile = new File(userBinDir, shWrapperName);
                    if (shWrapperFile.exists()) {
                        if (shWrapperFile.delete()) {
                            if (installationLogger != null) {
                                installationLogger.logFileOperation(InstallationLogger.FileOperation.DELETED,
                                    shWrapperFile.getAbsolutePath(), "Git Bash wrapper");
                            }
                        } else {
                            System.err.println("Warning: Failed to delete shell wrapper file: " + shWrapperFile.getAbsolutePath());
                        }
                    }
                }
            }

            // Remove the per-app bin directory if it contains the arch suffix pattern
            if (userBinDir != null && userBinDir.exists() && userBinDir.isDirectory()) {
                String binDirPath = userBinDir.getAbsolutePath();
                if (binDirPath.contains(".jdeploy" + File.separator + "bin-")) {
                    try {
                        File[] remainingFiles = userBinDir.listFiles();
                        if (remainingFiles != null) {
                            for (File f : remainingFiles) {
                                f.delete();
                            }
                        }
                        userBinDir.delete();
                        System.out.println("Removed per-app bin directory: " + binDirPath);
                        if (installationLogger != null) {
                            installationLogger.logDirectoryOperation(InstallationLogger.DirectoryOperation.DELETED,
                                binDirPath, "Per-app bin directory");
                        }
                    } catch (Exception e) {
                        System.err.println("Warning: Failed to remove per-app bin directory: " + e.getMessage());
                        if (installationLogger != null) {
                            installationLogger.logError("Failed to remove per-app bin directory: " + binDirPath, e);
                        }
                    }
                }
            }

            // Use the same per-app directory for PATH cleanup
            File perAppPathDir = userBinDir;

            // Remove from PATH if it was added
            if (metadata.optBoolean(CliInstallerConstants.PATH_UPDATED_KEY, false)) {
                removeFromPath(perAppPathDir);
                if (installationLogger != null) {
                    installationLogger.logPathChange(false, perAppPathDir.getAbsolutePath(), "Windows user PATH");
                }
            }

            // Remove from Git Bash path if it was added
            if (metadata.optBoolean(CliInstallerConstants.GIT_BASH_PATH_UPDATED_KEY, false)) {
                removeFromGitBashPath(perAppPathDir);
                if (installationLogger != null) {
                    installationLogger.logInfo("Removed from Git Bash PATH: " + perAppPathDir.getAbsolutePath());
                }
            }

            // Delete manifest file via repository
            if (packageName != null && !packageName.isEmpty()) {
                try {
                    manifestRepository.delete(packageName, source);
                    System.out.println("Deleted manifest for package: " + packageName);
                    if (installationLogger != null) {
                        installationLogger.logInfo("Deleted CLI manifest for package: " + packageName);
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to delete manifest: " + e.getMessage());
                }
            }

            // Delete legacy metadata file
            if (metadataFile.delete()) {
                if (installationLogger != null) {
                    installationLogger.logFileOperation(InstallationLogger.FileOperation.DELETED,
                        metadataFile.getAbsolutePath(), "CLI metadata file");
                }
            } else {
                System.err.println("Warning: Failed to delete metadata file: " + metadataFile.getAbsolutePath());
            }

        } catch (IOException e) {
            System.err.println("Warning: Failed to uninstall CLI commands: " + e.getMessage());
        }
    }

    @Override
    public boolean addToPath(File binDir) {
        if (binDir == null) {
            return false;
        }

        try {
            // Use InstallWindowsRegistry to manage PATH via registry
            InstallWindowsRegistry registry = createRegistryHelper();
            boolean result = registry.addToUserPath(binDir);
            if (installationLogger != null) {
                if (result) {
                    installationLogger.logPathChange(true, binDir.getAbsolutePath(), "Windows user PATH");
                } else {
                    installationLogger.logInfo("PATH already contains: " + binDir.getAbsolutePath());
                }
            }
            return result;
        } catch (Exception e) {
            System.err.println("Warning: Failed to update user PATH in registry: " + e.getMessage());
            if (installationLogger != null) {
                installationLogger.logError("Failed to update user PATH in registry", e);
            }
            return false;
        }
    }

    /**
     * Removes a directory from the user's PATH registry entry.
     * 
     * @param binDir the directory to remove from PATH
     * @return true if the PATH was successfully updated, false otherwise
     */
    private boolean removeFromPath(File binDir) {
        if (binDir == null) {
            return false;
        }

        try {
            InstallWindowsRegistry registry = createRegistryHelper();
            return registry.removeFromUserPath(binDir);
        } catch (Exception e) {
            System.err.println("Warning: Failed to remove from user PATH: " + e.getMessage());
            return false;
        }
    }

    /**
     * Write .cmd wrapper scripts for each command into the bin directory.
     * Public for testing and internal use by InstallWindows.
     * 
     * @param binDir the directory where wrappers will be created
     * @param launcherPath the path to the launcher executable
     * @param commands list of command specifications
     * @return list of created wrapper files
     * @throws IOException if wrapper creation fails
     */
    public List<File> writeCommandWrappersForTest(File binDir, File launcherPath, List<CommandSpec> commands) throws IOException {
        List<File> created = new ArrayList<>();

        if (!binDir.exists()) {
            if (!binDir.mkdirs()) {
                if (installationLogger != null) {
                    installationLogger.logDirectoryOperation(InstallationLogger.DirectoryOperation.FAILED,
                        binDir.getAbsolutePath(), "Failed to create");
                }
                throw new IOException("Failed to create bin directory: " + binDir.getAbsolutePath());
            }
            if (installationLogger != null) {
                installationLogger.logDirectoryOperation(InstallationLogger.DirectoryOperation.CREATED,
                    binDir.getAbsolutePath(), "CLI commands bin directory");
            }
        }

        for (CommandSpec cs : commands) {
            String name = cs.getName();
            File cmdWrapper = new File(binDir, name + ".cmd");
            File shWrapper = new File(binDir, name);

            if (installationLogger != null) {
                installationLogger.logInfo("Processing CLI command: " + name);
            }

            // Check for collision with existing wrapper (check .cmd as the primary indicator)
            boolean wasOverwritten = false;
            if (cmdWrapper.exists()) {
                String existingLauncherPath = extractLauncherPathFromCmdFile(cmdWrapper);

                if (existingLauncherPath != null && !existingLauncherPath.equals(launcherPath.getAbsolutePath())) {
                    // Different app owns this command - invoke collision handler
                    CollisionAction action = collisionHandler.handleCollision(
                        name,
                        existingLauncherPath,
                        launcherPath.getAbsolutePath()
                    );

                    if (action == CollisionAction.SKIP) {
                        System.out.println("Skipping command '" + name + "' - already owned by another app");
                        if (installationLogger != null) {
                            installationLogger.logCliCommand(name, InstallationLogger.FileOperation.SKIPPED_COLLISION,
                                cmdWrapper.getAbsolutePath(),
                                "Owned by: " + existingLauncherPath);
                        }
                        continue;
                    }
                    // OVERWRITE - fall through to delete and recreate
                    System.out.println("Overwriting command '" + name + "' from another app");
                    if (installationLogger != null) {
                        installationLogger.logInfo("Overwriting command from different app: " + existingLauncherPath);
                    }
                }
                // Same app or couldn't parse - silently overwrite
                wasOverwritten = true;
                cmdWrapper.delete();
                if (installationLogger != null) {
                    installationLogger.logFileOperation(InstallationLogger.FileOperation.DELETED,
                        cmdWrapper.getAbsolutePath(), "Existing wrapper (same app update)");
                }
            }
            if (shWrapper.exists()) {
                shWrapper.delete();
                if (installationLogger != null) {
                    installationLogger.logFileOperation(InstallationLogger.FileOperation.DELETED,
                        shWrapper.getAbsolutePath(), "Existing shell wrapper");
                }
            }

            // Generate wrapper content based on implementations
            String cmdContent = generateCmdContent(launcherPath, cs);
            String shContent = generateShellContent(launcherPath, cs);

            FileUtils.writeStringToFile(cmdWrapper, cmdContent, "UTF-8");
            cmdWrapper.setExecutable(true, false);
            created.add(cmdWrapper);
            if (installationLogger != null) {
                installationLogger.logCliCommand(name,
                    wasOverwritten ? InstallationLogger.FileOperation.OVERWRITTEN : InstallationLogger.FileOperation.CREATED,
                    cmdWrapper.getAbsolutePath(),
                    "Launcher: " + launcherPath.getAbsolutePath());
            }

            FileUtils.writeStringToFile(shWrapper, shContent, "UTF-8");
            shWrapper.setExecutable(true, false);
            if (installationLogger != null) {
                installationLogger.logFileOperation(
                    wasOverwritten ? InstallationLogger.FileOperation.OVERWRITTEN : InstallationLogger.FileOperation.CREATED,
                    shWrapper.getAbsolutePath(), "Git Bash wrapper for " + name);
            }
        }

        return created;
    }

    /**
     * Generates Windows batch (.cmd) wrapper content based on command implementations.
     *
     * @param launcherPath the path to the launcher executable
     * @param command      the command specification including implementations
     * @return the .cmd file content with \r\n line endings
     */
    private String generateCmdContent(File launcherPath, CommandSpec command) {
        StringBuilder sb = new StringBuilder();
        sb.append("@echo off\r\n");

        String launcherPathStr = launcherPath.getAbsolutePath();
        String commandName = command.getName();
        List<String> implementations = command.getImplementations();

        // Check for launcher implementation (highest priority, mutually exclusive)
        if (implementations.contains("launcher")) {
            // For launcher: just execute the binary directly with all args
            sb.append("\"").append(launcherPathStr).append("\" %*\r\n");
            return sb.toString();
        }

        boolean hasUpdater = implementations.contains("updater");
        boolean hasServiceController = implementations.contains("service_controller");

        if (hasUpdater || hasServiceController) {
            // Generate conditional batch script

            // Check for updater: single "update" argument
            if (hasUpdater) {
                sb.append("REM Check if single argument is \"update\"\r\n");
                sb.append("if \"%~1\"==\"update\" if \"%~2\"==\"\" (\r\n");
                sb.append("  \"").append(launcherPathStr).append("\" --jdeploy:update\r\n");
                sb.append("  goto :eof\r\n");
                sb.append(")\r\n\r\n");
            }

            // Check for service_controller: first argument is "service"
            if (hasServiceController) {
                sb.append("REM Check if first argument is \"service\"\r\n");
                sb.append("if \"%~1\"==\"service\" (\r\n");
                sb.append("  shift\r\n");
                sb.append("  \"").append(launcherPathStr).append("\" ");
                sb.append(CliInstallerConstants.JDEPLOY_COMMAND_ARG_PREFIX).append(commandName);
                sb.append(" --jdeploy:service %*\r\n");
                sb.append("  goto :eof\r\n");
                sb.append(")\r\n\r\n");
            }

            // Default: normal command
            sb.append("REM Default: normal command\r\n");
            sb.append("\"").append(launcherPathStr).append("\" ");
            sb.append(CliInstallerConstants.JDEPLOY_COMMAND_ARG_PREFIX).append(commandName);
            sb.append(" -- %*\r\n");
        } else {
            // Standard command (no special implementations)
            sb.append("\"").append(launcherPathStr).append("\" ");
            sb.append(CliInstallerConstants.JDEPLOY_COMMAND_ARG_PREFIX).append(commandName);
            sb.append(" -- %*\r\n");
        }

        return sb.toString();
    }

    /**
     * Generates shell script wrapper content for Git Bash / MSYS2 based on command implementations.
     *
     * @param launcherPath the path to the launcher executable
     * @param command      the command specification including implementations
     * @return the shell script content with \n line endings
     */
    private String generateShellContent(File launcherPath, CommandSpec command) {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/sh\n");

        String msysLauncherPath = convertToMsysPath(launcherPath);
        String commandName = command.getName();
        List<String> implementations = command.getImplementations();

        // Check for launcher implementation (highest priority, mutually exclusive)
        if (implementations.contains("launcher")) {
            // For launcher: just execute the binary directly with all args
            sb.append("exec \"").append(msysLauncherPath).append("\" \"$@\"\n");
            return sb.toString();
        }

        boolean hasUpdater = implementations.contains("updater");
        boolean hasServiceController = implementations.contains("service_controller");

        if (hasUpdater || hasServiceController) {
            // Generate conditional shell script

            // Check for updater: single "update" argument
            if (hasUpdater) {
                sb.append("# Check if single argument is \"update\"\n");
                sb.append("if [ \"$#\" -eq 1 ] && [ \"$1\" = \"update\" ]; then\n");
                sb.append("  exec \"").append(msysLauncherPath).append("\" --jdeploy:update\n");
                sb.append("fi\n\n");
            }

            // Check for service_controller: first argument is "service"
            if (hasServiceController) {
                sb.append("# Check if first argument is \"service\"\n");
                sb.append("if [ \"$1\" = \"service\" ]; then\n");
                sb.append("  shift\n");
                sb.append("  exec \"").append(msysLauncherPath).append("\" ");
                sb.append(CliInstallerConstants.JDEPLOY_COMMAND_ARG_PREFIX).append(commandName);
                sb.append(" --jdeploy:service \"$@\"\n");
                sb.append("fi\n\n");
            }

            // Default: normal command
            sb.append("# Default: normal command\n");
            sb.append("exec \"").append(msysLauncherPath).append("\" ");
            sb.append(CliInstallerConstants.JDEPLOY_COMMAND_ARG_PREFIX).append(commandName);
            sb.append(" -- \"$@\"\n");
        } else {
            // Standard command (no special implementations)
            sb.append("exec \"").append(msysLauncherPath).append("\" ");
            sb.append(CliInstallerConstants.JDEPLOY_COMMAND_ARG_PREFIX).append(commandName);
            sb.append(" -- \"$@\"\n");
        }

        return sb.toString();
    }

    /**
     * Extracts the launcher path from an existing .cmd wrapper file.
     * Parses the file looking for the pattern: "path\to\launcher.exe" --jdeploy:command=
     *
     * @param cmdFile the path to the existing .cmd file
     * @return the launcher path if found, or null if parsing fails
     */
    protected String extractLauncherPathFromCmdFile(File cmdFile) {
        try {
            String content = new String(Files.readAllBytes(cmdFile.toPath()), StandardCharsets.UTF_8);
            // Pattern matches: "path\to\launcher.exe" --jdeploy:command=
            Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s+--jdeploy:command=");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to read existing .cmd file " + cmdFile + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Persist CLI installation metadata to a JSON file for later uninstallation.
     * 
     * @param appDir the application directory where metadata will be stored
     * @param createdWrappers list of created wrapper files
     * @param pathUpdated whether the PATH was updated
     * @param gitBashPathUpdated whether the Git Bash path was updated
     * @param cliExePath the CLI launcher executable file, or null if not created
     * @param sharedBinDir the shared bin directory where wrappers are stored
     * @param perAppPathDir the per-app PATH directory for registry operations
     * @param packageName the package name (for manifest-based cleanup)
     * @param source the source URL (null for NPM packages, or GitHub URL for GitHub packages)
     * @throws IOException if metadata file write fails
     */
    private void persistMetadata(File appDir, List<File> createdWrappers, boolean pathUpdated, boolean gitBashPathUpdated, File cliExePath, File sharedBinDir, File perAppPathDir, String packageName, String source) throws IOException {
        JSONObject metadata = new JSONObject();

        // Store list of created wrapper file names
        JSONArray wrappersArray = new JSONArray();
        for (File wrapper : createdWrappers) {
            wrappersArray.put(wrapper.getName());
        }
        metadata.put(CliInstallerConstants.CREATED_WRAPPERS_KEY, wrappersArray);

        // Store whether PATH was updated
        metadata.put(CliInstallerConstants.PATH_UPDATED_KEY, pathUpdated);

        // Store whether Git Bash path was updated
        metadata.put(CliInstallerConstants.GIT_BASH_PATH_UPDATED_KEY, gitBashPathUpdated);

        // Store directory paths for uninstallation
        metadata.put("sharedBinDir", sharedBinDir.getAbsolutePath());
        metadata.put("perAppPathDir", perAppPathDir.getAbsolutePath());

        // Store CLI exe path if it exists
        if (cliExePath != null) {
            metadata.put(CliInstallerConstants.CLI_EXE_KEY, cliExePath.getName());
        }

        // Store package information for manifest-based cleanup
        metadata.put("packageName", packageName);
        if (source != null) {
            metadata.put("source", source);
        }

        // Write metadata file
        File metadataFile = new File(appDir, CliInstallerConstants.CLI_METADATA_FILE);
        FileUtils.writeStringToFile(metadataFile, metadata.toString(), "UTF-8");
    }

    /**
     * Adds the bin directory to the Git Bash path by modifying BOTH .bashrc AND .bash_profile.
     *
     * @param binDir the directory to add to the path
     * @return true if the path was updated in at least one file, false otherwise
     */
    private boolean addToGitBashPath(File binDir) {
        File homeDir = new File(System.getProperty("user.home"));
        String msysPath = convertToMsysPath(binDir);

        File bashProfile = new File(homeDir, ".bash_profile");
        File bashrc = new File(homeDir, ".bashrc");

        boolean profileUpdated = addPathToGitBashConfigFile(bashProfile, msysPath);
        boolean bashrcUpdated = addPathToGitBashConfigFile(bashrc, msysPath);

        return profileUpdated || bashrcUpdated;
    }

    /**
     * Helper to add a path to a specific Git Bash config file.
     */
    private boolean addPathToGitBashConfigFile(File configFile, String msysPath) {
        try {
            String content = "";
            if (configFile.exists()) {
                content = FileUtils.readFileToString(configFile, "UTF-8");
            } else {
                if (configFile.getParentFile() != null && !configFile.getParentFile().exists()) {
                    configFile.getParentFile().mkdirs();
                }
            }

            if (content.contains(msysPath)) {
                return true; // Already present
            }

            String exportLine = "export PATH=\"" + msysPath + ":$PATH\"";
            StringBuilder sb = new StringBuilder(content);
            if (!content.isEmpty() && !content.endsWith("\n")) {
                sb.append("\n");
            }
            sb.append(exportLine).append("\n");

            FileUtils.writeStringToFile(configFile, sb.toString(), "UTF-8");
            return true;
        } catch (IOException e) {
            System.err.println("Warning: Failed to update Git Bash config " + configFile.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Removes the bin directory from Git Bash configuration (.bashrc and .bash_profile).
     *
     * @param binDir the directory to remove
     * @return true if successfully processed
     */
    private boolean removeFromGitBashPath(File binDir) {
        File homeDir = new File(System.getProperty("user.home"));
        String msysPath = convertToMsysPath(binDir);

        boolean profileUpdated = removeFromGitBashConfigFile(new File(homeDir, ".bash_profile"), msysPath);
        boolean bashrcUpdated = removeFromGitBashConfigFile(new File(homeDir, ".bashrc"), msysPath);

        return profileUpdated || bashrcUpdated;
    }

    /**
     * Helper to remove a path from a specific Git Bash config file.
     */
    private boolean removeFromGitBashConfigFile(File configFile, String msysPath) {
        if (!configFile.exists()) {
            return false;
        }

        try {
            String content = FileUtils.readFileToString(configFile, "UTF-8");
            if (!content.contains(msysPath)) {
                return false;
            }

            String exportLine = "export PATH=\"" + msysPath + ":$PATH\"";
            String newContent = content.replace(exportLine + "\r\n", "")
                                       .replace(exportLine + "\n", "")
                                       .replace(exportLine, "");

            if (!newContent.equals(content)) {
                FileUtils.writeStringToFile(configFile, newContent.trim(), "UTF-8");
                return true;
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to remove from Git Bash config " + configFile.getName() + ": " + e.getMessage());
        }
        return false;
    }

    /**
     * Converts a Windows file path to MSYS2/Git Bash format.
     * E.g., C:\Users\Name -> /c/Users/Name
     * 
     * @param windowsPath the Windows file path
     * @return the MSYS2 formatted path
     */
    protected String convertToMsysPath(File windowsPath) {
        String path = windowsPath.getAbsolutePath();
        // Replace backslashes with forward slashes
        path = path.replace('\\', '/');
        
        // Convert drive letters, e.g., "C:/" to "/c/"
        if (path.length() >= 2 && path.charAt(1) == ':') {
            char drive = Character.toLowerCase(path.charAt(0));
            path = "/" + drive + path.substring(2);
        }
        
        return path;
    }

    /**
     * Creates an InstallWindowsRegistry instance for registry operations.
     * Uses injected RegistryOperations if available, otherwise uses JnaRegistryOperations.
     * This method can be overridden in tests.
     * 
     * @return a new InstallWindowsRegistry with minimal configuration
     */
    protected InstallWindowsRegistry createRegistryHelper() {
        // Create a registry helper with no AppInfo or backup logging
        // for PATH-only operations
        if (registryOperations != null) {
            return new InstallWindowsRegistry(null, null, null, null, registryOperations);
        }
        return new InstallWindowsRegistry(null, null, null, null);
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

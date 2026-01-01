package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
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
                if (cliExeFile.exists() && !cliExeFile.delete()) {
                    System.err.println("Warning: Failed to delete CLI exe: " + cliExeFile.getAbsolutePath());
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
                    if (wrapperFile.exists() && !wrapperFile.delete()) {
                        System.err.println("Warning: Failed to delete wrapper file: " + wrapperFile.getAbsolutePath());
                    }

                    // Also attempt to delete the extensionless version (for Git Bash)
                    String shWrapperName = wrapperName.endsWith(".cmd")
                            ? wrapperName.substring(0, wrapperName.length() - 4)
                            : wrapperName;

                    File shWrapperFile = new File(userBinDir, shWrapperName);
                    if (shWrapperFile.exists() && !shWrapperFile.delete()) {
                        System.err.println("Warning: Failed to delete shell wrapper file: " + shWrapperFile.getAbsolutePath());
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
                    } catch (Exception e) {
                        System.err.println("Warning: Failed to remove per-app bin directory: " + e.getMessage());
                    }
                }
            }

            // Use the same per-app directory for PATH cleanup
            File perAppPathDir = userBinDir;

            // Remove from PATH if it was added
            if (metadata.optBoolean(CliInstallerConstants.PATH_UPDATED_KEY, false)) {
                removeFromPath(perAppPathDir);
            }

            // Remove from Git Bash path if it was added
            if (metadata.optBoolean(CliInstallerConstants.GIT_BASH_PATH_UPDATED_KEY, false)) {
                removeFromGitBashPath(perAppPathDir);
            }

            // Delete manifest file via repository
            if (packageName != null && !packageName.isEmpty()) {
                try {
                    manifestRepository.delete(packageName, source);
                    System.out.println("Deleted manifest for package: " + packageName);
                } catch (Exception e) {
                    System.err.println("Warning: Failed to delete manifest: " + e.getMessage());
                }
            }

            // Delete legacy metadata file
            if (!metadataFile.delete()) {
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
            return registry.addToUserPath(binDir);
        } catch (Exception e) {
            System.err.println("Warning: Failed to update user PATH in registry: " + e.getMessage());
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
                throw new IOException("Failed to create bin directory: " + binDir.getAbsolutePath());
            }
        }

        for (CommandSpec cs : commands) {
            String name = cs.getName();
            File cmdWrapper = new File(binDir, name + ".cmd");
            File shWrapper = new File(binDir, name);

            // Check for collision with existing wrapper (check .cmd as the primary indicator)
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
                        continue;
                    }
                    // OVERWRITE - fall through to delete and recreate
                    System.out.println("Overwriting command '" + name + "' from another app");
                }
                // Same app or couldn't parse - silently overwrite
                cmdWrapper.delete();
            }
            if (shWrapper.exists()) {
                shWrapper.delete();
            }

            // 1. Windows batch wrapper (.cmd): invoke the launcher with --jdeploy:command=<name> and forward all args
            // We use \r\n for Windows batch files
            String cmdContent = "@echo off\r\n\"" + launcherPath.getAbsolutePath() + "\" " + 
                           CliInstallerConstants.JDEPLOY_COMMAND_ARG_PREFIX + name + " -- %*\r\n";

            FileUtils.writeStringToFile(cmdWrapper, cmdContent, "UTF-8");
            cmdWrapper.setExecutable(true, false);
            created.add(cmdWrapper);

            // 2. Extensionless shell script for Git Bash / MSYS2
            // We use \n for shell scripts
            String msysLauncherPath = convertToMsysPath(launcherPath);
            String shContent = "#!/bin/sh\n\"" + msysLauncherPath + "\" " + 
                             CliInstallerConstants.JDEPLOY_COMMAND_ARG_PREFIX + name + " -- \"$@\"\n";
            
            FileUtils.writeStringToFile(shWrapper, shContent, "UTF-8");
            shWrapper.setExecutable(true, false);
        }

        return created;
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

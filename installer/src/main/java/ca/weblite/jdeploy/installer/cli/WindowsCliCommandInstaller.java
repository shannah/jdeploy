package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
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

        // Determine bin directory location
        File userBinDir = new File(System.getProperty("user.home") + File.separator + ".jdeploy" + File.separator + "bin");

        try {
            // Write .cmd wrappers for each command
            List<File> wrapperFiles = writeCommandWrappersForTest(userBinDir, launcherPath, commands);
            createdFiles.addAll(wrapperFiles);

            // Update user PATH via registry
            boolean pathUpdated = addToPath(userBinDir);

            // Update Git Bash path if applicable
            boolean gitBashPathUpdated = addToGitBashPath(userBinDir);

            // Determine if the launcher is the CLI variant
            File cliExePath = null;
            if (launcherPath.getName().contains(CliInstallerConstants.CLI_LAUNCHER_SUFFIX)) {
                cliExePath = launcherPath;
            }

            // Persist metadata for uninstall in the app directory
            File appDir = launcherPath.getParentFile();
            persistMetadata(appDir, wrapperFiles, pathUpdated, gitBashPathUpdated, cliExePath);

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

            // Clean up CLI exe if it was installed
            String cliExeName = metadata.optString(CliInstallerConstants.CLI_EXE_KEY, null);
            if (cliExeName != null && !cliExeName.isEmpty()) {
                File cliExeFile = new File(appDir, cliExeName);
                if (cliExeFile.exists() && !cliExeFile.delete()) {
                    System.err.println("Warning: Failed to delete CLI exe: " + cliExeFile.getAbsolutePath());
                }
            }

            // Clean up wrapper files
            JSONArray wrappersArray = metadata.optJSONArray(CliInstallerConstants.CREATED_WRAPPERS_KEY);
            if (wrappersArray != null) {
                File userBinDir = new File(System.getProperty("user.home") + File.separator + ".jdeploy" + File.separator + "bin");
                for (int i = 0; i < wrappersArray.length(); i++) {
                    String wrapperName = wrappersArray.getString(i);
                    File wrapperFile = new File(userBinDir, wrapperName);
                    if (wrapperFile.exists() && !wrapperFile.delete()) {
                        System.err.println("Warning: Failed to delete wrapper file: " + wrapperFile.getAbsolutePath());
                    }
                }
            }

            // Remove from PATH if it was added
            boolean pathWasUpdated = metadata.optBoolean(CliInstallerConstants.PATH_UPDATED_KEY, false);
            File userBinDir = new File(System.getProperty("user.home") + File.separator + ".jdeploy" + File.separator + "bin");
            if (pathWasUpdated) {
                removeFromPath(userBinDir);
            }

            // Remove from Git Bash path if it was added
            boolean gitBashPathWasUpdated = metadata.optBoolean(CliInstallerConstants.GIT_BASH_PATH_UPDATED_KEY, false);
            if (gitBashPathWasUpdated) {
                removeFromGitBashPath(userBinDir);
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
            File wrapper = new File(binDir, name + ".cmd");

            // Check for collision with existing wrapper
            if (wrapper.exists()) {
                String existingLauncherPath = extractLauncherPathFromCmdFile(wrapper);
                
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
                wrapper.delete();
            }

            // Windows batch wrapper: invoke the launcher with --jdeploy:command=<name> and forward all args
            String content = "@echo off\r\n\"" + launcherPath.getAbsolutePath() + "\" " + 
                           CliInstallerConstants.JDEPLOY_COMMAND_ARG_PREFIX + name + " -- %*\r\n";

            FileUtils.writeStringToFile(wrapper, content, "UTF-8");
            wrapper.setExecutable(true, false);
            created.add(wrapper);
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
     * @throws IOException if metadata file write fails
     */
    private void persistMetadata(File appDir, List<File> createdWrappers, boolean pathUpdated, boolean gitBashPathUpdated, File cliExePath) throws IOException {
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

        // Store CLI exe path if it exists
        if (cliExePath != null) {
            metadata.put(CliInstallerConstants.CLI_EXE_KEY, cliExePath.getName());
        }

        // Write metadata file
        File metadataFile = new File(appDir, CliInstallerConstants.CLI_METADATA_FILE);
        FileUtils.writeStringToFile(metadataFile, metadata.toString(), "UTF-8");
    }

    /**
     * Adds the bin directory to the Git Bash path by modifying .bashrc or .bash_profile.
     * 
     * @param binDir the directory to add to the path
     * @return true if the path was updated, false otherwise
     */
    private boolean addToGitBashPath(File binDir) {
        File homeDir = new File(System.getProperty("user.home"));
        // Git Bash uses bash as the shell
        File configFile = UnixPathManager.selectConfigFile("bash", homeDir);
        
        // If UnixPathManager didn't find a config file, we don't proceed.
        // It typically looks for .bashrc or .bash_profile.
        if (configFile == null) {
            return false;
        }

        try {
            String content = "";
            if (configFile.exists()) {
                content = FileUtils.readFileToString(configFile, "UTF-8");
            } else {
                if (configFile.getParentFile() != null && !configFile.getParentFile().exists()) {
                    configFile.getParentFile().mkdirs();
                }
            }

            String msysPath = convertToMsysPath(binDir);
            String exportLine = "export PATH=\"$PATH:" + msysPath + "\"";

            if (content.contains(msysPath)) {
                return false; // Already in path
            }

            StringBuilder sb = new StringBuilder(content);
            if (!content.isEmpty() && !content.endsWith("\n")) {
                sb.append("\n");
            }
            sb.append(exportLine).append("\n");
            
            FileUtils.writeStringToFile(configFile, sb.toString(), "UTF-8");
            return true;
        } catch (IOException e) {
            System.err.println("Warning: Failed to update Git Bash config: " + e.getMessage());
            return false;
        }
    }

    /**
     * Removes the bin directory from Git Bash configuration.
     * 
     * @param binDir the directory to remove
     * @return true if successfully processed
     */
    private boolean removeFromGitBashPath(File binDir) {
        File homeDir = new File(System.getProperty("user.home"));
        File configFile = UnixPathManager.selectConfigFile("bash", homeDir);
        if (configFile == null || !configFile.exists()) {
            return false;
        }

        try {
            String content = FileUtils.readFileToString(configFile, "UTF-8");
            String msysPath = convertToMsysPath(binDir);
            String exportLine = "export PATH=\"$PATH:" + msysPath + "\"";
            
            if (content.contains(exportLine)) {
                // Remove the line and ensure the file remains clean
                content = content.replace(exportLine + "\r\n", "");
                content = content.replace(exportLine + "\n", "");
                content = content.replace(exportLine, "");
                
                FileUtils.writeStringToFile(configFile, content.trim(), "UTF-8");
                return true;
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to remove from Git Bash config: " + e.getMessage());
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
}

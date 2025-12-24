package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.win.InstallWindowsRegistry;
import ca.weblite.jdeploy.models.CommandSpec;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Windows implementation of CLI command installer.
 * 
 * Handles creation of .cmd wrapper scripts, PATH registry updates,
 * and metadata persistence for CLI command installation on Windows.
 */
public class WindowsCliCommandInstaller implements CliCommandInstaller {

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

            // Persist metadata for uninstall
            persistMetadata(settings.getAppInfo() != null ? new File(System.getProperty("user.home"), ".jdeploy") : userBinDir, 
                           wrapperFiles, pathUpdated);

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
            if (pathWasUpdated) {
                File userBinDir = new File(System.getProperty("user.home") + File.separator + ".jdeploy" + File.separator + "bin");
                removeFromPath(userBinDir);
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

            // Windows batch wrapper: invoke the launcher with --jdeploy:command=<name> and forward all args
            String content = "@echo off\r\n\"" + launcherPath.getAbsolutePath() + "\" " + 
                           CliInstallerConstants.JDEPLOY_COMMAND_ARG_PREFIX + name + " %*\r\n";

            FileUtils.writeStringToFile(wrapper, content, "UTF-8");
            wrapper.setExecutable(true, false);
            created.add(wrapper);
        }

        return created;
    }

    /**
     * Persist CLI installation metadata to a JSON file for later uninstallation.
     * 
     * @param appDir the application directory where metadata will be stored
     * @param createdWrappers list of created wrapper files
     * @param pathUpdated whether the PATH was updated
     * @throws IOException if metadata file write fails
     */
    private void persistMetadata(File appDir, List<File> createdWrappers, boolean pathUpdated) throws IOException {
        JSONObject metadata = new JSONObject();

        // Store list of created wrapper file names
        JSONArray wrappersArray = new JSONArray();
        for (File wrapper : createdWrappers) {
            wrappersArray.put(wrapper.getName());
        }
        metadata.put(CliInstallerConstants.CREATED_WRAPPERS_KEY, wrappersArray);

        // Store whether PATH was updated
        metadata.put(CliInstallerConstants.PATH_UPDATED_KEY, pathUpdated);

        // Write metadata file
        File metadataFile = new File(appDir, CliInstallerConstants.CLI_METADATA_FILE);
        FileUtils.writeStringToFile(metadataFile, metadata.toString(), "UTF-8");
    }

    /**
     * Creates an InstallWindowsRegistry instance for registry operations.
     * This method can be overridden in tests.
     * 
     * @return a new InstallWindowsRegistry with minimal configuration
     */
    protected InstallWindowsRegistry createRegistryHelper() {
        // Create a registry helper with no AppInfo or backup logging
        // for PATH-only operations
        return new InstallWindowsRegistry(null, null, null, null);
    }
}

package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.models.CommandSpec;

import java.io.File;
import java.util.List;

/**
 * Interface for platform-specific CLI command installation.
 * 
 * Implementations handle the installation, uninstallation, and PATH management
 * of CLI commands across different operating systems (Windows, Linux, macOS).
 */
public interface CliCommandInstaller {

    /**
     * Installs CLI commands for the given launcher executable.
     * 
     * @param launcherPath the path to the main launcher executable
     * @param commands list of command specifications to install
     * @param settings installation settings containing platform-specific configuration
     * @return list of files created during the installation process
     */
    List<File> installCommands(File launcherPath, List<CommandSpec> commands, InstallationSettings settings);

    /**
     * Uninstalls CLI commands that were previously installed.
     * 
     * @param appDir the application directory containing installed command files
     */
    void uninstallCommands(File appDir);

    /**
     * Adds a directory to the system PATH environment variable.
     * 
     * @param binDir the directory to add to PATH
     * @return true if the PATH was successfully updated, false otherwise
     */
    boolean addToPath(File binDir);
}

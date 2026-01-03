package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.logging.InstallationLogger;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.models.CommandSpec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Linux-specific implementation of CLI command installer.
 * Handles installation and uninstallation of CLI commands on Linux systems,
 * including script creation, symlink management, and PATH updates.
 */
public class LinuxCliCommandInstaller extends AbstractUnixCliCommandInstaller {

    /**
     * Sets the collision handler for detecting and resolving command name conflicts.
     * 
     * @param collisionHandler the handler to use for collision resolution
     */
    public void setCollisionHandler(CollisionHandler collisionHandler) {
        super.setCollisionHandler(collisionHandler);
    }

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

        // Branch installations do not support CLI commands or launchers
        if (settings.isBranchInstallation()) {
            System.out.println("Skipping CLI command/launcher installation for branch installation");
            if (installationLogger != null) {
                installationLogger.logInfo("Branch installation detected - skipping CLI commands and launcher");
            }
            return createdFiles;
        }

        if (launcherPath == null || !launcherPath.exists()) {
            System.err.println("Warning: Launcher path does not exist: " + launcherPath);
            return createdFiles;
        }

        boolean anyCreated = false;
        File commandsBinDir = null;
        File launcherBinDir = null;

        // Create command scripts in per-app directory if requested
        if (settings.isInstallCliCommands() && commands != null && !commands.isEmpty()) {
            commandsBinDir = getBinDir(settings);  // Returns ~/.jdeploy/bin-{arch}/{fqpn}

            if (!ensureBinDirExists(commandsBinDir)) {
                System.err.println("Warning: Failed to create CLI commands bin directory: " + commandsBinDir);
            } else {
                createdFiles.addAll(installCommandScripts(launcherPath, commands, commandsBinDir));
                anyCreated = !createdFiles.isEmpty();

                if (anyCreated) {
                    // Add per-app commands directory to PATH
                    addToPath(commandsBinDir);
                    if (installationLogger != null) {
                        installationLogger.logPathChange(true, commandsBinDir.getAbsolutePath(), "Linux CLI commands");
                    }
                }
            }
        }

        // Create CLI launcher symlink in ~/.local/bin if requested
        if (settings.isInstallCliLauncher()) {
            launcherBinDir = getCliLauncherBinDir();  // Returns ~/.local/bin

            if (!ensureBinDirExists(launcherBinDir)) {
                System.err.println("Warning: Failed to create CLI launcher bin directory: " + launcherBinDir);
            } else {
                String commandName = deriveCommandName(settings);
                File symlinkPath = new File(launcherBinDir, commandName);

                if (symlinkPath.exists()) {
                    symlinkPath.delete();
                }

                try {
                    Files.createSymbolicLink(symlinkPath.toPath(), launcherPath.toPath());
                    System.out.println("Created command-line symlink: " + symlinkPath.getAbsolutePath());
                    if (installationLogger != null) {
                        installationLogger.logShortcut(InstallationLogger.FileOperation.CREATED,
                                symlinkPath.getAbsolutePath(), launcherPath.getAbsolutePath());
                    }
                    settings.setCommandLineSymlinkCreated(true);
                    createdFiles.add(symlinkPath);
                    anyCreated = true;

                    // Add ~/.local/bin to PATH
                    addToPath(launcherBinDir);
                    if (installationLogger != null) {
                        installationLogger.logPathChange(true, launcherBinDir.getAbsolutePath(), "Linux CLI launcher");
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to create command-line symlink: " + e.getMessage());
                    if (installationLogger != null) {
                        installationLogger.logShortcut(InstallationLogger.FileOperation.FAILED,
                                symlinkPath.getAbsolutePath(), e.getMessage());
                    }
                }
            }
        }

        // Save metadata if any files were created
        if (anyCreated) {
            File appDir = launcherPath.getParentFile();
            // Use commands bin dir for metadata if available, otherwise launcher bin dir
            File binDirForMetadata = commandsBinDir != null ? commandsBinDir : launcherBinDir;
            File metadataDir = (appDir != null && !appDir.equals(binDirForMetadata)) ? appDir : binDirForMetadata;
            boolean pathUpdated = commandsBinDir != null || launcherBinDir != null;
            saveMetadata(metadataDir, createdFiles, pathUpdated, binDirForMetadata, settings.getPackageName(), settings.getSource());
            settings.setAddedToPath(pathUpdated);

            // Register services after successful installation
            if (commands != null && settings.getPackageName() != null) {
                String version = settings.getNpmPackageVersion() != null ?
                    settings.getNpmPackageVersion().getVersion() : "unknown";
                String branchName = null; // TODO: Extract from settings if branch installation
                registerServices(commands, settings.getPackageName(), version, branchName);
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

        File localBinDir = getCliLauncherBinDir();  // Use CLI Launcher bin dir (~/.local/bin)

        // Create ~/.local/bin if it doesn't exist
        if (!ensureBinDirExists(localBinDir)) {
            return null;
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
            if (installationLogger != null) {
                installationLogger.logShortcut(InstallationLogger.FileOperation.CREATED,
                        symlinkPath.getAbsolutePath(), launcherPath.getAbsolutePath());
            }
            settings.setCommandLineSymlinkCreated(true);

            // Update PATH and save metadata
            boolean pathUpdated = addToPath(localBinDir);
            if (pathUpdated && installationLogger != null) {
                installationLogger.logPathChange(true, localBinDir.getAbsolutePath(), "Linux CLI launcher (installLauncher)");
            }
            File appDir = launcherPath.getParentFile();
            // Save metadata to launcher's parent directory if it differs from bin, otherwise use bin
            File metadataDir = (appDir != null && !appDir.equals(localBinDir)) ? appDir : localBinDir;
            saveMetadata(metadataDir, java.util.Collections.singletonList(symlinkPath), pathUpdated, localBinDir, settings.getPackageName(), settings.getSource());

            return symlinkPath;
        } catch (IOException ioe) {
            System.err.println("Warning: Failed to create launcher symlink: " + ioe.getMessage());
            if (installationLogger != null) {
                installationLogger.logShortcut(InstallationLogger.FileOperation.FAILED,
                        symlinkPath.getAbsolutePath(), ioe.getMessage());
            }
            return null;
        }
    }

    @Override
    protected void writeCommandScript(File scriptPath, String launcherPath, CommandSpec command) throws IOException {
        String content = generateContent(launcherPath, command);
        try (FileOutputStream fos = new FileOutputStream(scriptPath)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        // make executable for owner/group/other (non-strict)
        scriptPath.setExecutable(true, false);
    }

    /**
     * Generate the content of a POSIX shell script that exec's the given launcher with
     * the configured command name and forwards user-supplied args.
     * Handles special implementations: updater, launcher, service_controller.
     *
     * @param launcherPath Absolute path to the CLI-capable launcher binary.
     * @param command      The command specification including implementations.
     * @return Script content (including shebang and trailing newline).
     */
    private static String generateContent(String launcherPath, CommandSpec command) {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/sh\n");

        String escapedLauncher = escapeDoubleQuotes(launcherPath);
        String commandName = command.getName();
        List<String> implementations = command.getImplementations();

        // Check for launcher implementation (highest priority, mutually exclusive)
        if (implementations.contains("launcher")) {
            // Convert relative file paths to absolute paths
            sb.append("\n");
            sb.append("# Convert relative file paths to absolute paths\n");
            sb.append("CONVERTED_ARGS=\"\"\n");
            sb.append("for arg in \"$@\"; do\n");
            sb.append("  if [ -e \"$arg\" ] && [ \"$arg\" = \"${arg#/}\" ]; then\n");
            sb.append("    # It's a relative path that exists, convert to absolute\n");
            sb.append("    if command -v realpath >/dev/null 2>&1; then\n");
            sb.append("      arg_abs=\"$(realpath \"$arg\")\"\n");
            sb.append("    else\n");
            sb.append("      # Fallback for systems without realpath\n");
            sb.append("      arg_abs=\"$(cd \"$(dirname \"$arg\")\" && pwd)/$(basename \"$arg\")\"\n");
            sb.append("    fi\n");
            sb.append("    CONVERTED_ARGS=\"$CONVERTED_ARGS $arg_abs\"\n");
            sb.append("  else\n");
            sb.append("    # Not a file path or already absolute, keep as-is\n");
            sb.append("    CONVERTED_ARGS=\"$CONVERTED_ARGS $arg\"\n");
            sb.append("  fi\n");
            sb.append("done\n");
            sb.append("\n");

            // For launcher: execute the binary directly with converted args
            sb.append("eval exec \"").append(escapedLauncher).append("\" $CONVERTED_ARGS\n");
            return sb.toString();
        }

        boolean hasUpdater = implementations.contains("updater");
        boolean hasServiceController = implementations.contains("service_controller");

        if (hasUpdater || hasServiceController) {
            // Generate conditional script with checks

            // Check for updater: single "update" argument
            if (hasUpdater) {
                sb.append("# Check if single argument is \"update\"\n");
                sb.append("if [ \"$#\" -eq 1 ] && [ \"$1\" = \"update\" ]; then\n");
                sb.append("  exec \"").append(escapedLauncher).append("\" --jdeploy:update\n");
                sb.append("fi\n\n");
            }

            // Check for service_controller: first argument is "service"
            if (hasServiceController) {
                sb.append("# Check if first argument is \"service\"\n");
                sb.append("if [ \"$1\" = \"service\" ]; then\n");
                sb.append("  shift\n");
                sb.append("  exec \"").append(escapedLauncher).append("\" ");
                sb.append(CliInstallerConstants.JDEPLOY_COMMAND_ARG_PREFIX).append(commandName);
                sb.append(" --jdeploy:service \"$@\"\n");
                sb.append("fi\n\n");
            }

            // Default: normal command
            sb.append("# Default: normal command\n");
            sb.append("exec \"").append(escapedLauncher).append("\" ");
            sb.append(CliInstallerConstants.JDEPLOY_COMMAND_ARG_PREFIX).append(commandName);
            sb.append(" -- \"$@\"\n");
        } else {
            // Standard command (no special implementations)
            sb.append("exec \"").append(escapedLauncher).append("\" ");
            sb.append(CliInstallerConstants.JDEPLOY_COMMAND_ARG_PREFIX).append(commandName);
            sb.append(" -- \"$@\"\n");
        }

        return sb.toString();
    }

    /**
     * Escapes special characters in a string for use within double quotes in a shell script.
     * Order matters: escape backslashes first to avoid double-escaping.
     *
     * @param s the string to escape
     * @return the escaped string safe for use in double quotes
     */
    private static String escapeDoubleQuotes(String s) {
        if (s == null) return "";
        // Order matters: escape backslashes first to avoid double-escaping
        // Then escape other special chars that have meaning inside double quotes
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("`", "\\`")
                .replace("$", "\\$");
    }
}

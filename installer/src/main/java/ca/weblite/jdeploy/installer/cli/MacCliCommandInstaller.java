package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.logging.InstallationLogger;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.util.DebugLogger;
import ca.weblite.jdeploy.models.CommandSpec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * macOS implementation of CLI command installation.
 *
 * Handles installation of CLI commands to ~/.jdeploy/bin-{arch}/{fqpn}, creation of symlinks
 * to the CLI launcher, PATH management, and persistence of command metadata.
 */
public class MacCliCommandInstaller extends AbstractUnixCliCommandInstaller {

    /**
     * Sets the collision handler for detecting and resolving command name conflicts.
     * 
     * @param collisionHandler the handler to use for collision resolution
     */
    public void setCollisionHandler(CollisionHandler collisionHandler) {
        super.setCollisionHandler(collisionHandler);
    }

    @Override
    protected File getBinDir(InstallationSettings settings) {
        return super.getBinDir(settings);
    }

    @Override
    public List<File> installCommands(File launcherPath, List<CommandSpec> commands, InstallationSettings settings) {
        List<File> createdFiles = new ArrayList<>();

        // Log entry with key parameters
        if (DebugLogger.isEnabled()) {
            int commandCount = (commands != null) ? commands.size() : 0;
            DebugLogger.log("MacCliCommandInstaller.installCommands() entry - launcherPath: " + launcherPath +
                            ", commands count: " + commandCount +
                            ", isInstallCliCommands: " + settings.isInstallCliCommands() +
                            ", isInstallCliLauncher: " + settings.isInstallCliLauncher() +
                            ", isBranchInstallation: " + settings.isBranchInstallation());
        }

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

        // Create per-command scripts in per-app directory if requested
        if (settings.isInstallCliCommands() && commands != null && !commands.isEmpty()) {
            commandsBinDir = getBinDir(settings);  // Returns ~/.jdeploy/bin-{arch}/{fqpn}
            DebugLogger.log("Determined commands binDir: " + commandsBinDir);

            DebugLogger.log("Calling ensureBinDirExists() for: " + commandsBinDir);
            if (!ensureBinDirExists(commandsBinDir)) {
                DebugLogger.log("ensureBinDirExists() failed for commands bin dir");
                System.err.println("Warning: Failed to create CLI commands bin directory: " + commandsBinDir);
            } else {
                DebugLogger.log("ensureBinDirExists() succeeded");
                DebugLogger.log("Starting installCommandScripts() for " + commands.size() + " commands");
                createdFiles.addAll(installCommandScripts(launcherPath, commands, commandsBinDir));
                DebugLogger.log("installCommandScripts() completed, created " + createdFiles.size() + " files");
                anyCreated = !createdFiles.isEmpty();

                if (anyCreated) {
                    // Add per-app commands directory to PATH
                    DebugLogger.log("Calling addToPath() for: " + commandsBinDir);
                    addToPath(commandsBinDir);
                    if (installationLogger != null) {
                        installationLogger.logPathChange(true, commandsBinDir.getAbsolutePath(), "macOS CLI commands");
                    }
                }
            }
        } else {
            DebugLogger.log("Skipping installCommandScripts() - isInstallCliCommands: " + settings.isInstallCliCommands() +
                            ", commands: " + (commands != null ? commands.size() : "null"));
        }

        // Create CLI launcher symlink in the same per-app directory if requested
        // Note: On macOS, CLI Launcher uses the same directory as CLI Commands (unlike Linux)
        if (settings.isInstallCliLauncher()) {
            // Use commands bin dir if available, otherwise create a new per-app bin dir for the launcher
            launcherBinDir = commandsBinDir != null ? commandsBinDir : getBinDir(settings);
            DebugLogger.log("Determined launcher binDir: " + launcherBinDir);

            if (!ensureBinDirExists(launcherBinDir)) {
                DebugLogger.log("ensureBinDirExists() failed for launcher bin dir");
                System.err.println("Warning: Failed to create CLI launcher bin directory: " + launcherBinDir);
            } else {
                String commandName = deriveCommandName(settings);
                File symlinkPath = new File(launcherBinDir, commandName);
                DebugLogger.log("Creating symlink for primary command: " + commandName + " at " + symlinkPath);

                if (symlinkPath.exists()) {
                    symlinkPath.delete();
                }

                try {
                    Files.createSymbolicLink(symlinkPath.toPath(), launcherPath.toPath());
                    System.out.println("Created command-line symlink: " + symlinkPath.getAbsolutePath());
                    DebugLogger.log("Symlink created successfully: " + symlinkPath);
                    if (installationLogger != null) {
                        installationLogger.logShortcut(InstallationLogger.FileOperation.CREATED,
                                symlinkPath.getAbsolutePath(), launcherPath.getAbsolutePath());
                    }
                    settings.setCommandLineSymlinkCreated(true);
                    createdFiles.add(symlinkPath);
                    anyCreated = true;

                    // Add to PATH if not already added for commands
                    if (commandsBinDir == null) {
                        DebugLogger.log("Calling addToPath() for: " + launcherBinDir);
                        addToPath(launcherBinDir);
                        if (installationLogger != null) {
                            installationLogger.logPathChange(true, launcherBinDir.getAbsolutePath(), "macOS CLI launcher");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to create command-line symlink: " + e.getMessage());
                    DebugLogger.log("Failed to create symlink: " + e.getMessage());
                    if (installationLogger != null) {
                        installationLogger.logShortcut(InstallationLogger.FileOperation.FAILED,
                                symlinkPath.getAbsolutePath(), e.getMessage());
                    }
                }
            }
        } else {
            DebugLogger.log("Skipping symlink creation - isInstallCliLauncher is false");
        }

        // Save metadata if any files were created
        if (anyCreated) {
            File appDir = launcherPath.getParentFile();
            // Use commands bin dir for metadata if available, otherwise launcher bin dir
            File binDirForMetadata = commandsBinDir != null ? commandsBinDir : launcherBinDir;
            File metadataDir = (appDir != null && !appDir.equals(binDirForMetadata)) ? appDir : binDirForMetadata;
            boolean pathUpdated = commandsBinDir != null || launcherBinDir != null;
            DebugLogger.log("Saving metadata to: " + metadataDir + " with " + createdFiles.size() + " files");
            saveMetadata(metadataDir, createdFiles, pathUpdated, binDirForMetadata, settings.getPackageName(), settings.getSource());
            settings.setAddedToPath(pathUpdated);
            DebugLogger.log("Updated settings - addedToPath: " + pathUpdated);

            // Register services after successful installation
            if (commands != null && settings.getPackageName() != null) {
                String version = settings.getNpmPackageVersion() != null ?
                    settings.getNpmPackageVersion().getVersion() : "unknown";
                String branchName = null; // TODO: Extract from settings if branch installation
                registerServices(commands, settings.getPackageName(), version, branchName);
            }
        } else {
            DebugLogger.log("No files were created, skipping PATH update and metadata save");
        }

        DebugLogger.log("MacCliCommandInstaller.installCommands() exit - returning " + createdFiles.size() + " files");
        return createdFiles;
    }

    @Override
    protected void writeCommandScript(File scriptPath, String launcherPath, CommandSpec command) throws IOException {
        String content = generateContent(launcherPath, command);
        try (FileOutputStream fos = new FileOutputStream(scriptPath)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        scriptPath.setExecutable(true, false);
    }

    /**
     * Generate the content of a bash script that executes the given launcher with
     * the configured command name and forwards user-supplied args.
     * Handles special implementations: updater, launcher, service_controller.
     *
     * @param launcherPath Absolute path to the CLI-capable launcher binary.
     * @param command      The command specification including implementations.
     * @return Script content (including shebang and trailing newline).
     */
    private static String generateContent(String launcherPath, CommandSpec command) {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/bash\n");

        String escapedLauncher = escapeDoubleQuotes(launcherPath);
        String commandName = command.getName();
        List<String> implementations = command.getImplementations();

        // Check for launcher implementation (highest priority, mutually exclusive)
        if (implementations.contains("launcher")) {
            // Convert relative file paths to absolute paths
            sb.append("\n");
            sb.append("# Convert relative file paths to absolute paths\n");
            sb.append("CONVERTED_ARGS=()\n");
            sb.append("for arg in \"$@\"; do\n");
            sb.append("  if [ -e \"$arg\" ] && [ \"$arg\" = \"${arg#/}\" ]; then\n");
            sb.append("    # It's a relative path that exists, convert to absolute\n");
            sb.append("    if command -v realpath >/dev/null 2>&1; then\n");
            sb.append("      CONVERTED_ARGS+=(\"$(realpath \"$arg\")\")\n");
            sb.append("    else\n");
            sb.append("      # Fallback for systems without realpath\n");
            sb.append("      CONVERTED_ARGS+=(\"$(cd \"$(dirname \"$arg\")\" && pwd)/$(basename \"$arg\")\")\n");
            sb.append("    fi\n");
            sb.append("  else\n");
            sb.append("    # Not a file path or already absolute, keep as-is\n");
            sb.append("    CONVERTED_ARGS+=(\"$arg\")\n");
            sb.append("  fi\n");
            sb.append("done\n");
            sb.append("\n");

            // For launcher on macOS: use 'open' command to launch the .app bundle
            // Extract app bundle path if launcherPath is inside a .app
            String appPath = extractAppBundlePath(launcherPath);
            if (appPath != null) {
                sb.append("exec open -a \"").append(escapeDoubleQuotes(appPath)).append("\" \"${CONVERTED_ARGS[@]}\"\n");
            } else {
                // Fallback: execute binary directly
                sb.append("exec \"").append(escapedLauncher).append("\" \"${CONVERTED_ARGS[@]}\"\n");
            }
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
     * Extracts the .app bundle path from a launcher path inside a .app bundle.
     * For example: /Applications/MyApp.app/Contents/MacOS/Client4JLauncher-cli -> /Applications/MyApp.app
     *
     * @param launcherPath the launcher path
     * @return the .app bundle path, or null if not inside a .app bundle
     */
    private static String extractAppBundlePath(String launcherPath) {
        int appIndex = launcherPath.indexOf(".app/");
        if (appIndex > 0) {
            return launcherPath.substring(0, appIndex + 4); // Include .app
        }
        return null;
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

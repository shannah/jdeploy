package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.util.DebugLogger;
import ca.weblite.tools.io.IOUtil;
import ca.weblite.tools.platform.Platform;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for managing PATH updates on Unix-like systems (macOS and Linux).
 * Handles shell detection, config file selection, idempotency checks, and PATH exports.
 */
public class UnixPathManager {

    private UnixPathManager() {
        // Utility class, not instantiable
    }

    /**
     * Marker comment that users can add to shell config files to prevent automatic PATH modification.
     */
    public static final String NO_AUTO_PATH_MARKER = "# jdeploy:no-auto-path";

    /**
     * Adds a directory to the system PATH environment variable.
     * Updates shell configuration files (.bashrc, .zshrc, etc.) to persist the change.
     * This method is idempotent: it checks if the directory is already in PATH or config before adding.
     *
     * On Unix systems, we always write to BOTH bash and zsh configuration files to ensure
     * PATH works regardless of which shell the user is using. This is especially important
     * when running from GUI applications where the SHELL environment variable may not be set.
     *
     * @param binDir   directory to add to PATH
     * @param shell    shell path from environment (e.g., /bin/bash), or null to use default
     * @param pathEnv  current PATH environment variable
     * @param homeDir  user's home directory to update config files under
     * @return true if PATH was updated or already contained the directory, false otherwise
     */
    public static boolean addToPath(File binDir, String shell, String pathEnv, File homeDir) {
        return addToPath(binDir, shell, pathEnv, homeDir, Platform.getSystemPlatform());
    }

    /**
     * Adds a directory to the system PATH environment variable.
     * This overload allows specifying the platform for testing purposes.
     *
     * @param binDir   directory to add to PATH
     * @param shell    shell path from environment (e.g., /bin/bash), or null to use default
     * @param pathEnv  current PATH environment variable
     * @param homeDir  user's home directory to update config files under
     * @param platform the platform to use for determining behavior
     * @return true if PATH was updated or already contained the directory, false otherwise
     */
    static boolean addToPath(File binDir, String shell, String pathEnv, File homeDir, Platform platform) {
        // Log input parameters
        DebugLogger.log("UnixPathManager.addToPath() called with:");
        DebugLogger.log("  binDir: " + (binDir != null ? binDir.getAbsolutePath() : "null"));
        DebugLogger.log("  shell: " + (shell != null && !shell.isEmpty() ? shell : "(null or empty)"));
        DebugLogger.log("  homeDir: " + (homeDir != null ? homeDir.getAbsolutePath() : "null"));

        // Verify binDir exists and is a directory before modifying shell config
        if (binDir == null || !binDir.exists() || !binDir.isDirectory()) {
            DebugLogger.log("Early return: binDir validation failed");
            System.err.println("Warning: Cannot add to PATH - directory does not exist: " +
                    (binDir != null ? binDir.getAbsolutePath() : "null"));
            return false;
        }

        // Write to shell configuration files to ensure PATH works for both bash and zsh.
        // Platform-specific behavior:
        // - macOS: Write to .bash_profile (Terminal.app uses login shells) and .bashrc
        // - Linux: Write to .bashrc only (avoid creating .bash_profile which would break
        //          Ubuntu's convention where .profile sources .bashrc)
        boolean isMac = platform != null && platform.isMac();
        DebugLogger.log("Platform detection: isMac=" + isMac);
        DebugLogger.log("Writing to shell configuration files");

        boolean anyUpdated = false;

        // Update bash configuration files
        File bashrc = new File(homeDir, ".bashrc");
        boolean bashrcUpdated = addPathToConfigFile(bashrc, binDir, homeDir);
        anyUpdated = bashrcUpdated;

        // On macOS, also update login shell config because Terminal.app starts login shells.
        // Bash reads the first file it finds among: .bash_profile, .bash_login, .profile
        // We mirror this logic to avoid breaking existing setups:
        // - If .bash_profile exists → use it
        // - Else if .profile exists → use it (don't create .bash_profile)
        // - Else → create .bash_profile
        if (isMac) {
            File bashProfile = new File(homeDir, ".bash_profile");
            File profile = new File(homeDir, ".profile");

            File loginShellConfig;
            if (bashProfile.exists()) {
                loginShellConfig = bashProfile;
            } else if (profile.exists()) {
                loginShellConfig = profile;
                DebugLogger.log("Using existing .profile instead of creating .bash_profile");
            } else {
                loginShellConfig = bashProfile;
            }

            boolean profileUpdated = addPathToConfigFile(loginShellConfig, binDir, homeDir);
            anyUpdated = anyUpdated || profileUpdated;
        }

        // Update zsh configuration file (.zshrc)
        File zshrc = new File(homeDir, ".zshrc");
        boolean zshrcUpdated = addPathToConfigFile(zshrc, binDir, homeDir);
        anyUpdated = anyUpdated || zshrcUpdated;

        if (anyUpdated) {
            System.out.println("Please restart your terminal or source your shell configuration.");
            return true;
        }
        return false;
    }

    /**
     * Internal helper to add a PATH export to a specific configuration file.
     *
     * @param configFile the file to update
     * @param binDir     the binary directory to add
     * @param homeDir    the user's home directory
     * @return true if the file was updated or already contained the entry, false on error
     */
    private static boolean addPathToConfigFile(File configFile, File binDir, File homeDir) {
        try {
            String pathExportString = computePathExportString(binDir, homeDir);
            String displayPath = computeDisplayPath(binDir, homeDir);

            // Ensure configFile exists (create if necessary)
            if (!configFile.exists()) {
                DebugLogger.log("Config file does not exist, creating: " + configFile.getAbsolutePath());
                File parent = configFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                configFile.createNewFile();

                // If we're creating .bash_profile, source .profile if it exists
                // This prevents breaking existing user configurations, since bash
                // only reads the first file it finds among .bash_profile, .bash_login, .profile
                if (".bash_profile".equals(configFile.getName())) {
                    File profile = new File(homeDir, ".profile");
                    if (profile.exists()) {
                        DebugLogger.log("Sourcing .profile from new .bash_profile to preserve existing config");
                        String sourceProfile = "# Source .profile to preserve existing configuration\n" +
                                "if [ -f ~/.profile ]; then\n" +
                                "    . ~/.profile\n" +
                                "fi\n";
                        try (FileOutputStream fos = new FileOutputStream(configFile)) {
                            fos.write(sourceProfile.getBytes(StandardCharsets.UTF_8));
                        }
                        System.out.println("Created .bash_profile with sourcing of existing .profile");
                    }
                }
            } else {
                // Check for user override marker
                String content = IOUtil.readToString(new FileInputStream(configFile));
                if (content.contains(NO_AUTO_PATH_MARKER)) {
                    DebugLogger.log("Skipping PATH modification in " + configFile.getAbsolutePath() + " due to " + NO_AUTO_PATH_MARKER + " marker");
                    System.out.println("Skipping PATH modification in " + configFile.getName() + " (found " + NO_AUTO_PATH_MARKER + " marker)");
                    return true; // Return true to indicate success (user explicitly opted out)
                }

                // Remove any existing PATH entry for this binDir first
                removePathFromConfigFile(configFile, binDir, homeDir);
            }

            // Append PATH export to the config file (always at the end)
            DebugLogger.log("Writing PATH export to: " + configFile.getAbsolutePath());
            String pathExport = "\n# Added by jDeploy installer\nexport PATH=\"" + pathExportString + ":$PATH\"\n";
            try (FileOutputStream fos = new FileOutputStream(configFile, true)) {
                fos.write(pathExport.getBytes(StandardCharsets.UTF_8));
            }

            System.out.println("Added " + displayPath + " to PATH in " + configFile.getName());
            return true;
        } catch (Exception e) {
            DebugLogger.log("Failed to update config file " + configFile.getAbsolutePath() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Computes the path string to use in shell config exports.
     * If binDir is under homeDir, returns a $HOME-relative path (e.g., "$HOME/bin" or "$HOME/.local/bin").
     * Otherwise returns the absolute path.
     *
     * @param binDir  the binary directory
     * @param homeDir the user's home directory
     * @return the path string to use in export statements
     */
    private static String computePathExportString(File binDir, File homeDir) {
        String homePath = homeDir.getAbsolutePath();
        String binPath = binDir.getAbsolutePath();

        if (binPath.startsWith(homePath)) {
            // Remove homeDir prefix and leading separator
            String relativePath = binPath.substring(homePath.length());
            if (relativePath.startsWith(File.separator)) {
                relativePath = relativePath.substring(1);
            }
            return "$HOME/" + relativePath.replace(File.separatorChar, '/');
        }
        return binPath;
    }

    /**
     * Computes a user-friendly display path (using ~ for home directory).
     *
     * @param binDir  the binary directory
     * @param homeDir the user's home directory
     * @return the display path string
     */
    private static String computeDisplayPath(File binDir, File homeDir) {
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
     * Checks if the given config file contains the no-auto-path marker.
     * If present, automatic PATH modification should be skipped for this file.
     *
     * @param configFile the shell configuration file to check
     * @return true if the marker is present, false otherwise
     */
    public static boolean hasNoAutoPathMarker(File configFile) {
        if (configFile == null || !configFile.exists()) {
            return false;
        }
        try {
            String content = IOUtil.readToString(new FileInputStream(configFile));
            return content.contains(NO_AUTO_PATH_MARKER);
        } catch (Exception e) {
            DebugLogger.log("Failed to check for no-auto-path marker in " + configFile.getAbsolutePath() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Removes an existing PATH export for the given binDir from a config file.
     * Removes lines that contain the jDeploy installer comment AND the path export for this specific directory.
     *
     * @param configFile the shell configuration file to modify
     * @param binDir the binary directory whose PATH entry should be removed
     * @param homeDir the user's home directory (for computing relative paths)
     * @return true if any entry was removed, false otherwise
     */
    public static boolean removePathFromConfigFile(File configFile, File binDir, File homeDir) {
        if (configFile == null || !configFile.exists()) {
            return false;
        }
        try {
            String content = IOUtil.readToString(new FileInputStream(configFile));
            String pathExportString = computePathExportString(binDir, homeDir);
            String absolutePath = binDir.getAbsolutePath();
            
            // Build patterns to match jDeploy-added PATH entries for this directory
            // We want to remove both the comment line and the export line
            String[] lines = content.split("\n", -1);
            StringBuilder result = new StringBuilder();
            boolean removed = false;
            boolean skipNextExport = false;
            
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                String trimmed = line.trim();
                
                // Check if this is a jDeploy comment line
                if (trimmed.equals("# Added by jDeploy installer")) {
                    // Look ahead to see if the next line is an export for our binDir
                    if (i + 1 < lines.length) {
                        String nextLine = lines[i + 1].trim();
                        if (nextLine.startsWith("export PATH=\"") && 
                            (nextLine.contains(pathExportString) || nextLine.contains(absolutePath))) {
                            // Skip this comment line and mark to skip the export line
                            skipNextExport = true;
                            removed = true;
                            continue;
                        }
                    }
                }
                
                // Check if this is an export line we should skip
                if (skipNextExport && trimmed.startsWith("export PATH=\"") &&
                    (trimmed.contains(pathExportString) || trimmed.contains(absolutePath))) {
                    skipNextExport = false;
                    continue;
                }
                
                // Also check for standalone export lines (not preceded by jDeploy comment)
                if (trimmed.startsWith("export PATH=\"") &&
                    (trimmed.contains(pathExportString) || trimmed.contains(absolutePath)) &&
                    trimmed.contains(":$PATH\"")) {
                    removed = true;
                    continue;
                }
                
                skipNextExport = false;
                if (result.length() > 0 || !line.isEmpty()) {
                    if (result.length() > 0) {
                        result.append("\n");
                    }
                    result.append(line);
                }
            }
            
            if (removed) {
                // Write back the modified content
                // Preserve trailing newline if original had one
                String newContent = result.toString();
                if (content.endsWith("\n") && !newContent.endsWith("\n")) {
                    newContent += "\n";
                }
                try (FileOutputStream fos = new FileOutputStream(configFile)) {
                    fos.write(newContent.getBytes(StandardCharsets.UTF_8));
                }
                DebugLogger.log("Removed existing PATH entry for " + binDir.getAbsolutePath() + " from " + configFile.getAbsolutePath());
            }
            
            return removed;
        } catch (Exception e) {
            DebugLogger.log("Failed to remove PATH entry from " + configFile.getAbsolutePath() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Selects the shell configuration file for PATH updates.
     * Returns shell-specific config files when possible:
     * - ~/.zshrc for zsh
     * - ~/.bashrc for bash (falls back to ~/.bash_profile if .bashrc doesn't exist)
     * - ~/.profile for unknown shells (POSIX fallback)
     *
     * @param shell   the shell path (e.g., /bin/bash, /bin/zsh, /usr/bin/fish)
     * @param homeDir the user's home directory
     * @return the config File to update
     */
    static File selectConfigFile(String shell, File homeDir) {
        String shellName = new File(shell).getName();

        if ("zsh".equals(shellName)) {
            return new File(homeDir, ".zshrc");
        } else if ("bash".equals(shellName)) {
            File bashrc = new File(homeDir, ".bashrc");
            if (bashrc.exists()) {
                return bashrc;
            }
            File bashProfile = new File(homeDir, ".bash_profile");
            if (bashProfile.exists()) {
                return bashProfile;
            }
            // Default to .bashrc if neither exists yet
            return bashrc;
        } else {
            // For unknown shells (fish, tcsh, csh, sh, etc.), use POSIX-compatible .profile
            if ("fish".equals(shellName) || "tcsh".equals(shellName) || "csh".equals(shellName)) {
                System.out.println("Note: " + shellName + " shell detected. Using ~/.profile for POSIX compatibility.");
                System.out.println("You may need to manually configure your shell to source ~/.profile or add ~/.local/bin to your PATH.");
            } else if (!"sh".equals(shellName)) {
                System.out.println("Note: Non-standard shell '" + shellName + "' detected. Using ~/.profile for POSIX compatibility.");
            }
            return new File(homeDir, ".profile");
        }
    }
}

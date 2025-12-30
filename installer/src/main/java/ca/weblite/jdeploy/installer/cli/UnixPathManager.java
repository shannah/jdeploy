package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.util.DebugLogger;
import ca.weblite.tools.io.IOUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
     * Adds a directory to the system PATH environment variable.
     * Updates shell configuration files (.bashrc, .zshrc, etc.) to persist the change.
     * This method is idempotent: it checks if the directory is already in PATH or config before adding.
     *
     * @param binDir   directory to add to PATH
     * @param shell    shell path from environment (e.g., /bin/bash), or null to use default
     * @param pathEnv  current PATH environment variable
     * @param homeDir  user's home directory to update config files under
     * @return true if PATH was updated or already contained the directory, false otherwise
     */
    public static boolean addToPath(File binDir, String shell, String pathEnv, File homeDir) {
        // Log input parameters
        DebugLogger.log("UnixPathManager.addToPath() called with:");
        DebugLogger.log("  binDir: " + (binDir != null ? binDir.getAbsolutePath() : "null"));
        DebugLogger.log("  shell: " + (shell != null && !shell.isEmpty() ? shell : "(null or empty, will default to /bin/bash)"));
        DebugLogger.log("  homeDir: " + (homeDir != null ? homeDir.getAbsolutePath() : "null"));

        // Verify binDir exists and is a directory before modifying shell config
        if (binDir == null || !binDir.exists() || !binDir.isDirectory()) {
            DebugLogger.log("Early return: binDir validation failed");
            System.err.println("Warning: Cannot add to PATH - directory does not exist: " +
                    (binDir != null ? binDir.getAbsolutePath() : "null"));
            return false;
        }

        // Detect the user's shell; default to bash when unknown
        if (shell == null || shell.isEmpty()) {
            DebugLogger.log("Shell is null or empty, defaulting to /bin/bash");
            shell = "/bin/bash";
        }

        String shellName = new File(shell).getName();
        if ("bash".equals(shellName)) {
            // For bash, write to BOTH .bash_profile and .bashrc to handle login and non-login shells
            File bashProfile = new File(homeDir, ".bash_profile");
            File bashrc = new File(homeDir, ".bashrc");

            boolean profileUpdated = addPathToConfigFile(bashProfile, binDir, homeDir);
            boolean bashrcUpdated = addPathToConfigFile(bashrc, binDir, homeDir);

            if (profileUpdated || bashrcUpdated) {
                System.out.println("Please restart your terminal or source your shell configuration.");
                return true;
            }
            return false;
        } else {
            // For non-bash shells, we can use the PATH environment check as an optimization
            // since we only have one configuration file to worry about.
            if (pathEnv != null && pathEnv.contains(binDir.getAbsolutePath())) {
                DebugLogger.log("Early return: binDir already in PATH environment variable for non-bash shell");
                return true;
            }

            File configFile = selectConfigFile(shell, homeDir);
            if (configFile == null) {
                return false;
            }
            return addPathToConfigFile(configFile, binDir, homeDir);
        }
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
            } else {
                // Check file contents to avoid duplicate entries
                String content = IOUtil.readToString(new FileInputStream(configFile));
                if (content.contains(pathExportString) || content.contains(binDir.getAbsolutePath())) {
                    DebugLogger.log("PATH entry already exists in: " + configFile.getAbsolutePath());
                    return true;
                }
            }

            // Append PATH export to the config file
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

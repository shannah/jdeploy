package ca.weblite.jdeploy.installer.cli;

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
        try {
            // Verify binDir exists and is a directory before modifying shell config
            if (binDir == null || !binDir.exists() || !binDir.isDirectory()) {
                System.err.println("Warning: Cannot add to PATH - directory does not exist: " + 
                    (binDir != null ? binDir.getAbsolutePath() : "null"));
                return false;
            }

            // Detect the user's shell; default to bash when unknown
            if (shell == null || shell.isEmpty()) {
                shell = "/bin/bash";
            }

            File configFile = selectConfigFile(shell, homeDir);
            if (configFile == null) {
                return false;
            }

            // If PATH already contains binDir, nothing to do
            if (pathEnv != null && pathEnv.contains(binDir.getAbsolutePath())) {
                System.out.println("~/.local/bin is already in PATH");
                return true;
            }

            // Ensure configFile exists (create if necessary)
            if (!configFile.exists()) {
                File parent = configFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try {
                    configFile.createNewFile();
                } catch (Exception ignored) { }
            } else {
                // Check file contents to avoid duplicate entries
                String content = IOUtil.readToString(new FileInputStream(configFile));
                if (content.contains("$HOME/.local/bin") || content.contains(binDir.getAbsolutePath())) {
                    System.out.println("~/.local/bin is already in PATH configuration");
                    return true;
                }
            }

            // Append PATH export to the config file
            String pathExport = "\n# Added by jDeploy installer\nexport PATH=\"$HOME/.local/bin:$PATH\"\n";
            try (FileOutputStream fos = new FileOutputStream(configFile, true)) {
                fos.write(pathExport.getBytes(StandardCharsets.UTF_8));
            }

            System.out.println("Added ~/.local/bin to PATH in " + configFile.getName());
            System.out.println("Please restart your terminal or run: source " + configFile.getAbsolutePath());
            return true;

        } catch (Exception e) {
            System.err.println("Warning: Failed to add ~/.local/bin to PATH: " + e.getMessage());
            System.out.println("You may need to manually add ~/.local/bin to your PATH");
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

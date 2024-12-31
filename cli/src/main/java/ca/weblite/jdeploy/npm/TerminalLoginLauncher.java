package ca.weblite.jdeploy.npm;

import ca.weblite.jdeploy.JDeploy; // Ensure this class is available in your classpath
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * NativeTerminalLauncher is responsible for opening the system's native terminal
 * and executing the command: java -jar jdeploy-cli.jar login
 */
public class TerminalLoginLauncher {

    /**
     * Launches the native terminal and executes the login command.
     *
     * @throws IOException If an I/O error occurs.
     * @throws URISyntaxException If the JDeploy class location URI is invalid.
     */
    public static void launchLoginTerminal() throws IOException, URISyntaxException {
        // Step 1: Locate the Java executable
        String javaExecutablePath = getJavaExecutablePath();
        if (javaExecutablePath == null) {
            throw new IOException("Java executable not found.");
        }

        // Step 2: Locate the jdeploy-cli.jar file
        String jdeployJarPath = getJDeployJarPath();
        if (jdeployJarPath == null) {
            throw new IOException("jdeploy-cli.jar not found.");
        }

        // Step 3: Construct the command to execute
        String command = "\"" + javaExecutablePath + "\" -jar \"" + jdeployJarPath + "\" login";

        // Step 4: Open the native terminal with the command
        openNativeTerminal(command);
    }

    /**
     * Retrieves the path to the Java executable based on the java.home system property.
     *
     * @return Absolute path to the Java executable.
     */
    private static String getJavaExecutablePath() {
        String javaHome = System.getProperty("java.home");
        String os = System.getProperty("os.name").toLowerCase();
        String javaBin = javaHome + File.separator + "bin" + File.separator + (isWindows(os) ? "java.exe" : "java");
        File javaFile = new File(javaBin);
        return javaFile.exists() ? javaFile.getAbsolutePath() : null;
    }

    /**
     * Retrieves the absolute path to the jdeploy-cli.jar file containing the JDeploy class.
     *
     * @return Absolute path to jdeploy-cli.jar.
     * @throws URISyntaxException If the JDeploy class location URI is invalid.
     */
    private static String getJDeployJarPath() throws URISyntaxException {
        URL jarUrl = JDeploy.class.getProtectionDomain().getCodeSource().getLocation();
        File jarFile = new File(jarUrl.toURI());
        return jarFile.exists() && jarFile.isFile() ? jarFile.getAbsolutePath() : null;
    }

    /**
     * Opens the system's native terminal application and executes the specified command.
     *
     * @param command The command to execute in the terminal.
     * @throws IOException If an I/O error occurs while launching the terminal.
     */
    private static void openNativeTerminal(String command) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            // Windows: Use cmd.exe to execute the command and keep the window open
            // /k keeps the window open after the command executes
            new ProcessBuilder("cmd.exe", "/c", "start", "cmd.exe", "/k", command).start();
        } else if (os.contains("mac")) {
            // macOS: Use AppleScript to tell Terminal.app to execute the command
            String[] appleScriptCommand = {
                    "osascript",
                    "-e",
                    "tell application \"Terminal\"",
                    "-e",
                    "activate",
                    "-e",
                    "do script \"" + escapeForAppleScript(command) + "\"",
                    "-e",
                    "end tell"
            };
            new ProcessBuilder(appleScriptCommand).start();
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            // Linux: Try common terminal emulators
            List<String> terminals = Arrays.asList(
                    "gnome-terminal",
                    "konsole",
                    "xfce4-terminal",
                    "xterm",
                    "lxterminal",
                    "terminator",
                    "tilix",
                    "alacritty"
            );

            boolean terminalFound = false;
            for (String terminal : terminals) {
                if (isCommandAvailable(terminal)) {
                    // Depending on the terminal, the arguments to execute a command vary
                    if (terminal.equals("gnome-terminal")) {
                        new ProcessBuilder("gnome-terminal", "--", "bash", "-c", command + "; exec bash").start();
                    } else if (terminal.equals("konsole")) {
                        new ProcessBuilder("konsole", "-e", "bash", "-c", command + "; exec bash").start();
                    } else if (terminal.equals("xfce4-terminal")) {
                        new ProcessBuilder("xfce4-terminal", "--command", "bash", "-c", command + "; exec bash").start();
                    } else if (terminal.equals("xterm")) {
                        new ProcessBuilder("xterm", "-hold", "-e", command).start();
                    } else if (terminal.equals("lxterminal")) {
                        new ProcessBuilder("lxterminal", "-e", "bash", "-c", command + "; exec bash").start();
                    } else if (terminal.equals("terminator")) {
                        new ProcessBuilder("terminator", "-x", "bash", "-c", command + "; exec bash").start();
                    } else if (terminal.equals("tilix")) {
                        new ProcessBuilder("tilix", "-e", "bash", "-c", command + "; exec bash").start();
                    } else if (terminal.equals("alacritty")) {
                        new ProcessBuilder("alacritty", "-e", "bash", "-c", command + "; exec bash").start();
                    } else {
                        // Default fallback
                        new ProcessBuilder(terminal, "-e", "bash", "-c", command + "; exec bash").start();
                    }
                    terminalFound = true;
                    break;
                }
            }

            if (!terminalFound) {
                throw new IOException("No supported terminal emulator found. Please install gnome-terminal, konsole, xfce4-terminal, xterm, lxterminal, terminator, tilix, or alacritty.");
            }
        } else {
            throw new IOException("Unsupported operating system: " + os);
        }
    }

    /**
     * Checks if a command is available on the system by using the 'which' command.
     *
     * @param command The command to check.
     * @return True if the command exists, false otherwise.
     */
    private static boolean isCommandAvailable(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", command);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (InterruptedException | IOException e) {
            return false;
        }
    }

    /**
     * Escapes special characters in the command string for AppleScript.
     *
     * @param command The command to escape.
     * @return The escaped command string.
     */
    private static String escapeForAppleScript(String command) {
        return command.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Determines if the operating system is Windows.
     *
     * @param os The operating system name in lowercase.
     * @return True if Windows, false otherwise.
     */
    private static boolean isWindows(String os) {
        return os.contains("win");
    }

    /**
     * Main method for testing the NativeTerminalLauncher.
     * You can run this method to test the terminal launch functionality.
     */
    public static void main(String[] args) {
        try {
            launchLoginTerminal();
            System.out.println("Terminal launched successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to launch terminal: " + e.getMessage());
        }
    }
}


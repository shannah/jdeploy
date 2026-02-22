package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.models.CommandSpec;
import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.packaging.PackagingContext;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Service to run locally-installed jDeploy applications.
 *
 * This service enables developers to launch their applications from the installed location,
 * either as a GUI app or by running specific CLI commands.
 *
 * Usage:
 * - runApp(): Launches the GUI application
 * - runCommand(): Runs a specific CLI command with arguments
 * - debugApp(): Launches the GUI application with Java remote debugging enabled
 * - debugCommand(): Runs a specific CLI command with debugging enabled
 */
@Singleton
public class LocalRunService {

    private static final String ENV_DEBUG_PORT = "JDEPLOY_DEBUG_PORT";
    private static final String ENV_DEBUG_SUSPEND = "JDEPLOY_DEBUG_SUSPEND";
    private static final int DEFAULT_DEBUG_PORT = 5005;

    private final InstalledAppLocator installedAppLocator;

    @Inject
    public LocalRunService(InstalledAppLocator installedAppLocator) {
        this.installedAppLocator = installedAppLocator;
    }

    /**
     * Launches the GUI application from its installed location.
     *
     * @param context The packaging context containing the project directory
     * @param out Output stream for progress messages
     * @throws IOException if there's an I/O error
     * @throws NotInstalledException if the application is not installed
     */
    public void runApp(PackagingContext context, PrintStream out) throws IOException, NotInstalledException {
        InstalledAppLocator.InstalledApp app = installedAppLocator.locate(context);

        if (!app.isInstalled()) {
            throw new NotInstalledException("Application '" + app.getTitle() + "' is not installed locally.");
        }

        out.println("Launching " + app.getTitle() + "...");

        String osName = System.getProperty("os.name").toLowerCase();

        try {
            ProcessBuilder pb;
            if (osName.contains("mac")) {
                // On macOS, use 'open' command to launch the .app bundle
                pb = new ProcessBuilder("open", app.getAppBundle().getAbsolutePath());
            } else {
                // On Windows and Linux, execute the binary directly
                pb = new ProcessBuilder(app.getGuiExecutable().getAbsolutePath());
            }

            pb.inheritIO();
            pb.start();

            // Don't wait for the process - GUI apps should run independently
            out.println("Application launched.");

        } catch (IOException e) {
            throw new IOException("Failed to launch application: " + e.getMessage(), e);
        }
    }

    /**
     * Runs a specific CLI command with the given arguments.
     *
     * @param context The packaging context containing the project directory
     * @param commandName The name of the command to run
     * @param args Arguments to pass to the command
     * @param out Output stream for progress messages
     * @return The exit code from the command
     * @throws IOException if there's an I/O error
     * @throws NotInstalledException if the application is not installed
     * @throws CommandNotFoundException if the command is not defined in package.json
     */
    public int runCommand(PackagingContext context, String commandName, String[] args, PrintStream out)
            throws IOException, NotInstalledException, CommandNotFoundException {

        // Validate command exists in package.json
        validateCommand(context.directory, commandName);

        InstalledAppLocator.InstalledApp app = installedAppLocator.locate(context);

        if (!app.isInstalled()) {
            throw new NotInstalledException("Application '" + app.getTitle() + "' is not installed locally.");
        }

        File cliLauncher = app.getCliLauncher();
        if (cliLauncher == null || !cliLauncher.exists()) {
            throw new NotInstalledException("CLI launcher not found. The application may need to be reinstalled.");
        }

        // Build command with jdeploy:command prefix
        List<String> command = new ArrayList<>();
        command.add(cliLauncher.getAbsolutePath());
        command.add(CliInstallerConstants.JDEPLOY_COMMAND_ARG_PREFIX + commandName);
        command.add("--");
        command.addAll(Arrays.asList(args));

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.inheritIO();
            Process process = pb.start();

            // Wait for command to complete and return exit code
            return process.waitFor();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Command execution interrupted", e);
        } catch (IOException e) {
            throw new IOException("Failed to run command: " + e.getMessage(), e);
        }
    }

    /**
     * Launches the GUI application with Java remote debugging enabled.
     *
     * @param context The packaging context containing the project directory
     * @param port The debug port (default 5005)
     * @param suspend Whether to suspend and wait for debugger to attach
     * @param out Output stream for progress messages
     * @throws IOException if there's an I/O error
     * @throws NotInstalledException if the application is not installed
     */
    public void debugApp(PackagingContext context, int port, boolean suspend, PrintStream out)
            throws IOException, NotInstalledException {

        InstalledAppLocator.InstalledApp app = installedAppLocator.locate(context);

        if (!app.isInstalled()) {
            throw new NotInstalledException("Application '" + app.getTitle() + "' is not installed locally.");
        }

        out.println("Launching " + app.getTitle() + " with debugging enabled...");
        out.println("Debug port: " + port + ", suspend: " + suspend);

        String osName = System.getProperty("os.name").toLowerCase();

        try {
            ProcessBuilder pb;
            if (osName.contains("mac")) {
                // On macOS, use 'open' command with --env to pass environment variables
                pb = new ProcessBuilder(
                        "open",
                        "--env", ENV_DEBUG_PORT + "=" + port,
                        "--env", ENV_DEBUG_SUSPEND + "=" + suspend,
                        app.getAppBundle().getAbsolutePath()
                );
            } else {
                // On Windows and Linux, set env vars in ProcessBuilder and execute binary
                pb = new ProcessBuilder(app.getGuiExecutable().getAbsolutePath());
                pb.environment().put(ENV_DEBUG_PORT, String.valueOf(port));
                pb.environment().put(ENV_DEBUG_SUSPEND, String.valueOf(suspend));
            }

            pb.inheritIO();
            pb.start();

            out.println("Application launched in debug mode.");
            if (suspend) {
                out.println("Waiting for debugger to attach on port " + port + "...");
            }

        } catch (IOException e) {
            throw new IOException("Failed to launch application in debug mode: " + e.getMessage(), e);
        }
    }

    /**
     * Runs a specific CLI command with Java remote debugging enabled.
     *
     * @param context The packaging context containing the project directory
     * @param commandName The name of the command to run
     * @param args Arguments to pass to the command
     * @param port The debug port (default 5005)
     * @param suspend Whether to suspend and wait for debugger to attach
     * @param out Output stream for progress messages
     * @return The exit code from the command
     * @throws IOException if there's an I/O error
     * @throws NotInstalledException if the application is not installed
     * @throws CommandNotFoundException if the command is not defined in package.json
     */
    public int debugCommand(PackagingContext context, String commandName, String[] args,
                            int port, boolean suspend, PrintStream out)
            throws IOException, NotInstalledException, CommandNotFoundException {

        // Validate command exists in package.json
        validateCommand(context.directory, commandName);

        InstalledAppLocator.InstalledApp app = installedAppLocator.locate(context);

        if (!app.isInstalled()) {
            throw new NotInstalledException("Application '" + app.getTitle() + "' is not installed locally.");
        }

        File cliLauncher = app.getCliLauncher();
        if (cliLauncher == null || !cliLauncher.exists()) {
            throw new NotInstalledException("CLI launcher not found. The application may need to be reinstalled.");
        }

        out.println("Running command '" + commandName + "' with debugging enabled...");
        out.println("Debug port: " + port + ", suspend: " + suspend);

        // Build command with jdeploy:command prefix
        List<String> command = new ArrayList<>();
        command.add(cliLauncher.getAbsolutePath());
        command.add(CliInstallerConstants.JDEPLOY_COMMAND_ARG_PREFIX + commandName);
        command.add("--");
        command.addAll(Arrays.asList(args));

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.environment().put(ENV_DEBUG_PORT, String.valueOf(port));
            pb.environment().put(ENV_DEBUG_SUSPEND, String.valueOf(suspend));
            pb.inheritIO();
            Process process = pb.start();

            if (suspend) {
                out.println("Waiting for debugger to attach on port " + port + "...");
            }

            // Wait for command to complete and return exit code
            return process.waitFor();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Command execution interrupted", e);
        } catch (IOException e) {
            throw new IOException("Failed to run command in debug mode: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the default debug port.
     */
    public static int getDefaultDebugPort() {
        return DEFAULT_DEBUG_PORT;
    }

    /**
     * Validates that the command is defined in the project's package.json.
     */
    private void validateCommand(File projectDir, String commandName) throws IOException, CommandNotFoundException {
        File packageJsonFile = new File(projectDir, "package.json");

        if (!packageJsonFile.exists()) {
            throw new IOException("No package.json found in " + projectDir.getAbsolutePath());
        }

        String content = FileUtils.readFileToString(packageJsonFile, StandardCharsets.UTF_8);
        JSONObject packageJson = new JSONObject(content);

        Path packageJsonPath = packageJsonFile.toPath();
        JDeployProject project = new JDeployProject(packageJsonPath, packageJson);

        List<CommandSpec> commands = project.getCommandSpecs();

        boolean found = false;
        for (CommandSpec cmd : commands) {
            if (cmd.getName().equals(commandName)) {
                found = true;
                break;
            }
        }

        if (!found) {
            StringBuilder message = new StringBuilder();
            message.append("Command '").append(commandName).append("' is not defined in package.json.");

            if (!commands.isEmpty()) {
                message.append("\nAvailable commands: ");
                for (int i = 0; i < commands.size(); i++) {
                    if (i > 0) message.append(", ");
                    message.append(commands.get(i).getName());
                }
            } else {
                message.append("\nNo commands are defined in jdeploy.commands.");
            }

            throw new CommandNotFoundException(commandName, message.toString());
        }
    }
}

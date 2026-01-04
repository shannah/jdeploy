package ca.weblite.jdeploy.installer.services;

import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
import ca.weblite.tools.platform.Platform;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Executes service control operations (status, stop, start, install, uninstall).
 *
 * Single Responsibility: Execute service commands and return results.
 * Handles platform differences, timeouts, and error conditions.
 *
 * @author Steve Hannah
 */
public class ServiceOperationExecutor {
    private static final int OPERATION_TIMEOUT_SECONDS = 10;
    private final File cliLauncherPath;
    private final String packageName;
    private final String source;

    public ServiceOperationExecutor(File cliLauncherPath, String packageName, String source) {
        this.cliLauncherPath = cliLauncherPath;
        this.packageName = packageName;
        this.source = source;
    }

    /**
     * Checks if a service is currently running.
     *
     * @param commandName The service command name
     * @return Success with exit code 0 if running, failure otherwise
     */
    public ServiceOperationResult checkStatus(String commandName) {
        return executeServiceCommand(commandName, "status");
    }

    /**
     * Stops a running service.
     *
     * @param commandName The service command name
     * @return Success if stopped (or already stopped), failure on errors
     */
    public ServiceOperationResult stop(String commandName) {
        return executeServiceCommand(commandName, "stop");
    }

    /**
     * Starts a service.
     *
     * @param commandName The service command name
     * @return Success if started, failure on errors
     */
    public ServiceOperationResult start(String commandName) {
        return executeServiceCommand(commandName, "start");
    }

    /**
     * Installs a service with the native service manager.
     *
     * @param commandName The service command name
     * @return Success if installed, failure on errors
     */
    public ServiceOperationResult install(String commandName) {
        return executeServiceCommand(commandName, "install");
    }

    /**
     * Uninstalls a service from the native service manager.
     *
     * @param commandName The service command name
     * @return Success if uninstalled, failure on errors
     */
    public ServiceOperationResult uninstall(String commandName) {
        return executeServiceCommand(commandName, "uninstall");
    }

    /**
     * Runs the application update command.
     *
     * @return Success if update completed, failure on errors
     */
    public ServiceOperationResult runApplicationUpdate() {
        if (cliLauncherPath == null || !cliLauncherPath.exists()) {
            return ServiceOperationResult.failure("CLI launcher not found: " + cliLauncherPath);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                cliLauncherPath.getAbsolutePath(),
                "--jdeploy:update"
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean completed = process.waitFor(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                return ServiceOperationResult.failure("Application update timed out after " + OPERATION_TIMEOUT_SECONDS + " seconds");
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return ServiceOperationResult.success("Application update completed");
            } else {
                return ServiceOperationResult.failure("Application update failed", exitCode);
            }
        } catch (Exception e) {
            return ServiceOperationResult.failure("Application update error: " + e.getMessage());
        }
    }

    /**
     * Executes a service command (e.g., "myapp-server service stop").
     *
     * @param commandName The service command name
     * @param operation The operation (status, stop, start, install, uninstall)
     * @return Result of the operation
     */
    private ServiceOperationResult executeServiceCommand(String commandName, String operation) {
        File commandPath = getAbsoluteCommandPath(commandName);

        if (commandPath == null || !commandPath.exists()) {
            return ServiceOperationResult.failure(
                "Command not found: " + commandName +
                " (expected at: " + (commandPath != null ? commandPath.getAbsolutePath() : "unknown") + ")"
            );
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(commandPath.getAbsolutePath(), "service", operation);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean completed = process.waitFor(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                return ServiceOperationResult.failure(
                    "Service " + operation + " timed out after " + OPERATION_TIMEOUT_SECONDS + " seconds"
                );
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return ServiceOperationResult.success();
            } else {
                return ServiceOperationResult.failure(
                    "Service " + operation + " returned non-zero exit code",
                    exitCode
                );
            }
        } catch (Exception e) {
            return ServiceOperationResult.failure(
                "Service " + operation + " error: " + e.getMessage()
            );
        }
    }

    /**
     * Gets the absolute path to a CLI command installed in the per-app bin directory.
     * Uses CliCommandBinDirResolver to find the per-app bin directory and constructs
     * the platform-specific command path.
     *
     * Commands are installed in: ~/.jdeploy/bin-{arch}/{fqpn}/
     *
     * Platform-specific behavior:
     * - Windows: Commands are .cmd files (e.g., myapp-server.cmd)
     * - Mac/Linux: Commands are shell scripts with no extension
     *
     * @param commandName The base command name
     * @return Absolute File path to the command, or null if cannot be determined
     */
    private File getAbsoluteCommandPath(String commandName) {
        try {
            File binDir = CliCommandBinDirResolver.getPerAppBinDir(packageName, source);

            if (Platform.getSystemPlatform().isWindows()) {
                // Windows: use .cmd files (not shell scripts for Git Bash/Cygwin)
                return new File(binDir, commandName + ".cmd");
            } else {
                // Mac/Linux: shell scripts with no extension
                return new File(binDir, commandName);
            }
        } catch (Exception e) {
            return null;
        }
    }
}

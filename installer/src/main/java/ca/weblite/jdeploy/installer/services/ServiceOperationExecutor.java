package ca.weblite.jdeploy.installer.services;

import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
import ca.weblite.tools.platform.Platform;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    private static final int ELEVATED_OPERATION_TIMEOUT_SECONDS = 120; // Longer timeout for UAC prompt

    // Operations that require elevation on Windows
    private static final List<String> ELEVATED_OPERATIONS = Arrays.asList(
        "install", "uninstall", "start", "stop"
    );

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
     * @param version The version to update to (required for apps with services)
     * @return Success if update completed, failure on errors
     */
    public ServiceOperationResult runApplicationUpdate(String version) {
        if (cliLauncherPath == null || !cliLauncherPath.exists()) {
            return ServiceOperationResult.failure("CLI launcher not found: " + cliLauncherPath);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                cliLauncherPath.getAbsolutePath(),
                "--jdeploy:update",
                "--jdeploy:version=" + version,
                "--jdeploy:disable-launcher-update"
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Consume output stream in background thread to prevent blocking when buffer fills up
            Thread outputConsumer = new Thread(() -> {
                try {
                    java.io.InputStream inputStream = process.getInputStream();
                    byte[] buffer = new byte[1024];
                    while (inputStream.read(buffer) != -1) {
                        // Discard output
                    }
                } catch (Exception e) {
                    // Ignore - process may have been destroyed
                }
            });
            outputConsumer.setDaemon(true);
            outputConsumer.start();

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

        // On Windows, certain operations require elevation
        boolean needsElevation = Platform.getSystemPlatform().isWindows()
            && ELEVATED_OPERATIONS.contains(operation);

        if (needsElevation) {
            return executeElevatedServiceCommand(commandPath, operation, commandName);
        } else {
            return executeNormalServiceCommand(commandPath, operation);
        }
    }

    /**
     * Executes a service command without elevation.
     */
    private ServiceOperationResult executeNormalServiceCommand(File commandPath, String operation) {
        try {
            ProcessBuilder pb = new ProcessBuilder(commandPath.getAbsolutePath(), "service", operation);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Consume output stream in background thread to prevent blocking when buffer fills up
            Thread outputConsumer = new Thread(() -> {
                try {
                    java.io.InputStream inputStream = process.getInputStream();
                    byte[] buffer = new byte[1024];
                    while (inputStream.read(buffer) != -1) {
                        // Discard output
                    }
                } catch (Exception e) {
                    // Ignore - process may have been destroyed
                }
            });
            outputConsumer.setDaemon(true);
            outputConsumer.start();

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
     * Executes a service command with elevation (Windows only).
     * Uses PowerShell Start-Process -Verb RunAs to trigger UAC elevation.
     */
    private ServiceOperationResult executeElevatedServiceCommand(File commandPath, String operation, String commandName) {
        try {
            // Build a PowerShell script that:
            // 1. Runs the command elevated
            // 2. Returns the exit code
            String escapedPath = commandPath.getAbsolutePath().replace("'", "''");
            String psScript = String.format(
                "$p = Start-Process -FilePath '%s' -ArgumentList 'service','%s' -Verb RunAs -Wait -PassThru -WindowStyle Hidden; " +
                "exit $p.ExitCode",
                escapedPath, operation
            );

            ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-ExecutionPolicy", "Bypass", "-Command", psScript
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Consume output stream in background thread
            StringBuilder outputBuilder = new StringBuilder();
            Thread outputConsumer = new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                    );
                    String line;
                    while ((line = reader.readLine()) != null) {
                        outputBuilder.append(line).append("\n");
                    }
                } catch (Exception e) {
                    // Ignore
                }
            });
            outputConsumer.setDaemon(true);
            outputConsumer.start();

            boolean completed = process.waitFor(ELEVATED_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                return ServiceOperationResult.failure(
                    "Elevated service " + operation + " timed out after " + ELEVATED_OPERATION_TIMEOUT_SECONDS + " seconds"
                );
            }

            int exitCode = process.exitValue();

            // If install succeeded, configure permissions for future non-elevated access
            if (exitCode == 0 && "install".equals(operation)) {
                configureServicePermissions(commandName);
            }

            if (exitCode == 0) {
                return ServiceOperationResult.success();
            } else {
                String output = outputBuilder.toString().trim();
                String message = "Elevated service " + operation + " returned non-zero exit code";
                if (!output.isEmpty()) {
                    message += ": " + output;
                }
                return ServiceOperationResult.failure(message, exitCode);
            }
        } catch (Exception e) {
            return ServiceOperationResult.failure(
                "Elevated service " + operation + " error: " + e.getMessage()
            );
        }
    }

    /**
     * Configures Windows service permissions to allow the current user to start/stop
     * the service without requiring elevation in the future.
     *
     * This runs after service install to grant the installing user control over the service.
     */
    private void configureServicePermissions(String commandName) {
        if (!Platform.getSystemPlatform().isWindows()) {
            return;
        }

        try {
            // The service name is typically the command name
            // We need to grant the current user start/stop permissions using sc sdset
            // First, get the current security descriptor, then add our ACE

            // Get current user's SID
            ProcessBuilder getSidPb = new ProcessBuilder(
                "powershell", "-Command",
                "([System.Security.Principal.WindowsIdentity]::GetCurrent()).User.Value"
            );
            getSidPb.redirectErrorStream(true);
            Process getSidProcess = getSidPb.start();

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(getSidProcess.getInputStream())
            );
            String userSid = reader.readLine();
            getSidProcess.waitFor(10, TimeUnit.SECONDS);

            if (userSid == null || userSid.trim().isEmpty() || !userSid.startsWith("S-1-")) {
                System.err.println("Warning: Could not determine user SID for service permissions");
                return;
            }
            userSid = userSid.trim();

            // Grant the user start/stop/status permissions using PowerShell elevation
            // RPWPDTLO = Start, Stop, Pause/Continue, Interrogate
            String psScript = String.format(
                "$sd = (sc.exe sdshow '%s' | Where-Object { $_ -match '^D:' }); " +
                "if ($sd) { " +
                "  $newAce = '(A;;RPWPDTLO;;;%s)'; " +
                "  $newSd = $sd -replace '(D:\\(.*\\))S:', \"D:`$1${newAce}S:\"; " +
                "  if ($newSd -eq $sd) { $newSd = $sd + $newAce }; " +
                "  sc.exe sdset '%s' $newSd " +
                "}",
                commandName, userSid, commandName
            );

            ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-ExecutionPolicy", "Bypass", "-Command",
                "$p = Start-Process -FilePath 'powershell' -ArgumentList '-ExecutionPolicy','Bypass','-Command','" +
                    psScript.replace("'", "''") + "' -Verb RunAs -Wait -PassThru -WindowStyle Hidden; exit $p.ExitCode"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Consume output
            BufferedReader procReader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            while (procReader.readLine() != null) {
                // Discard
            }

            process.waitFor(30, TimeUnit.SECONDS);

            if (process.exitValue() != 0) {
                System.err.println("Warning: Could not configure service permissions for " + commandName);
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to configure service permissions: " + e.getMessage());
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

package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.packaging.PackageService;
import ca.weblite.jdeploy.packaging.PackagingContext;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for testing jDeploy applications on Windows using Docker containers.
 *
 * Uses the dockur/windows Docker image to run a Windows VM inside a container,
 * then installs and verifies the jDeploy application.
 *
 * Supports two modes:
 * - HEADLESS: Automated installation and verification
 * - RDP: Interactive mode for manual testing via Remote Desktop
 */
public class WindowsDockerTestService {

    private final PackageService packageService;
    private final LocalJDeployFilesGenerator jdeployFilesGenerator;

    public WindowsDockerTestService(
            PackageService packageService,
            LocalJDeployFilesGenerator jdeployFilesGenerator
    ) {
        this.packageService = packageService;
        this.jdeployFilesGenerator = jdeployFilesGenerator;
    }

    /**
     * Runs Windows Docker test.
     *
     * @param context Packaging context
     * @param config Docker configuration
     * @param out Output stream for progress messages
     * @return Test result
     * @throws IOException if file operations fail
     * @throws InterruptedException if interrupted while waiting
     */
    public WindowsTestResult runTest(
            PackagingContext context,
            WindowsDockerConfig config,
            PrintStream out
    ) throws IOException, InterruptedException {

        // Check Docker availability
        if (!isDockerAvailable()) {
            return WindowsTestResult.failed(
                    "Docker is not available. Please install Docker Desktop and ensure it's running.");
        }

        File projectDir = context.directory;
        File bundleDir = new File(projectDir, "jdeploy-bundle");
        File jdeployDir = new File(projectDir, "jdeploy");
        File sharedDir = new File(projectDir, ".windows-test");

        // Clean up any previous test artifacts
        if (sharedDir.exists()) {
            FileUtils.deleteDirectory(sharedDir);
        }

        try {
            // Step 1: Package the application
            out.println("Packaging application...");
            packageService.createJdeployBundle(context);

            if (!bundleDir.exists() || !bundleDir.isDirectory()) {
                return WindowsTestResult.failed(
                        "jdeploy-bundle directory not found after packaging.");
            }

            // Step 2: Generate jdeploy-files
            out.println("Generating local jdeploy-files...");
            jdeployDir.mkdirs();
            File jdeployFilesDir = jdeployFilesGenerator.generate(projectDir, bundleDir, jdeployDir);

            // Step 3: Prepare shared folder
            out.println("Preparing shared folder for Windows container...");
            prepareSharedFolder(sharedDir, bundleDir, jdeployFilesDir);

            // Step 4: Start Docker container
            out.println("Starting Windows Docker container...");
            out.println("(First run may take several minutes to download and boot Windows)");
            Process dockerProcess = startDockerContainer(config, sharedDir, out);

            try {
                if (config.getMode() == WindowsDockerConfig.Mode.RDP) {
                    // Interactive RDP mode
                    return runInteractiveMode(config, dockerProcess, sharedDir, out);
                } else {
                    // Headless automated mode
                    return runHeadlessMode(config, dockerProcess, sharedDir, out);
                }
            } finally {
                // Ensure container is stopped
                stopContainer(dockerProcess);
            }

        } finally {
            // Clean up shared folder (unless in RDP mode and user wants to keep it)
            if (config.getMode() != WindowsDockerConfig.Mode.RDP && sharedDir.exists()) {
                try {
                    FileUtils.deleteDirectory(sharedDir);
                } catch (IOException e) {
                    out.println("Warning: Could not clean up " + sharedDir.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Checks if Docker is available and running.
     */
    private boolean isDockerAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "info");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output to prevent blocking
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {
                    // Consume output
                }
            }

            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Prepares the shared folder structure for the Windows container.
     */
    private void prepareSharedFolder(File sharedDir, File bundleDir, File jdeployFilesDir)
            throws IOException {

        sharedDir.mkdirs();

        // Create subdirectories
        File sharedBundleDir = new File(sharedDir, "jdeploy-bundle");
        File sharedJdeployFilesDir = new File(sharedDir, "jdeploy-files");
        File sharedScriptsDir = new File(sharedDir, "scripts");
        File sharedResultsDir = new File(sharedDir, "results");

        sharedBundleDir.mkdirs();
        sharedJdeployFilesDir.mkdirs();
        sharedScriptsDir.mkdirs();
        sharedResultsDir.mkdirs();

        // Copy jdeploy-bundle
        FileUtils.copyDirectory(bundleDir, sharedBundleDir);

        // Copy jdeploy-files
        FileUtils.copyDirectory(jdeployFilesDir, sharedJdeployFilesDir);

        // Copy PowerShell scripts from resources
        copyResourceScript("scripts/startup-wrapper.ps1", sharedScriptsDir);
        copyResourceScript("scripts/install-and-verify.ps1", sharedScriptsDir);
        copyResourceScript("scripts/verification-checks.ps1", sharedScriptsDir);
    }

    /**
     * Copies a PowerShell script from resources to the target directory.
     */
    private void copyResourceScript(String resourcePath, File targetDir) throws IOException {
        String scriptName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        File targetFile = new File(targetDir, scriptName);

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            FileUtils.copyInputStreamToFile(is, targetFile);
        }
    }

    /**
     * Starts the Docker container with Windows.
     */
    private Process startDockerContainer(WindowsDockerConfig config, File sharedDir, PrintStream out)
            throws IOException {

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");

        // Mount shared folder
        cmd.add("-v");
        cmd.add(sharedDir.getAbsolutePath() + ":/shared");

        // Windows version
        cmd.add("-e");
        cmd.add("VERSION=" + config.getWindowsVersion());

        // For headless mode, set up startup script
        if (config.getMode() == WindowsDockerConfig.Mode.HEADLESS) {
            // dockur/windows runs scripts from /oem/runonce.ps1 or similar
            // We'll detect startup via marker files instead
        }

        // For RDP mode, expose port
        if (config.getMode() == WindowsDockerConfig.Mode.RDP) {
            cmd.add("-p");
            cmd.add(config.getRdpPort() + ":3389");
        }

        // Use the dockur/windows image
        cmd.add(WindowsDockerConfig.DOCKER_IMAGE);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        return pb.start();
    }

    /**
     * Runs headless automated testing mode.
     */
    private WindowsTestResult runHeadlessMode(
            WindowsDockerConfig config,
            Process dockerProcess,
            File sharedDir,
            PrintStream out
    ) throws InterruptedException, IOException {

        File readyMarker = new File(sharedDir, "windows-ready.marker");
        File completeMarker = new File(sharedDir, "install-complete.marker");
        File resultsDir = new File(sharedDir, "results");
        File exitCodeFile = new File(resultsDir, "exit-code.txt");
        File verificationFile = new File(resultsDir, "verification.json");
        File errorFile = new File(resultsDir, "error.txt");

        long deadline = System.currentTimeMillis() + (config.getTimeoutMinutes() * 60 * 1000L);

        // Wait for Windows to boot
        out.println("Waiting for Windows to boot...");
        while (System.currentTimeMillis() < deadline) {
            if (readyMarker.exists()) {
                out.println("Windows is ready, installation starting...");
                break;
            }

            // Check if container exited
            if (!dockerProcess.isAlive()) {
                return WindowsTestResult.failed(
                        "Docker container exited unexpectedly before Windows was ready");
            }

            Thread.sleep(5000);
        }

        if (!readyMarker.exists()) {
            return WindowsTestResult.timeout(config.getTimeoutMinutes());
        }

        // Wait for installation to complete
        out.println("Waiting for installation and verification to complete...");
        while (System.currentTimeMillis() < deadline) {
            if (completeMarker.exists()) {
                out.println("Installation complete.");
                break;
            }

            // Check if container exited
            if (!dockerProcess.isAlive()) {
                // Container exited, check if it completed first
                if (completeMarker.exists()) {
                    break;
                }
                return WindowsTestResult.failed(
                        "Docker container exited unexpectedly during installation");
            }

            Thread.sleep(2000);
        }

        if (!completeMarker.exists()) {
            return WindowsTestResult.timeout(config.getTimeoutMinutes());
        }

        // Read results
        return readResults(resultsDir);
    }

    /**
     * Runs interactive RDP mode.
     */
    private WindowsTestResult runInteractiveMode(
            WindowsDockerConfig config,
            Process dockerProcess,
            File sharedDir,
            PrintStream out
    ) throws InterruptedException {

        out.println();
        out.println("========================================");
        out.println("Windows container started in RDP mode");
        out.println("========================================");
        out.println();
        out.println("Connect via Microsoft Remote Desktop:");
        out.println("  Host: localhost:" + config.getRdpPort());
        out.println("  Username: (leave blank or use 'Docker')");
        out.println("  Password: (leave blank)");
        out.println();
        out.println("The jdeploy-bundle and jdeploy-files are available at:");
        out.println("  C:\\shared\\jdeploy-bundle");
        out.println("  C:\\shared\\jdeploy-files");
        out.println();
        out.println("To install, open PowerShell and run:");
        out.println("  C:\\shared\\scripts\\install-and-verify.ps1");
        out.println();
        out.println("Press Ctrl+C to stop the container...");
        out.println();

        // Set up shutdown hook to handle Ctrl+C gracefully
        Thread currentThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            currentThread.interrupt();
        }));

        try {
            // Wait for process to end (user Ctrl+C or container exit)
            int exitCode = dockerProcess.waitFor();
            out.println("Container exited with code: " + exitCode);
        } catch (InterruptedException e) {
            out.println("\nStopping container...");
            throw e;
        }

        // For RDP mode, we don't have automated results
        return new WindowsTestResult()
                .setSuccess(true)
                .addCheck(new WindowsTestResult.Check(
                        "RDP session",
                        WindowsTestResult.CheckStatus.PASSED,
                        "Interactive session completed"));
    }

    /**
     * Reads test results from the results directory.
     */
    private WindowsTestResult readResults(File resultsDir) throws IOException {
        File verificationFile = new File(resultsDir, "verification.json");
        File errorFile = new File(resultsDir, "error.txt");
        File exitCodeFile = new File(resultsDir, "exit-code.txt");

        // Check for error file first
        if (errorFile.exists()) {
            String error = FileUtils.readFileToString(errorFile, StandardCharsets.UTF_8).trim();
            return WindowsTestResult.failed(error);
        }

        // Read verification results
        if (verificationFile.exists()) {
            String json = FileUtils.readFileToString(verificationFile, StandardCharsets.UTF_8);
            JSONObject jsonObj = new JSONObject(json);
            return WindowsTestResult.fromJson(jsonObj);
        }

        // Fall back to exit code
        if (exitCodeFile.exists()) {
            String exitCodeStr = FileUtils.readFileToString(exitCodeFile, StandardCharsets.UTF_8).trim();
            int exitCode = Integer.parseInt(exitCodeStr);
            return new WindowsTestResult().setSuccess(exitCode == 0);
        }

        return WindowsTestResult.failed("No results found in " + resultsDir.getAbsolutePath());
    }

    /**
     * Stops the Docker container.
     */
    private void stopContainer(Process dockerProcess) {
        if (dockerProcess != null && dockerProcess.isAlive()) {
            dockerProcess.destroy();
            try {
                // Give it a few seconds to shut down gracefully
                dockerProcess.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Force kill if still running
            if (dockerProcess.isAlive()) {
                dockerProcess.destroyForcibly();
            }
        }
    }
}

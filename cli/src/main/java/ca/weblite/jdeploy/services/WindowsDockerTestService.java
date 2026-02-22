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

            // Step 4: Check if this is the first run (installing to golden volume)
            boolean isFirstRun = !isGoldenVolumeReady();

            // Step 5: Start Docker container
            out.println("Starting Windows Docker container...");
            if (isFirstRun) {
                out.println("(First run: Installing Windows to golden snapshot - this takes ~20 minutes)");
                out.println("(Subsequent runs will start from clean snapshot in ~30 seconds)");
            }
            Process dockerProcess = startDockerContainer(config, sharedDir, out);

            try {
                if (config.getMode() == WindowsDockerConfig.Mode.RDP) {
                    // Interactive RDP mode
                    WindowsTestResult result = runInteractiveMode(config, dockerProcess, sharedDir, out, isFirstRun);
                    return result;
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

        // Create OEM folder with autostart batch file for Windows Startup
        File oemDir = new File(sharedDir, "oem");
        oemDir.mkdirs();
        copyResourceScript("scripts/windows/autostart-install.bat", oemDir);
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

    private static final String GOLDEN_VOLUME = "jdeploy-windows-golden";
    private static final String WORKING_VOLUME = "jdeploy-windows-storage";
    private static final String CONTAINER_NAME = "jdeploy-windows-test";

    /**
     * Checks if the golden Windows volume exists and is ready.
     */
    private boolean isGoldenVolumeReady() {
        try {
            // Check if volume exists
            ProcessBuilder pb = new ProcessBuilder("docker", "volume", "inspect", GOLDEN_VOLUME);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                return false;
            }

            // Check for completion marker inside the volume
            ProcessBuilder checkMarker = new ProcessBuilder(
                    "docker", "run", "--rm",
                    "-v", GOLDEN_VOLUME + ":/storage:ro",
                    "alpine", "test", "-f", "/storage/.jdeploy-windows-ready"
            );
            checkMarker.redirectErrorStream(true);
            Process markerProcess = checkMarker.start();
            return markerProcess.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Creates a completion marker in the golden volume.
     */
    private void markGoldenVolumeReady(PrintStream out) {
        try {
            out.println("Marking golden volume as ready...");
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "run", "--rm",
                    "-v", GOLDEN_VOLUME + ":/storage",
                    "alpine", "touch", "/storage/.jdeploy-windows-ready"
            );
            pb.redirectErrorStream(true);
            pb.start().waitFor();
        } catch (Exception e) {
            out.println("Warning: Could not mark golden volume as ready: " + e.getMessage());
        }
    }

    /**
     * Copies the golden volume to the working volume for a clean test environment.
     */
    private void copyGoldenToWorking(PrintStream out) throws IOException, InterruptedException {
        out.println("Copying golden Windows image to working volume (this gives you a clean state)...");

        // Remove existing working volume
        new ProcessBuilder("docker", "volume", "rm", "-f", WORKING_VOLUME)
                .redirectErrorStream(true).start().waitFor();

        // Create new working volume
        new ProcessBuilder("docker", "volume", "create", WORKING_VOLUME)
                .redirectErrorStream(true).start().waitFor();

        // Copy contents from golden to working
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm",
                "-v", GOLDEN_VOLUME + ":/source:ro",
                "-v", WORKING_VOLUME + ":/dest",
                "alpine", "sh", "-c", "cp -a /source/. /dest/"
        );
        pb.inheritIO();
        Process p = pb.start();
        int exitCode = p.waitFor();

        if (exitCode != 0) {
            throw new IOException("Failed to copy golden volume to working volume");
        }

        out.println("Clean Windows environment ready.");
    }

    /**
     * Starts the Docker container with Windows.
     */
    private Process startDockerContainer(WindowsDockerConfig config, File sharedDir, PrintStream out)
            throws IOException, InterruptedException {

        // Stop and remove any existing container with this name
        try {
            new ProcessBuilder("docker", "stop", CONTAINER_NAME)
                    .redirectErrorStream(true).start().waitFor();
            new ProcessBuilder("docker", "rm", CONTAINER_NAME)
                    .redirectErrorStream(true).start().waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Determine which volume to use
        String storageVolume;

        if (!isGoldenVolumeReady()) {
            // Golden doesn't exist - install Windows to golden volume
            out.println("First run: Installing Windows to golden volume (this will take ~20 minutes)...");
            out.println("Subsequent runs will be much faster.");
            storageVolume = GOLDEN_VOLUME;
        } else if (config.isClean()) {
            // Golden exists and clean requested - copy golden to working for clean state
            copyGoldenToWorking(out);
            storageVolume = WORKING_VOLUME;
        } else {
            // Golden exists, not clean - reuse working volume as-is
            out.println("Reusing existing Windows state (use --windows=rdp,clean for fresh state)");
            storageVolume = WORKING_VOLUME;
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("--name");
        cmd.add(CONTAINER_NAME);

        // Required for dockur/windows - needs privileged mode or device access
        cmd.add("--privileged");

        // Mount shared folder
        cmd.add("-v");
        cmd.add(sharedDir.getAbsolutePath() + ":/shared");

        // Mount OEM folder for autostart script (contents copied to C:\OEM on first boot)
        File oemDir = new File(sharedDir, "oem");
        if (oemDir.exists()) {
            cmd.add("-v");
            cmd.add(oemDir.getAbsolutePath() + ":/oem");
        }

        // Mount storage volume (either golden for first install, or working for subsequent runs)
        cmd.add("-v");
        cmd.add(storageVolume + ":/storage");

        // Windows version
        cmd.add("-e");
        cmd.add("VERSION=" + config.getWindowsVersion());

        // RAM allocation (Windows needs at least 4GB)
        cmd.add("-e");
        cmd.add("RAM_SIZE=4G");

        // Disable KVM on macOS (no /dev/kvm available)
        // This makes it slower but allows running on macOS
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            cmd.add("-e");
            cmd.add("KVM=N");
            out.println("Note: Running without KVM acceleration (macOS). Windows boot will be slower.");
        }

        // Expose web interface port (always needed for viewing Windows desktop)
        cmd.add("-p");
        cmd.add(config.getWebPort() + ":8006");

        // For RDP mode, also expose RDP port
        if (config.getMode() == WindowsDockerConfig.Mode.RDP) {
            cmd.add("-p");
            cmd.add(config.getRdpPort() + ":3389");
        }

        // Use the dockur/windows image
        cmd.add(WindowsDockerConfig.DOCKER_IMAGE);

        out.println("Running: " + String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.inheritIO();  // Show Docker output in real-time

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
     * Runs interactive mode with web-based viewer.
     */
    private WindowsTestResult runInteractiveMode(
            WindowsDockerConfig config,
            Process dockerProcess,
            File sharedDir,
            PrintStream out,
            boolean isFirstRun
    ) throws InterruptedException {

        out.println();
        out.println("========================================");
        out.println("Windows container started");
        out.println("========================================");
        out.println();
        if (isFirstRun) {
            out.println("MODE: First run - creating golden snapshot");
            out.println("      (This Windows installation will be saved for future runs)");
        } else if (config.isClean()) {
            out.println("MODE: Clean state (from golden snapshot)");
        } else {
            out.println("MODE: Reusing previous state");
            out.println("      (Use --windows=rdp,clean for fresh state)");
        }
        out.println();
        out.println("Web viewer will open at: http://localhost:" + config.getWebPort());
        out.println();
        out.println("You can also connect via RDP:");
        out.println("  Host: localhost:" + config.getRdpPort());
        out.println("  Username: (leave blank or use 'Docker')");
        out.println("  Password: (leave blank)");
        out.println();
        out.println("The jdeploy-bundle and jdeploy-files are available at:");
        out.println("  C:\\shared\\jdeploy-bundle");
        out.println("  C:\\shared\\jdeploy-files");
        out.println();
        out.println("To install manually, open Command Prompt and run:");
        out.println("  C:\\OEM\\autostart-install.bat");
        out.println();
        out.println("To enable autostart for future runs, copy the batch file to Startup:");
        out.println("  copy C:\\OEM\\autostart-install.bat \"%APPDATA%\\Microsoft\\Windows\\Start Menu\\Programs\\Startup\\\"");
        out.println();
        out.println("Press Ctrl+C to stop the container...");
        out.println();

        // Start a thread to wait for web interface and then open browser
        Thread browserOpenerThread = new Thread(() -> {
            try {
                // Wait for web interface to become available
                out.println("Waiting for web interface to become available...");
                long deadline = System.currentTimeMillis() + 180000; // 3 minute timeout for Windows boot
                boolean ready = false;

                while (System.currentTimeMillis() < deadline && dockerProcess.isAlive()) {
                    try {
                        java.net.Socket socket = new java.net.Socket();
                        socket.connect(new java.net.InetSocketAddress("localhost", config.getWebPort()), 1000);
                        socket.close();
                        ready = true;
                        break;
                    } catch (Exception e) {
                        // Not ready yet, wait and retry
                        Thread.sleep(3000);
                    }
                }

                if (ready && dockerProcess.isAlive()) {
                    out.println("Web interface ready! Opening browser...");
                    openBrowser("http://localhost:" + config.getWebPort(), out);

                    // If this is the first run, mark golden volume as ready after Windows boots
                    if (isFirstRun) {
                        // Wait a bit more for Windows to fully initialize
                        Thread.sleep(30000);
                        out.println("Marking golden snapshot as ready for future runs...");
                        markGoldenVolumeReady(out);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        browserOpenerThread.setDaemon(true);
        browserOpenerThread.start();

        // Set up shutdown hook to handle Ctrl+C gracefully
        final String containerToStop = "jdeploy-windows-test";
        Thread shutdownHook = new Thread(() -> {
            out.println("\nStopping Windows container...");
            try {
                Process stopProcess = new ProcessBuilder("docker", "stop", containerToStop)
                        .redirectErrorStream(true).start();
                stopProcess.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                // Ignore errors during shutdown
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        try {
            // Wait for process to end (user Ctrl+C or container exit)
            int exitCode = dockerProcess.waitFor();
            out.println("Container exited with code: " + exitCode);
            // Remove shutdown hook if we exited normally
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException e) {
                // Already shutting down
            }
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

    /**
     * Opens the RDP client to connect to the Windows container.
     */
    private void openRdpClient(int port, PrintStream out) {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("mac")) {
                // macOS: Use open with rdp:// URL scheme (works with Microsoft Remote Desktop)
                ProcessBuilder pb = new ProcessBuilder("open", "rdp://localhost:" + port);
                pb.start();
            } else if (os.contains("win")) {
                // Windows: Use mstsc with connection string
                ProcessBuilder pb = new ProcessBuilder("mstsc", "/v:localhost:" + port);
                pb.start();
            } else if (os.contains("linux")) {
                // Linux: Try common RDP clients
                String[] clients = {"xfreerdp", "rdesktop", "remmina"};
                for (String client : clients) {
                    try {
                        ProcessBuilder pb;
                        if ("xfreerdp".equals(client)) {
                            pb = new ProcessBuilder(client, "/v:localhost:" + port, "/cert:ignore");
                        } else if ("rdesktop".equals(client)) {
                            pb = new ProcessBuilder(client, "localhost:" + port);
                        } else {
                            // remmina needs a connection file, skip for now
                            continue;
                        }
                        pb.start();
                        return;
                    } catch (IOException e) {
                        // Client not found, try next
                    }
                }
                out.println("Note: No RDP client found. Install xfreerdp or rdesktop to auto-connect.");
            }
        } catch (IOException e) {
            out.println("Could not open RDP client: " + e.getMessage());
            out.println("Please connect manually using your RDP client to localhost:" + port);
        }
    }

    /**
     * Opens a URL in the default browser.
     */
    private void openBrowser(String url, PrintStream out) {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("mac")) {
                ProcessBuilder pb = new ProcessBuilder("open", url);
                pb.start();
            } else if (os.contains("win")) {
                ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", url);
                pb.start();
            } else if (os.contains("linux")) {
                ProcessBuilder pb = new ProcessBuilder("xdg-open", url);
                pb.start();
            }
        } catch (IOException e) {
            out.println("Could not open browser: " + e.getMessage());
            out.println("Please open manually: " + url);
        }
    }
}

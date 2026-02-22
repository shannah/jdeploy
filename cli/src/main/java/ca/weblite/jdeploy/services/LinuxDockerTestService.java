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
 * Service for testing jDeploy applications on Linux using Docker containers.
 *
 * Uses the dorowu/ubuntu-desktop-lxde-vnc Docker image to run a Linux desktop
 * inside a container, accessible via VNC or browser (noVNC).
 *
 * Supports two modes:
 * - HEADLESS: Automated installation and verification
 * - VNC: Interactive mode for manual testing via VNC/browser
 */
public class LinuxDockerTestService {

    private final PackageService packageService;
    private final LocalJDeployFilesGenerator jdeployFilesGenerator;

    public LinuxDockerTestService(
            PackageService packageService,
            LocalJDeployFilesGenerator jdeployFilesGenerator
    ) {
        this.packageService = packageService;
        this.jdeployFilesGenerator = jdeployFilesGenerator;
    }

    /**
     * Runs Linux Docker test.
     *
     * @param context Packaging context
     * @param config Docker configuration
     * @param out Output stream for progress messages
     * @return Test result
     * @throws IOException if file operations fail
     * @throws InterruptedException if interrupted while waiting
     */
    public LinuxTestResult runTest(
            PackagingContext context,
            LinuxDockerConfig config,
            PrintStream out
    ) throws IOException, InterruptedException {

        // Check Docker availability
        if (!isDockerAvailable()) {
            return LinuxTestResult.failed(
                    "Docker is not available. Please install Docker Desktop and ensure it's running.");
        }

        File projectDir = context.directory;
        File bundleDir = new File(projectDir, "jdeploy-bundle");
        File jdeployDir = new File(projectDir, "jdeploy");
        File sharedDir = new File(projectDir, ".linux-test");

        // Clean up any previous test artifacts
        if (sharedDir.exists()) {
            FileUtils.deleteDirectory(sharedDir);
        }

        try {
            // Step 1: Package the application
            out.println("Packaging application...");
            packageService.createJdeployBundle(context);

            if (!bundleDir.exists() || !bundleDir.isDirectory()) {
                return LinuxTestResult.failed(
                        "jdeploy-bundle directory not found after packaging.");
            }

            // Step 2: Generate jdeploy-files
            out.println("Generating local jdeploy-files...");
            jdeployDir.mkdirs();
            File jdeployFilesDir = jdeployFilesGenerator.generate(projectDir, bundleDir, jdeployDir);

            // Step 3: Prepare shared folder
            out.println("Preparing shared folder for Linux container...");
            prepareSharedFolder(sharedDir, bundleDir, jdeployFilesDir);

            // Step 4: Start Docker container
            out.println("Starting Linux Docker container...");
            Process dockerProcess = startDockerContainer(config, sharedDir, out);

            try {
                if (config.getMode() == LinuxDockerConfig.Mode.VNC) {
                    // Interactive VNC mode
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
            // Clean up shared folder (unless in VNC mode and user wants to keep it)
            if (config.getMode() != LinuxDockerConfig.Mode.VNC && sharedDir.exists()) {
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
     * Prepares the shared folder structure for the Linux container.
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

        // Copy shell scripts from resources
        copyResourceScript("scripts/linux/install-and-verify.sh", sharedScriptsDir);
        copyResourceScript("scripts/linux/verification-checks.sh", sharedScriptsDir);

        // Make scripts executable
        new File(sharedScriptsDir, "install-and-verify.sh").setExecutable(true);
        new File(sharedScriptsDir, "verification-checks.sh").setExecutable(true);
    }

    /**
     * Copies a shell script from resources to the target directory.
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
     * Starts the Docker container with Linux desktop.
     */
    private Process startDockerContainer(LinuxDockerConfig config, File sharedDir, PrintStream out)
            throws IOException {

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");

        // Mount shared folder
        cmd.add("-v");
        cmd.add(sharedDir.getAbsolutePath() + ":/home/ubuntu/shared");

        // Set resolution
        cmd.add("-e");
        cmd.add("RESOLUTION=" + config.getResolution());

        // For VNC mode, expose ports
        if (config.getMode() == LinuxDockerConfig.Mode.VNC) {
            // noVNC (browser) port
            cmd.add("-p");
            cmd.add(config.getNoVncPort() + ":80");
            // VNC port
            cmd.add("-p");
            cmd.add(config.getVncPort() + ":5900");
        }

        // Use the Linux desktop image
        cmd.add(LinuxDockerConfig.DOCKER_IMAGE);

        out.println("Running: " + String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.inheritIO();  // Show Docker output in real-time

        return pb.start();
    }

    /**
     * Runs headless automated testing mode.
     */
    private LinuxTestResult runHeadlessMode(
            LinuxDockerConfig config,
            Process dockerProcess,
            File sharedDir,
            PrintStream out
    ) throws InterruptedException, IOException {

        File readyMarker = new File(sharedDir, "linux-ready.marker");
        File completeMarker = new File(sharedDir, "install-complete.marker");
        File resultsDir = new File(sharedDir, "results");

        long deadline = System.currentTimeMillis() + (config.getTimeoutMinutes() * 60 * 1000L);

        // Wait for container to be ready
        out.println("Waiting for Linux container to start...");
        Thread.sleep(5000);  // Give container time to initialize

        // Execute the installation script inside the container
        out.println("Running installation script...");

        // Use docker exec to run the script
        ProcessBuilder execPb = new ProcessBuilder(
                "docker", "exec", "-i",
                getContainerId(dockerProcess),
                "/bin/bash", "/home/ubuntu/shared/scripts/install-and-verify.sh"
        );
        execPb.redirectErrorStream(true);
        execPb.inheritIO();

        Process execProcess = execPb.start();
        int execExitCode = execProcess.waitFor();

        out.println("Installation script exited with code: " + execExitCode);

        // Read results
        return readResults(resultsDir);
    }

    /**
     * Gets the container ID from a running docker process.
     */
    private String getContainerId(Process dockerProcess) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("docker", "ps", "-q", "-l");
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String containerId;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            containerId = reader.readLine();
        }

        process.waitFor();
        return containerId != null ? containerId.trim() : "";
    }

    /**
     * Runs interactive VNC mode.
     */
    private LinuxTestResult runInteractiveMode(
            LinuxDockerConfig config,
            Process dockerProcess,
            File sharedDir,
            PrintStream out
    ) throws InterruptedException {

        out.println();
        out.println("========================================");
        out.println("Linux container started in VNC mode");
        out.println("========================================");
        out.println();
        out.println("Connect via browser (recommended):");
        out.println("  http://localhost:" + config.getNoVncPort());
        out.println();
        out.println("Or via VNC client:");
        out.println("  Host: localhost:" + config.getVncPort());
        out.println("  Password: (none)");
        out.println();
        out.println("The jdeploy-bundle and jdeploy-files are available at:");
        out.println("  /home/ubuntu/shared/jdeploy-bundle");
        out.println("  /home/ubuntu/shared/jdeploy-files");
        out.println();
        out.println("To install, open a terminal and run:");
        out.println("  bash /home/ubuntu/shared/scripts/install-and-verify.sh");
        out.println();
        out.println("Press Ctrl+C to stop the container...");
        out.println();

        // Start a thread to wait for container ready and open browser
        Thread browserOpenerThread = new Thread(() -> {
            try {
                // Wait a few seconds for the container to start
                Thread.sleep(5000);

                if (dockerProcess.isAlive()) {
                    out.println("Opening browser to noVNC interface...");
                    openBrowser("http://localhost:" + config.getNoVncPort(), out);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        browserOpenerThread.setDaemon(true);
        browserOpenerThread.start();

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

        // For VNC mode, we don't have automated results
        return new LinuxTestResult()
                .setSuccess(true)
                .addCheck(new LinuxTestResult.Check(
                        "VNC session",
                        LinuxTestResult.CheckStatus.PASSED,
                        "Interactive session completed"));
    }

    /**
     * Reads test results from the results directory.
     */
    private LinuxTestResult readResults(File resultsDir) throws IOException {
        File verificationFile = new File(resultsDir, "verification.json");
        File errorFile = new File(resultsDir, "error.txt");
        File exitCodeFile = new File(resultsDir, "exit-code.txt");

        // Check for error file first
        if (errorFile.exists()) {
            String error = FileUtils.readFileToString(errorFile, StandardCharsets.UTF_8).trim();
            return LinuxTestResult.failed(error);
        }

        // Read verification results
        if (verificationFile.exists()) {
            String json = FileUtils.readFileToString(verificationFile, StandardCharsets.UTF_8);
            JSONObject jsonObj = new JSONObject(json);
            return LinuxTestResult.fromJson(jsonObj);
        }

        // Fall back to exit code
        if (exitCodeFile.exists()) {
            String exitCodeStr = FileUtils.readFileToString(exitCodeFile, StandardCharsets.UTF_8).trim();
            int exitCode = Integer.parseInt(exitCodeStr);
            return new LinuxTestResult().setSuccess(exitCode == 0);
        }

        return LinuxTestResult.failed("No results found in " + resultsDir.getAbsolutePath());
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
                // Try common Linux browsers/openers
                String[] openers = {"xdg-open", "gnome-open", "kde-open"};
                for (String opener : openers) {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(opener, url);
                        pb.start();
                        return;
                    } catch (IOException e) {
                        // Opener not found, try next
                    }
                }
                out.println("Note: Could not auto-open browser. Please open manually: " + url);
            }
        } catch (IOException e) {
            out.println("Could not open browser: " + e.getMessage());
            out.println("Please open manually: " + url);
        }
    }
}

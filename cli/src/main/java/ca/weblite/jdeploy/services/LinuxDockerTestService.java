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

    private static final String CONTAINER_NAME = "jdeploy-linux-test";
    private static final String CONFIG_VOLUME = "jdeploy-linux-config";

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
            File jdeployFilesDir = null;

            if (config.isDevMode()) {
                // Dev mode: skip packaging, we'll build jdeploy from source in the container
                out.println("Dev mode: jdeploy will be built from source inside the container");
                out.println("  jDeploy project: " + config.getJdeployHome().getAbsolutePath());
                out.println("  App project: " + projectDir.getAbsolutePath());
            } else {
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
                jdeployFilesDir = jdeployFilesGenerator.generate(projectDir, bundleDir, jdeployDir);
            }

            // Step 3: Prepare shared folder
            out.println("Preparing shared folder for Linux container...");
            prepareSharedFolder(sharedDir, bundleDir, jdeployFilesDir, config);

            // Step 4: Start Docker container
            out.println("Starting Linux Docker container...");
            Process dockerProcess = startDockerContainer(config, sharedDir, projectDir, out);

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
    private void prepareSharedFolder(File sharedDir, File bundleDir, File jdeployFilesDir,
                                      LinuxDockerConfig config)
            throws IOException {

        sharedDir.mkdirs();

        // Create subdirectories
        File sharedScriptsDir = new File(sharedDir, "scripts");
        File sharedResultsDir = new File(sharedDir, "results");

        sharedScriptsDir.mkdirs();
        sharedResultsDir.mkdirs();

        if (!config.isDevMode()) {
            // Non-dev mode: copy bundle and jdeploy-files
            File sharedBundleDir = new File(sharedDir, "jdeploy-bundle");
            File sharedJdeployFilesDir = new File(sharedDir, "jdeploy-files");
            sharedBundleDir.mkdirs();
            sharedJdeployFilesDir.mkdirs();

            FileUtils.copyDirectory(bundleDir, sharedBundleDir);
            FileUtils.copyDirectory(jdeployFilesDir, sharedJdeployFilesDir);
        }

        // Copy shell scripts from resources
        copyResourceScript("scripts/linux/install-and-verify.sh", sharedScriptsDir);
        copyResourceScript("scripts/linux/verification-checks.sh", sharedScriptsDir);
        copyResourceScript("scripts/linux/install-and-launch.sh", sharedScriptsDir);
        copyResourceScript("scripts/linux/dev-install-and-launch.sh", sharedScriptsDir);

        // Make scripts executable
        new File(sharedScriptsDir, "install-and-verify.sh").setExecutable(true);
        new File(sharedScriptsDir, "verification-checks.sh").setExecutable(true);
        new File(sharedScriptsDir, "install-and-launch.sh").setExecutable(true);
        new File(sharedScriptsDir, "dev-install-and-launch.sh").setExecutable(true);

        // Write run arguments to file for the container script
        if (config.getRunArgs() != null && config.getRunArgs().length > 0) {
            String runArgs = String.join(" ", config.getRunArgs());
            FileUtils.writeStringToFile(
                    new File(sharedDir, "run-args.txt"),
                    runArgs,
                    StandardCharsets.UTF_8
            );
        }

        // Create autostart directory and entry for VNC mode
        File autostartDir = new File(sharedDir, "autostart");
        autostartDir.mkdirs();

        // Choose the right script based on dev mode
        String execScript = config.isDevMode()
                ? "/config/shared/scripts/dev-install-and-launch.sh"
                : "/config/shared/scripts/install-and-launch.sh";

        // Create .desktop autostart entry
        // Use xfce4-terminal explicitly for XFCE desktop with --hold to keep window open
        String desktopEntry =
                "[Desktop Entry]\n" +
                "Type=Application\n" +
                "Name=jDeploy Auto-Install\n" +
                "Exec=xfce4-terminal --hold -e \"bash " + execScript + "\"\n" +
                "Hidden=false\n" +
                "NoDisplay=false\n" +
                "X-GNOME-Autostart-enabled=true\n" +
                "Terminal=false\n";  // We're handling terminal ourselves

        File desktopFile = new File(autostartDir, "jdeploy-autostart.desktop");
        FileUtils.writeStringToFile(desktopFile, desktopEntry, StandardCharsets.UTF_8);

        // Create custom-cont-init.d script to set up autostart on container start
        File initDir = new File(sharedDir, "custom-cont-init.d");
        initDir.mkdirs();

        String initScript =
                "#!/bin/bash\n" +
                "# Set up autostart for jDeploy app\n" +
                "echo '[jdeploy-init] Setting up autostart...'\n" +
                "mkdir -p /config/.config/autostart\n" +
                "cp /config/shared/autostart/*.desktop /config/.config/autostart/\n" +
                "chmod +x /config/.config/autostart/*.desktop\n" +
                "echo '[jdeploy-init] Autostart setup complete:'\n" +
                "ls -la /config/.config/autostart/\n";

        File initScriptFile = new File(initDir, "01-setup-autostart");
        FileUtils.writeStringToFile(initScriptFile, initScript, StandardCharsets.UTF_8);
        initScriptFile.setExecutable(true);
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
     * Checks if the Linux container already exists.
     */
    private boolean containerExists() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "container", "inspect", CONTAINER_NAME);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            // Consume output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                while (reader.readLine() != null) {}
            }
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if the Linux container is running.
     */
    private boolean containerRunning() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "ps", "-q", "-f", "name=" + CONTAINER_NAME);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                return line != null && !line.isEmpty();
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Removes the existing Linux container and optionally the config volume.
     */
    private void removeContainer(boolean removeVolume, PrintStream out) {
        try {
            out.println("Removing existing Linux container...");
            new ProcessBuilder("docker", "stop", CONTAINER_NAME)
                    .redirectErrorStream(true).start().waitFor();
            new ProcessBuilder("docker", "rm", CONTAINER_NAME)
                    .redirectErrorStream(true).start().waitFor();
            if (removeVolume) {
                out.println("Removing config volume for clean state...");
                new ProcessBuilder("docker", "volume", "rm", "-f", CONFIG_VOLUME)
                        .redirectErrorStream(true).start().waitFor();
            }
        } catch (Exception e) {
            // Ignore errors
        }
    }

    /**
     * Starts the Docker container with Linux desktop.
     */
    private Process startDockerContainer(LinuxDockerConfig config, File sharedDir,
                                          File projectDir, PrintStream out)
            throws IOException, InterruptedException {

        // Handle existing container
        if (containerExists()) {
            if (config.isClean()) {
                // Clean mode: remove container and volume for fresh state
                removeContainer(true, out);
            } else if (containerRunning()) {
                // Container already running - stop it first so we can start fresh with new mounts
                out.println("Stopping existing container...");
                new ProcessBuilder("docker", "stop", CONTAINER_NAME)
                        .redirectErrorStream(true).start().waitFor();
            }
        }

        // If container exists but not clean, try to start it
        if (!config.isClean() && containerExists()) {
            out.println("Starting existing Linux container (use --linux=vnc,clean for fresh state)...");
            ProcessBuilder pb = new ProcessBuilder("docker", "start", "-a", CONTAINER_NAME);
            pb.redirectErrorStream(true);
            pb.inheritIO();
            return pb.start();
        }

        // Create new container
        if (config.isClean()) {
            out.println("Creating fresh Linux container (clean mode)...");
        } else {
            out.println("Creating new Linux container...");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--name");
        cmd.add(CONTAINER_NAME);

        // Mount persistent config volume (preserves installed apps between runs)
        cmd.add("-v");
        cmd.add(CONFIG_VOLUME + ":/config");

        // Mount shared folder to /config/shared
        cmd.add("-v");
        cmd.add(sharedDir.getAbsolutePath() + ":/config/shared");

        // Mount custom-cont-init.d for autostart setup (runs on container init)
        // Note: linuxserver images expect this at root level, not under /config
        File initDir = new File(sharedDir, "custom-cont-init.d");
        if (initDir.exists()) {
            cmd.add("-v");
            cmd.add(initDir.getAbsolutePath() + ":/custom-cont-init.d");
        }

        // Always mount app project directory
        cmd.add("-v");
        cmd.add(projectDir.getAbsolutePath() + ":/app");

        // Dev mode: also mount jdeploy project
        if (config.isDevMode()) {
            cmd.add("-v");
            cmd.add(config.getJdeployHome().getAbsolutePath() + ":/jdeploy");
        }

        // Set user/group IDs to match host user (avoids permission issues)
        cmd.add("-e");
        cmd.add("PUID=1000");
        cmd.add("-e");
        cmd.add("PGID=1000");

        // Set resolution via environment
        String[] resParts = config.getResolution().split("x");
        if (resParts.length == 2) {
            cmd.add("-e");
            cmd.add("SCREEN_WIDTH=" + resParts[0]);
            cmd.add("-e");
            cmd.add("SCREEN_HEIGHT=" + resParts[1]);
        }

        // For VNC mode, expose ports
        if (config.getMode() == LinuxDockerConfig.Mode.VNC) {
            // Web interface port (linuxserver/webtop uses 3000)
            cmd.add("-p");
            cmd.add(config.getNoVncPort() + ":3000");
            // VNC port (3001 in linuxserver/webtop)
            cmd.add("-p");
            cmd.add(config.getVncPort() + ":3001");
        }

        // Security option for GUI apps
        cmd.add("--security-opt");
        cmd.add("seccomp=unconfined");

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
                "/bin/bash", "/config/shared/scripts/install-and-verify.sh"
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
        out.println("Linux container started");
        out.println("========================================");
        out.println();
        if (config.isClean()) {
            out.println("MODE: Clean state (fresh container)");
        } else {
            out.println("MODE: Reusing previous state");
            out.println("      (Use --linux=vnc,clean for fresh state)");
        }
        if (config.isDevMode()) {
            out.println("      Dev mode: jdeploy will build from source");
        }
        out.println();
        out.println("Connect via browser (recommended):");
        out.println("  http://localhost:" + config.getNoVncPort());
        out.println();
        out.println("Or via VNC client:");
        out.println("  Host: localhost:" + config.getVncPort());
        out.println("  Password: (none)");
        out.println();

        if (config.isDevMode()) {
            out.println("Dev mode paths:");
            out.println("  jDeploy project: /jdeploy");
            out.println("  App project: /app");
            out.println();
            out.println("The app will be automatically built and launched.");
            out.println("Watch the terminal window for build progress.");
        } else {
            out.println("The jdeploy-bundle and jdeploy-files are available at:");
            out.println("  /config/shared/jdeploy-bundle");
            out.println("  /config/shared/jdeploy-files");
            out.println();
            out.println("To install manually, open a terminal and run:");
            out.println("  bash /config/shared/scripts/install-and-verify.sh");
        }

        out.println();
        out.println("Press Ctrl+C to stop the container...");
        out.println();

        // Start a thread to wait for container ready and open browser
        Thread browserOpenerThread = new Thread(() -> {
            try {
                // Wait for container to pull image and start web service
                // Poll for the web service to become available
                out.println("Waiting for web interface to become available...");
                long deadline = System.currentTimeMillis() + 120000; // 2 minute timeout
                boolean ready = false;

                while (System.currentTimeMillis() < deadline && dockerProcess.isAlive()) {
                    try {
                        java.net.Socket socket = new java.net.Socket();
                        socket.connect(new java.net.InetSocketAddress("localhost", config.getNoVncPort()), 1000);
                        socket.close();
                        ready = true;
                        break;
                    } catch (Exception e) {
                        // Not ready yet, wait and retry
                        Thread.sleep(2000);
                    }
                }

                if (ready && dockerProcess.isAlive()) {
                    out.println("Web interface ready! Opening browser...");
                    openBrowser("http://localhost:" + config.getNoVncPort(), out);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        browserOpenerThread.setDaemon(true);
        browserOpenerThread.start();

        // Set up shutdown hook to handle Ctrl+C gracefully
        Thread shutdownHook = new Thread(() -> {
            out.println("\nStopping Linux container...");
            try {
                Process stopProcess = new ProcessBuilder("docker", "stop", CONTAINER_NAME)
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

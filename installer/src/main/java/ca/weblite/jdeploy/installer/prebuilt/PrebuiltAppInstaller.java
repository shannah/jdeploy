package ca.weblite.jdeploy.installer.prebuilt;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Orchestrates the installation of prebuilt apps.
 *
 * This class manages the complete workflow of:
 * 1. Detecting if prebuilt apps are available for the current platform
 * 2. Attempting download from the "jdeploy" tag (latest) first
 * 3. Falling back to versioned tag if jdeploy tag download fails
 * 4. Extracting the downloaded tarball
 * 5. Returning the app bundle directory
 *
 * If all attempts fail, the installer should fall back to the standard
 * JRE + JAR installation method.
 */
public class PrebuiltAppInstaller {

    private final PrebuiltAppDetector detector;
    private final PrebuiltAppDownloader downloader;
    private final PrintStream out;
    private final PrintStream err;

    /**
     * Result of a prebuilt app installation attempt.
     */
    public static class InstallResult {
        private final boolean success;
        private final File appBundleDir;
        private final String errorMessage;
        private final InstallSource source;

        private InstallResult(boolean success, File appBundleDir, String errorMessage, InstallSource source) {
            this.success = success;
            this.appBundleDir = appBundleDir;
            this.errorMessage = errorMessage;
            this.source = source;
        }

        public static InstallResult success(File appBundleDir, InstallSource source) {
            return new InstallResult(true, appBundleDir, null, source);
        }

        public static InstallResult failure(String errorMessage) {
            return new InstallResult(false, null, errorMessage, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public File getAppBundleDir() {
            return appBundleDir;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public InstallSource getSource() {
            return source;
        }
    }

    /**
     * Source of the installed prebuilt app.
     */
    public enum InstallSource {
        /** Downloaded from the "jdeploy" rolling release tag */
        JDEPLOY_TAG,
        /** Downloaded from a versioned release tag (v{version}) */
        VERSIONED_TAG
    }

    public PrebuiltAppInstaller() {
        this(new PrebuiltAppDetector(), new PrebuiltAppDownloader(), System.out, System.err);
    }

    public PrebuiltAppInstaller(PrintStream out, PrintStream err) {
        this(new PrebuiltAppDetector(), new PrebuiltAppDownloader(), out, err);
    }

    public PrebuiltAppInstaller(
            PrebuiltAppDetector detector,
            PrebuiltAppDownloader downloader,
            PrintStream out,
            PrintStream err) {
        this.detector = detector;
        this.downloader = downloader;
        this.out = out;
        this.err = err;
    }

    /**
     * Sets a progress listener for download progress updates.
     *
     * @param listener the progress listener
     */
    public void setProgressListener(PrebuiltAppDownloader.ProgressListener listener) {
        downloader.setProgressListener(listener);
    }

    /**
     * Checks if a prebuilt app is available for the current platform.
     *
     * @param packageJson the package.json object
     * @return true if a prebuilt app is available
     */
    public boolean isPrebuiltAppAvailable(JSONObject packageJson) {
        if (!detector.hasGitHubSource(packageJson)) {
            return false;
        }

        String platform = detector.detectCurrentPlatform();
        return detector.hasPrebuiltApp(packageJson, platform);
    }

    /**
     * Attempts to install a prebuilt app for the current platform.
     *
     * The installation process:
     * 1. Checks if prebuilt app is available for current platform
     * 2. Attempts download from "jdeploy" tag first
     * 3. Falls back to versioned tag (v{version}) if jdeploy tag fails
     * 4. Extracts the tarball to the destination directory
     *
     * @param packageJson the package.json object
     * @param destinationDir the directory to install the app to
     * @return the installation result
     */
    public InstallResult install(JSONObject packageJson, File destinationDir) {
        // Check prerequisites
        if (!detector.hasGitHubSource(packageJson)) {
            return InstallResult.failure("No GitHub source configured");
        }

        String platform = detector.detectCurrentPlatform();
        if (!detector.hasPrebuiltApp(packageJson, platform)) {
            return InstallResult.failure("No prebuilt app available for platform: " + platform);
        }

        // Get app info
        String appName = packageJson.optString("name", "app");
        String version = packageJson.optString("version", "1.0.0");
        String repositoryUrl = detector.getGitHubRepositoryUrl(packageJson);

        if (repositoryUrl == null) {
            return InstallResult.failure("Could not extract GitHub repository URL");
        }

        // Create temp directory for downloads
        File downloadDir;
        try {
            downloadDir = createTempDownloadDir();
        } catch (IOException e) {
            return InstallResult.failure("Failed to create temp directory: " + e.getMessage());
        }

        try {
            // Attempt 1: Try jdeploy tag (rolling release)
            String jdeployTagUrl = detector.getGitHubJdeployTagUrl(repositoryUrl, appName, version, platform);
            log("Attempting download from jdeploy tag: " + jdeployTagUrl);

            if (downloader.isUrlAccessible(jdeployTagUrl)) {
                try {
                    downloader.downloadAndExtract(jdeployTagUrl, downloadDir, destinationDir);
                    log("Successfully installed prebuilt app from jdeploy tag");
                    return InstallResult.success(findAppBundleDir(destinationDir, platform), InstallSource.JDEPLOY_TAG);
                } catch (Exception e) {
                    logError("Download from jdeploy tag failed: " + e.getMessage());
                }
            } else {
                log("jdeploy tag URL not accessible, trying versioned tag...");
            }

            // Attempt 2: Try versioned tag
            String versionedUrl = detector.getGitHubDownloadUrl(repositoryUrl, appName, version, platform);
            log("Attempting download from versioned tag: " + versionedUrl);

            if (downloader.isUrlAccessible(versionedUrl)) {
                try {
                    downloader.downloadAndExtract(versionedUrl, downloadDir, destinationDir);
                    log("Successfully installed prebuilt app from versioned tag");
                    return InstallResult.success(findAppBundleDir(destinationDir, platform), InstallSource.VERSIONED_TAG);
                } catch (Exception e) {
                    return InstallResult.failure("Download from versioned tag failed: " + e.getMessage());
                }
            } else {
                return InstallResult.failure("Neither jdeploy tag nor versioned tag URL is accessible");
            }

        } finally {
            // Clean up temp download directory
            deleteDirectory(downloadDir);
        }
    }

    /**
     * Attempts to install a prebuilt app, returning null if unavailable.
     * This is a convenience method for callers who want to handle fallback themselves.
     *
     * @param packageJson the package.json object
     * @param destinationDir the directory to install the app to
     * @return the app bundle directory, or null if installation failed
     */
    public File installOrNull(JSONObject packageJson, File destinationDir) {
        InstallResult result = install(packageJson, destinationDir);
        if (result.isSuccess()) {
            return result.getAppBundleDir();
        }
        return null;
    }

    // ==================== Private Helper Methods ====================

    private File createTempDownloadDir() throws IOException {
        File tempDir = File.createTempFile("jdeploy-prebuilt-", ".tmp");
        tempDir.delete();
        tempDir.mkdirs();
        return tempDir;
    }

    private void deleteDirectory(File dir) {
        if (dir != null && dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            dir.delete();
        }
    }

    /**
     * Finds the app bundle directory after extraction.
     * The tarball typically extracts to a single directory containing the app bundle.
     */
    private File findAppBundleDir(File extractDir, String platform) {
        File[] files = extractDir.listFiles();
        if (files == null || files.length == 0) {
            return extractDir;
        }

        // If there's a single directory, it's likely the app bundle
        if (files.length == 1 && files[0].isDirectory()) {
            File candidate = files[0];

            // Check for platform-specific app bundle indicators
            if (platform.startsWith("mac")) {
                // Look for .app bundle
                if (candidate.getName().endsWith(".app")) {
                    return candidate;
                }
                // Check inside for .app
                File[] innerFiles = candidate.listFiles();
                if (innerFiles != null) {
                    for (File inner : innerFiles) {
                        if (inner.getName().endsWith(".app") && inner.isDirectory()) {
                            return inner;
                        }
                    }
                }
            } else if (platform.startsWith("win")) {
                // Look for .exe
                File[] exeFiles = candidate.listFiles((dir, name) -> name.endsWith(".exe"));
                if (exeFiles != null && exeFiles.length > 0) {
                    return candidate;
                }
            }

            return candidate;
        }

        // Multiple files/dirs at root - return the extract dir itself
        return extractDir;
    }

    private void log(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    private void logError(String message) {
        if (err != null) {
            err.println(message);
        }
    }
}

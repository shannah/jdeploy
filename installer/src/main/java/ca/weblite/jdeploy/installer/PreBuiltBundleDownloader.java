package ca.weblite.jdeploy.installer;

import ca.weblite.jdeploy.installer.npm.NPMPackageVersion;
import ca.weblite.jdeploy.installer.util.ArchitectureUtil;
import ca.weblite.tools.platform.Platform;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * Downloads pre-built native bundles from jdeploy.artifacts in package.json.
 * When artifacts are available for the current platform, this avoids running
 * Bundler.runit() and instead extracts the pre-built bundle directly.
 */
public class PreBuiltBundleDownloader {

    /**
     * Result of a pre-built bundle download and extraction.
     */
    public static class Result {
        private final boolean success;
        private final File bundleOutputDir;
        private final String errorMessage;

        private Result(boolean success, File bundleOutputDir, String errorMessage) {
            this.success = success;
            this.bundleOutputDir = bundleOutputDir;
            this.errorMessage = errorMessage;
        }

        public static Result success(File bundleOutputDir) {
            return new Result(true, bundleOutputDir, null);
        }

        public static Result failure(String errorMessage) {
            return new Result(false, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public File getBundleOutputDir() {
            return bundleOutputDir;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Gets the platform key for the current system (e.g., "mac-arm64", "win-x64").
     */
    public static String getCurrentPlatformKey() {
        String platform;
        if (Platform.getSystemPlatform().isMac()) {
            platform = "mac";
        } else if (Platform.getSystemPlatform().isWindows()) {
            platform = "win";
        } else if (Platform.getSystemPlatform().isLinux()) {
            platform = "linux";
        } else {
            return null;
        }
        return platform + "-" + ArchitectureUtil.getArchitecture();
    }

    /**
     * Checks whether pre-built bundle artifacts are available for the current platform.
     */
    public static boolean isAvailable(NPMPackageVersion packageVersion) {
        if (packageVersion == null) {
            return false;
        }
        String platformKey = getCurrentPlatformKey();
        if (platformKey == null) {
            return false;
        }
        JSONObject artifact = packageVersion.getArtifact(platformKey);
        return artifact != null && artifact.has("url") && artifact.has("sha256");
    }

    /**
     * Downloads and extracts the pre-built bundle for the current platform.
     *
     * @param packageVersion the NPM package version with artifacts metadata
     * @param outputDir      the directory to extract the bundle into (e.g., tmpBundles/{target})
     * @return Result indicating success or failure
     */
    public static Result download(NPMPackageVersion packageVersion, File outputDir) {
        String platformKey = getCurrentPlatformKey();
        if (platformKey == null) {
            return Result.failure("Unsupported platform");
        }

        JSONObject artifact = packageVersion.getArtifact(platformKey);
        if (artifact == null) {
            return Result.failure("No artifact found for platform: " + platformKey);
        }

        String url = artifact.optString("url", null);
        String expectedSha256 = artifact.optString("sha256", null);
        if (url == null || expectedSha256 == null) {
            return Result.failure("Artifact missing url or sha256 for platform: " + platformKey);
        }

        try {
            return downloadAndExtract(url, expectedSha256, outputDir);
        } catch (Exception e) {
            return Result.failure("Failed to download bundle: " + e.getMessage());
        }
    }

    /**
     * Downloads and extracts the CLI bundle for Windows.
     * Only relevant on Windows where the CLI binary is a separate artifact.
     *
     * @param packageVersion the NPM package version with artifacts metadata
     * @param outputDir      the directory to extract the CLI bundle into
     * @return Result indicating success or failure, or null if no CLI artifact
     */
    public static Result downloadCliBundleIfAvailable(NPMPackageVersion packageVersion, File outputDir) {
        if (!Platform.getSystemPlatform().isWindows()) {
            return null;
        }

        String platformKey = getCurrentPlatformKey();
        if (platformKey == null) {
            return null;
        }

        JSONObject artifact = packageVersion.getArtifact(platformKey);
        if (artifact == null || !artifact.has("cli")) {
            return null;
        }

        JSONObject cliArtifact = artifact.getJSONObject("cli");
        String url = cliArtifact.optString("url", null);
        String expectedSha256 = cliArtifact.optString("sha256", null);
        if (url == null || expectedSha256 == null) {
            return null;
        }

        try {
            return downloadAndExtract(url, expectedSha256, outputDir);
        } catch (Exception e) {
            return Result.failure("Failed to download CLI bundle: " + e.getMessage());
        }
    }

    private static Result downloadAndExtract(String url, String expectedSha256, File outputDir) throws IOException {
        // Download to temp file
        File tempJar = File.createTempFile("jdeploy-bundle-", ".jar");
        try {
            downloadFile(url, tempJar);

            // Verify SHA-256
            String actualSha256 = computeSha256(tempJar);
            if (!expectedSha256.equalsIgnoreCase(actualSha256)) {
                return Result.failure(
                        "SHA-256 mismatch for " + url + ": expected " + expectedSha256 + ", got " + actualSha256
                );
            }

            // Extract JAR contents to output directory
            outputDir.mkdirs();
            extractJar(tempJar, outputDir);

            return Result.success(outputDir);
        } finally {
            tempJar.delete();
        }
    }

    private static void downloadFile(String url, File dest) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP " + responseCode + " downloading " + url);
        }

        try (InputStream in = connection.getInputStream();
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void extractJar(File jarFile, File outputDir) throws IOException {
        try (JarInputStream jis = new JarInputStream(new FileInputStream(jarFile))) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                if (entry.isDirectory()) {
                    new File(outputDir, entry.getName()).mkdirs();
                    continue;
                }

                File outFile = new File(outputDir, entry.getName());
                // Prevent zip slip
                if (!outFile.getCanonicalPath().startsWith(outputDir.getCanonicalPath() + File.separator)) {
                    throw new IOException("Zip slip detected: " + entry.getName());
                }

                outFile.getParentFile().mkdirs();
                try (OutputStream out = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = jis.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }

                // Preserve executable permission for binaries
                if (entry.getName().endsWith(".sh") ||
                        !entry.getName().contains(".") ||
                        entry.getName().endsWith("Launcher") ||
                        entry.getName().endsWith("Launcher-cli")) {
                    outFile.setExecutable(true, false);
                }
            }
        }
    }

    private static String computeSha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (InputStream is = new FileInputStream(file)) {
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }
    }
}

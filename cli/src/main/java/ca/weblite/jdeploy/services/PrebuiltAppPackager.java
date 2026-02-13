package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.Platform;
import ca.weblite.jdeploy.npm.NPM;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Service for packaging native app bundles into tarballs for prebuilt app distribution.
 *
 * This service handles the creation of platform-specific prebuilt app tarballs that can be
 * uploaded to GitHub releases. The tarballs follow the naming convention:
 * {appname}-{version}-{platform}-bin.tgz
 *
 * The "-bin" suffix distinguishes prebuilt app tarballs from regular platform bundle tarballs.
 */
@Singleton
public class PrebuiltAppPackager {

    private static final String PREBUILT_SUFFIX = "-bin";
    private static final String TARBALL_EXTENSION = ".tgz";

    @Inject
    public PrebuiltAppPackager() {
    }

    /**
     * Packages a native app bundle directory into a tarball.
     *
     * @param nativeBundleDir the directory containing the native app bundle
     * @param appName the application name (from package.json)
     * @param version the application version (from package.json)
     * @param platform the target platform
     * @param outputDir the directory where the tarball will be created
     * @param npm the NPM instance for packing
     * @return the created tarball file
     * @throws IOException if packaging fails
     */
    public File packageNativeBundle(
            File nativeBundleDir,
            String appName,
            String version,
            Platform platform,
            File outputDir,
            NPM npm) throws IOException {

        if (!nativeBundleDir.exists() || !nativeBundleDir.isDirectory()) {
            throw new IllegalArgumentException("Native bundle directory must exist: " + nativeBundleDir);
        }

        // Ensure output directory exists
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // Prepare bundle for npm pack by creating/updating package.json
        File preparedDir = prepareBundleForPacking(nativeBundleDir, appName, version, platform);

        try {
            // Use npm pack to create tarball
            npm.pack(preparedDir, outputDir, true);

            // Find and rename the tarball to our naming convention
            return findAndRenameTarball(preparedDir, appName, version, platform, outputDir);

        } finally {
            // Clean up prepared directory if it's different from input
            if (!preparedDir.equals(nativeBundleDir) && preparedDir.exists()) {
                FileUtils.deleteDirectory(preparedDir);
            }
        }
    }

    /**
     * Packages a native app bundle directory into a tarball without using npm.
     * This is an alternative approach that doesn't require npm.
     *
     * @param nativeBundleDir the directory containing the native app bundle
     * @param appName the application name
     * @param version the application version
     * @param platform the target platform
     * @param outputDir the directory where the tarball will be created
     * @return the created tarball file
     * @throws IOException if packaging fails
     */
    public File packageNativeBundleWithoutNpm(
            File nativeBundleDir,
            String appName,
            String version,
            Platform platform,
            File outputDir) throws IOException {

        if (!nativeBundleDir.exists() || !nativeBundleDir.isDirectory()) {
            throw new IllegalArgumentException("Native bundle directory must exist: " + nativeBundleDir);
        }

        // Ensure output directory exists
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String tarballName = getTarballName(appName, version, platform);
        File tarballFile = new File(outputDir, tarballName);

        // Use ProcessBuilder to create tarball (cross-platform with tar command)
        createTarballUsingTar(nativeBundleDir, tarballFile);

        return tarballFile;
    }

    /**
     * Gets the tarball filename for a prebuilt app.
     * Format: {appname}-{version}-{platform}-bin.tgz
     *
     * @param appName the application name
     * @param version the application version
     * @param platform the target platform
     * @return the tarball filename
     */
    public String getTarballName(String appName, String version, Platform platform) {
        return appName + "-" + version + "-" + platform.getIdentifier() + PREBUILT_SUFFIX + TARBALL_EXTENSION;
    }

    /**
     * Generates a SHA-256 checksum for a file.
     * The checksum is returned as a Base64-encoded string.
     *
     * @param file the file to checksum
     * @return the Base64-encoded SHA-256 checksum
     * @throws IOException if reading the file fails
     */
    public String generateChecksum(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = calculateFileHash(file, digest);
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Generates a SHA-256 checksum for a file as a hex string.
     *
     * @param file the file to checksum
     * @return the hex-encoded SHA-256 checksum
     * @throws IOException if reading the file fails
     */
    public String generateChecksumHex(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = calculateFileHash(file, digest);
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Verifies that a file matches an expected checksum.
     *
     * @param file the file to verify
     * @param expectedChecksum the expected Base64-encoded checksum
     * @return true if the checksum matches
     * @throws IOException if reading the file fails
     */
    public boolean verifyChecksum(File file, String expectedChecksum) throws IOException {
        String actualChecksum = generateChecksum(file);
        return expectedChecksum.equals(actualChecksum);
    }

    // ==================== Private Helper Methods ====================

    /**
     * Prepares a bundle directory for npm pack by ensuring it has a valid package.json.
     */
    private File prepareBundleForPacking(
            File nativeBundleDir,
            String appName,
            String version,
            Platform platform) throws IOException {

        File packageJsonFile = new File(nativeBundleDir, "package.json");

        // If package.json doesn't exist, create a minimal one
        if (!packageJsonFile.exists()) {
            JSONObject packageJson = new JSONObject();
            // Use a temporary name for npm pack (will be renamed after)
            String tempName = appName + "-prebuilt-" + platform.getIdentifier();
            packageJson.put("name", tempName);
            packageJson.put("version", version);
            packageJson.put("description", "Prebuilt app bundle for " + platform.getIdentifier());

            FileUtils.writeStringToFile(packageJsonFile, packageJson.toString(2), StandardCharsets.UTF_8);
        } else {
            // Update existing package.json with temp name to ensure unique tarball name
            String content = FileUtils.readFileToString(packageJsonFile, StandardCharsets.UTF_8);
            JSONObject packageJson = new JSONObject(content);

            String tempName = appName + "-prebuilt-" + platform.getIdentifier();
            packageJson.put("name", tempName);
            packageJson.put("version", version);

            FileUtils.writeStringToFile(packageJsonFile, packageJson.toString(2), StandardCharsets.UTF_8);
        }

        return nativeBundleDir;
    }

    /**
     * Finds the tarball generated by npm pack and renames it to our naming convention.
     */
    private File findAndRenameTarball(
            File bundleDir,
            String appName,
            String version,
            Platform platform,
            File outputDir) throws IOException {

        // Read the package.json to get the name used by npm pack
        File packageJsonFile = new File(bundleDir, "package.json");
        String content = FileUtils.readFileToString(packageJsonFile, StandardCharsets.UTF_8);
        JSONObject packageJson = new JSONObject(content);
        String packageName = packageJson.optString("name", "app");

        // npm pack generates: {packageName}-{version}.tgz
        String npmPackFilename = packageName + "-" + version + ".tgz";
        File npmPackFile = new File(outputDir, npmPackFilename);

        if (!npmPackFile.exists()) {
            throw new IOException("Expected tarball not found: " + npmPackFile.getAbsolutePath());
        }

        // Our RFC-compliant filename with -bin suffix
        String targetFilename = getTarballName(appName, version, platform);
        File targetFile = new File(outputDir, targetFilename);

        // If they're the same, no need to rename
        if (npmPackFilename.equals(targetFilename)) {
            return npmPackFile;
        }

        // Rename to target name
        if (targetFile.exists()) {
            targetFile.delete();
        }

        if (!npmPackFile.renameTo(targetFile)) {
            throw new IOException("Failed to rename tarball from " + npmPackFilename + " to " + targetFilename);
        }

        return targetFile;
    }

    /**
     * Creates a tarball using the tar command.
     */
    private void createTarballUsingTar(File sourceDir, File tarballFile) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "tar",
                "-czf",
                tarballFile.getAbsolutePath(),
                "-C",
                sourceDir.getParentFile().getAbsolutePath(),
                sourceDir.getName()
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("tar command failed with exit code: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("tar command was interrupted", e);
        }
    }

    /**
     * Calculates the hash of a file using the given digest.
     */
    private byte[] calculateFileHash(File file, MessageDigest digest) throws IOException {
        try (InputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int numRead;
            while ((numRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, numRead);
            }
        }
        return digest.digest();
    }

    /**
     * Converts a byte array to a hex string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}

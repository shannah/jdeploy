package ca.weblite.jdeploy.installer.prebuilt;

import ca.weblite.jdeploy.installer.npm.NPMPackageVersion;
import ca.weblite.jdeploy.installer.views.InstallationForm;
import ca.weblite.tools.platform.Platform;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class PrebuiltBundleDownloader {

    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 120_000;
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_REDIRECTS = 5;

    /**
     * Downloads and replaces the installed app with a pre-built bundle from artifacts.
     * Returns true if the pre-built bundle was successfully downloaded and installed.
     * On any failure, the existing installed bundle is left untouched and false is returned.
     */
    public boolean downloadAndReplace(
            NPMPackageVersion npmPackageVersion,
            String platformKey,
            File installedApp,
            InstallationForm form
    ) {
        if (npmPackageVersion == null || installedApp == null) {
            return false;
        }

        PrebuiltArtifactInfo artifact = npmPackageVersion.getPrebuiltArtifact(platformKey);
        if (artifact == null) {
            return false;
        }

        System.out.println("Pre-built bundle available for " + platformKey + ", downloading...");

        try {
            boolean mainReplaced = downloadVerifyAndReplace(
                    artifact.getUrl(),
                    artifact.getSha256(),
                    installedApp,
                    form,
                    "Downloading pre-built bundle..."
            );

            if (!mainReplaced) {
                return false;
            }

            // On Windows, also handle the CLI artifact if present
            if (Platform.getSystemPlatform().isWindows() && artifact.hasCli()) {
                File cliExe = resolveWindowsCliExe(installedApp);
                if (cliExe != null) {
                    boolean cliReplaced = downloadVerifyAndReplace(
                            artifact.getCliUrl(),
                            artifact.getCliSha256(),
                            cliExe,
                            form,
                            "Downloading pre-built CLI bundle..."
                    );
                    if (!cliReplaced) {
                        System.err.println("Warning: Failed to download pre-built CLI bundle, using generated version");
                    }
                }
            }

            return true;

        } catch (Exception e) {
            System.err.println("Warning: Failed to install pre-built bundle: " + e.getMessage());
            e.printStackTrace(System.err);
            return false;
        }
    }

    private boolean downloadVerifyAndReplace(
            String url,
            String expectedSha256,
            File installedApp,
            InstallationForm form,
            String progressMessage
    ) {
        File tempJar = null;
        File tempExtractDir = null;
        File backup = null;

        try {
            updateProgress(form, progressMessage);

            // Download to temp file
            tempJar = File.createTempFile("jdeploy-prebuilt-", ".jar");
            tempJar.deleteOnExit();
            downloadFile(url, tempJar, form);

            // Verify SHA-256
            updateProgress(form, "Verifying bundle integrity...");
            if (!verifySha256(tempJar, expectedSha256)) {
                System.err.println("SHA-256 verification failed for pre-built bundle from: " + url);
                System.err.println("Expected: " + expectedSha256);
                System.err.println("This could indicate a corrupted download or tampered artifact.");
                return false;
            }

            // Extract JAR contents
            updateProgress(form, "Extracting pre-built bundle...");
            tempExtractDir = Files.createTempDirectory("jdeploy-prebuilt-extract-").toFile();
            tempExtractDir.deleteOnExit();
            extractBundleJar(tempJar, tempExtractDir);

            // Find the extracted bundle entry
            File extractedBundle = findExtractedBundle(tempExtractDir);
            if (extractedBundle == null) {
                System.err.println("Warning: No bundle found in pre-built JAR from: " + url);
                return false;
            }

            // Backup existing app before replacement
            backup = createBackup(installedApp);

            // Replace
            updateProgress(form, "Installing pre-built bundle...");
            replaceInstalledApp(installedApp, extractedBundle);

            // Success - remove backup
            if (backup != null) {
                deleteRecursive(backup);
            }

            return true;

        } catch (Exception e) {
            System.err.println("Warning: Pre-built bundle replacement failed: " + e.getMessage());
            // Restore backup if we have one and the installed app was removed
            if (backup != null && !installedApp.exists()) {
                try {
                    restoreBackup(backup, installedApp);
                    System.out.println("Restored original bundle from backup");
                } catch (IOException restoreEx) {
                    System.err.println("CRITICAL: Failed to restore backup: " + restoreEx.getMessage());
                }
            }
            return false;
        } finally {
            // Clean up temp files
            if (tempJar != null && tempJar.exists()) {
                tempJar.delete();
            }
            if (tempExtractDir != null && tempExtractDir.exists()) {
                deleteRecursive(tempExtractDir);
            }
        }
    }

    void downloadFile(String urlStr, File dest, InstallationForm form) throws IOException {
        downloadFileWithRedirects(urlStr, dest, form, 0);
    }

    private void downloadFileWithRedirects(String urlStr, File dest, InstallationForm form, int redirectCount) throws IOException {
        if (redirectCount > MAX_REDIRECTS) {
            throw new IOException("Too many redirects (>" + MAX_REDIRECTS + ") for URL: " + urlStr);
        }

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "jDeploy-Installer");

            int responseCode = conn.getResponseCode();

            // Handle redirects manually to support cross-protocol redirects (GitHub uses these)
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                    || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                    || responseCode == 307 || responseCode == 308) {
                String location = conn.getHeaderField("Location");
                if (location == null) {
                    throw new IOException("Redirect response without Location header");
                }
                conn.disconnect();
                downloadFileWithRedirects(location, dest, form, redirectCount + 1);
                return;
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + responseCode + " downloading " + urlStr);
            }

            long contentLength = conn.getContentLengthLong();

            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long totalRead = 0;
                int bytesRead;
                int lastPercentReported = -1;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    if (contentLength > 0) {
                        int percent = (int) ((totalRead * 100) / contentLength);
                        if (percent != lastPercentReported && percent % 5 == 0) {
                            lastPercentReported = percent;
                            updateProgress(form, "Downloading pre-built bundle... " + percent + "%");
                        }
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    boolean verifySha256(File file, String expectedHash) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            String actualHash = bytesToHex(digest.digest());
            return actualHash.equalsIgnoreCase(expectedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }
    }

    void extractBundleJar(File jarFile, File destDir) throws IOException {
        try (JarInputStream jis = new JarInputStream(new BufferedInputStream(new FileInputStream(jarFile)))) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                if (entry.getName().startsWith("META-INF/")) {
                    continue;
                }

                File outFile = new File(destDir, entry.getName());

                // Guard against zip slip
                String canonicalDest = destDir.getCanonicalPath();
                String canonicalOut = outFile.getCanonicalPath();
                if (!canonicalOut.startsWith(canonicalDest + File.separator) && !canonicalOut.equals(canonicalDest)) {
                    throw new IOException("Zip slip detected: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    outFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        while ((bytesRead = jis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                }
                jis.closeEntry();
            }
        }
    }

    private File findExtractedBundle(File extractDir) {
        File[] files = extractDir.listFiles();
        if (files == null || files.length == 0) {
            return null;
        }
        // The JAR should contain a single top-level entry: .app dir, .exe, or binary
        for (File f : files) {
            if (f.getName().endsWith(".app") || f.getName().endsWith(".exe") || f.isFile() || f.isDirectory()) {
                return f;
            }
        }
        return files[0];
    }

    private File createBackup(File installedApp) throws IOException {
        if (!installedApp.exists()) {
            return null;
        }
        File backup;
        if (installedApp.isDirectory()) {
            backup = new File(installedApp.getParentFile(), installedApp.getName() + ".bak");
            if (backup.exists()) {
                FileUtils.deleteDirectory(backup);
            }
            // Rename for fast backup
            if (!installedApp.renameTo(backup)) {
                // Fall back to copy
                FileUtils.copyDirectory(installedApp, backup);
            }
        } else {
            backup = new File(installedApp.getParentFile(), installedApp.getName() + ".bak");
            if (backup.exists()) {
                backup.delete();
            }
            if (!installedApp.renameTo(backup)) {
                FileUtils.copyFile(installedApp, backup);
            }
        }
        return backup;
    }

    private void restoreBackup(File backup, File target) throws IOException {
        if (backup.isDirectory()) {
            if (target.exists()) {
                FileUtils.deleteDirectory(target);
            }
            if (!backup.renameTo(target)) {
                FileUtils.copyDirectory(backup, target);
                FileUtils.deleteDirectory(backup);
            }
        } else {
            if (target.exists()) {
                target.delete();
            }
            if (!backup.renameTo(target)) {
                FileUtils.copyFile(backup, target);
                backup.delete();
            }
        }
    }

    private void replaceInstalledApp(File installedApp, File extractedBundle) throws IOException {
        if (extractedBundle.isDirectory()) {
            // macOS .app bundle or similar directory structure
            if (installedApp.exists() && installedApp.isDirectory()) {
                FileUtils.deleteDirectory(installedApp);
            } else if (installedApp.exists()) {
                installedApp.delete();
            }
            FileUtils.moveDirectory(extractedBundle, installedApp);
        } else {
            // Windows .exe or Linux binary
            if (installedApp.exists()) {
                installedApp.delete();
            }
            FileUtils.moveFile(extractedBundle, installedApp);
            if (!Platform.getSystemPlatform().isWindows()) {
                installedApp.setExecutable(true, false);
            }
        }
    }

    private File resolveWindowsCliExe(File mainExe) {
        if (mainExe == null || !mainExe.getName().endsWith(".exe")) {
            return null;
        }
        String cliExeName = mainExe.getName().replace(".exe", "-cli.exe");
        File cliExe = new File(mainExe.getParentFile(), cliExeName);
        return cliExe.exists() ? cliExe : null;
    }

    private void updateProgress(InstallationForm form, String message) {
        if (form != null) {
            form.setInProgress(true, message);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}

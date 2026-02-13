package ca.weblite.jdeploy.installer.prebuilt;

import ca.weblite.tools.io.ArchiveUtil;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Downloads and extracts prebuilt app tarballs from GitHub releases.
 * Provides checksum verification to ensure download integrity.
 */
public class PrebuiltAppDownloader {

    private static final int CONNECT_TIMEOUT_MS = 30000; // 30 seconds
    private static final int READ_TIMEOUT_MS = 300000;   // 5 minutes for large downloads
    private static final int BUFFER_SIZE = 8192;
    private static final String USER_AGENT = "jDeploy-Installer/1.0";

    /**
     * Progress listener interface for tracking download progress.
     */
    public interface ProgressListener {
        /**
         * Called during download to report progress.
         *
         * @param bytesRead total bytes downloaded so far
         * @param totalBytes total bytes expected, or -1 if unknown
         */
        void onProgress(long bytesRead, long totalBytes);
    }

    private ProgressListener progressListener;

    public PrebuiltAppDownloader() {
    }

    /**
     * Sets the progress listener for download progress notifications.
     *
     * @param listener the progress listener
     */
    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }

    /**
     * Downloads a file from the specified URL to the destination directory.
     *
     * @param url the URL to download from
     * @param destinationDir the directory to save the file
     * @return the downloaded file
     * @throws IOException if the download fails
     */
    public File download(String url, File destinationDir) throws IOException {
        return download(url, destinationDir, null);
    }

    /**
     * Downloads a file from the specified URL to the destination directory with a specific filename.
     *
     * @param url the URL to download from
     * @param destinationDir the directory to save the file
     * @param filename the filename to use, or null to extract from URL
     * @return the downloaded file
     * @throws IOException if the download fails
     */
    public File download(String url, File destinationDir, String filename) throws IOException {
        if (!destinationDir.exists()) {
            destinationDir.mkdirs();
        }

        URL downloadUrl = new URL(url);

        // Extract filename from URL if not provided
        if (filename == null || filename.isEmpty()) {
            String path = downloadUrl.getPath();
            filename = path.substring(path.lastIndexOf('/') + 1);
        }

        File destinationFile = new File(destinationDir, filename);

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) downloadUrl.openConnection();
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP request failed with code: " + responseCode +
                        " for URL: " + url);
            }

            long totalBytes = connection.getContentLengthLong();

            try (InputStream inputStream = new BufferedInputStream(connection.getInputStream());
                 OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(destinationFile))) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long totalBytesRead = 0;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    if (progressListener != null) {
                        progressListener.onProgress(totalBytesRead, totalBytes);
                    }
                }

                outputStream.flush();
            }

            return destinationFile;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Checks if a URL is accessible (returns HTTP 200).
     *
     * @param url the URL to check
     * @return true if the URL is accessible
     */
    public boolean isUrlAccessible(String url) {
        HttpURLConnection connection = null;
        try {
            URL checkUrl = new URL(url);
            connection = (HttpURLConnection) checkUrl.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(CONNECT_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;

        } catch (IOException e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Verifies the checksum of a file against an expected value.
     * The expected checksum should be a Base64-encoded SHA-256 hash.
     *
     * @param file the file to verify
     * @param expectedChecksum the expected Base64-encoded SHA-256 checksum
     * @return true if the checksum matches
     * @throws IOException if reading the file fails
     */
    public boolean verifyChecksum(File file, String expectedChecksum) throws IOException {
        if (expectedChecksum == null || expectedChecksum.isEmpty()) {
            return true; // No checksum to verify
        }

        String actualChecksum = calculateChecksum(file);
        return expectedChecksum.equals(actualChecksum);
    }

    /**
     * Verifies the checksum using a hex-encoded SHA-256 hash.
     *
     * @param file the file to verify
     * @param expectedChecksumHex the expected hex-encoded SHA-256 checksum
     * @return true if the checksum matches
     * @throws IOException if reading the file fails
     */
    public boolean verifyChecksumHex(File file, String expectedChecksumHex) throws IOException {
        if (expectedChecksumHex == null || expectedChecksumHex.isEmpty()) {
            return true; // No checksum to verify
        }

        String actualChecksum = calculateChecksumHex(file);
        return expectedChecksumHex.equalsIgnoreCase(actualChecksum);
    }

    /**
     * Calculates the SHA-256 checksum of a file, returned as Base64.
     *
     * @param file the file to checksum
     * @return the Base64-encoded SHA-256 checksum
     * @throws IOException if reading the file fails
     */
    public String calculateChecksum(File file) throws IOException {
        byte[] hash = calculateSha256Hash(file);
        return Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Calculates the SHA-256 checksum of a file, returned as hex.
     *
     * @param file the file to checksum
     * @return the hex-encoded SHA-256 checksum
     * @throws IOException if reading the file fails
     */
    public String calculateChecksumHex(File file) throws IOException {
        byte[] hash = calculateSha256Hash(file);
        return bytesToHex(hash);
    }

    /**
     * Extracts a tarball (.tgz or .tar.gz) to a destination directory.
     *
     * @param tarball the tarball file to extract
     * @param destinationDir the directory to extract to
     * @throws IOException if extraction fails
     */
    public void extract(File tarball, File destinationDir) throws IOException {
        if (!tarball.exists()) {
            throw new FileNotFoundException("Tarball not found: " + tarball.getAbsolutePath());
        }

        if (!destinationDir.exists()) {
            destinationDir.mkdirs();
        }

        // Use ArchiveUtil for extraction - handles .tgz files
        ArchiveUtil.extract(tarball, destinationDir, null);
    }

    /**
     * Downloads a tarball, verifies its checksum, and extracts it.
     * This is a convenience method that combines download, verify, and extract.
     *
     * @param url the URL to download from
     * @param expectedChecksum the expected checksum (Base64-encoded SHA-256), or null to skip verification
     * @param downloadDir the directory to download the tarball to
     * @param extractDir the directory to extract the contents to
     * @return the extracted directory (same as extractDir)
     * @throws IOException if any operation fails
     * @throws ChecksumVerificationException if checksum verification fails
     */
    public File downloadAndExtract(String url, String expectedChecksum, File downloadDir, File extractDir)
            throws IOException, ChecksumVerificationException {

        // Download the tarball
        File tarball = download(url, downloadDir);

        try {
            // Verify checksum if provided
            if (expectedChecksum != null && !expectedChecksum.isEmpty()) {
                if (!verifyChecksum(tarball, expectedChecksum)) {
                    throw new ChecksumVerificationException(
                            "Checksum verification failed for: " + tarball.getName());
                }
            }

            // Extract the tarball
            extract(tarball, extractDir);

            return extractDir;

        } finally {
            // Clean up tarball after extraction
            if (tarball.exists()) {
                tarball.delete();
            }
        }
    }

    /**
     * Downloads a tarball and extracts it without checksum verification.
     *
     * @param url the URL to download from
     * @param downloadDir the directory to download the tarball to
     * @param extractDir the directory to extract the contents to
     * @return the extracted directory
     * @throws IOException if any operation fails
     */
    public File downloadAndExtract(String url, File downloadDir, File extractDir) throws IOException {
        try {
            return downloadAndExtract(url, null, downloadDir, extractDir);
        } catch (ChecksumVerificationException e) {
            // Should never happen when checksum is null
            throw new IOException(e);
        }
    }

    // ==================== Private Helper Methods ====================

    private byte[] calculateSha256Hash(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int numRead;
                while ((numRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, numRead);
                }
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Exception thrown when checksum verification fails.
     */
    public static class ChecksumVerificationException extends Exception {
        public ChecksumVerificationException(String message) {
            super(message);
        }

        public ChecksumVerificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

package ca.weblite.jdeploy.publishing.s3;

import ca.weblite.jdeploy.models.BundleArtifact;
import ca.weblite.jdeploy.models.BundleManifest;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;

/**
 * Uploads bundle artifact JARs to Amazon S3.
 * Uses HTTP PUT with pre-signed URLs or direct S3 REST API.
 *
 * This implementation uses the S3 REST API directly to avoid adding the
 * AWS SDK as a heavyweight dependency. It requires AWS credentials to be
 * available via environment variables.
 */
@Singleton
public class S3BundleUploader {

    private final S3Config config;

    @Inject
    public S3BundleUploader(S3Config config) {
        this.config = config;
    }

    /**
     * Uploads all artifacts in the manifest to S3 and sets their URLs.
     *
     * @param manifest the bundle manifest containing artifacts to upload
     * @param out      print stream for status messages
     * @throws IOException if upload fails
     */
    public void uploadAll(BundleManifest manifest, PrintStream out) throws IOException {
        if (!config.isConfigured()) {
            throw new IOException("S3 is not configured. Set JDEPLOY_S3_BUCKET environment variable.");
        }

        List<BundleArtifact> artifacts = manifest.getArtifacts();
        out.println("Uploading " + artifacts.size() + " bundle artifact(s) to S3...");

        for (BundleArtifact artifact : artifacts) {
            String key = config.getKey(artifact.getFilename());
            uploadFile(artifact.getFile(), key, out);
            String publicUrl = config.getPublicUrl(artifact.getFilename());
            artifact.setUrl(publicUrl);
            out.println("  Uploaded: " + artifact.getFilename() + " -> " + publicUrl);
        }

        out.println("S3 upload complete.");
    }

    private void uploadFile(File file, String key, PrintStream out) throws IOException {
        String bucket = config.getBucket();
        String region = config.getRegion();
        String accessKey = config.getAccessKeyId();
        String secretKey = config.getSecretAccessKey();

        if (accessKey == null || secretKey == null) {
            throw new IOException(
                    "AWS credentials not found. Set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY environment variables."
            );
        }

        // Use AWS S3 REST API v4 signing
        byte[] fileContent = Files.readAllBytes(file.toPath());
        String host = bucket + ".s3." + region + ".amazonaws.com";
        String urlStr = "https://" + host + "/" + key;

        S3RequestSigner signer = new S3RequestSigner(accessKey, secretKey, region);
        HttpURLConnection conn = signer.createSignedPutRequest(urlStr, host, key, fileContent, "application/java-archive");

        try {
            conn.getOutputStream().write(fileContent);
            conn.getOutputStream().flush();

            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                String errorBody = "";
                try (InputStream errorStream = conn.getErrorStream()) {
                    if (errorStream != null) {
                        errorBody = readStream(errorStream);
                    }
                }
                throw new IOException("S3 upload failed for " + key + ": HTTP " + responseCode + " " + errorBody);
            }
        } finally {
            conn.disconnect();
        }
    }

    private String readStream(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, len));
        }
        return sb.toString();
    }
}

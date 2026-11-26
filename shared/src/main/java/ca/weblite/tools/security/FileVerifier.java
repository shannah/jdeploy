package ca.weblite.tools.security;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;
import org.json.JSONObject;

public class FileVerifier {

    private static final String ALGORITHM = "SHA-256";
    private static final String SIGNING_ALGORITHM = "SHA256withRSA";
    private static final String MANIFEST_FILENAME = "jdeploy.mf";
    private static final String MANIFEST_SIGNATURE_FILENAME = "jdeploy.mf.sig";
    private static final String CERTIFICATE_FILENAME = "jdeploy.cer";
    private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    public static VerificationResult verifyDirectory(
            String version,
            String directoryPath,
            CertificateVerifier certificateVerifier
    ) throws Exception {
        return verifyDirectory(version, directoryPath, certificateVerifier, false, null, false);
    }

    /**
     * Verifies with an RFC 3161 timestamp policy. When the package carries a
     * timestamp token ({@link TimestampAuthority#TOKEN_FILENAME}), the
     * authority's time is the signing time; the token must verify and its
     * authority must chain to {@code tsaTrustAnchors} (null: the JVM's default
     * trust store). Without a token, {@code requireTimestamp} rejects the
     * package as {@link VerificationResult#UNTRUSTED_CERTIFICATE}; otherwise the
     * manifest's own timestamp is the signing time.
     */
    public static VerificationResult verifyDirectory(
            String version,
            String directoryPath,
            CertificateVerifier certificateVerifier,
            boolean requireTimestamp,
            KeyStore tsaTrustAnchors
    ) throws Exception {
        return verifyDirectory(version, directoryPath, certificateVerifier, requireTimestamp, tsaTrustAnchors, false);
    }

    private static VerificationResult verifyDirectory(
            String version,
            String directoryPath,
            CertificateVerifier certificateVerifier,
            boolean requireTimestamp,
            KeyStore tsaTrustAnchors,
            boolean verbose
    ) throws Exception {
        // Load the certificate chain from the file
        Path certificatePath = Paths.get(directoryPath, CERTIFICATE_FILENAME);
        if (!Files.exists(certificatePath)) {
            return VerificationResult.NOT_SIGNED_AT_ALL;
        }

        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        List<Certificate> certificateChain = new ArrayList<>();
        try (InputStream in = Files.newInputStream(certificatePath)) {
            while (in.available() > 0) {
                certificateChain.add(certFactory.generateCertificate(in));
            }
        }

        // The signing time: a timestamp authority's, when the package carries a
        // token (it cannot be back-dated by the signer); otherwise the manifest's
        // own timestamp, whose signature is verified below before anything is
        // accepted, so it cannot be changed without the signing key.
        Date signingTime;
        Path tokenPath = Paths.get(directoryPath, TimestampAuthority.TOKEN_FILENAME);
        if (Files.exists(tokenPath)) {
            Path signaturePath = Paths.get(directoryPath, MANIFEST_SIGNATURE_FILENAME);
            if (!Files.exists(signaturePath)) {
                return VerificationResult.NOT_SIGNED_AT_ALL;
            }
            try {
                signingTime = TimestampAuthority.verifyToken(
                        Files.readAllBytes(tokenPath), Files.readAllBytes(signaturePath), tsaTrustAnchors);
            } catch (TimestampAuthority.TimestampException ex) {
                if (verbose) {
                    System.err.println(ex.getMessage());
                }
                return ex.isUntrustedAuthority()
                        ? VerificationResult.UNTRUSTED_CERTIFICATE
                        : VerificationResult.SIGNATURE_MISMATCH;
            }
        } else if (requireTimestamp) {
            if (verbose) {
                System.err.println("The package has no timestamp, and one is required");
            }
            return VerificationResult.UNTRUSTED_CERTIFICATE;
        } else {
            signingTime = readSigningTime(directoryPath);
        }

        // Check that the chain led to a pinned certificate when the package was
        // signed, so a release signed while its signing certificate was valid
        // keeps verifying after that certificate expires.
        if (!certificateVerifier.isTrusted(certificateChain, signingTime)) {
            return VerificationResult.UNTRUSTED_CERTIFICATE;
        }

        PublicKey publicKey = certificateChain.get(0).getPublicKey();

        // Read the manifest file
        Path manifestPath = Paths.get(directoryPath, MANIFEST_FILENAME);
        if (!Files.exists(manifestPath)) {
            return VerificationResult.NOT_SIGNED_AT_ALL;
        }
        byte[] manifestContent = Files.readAllBytes(manifestPath);

        // Read the manifest signature file
        Path manifestSignaturePath = Paths.get(directoryPath, MANIFEST_SIGNATURE_FILENAME);
        if (!Files.exists(manifestSignaturePath)) {
            return VerificationResult.NOT_SIGNED_AT_ALL;
        }
        byte[] manifestSignature = Files.readAllBytes(manifestSignaturePath);

        // Verify the manifest signature
        if (!verifySignature(manifestContent, manifestSignature, publicKey, version)) {
            if (verbose) {
                System.err.println("Manifest signature mismatch");
                (new RuntimeException()).printStackTrace(System.err);
            }
            return VerificationResult.SIGNATURE_MISMATCH;
        }

        // Parse the manifest JSON
        JSONObject manifest = new JSONObject(new String(manifestContent));

        // Verify the timestamp
        Date timestamp = signingTime != null ? signingTime : parseTimestamp(manifest.getString("timestamp"));
        ((X509Certificate) certificateChain.get(0)).checkValidity(timestamp);

        // Verify each file listed in the manifest
        Path baseDir = Paths.get(directoryPath);
        for (String relativePath : manifest.keySet()) {
            if (relativePath.equals("timestamp")) continue;

            Path filePath = baseDir.resolve(relativePath);
            if (!Files.exists(filePath)) {
                if (verbose) {
                    System.err.println("File not found: " + relativePath);
                    (new RuntimeException()).printStackTrace(System.err);
                }
                return VerificationResult.SIGNATURE_MISMATCH;
            }

            JSONObject fileEntry = manifest.getJSONObject(relativePath);
            byte[] fileContent = Files.readAllBytes(filePath);
            byte[] expectedHash = decodeHex(fileEntry.getString("hash"));
            byte[] actualHash = hash(fileContent);

            // Verify the file hash
            if (!Arrays.equals(expectedHash, actualHash)) {
                System.out.println("File hash mismatch for: " + relativePath);
                System.out.println("Expected: " + encodeHex(expectedHash));
                System.out.println("Actual: " + encodeHex(actualHash));
                return VerificationResult.SIGNATURE_MISMATCH;
            }

            // Verify the file signature
            byte[] fileSignature = decodeHex(fileEntry.getString("signature"));
            if (!verifySignature(actualHash, fileSignature, publicKey)) {
                System.out.println("File signature mismatch for: " + relativePath);
                return VerificationResult.SIGNATURE_MISMATCH;
            }
        }

        return VerificationResult.SIGNED_CORRECTLY;
    }

    /**
     * The signing time recorded in the manifest, or null (meaning "now") when
     * there is no readable manifest; the checks that follow reject such a
     * package anyway.
     */
    private static Date readSigningTime(String directoryPath) {
        try {
            Path manifestPath = Paths.get(directoryPath, MANIFEST_FILENAME);
            if (!Files.exists(manifestPath)) {
                return null;
            }
            JSONObject manifest = new JSONObject(new String(Files.readAllBytes(manifestPath)));
            return parseTimestamp(manifest.getString("timestamp"));
        } catch (Exception ex) {
            return null;
        }
    }

    /** Parses the manifest timestamp as UTC, matching FileSigner. */
    static Date parseTimestamp(String timestamp) throws java.text.ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat(TIMESTAMP_FORMAT);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.parse(timestamp);
    }

    private static byte[] hash(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
        return digest.digest(data);
    }

    private static boolean verifySignature(byte[] data, byte[] signature, PublicKey publicKey) throws Exception {
        Signature sig = Signature.getInstance(SIGNING_ALGORITHM);
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);
    }

    private static boolean verifySignature(byte[] data, byte[] signature, PublicKey publicKey, String version) throws Exception {
        Signature sig = Signature.getInstance(SIGNING_ALGORITHM);
        sig.initVerify(publicKey);
        sig.update(data);
        sig.update(version.getBytes());
        return sig.verify(signature);
    }

    private static String encodeHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] decodeHex(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}

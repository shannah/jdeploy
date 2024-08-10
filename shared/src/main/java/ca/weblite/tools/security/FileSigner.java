package ca.weblite.tools.security;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.Certificate;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.json.JSONObject;

public class FileSigner {

    private static final String ALGORITHM = "SHA-256";
    private static final String SIGNING_ALGORITHM = "SHA256withRSA";
    private static final String MANIFEST_FILENAME = "jdeploy.mf";
    private static final String MANIFEST_SIGNATURE_FILENAME = "jdeploy.mf.sig";
    private static final String CERTIFICATE_FILENAME = "jdeploy.cer";
    private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    public static void signDirectory(String version, String directoryPath, KeyProvider keyProvider) throws Exception {
        PrivateKey privateKey = keyProvider.getSigningKey();
        List<Certificate> certificateChain = keyProvider.getSigningCertificateChain();
        if (certificateChain == null || certificateChain.isEmpty()) {
            throw new IllegalArgumentException("KeyProvider failed to find any certificates in the signing certificate chain");
        }

        // Generate the manifest with timestamp
        String timestamp = new SimpleDateFormat(TIMESTAMP_FORMAT).format(new Date());
        JSONObject manifest = generateManifest(directoryPath, privateKey, timestamp);

        // Save the manifest file
        Path manifestPath = Paths.get(directoryPath, MANIFEST_FILENAME);
        try (Writer writer = new FileWriter(manifestPath.toFile())) {
            writer.write(manifest.toString(4)); // Pretty print with 4-space indentation
        }

        // Sign the manifest file
        byte[] manifestContent = Files.readAllBytes(manifestPath);
        byte[] manifestSignature = signWithVersion(manifestContent, privateKey, version);

        // Save the manifest signature file
        Path manifestSignaturePath = Paths.get(directoryPath, MANIFEST_SIGNATURE_FILENAME);
        Files.write(manifestSignaturePath, manifestSignature);

        // Save the certificate chain file
        Path certificatePath = Paths.get(directoryPath, CERTIFICATE_FILENAME);
        try (FileOutputStream fos = new FileOutputStream(certificatePath.toFile())) {
            for (Certificate cert : certificateChain) {
                fos.write(cert.getEncoded());
            }
        }
    }

    private static JSONObject generateManifest(String directoryPath, PrivateKey privateKey, String timestamp) throws Exception {
        JSONObject manifest = new JSONObject();
        Path baseDir = Paths.get(directoryPath);
        manifest.put("timestamp", timestamp);

        try (Stream<Path> paths = Files.walk(baseDir)) {
            List<Path> filePaths = paths.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals(MANIFEST_FILENAME))
                    .filter(path -> !path.getFileName().toString().equals(MANIFEST_SIGNATURE_FILENAME))
                    .filter(path -> !path.getFileName().toString().equals(CERTIFICATE_FILENAME))
                    .collect(Collectors.toList());

            for (Path filePath : filePaths) {
                byte[] fileContent = Files.readAllBytes(filePath);
                byte[] hash = hash(fileContent);
                byte[] signature = sign(hash, privateKey);

                JSONObject fileEntry = new JSONObject();
                fileEntry.put("hash", encodeHex(hash));
                fileEntry.put("signature", encodeHex(signature));

                // Compute the relative path and use it as the key in the manifest
                String relativePath = baseDir.relativize(filePath).toString();
                manifest.put(relativePath, fileEntry);
            }
        }
        return manifest;
    }

    private static byte[] hash(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
        return digest.digest(data);
    }

    private static byte[] hashWithVersion(byte[] data, String version) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
        digest.update(data);
        digest.update(version.getBytes());
        return digest.digest();
    }

    private static byte[] sign(byte[] data, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance(SIGNING_ALGORITHM);
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign();
    }

    private static byte[] signWithVersion(byte[] data, PrivateKey privateKey, String version) throws Exception {
        Signature signature = Signature.getInstance(SIGNING_ALGORITHM);
        signature.initSign(privateKey);
        signature.update(data);
        signature.update(version.getBytes());
        return signature.sign();
    }

    private static String encodeHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

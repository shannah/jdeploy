package ca.weblite.tools.security;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.json.JSONObject;

public class FileSigner {

    private static final String ALGORITHM = "SHA-256";
    private static final String SIGNING_ALGORITHM = "SHA256withRSA";
    private static final String MANIFEST_FILENAME = "jdeploy.mf";
    private static final String MANIFEST_SIGNATURE_FILENAME = "jdeploy.mf.sig";

    public static void signDirectory(String version, String directoryPath, KeyProvider keyProvider) throws Exception {
        PrivateKey privateKey = keyProvider.getPrivateKey();

        // Generate the manifest
        JSONObject manifest = generateManifest(directoryPath, privateKey);

        // Save the manifest file
        Path manifestPath = Paths.get(directoryPath, MANIFEST_FILENAME);
        try (Writer writer = new FileWriter(manifestPath.toFile())) {
            writer.write(manifest.toString(4)); // Pretty print with 4-space indentation
        }

        // Sign the manifest file
        byte[] manifestContent = Files.readAllBytes(manifestPath);
        byte[] manifestHash = hashWithVersion(manifestContent, version);
        byte[] manifestSignature = sign(manifestHash, privateKey);

        // Save the manifest signature file
        Path manifestSignaturePath = Paths.get(directoryPath, MANIFEST_SIGNATURE_FILENAME);
        Files.write(manifestSignaturePath, encodeHex(manifestSignature).getBytes());
    }

    private static JSONObject generateManifest(String directoryPath, PrivateKey privateKey) throws Exception {
        JSONObject manifest = new JSONObject();
        try (Stream<Path> paths = Files.walk(Paths.get(directoryPath))) {
            List<Path> filePaths = paths.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals(MANIFEST_FILENAME))
                    .filter(path -> !path.getFileName().toString().equals(MANIFEST_SIGNATURE_FILENAME))
                    .collect(Collectors.toList());

            for (Path filePath : filePaths) {
                byte[] fileContent = Files.readAllBytes(filePath);
                byte[] hash = hash(fileContent);
                byte[] signature = sign(hash, privateKey);

                JSONObject fileEntry = new JSONObject();
                fileEntry.put("hash", encodeHex(hash));
                fileEntry.put("signature", encodeHex(signature));

                manifest.put(filePath.toString(), fileEntry);
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

    private static String encodeHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

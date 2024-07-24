package ca.weblite.tools.security;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.*;

public class FileSignerTest {

    private static final String ALGORITHM = "SHA-256";
    private static final String SIGNING_ALGORITHM = "SHA256withRSA";
    private static final String VERSION = "1.0.0";
    private static final String MANIFEST_FILENAME = "jdeploy.mf";
    private static final String MANIFEST_SIGNATURE_FILENAME = "jdeploy.mf.sig";

    private Path tempDir;
    private PrivateKey privateKey;
    private PublicKey publicKey;

    @BeforeEach
    public void setUp() throws Exception {
        // Create a temporary directory
        tempDir = Files.createTempDirectory("testDir");

        // Create some temporary files in the directory
        Files.write(tempDir.resolve("file1.txt"), "Hello World".getBytes());
        Files.write(tempDir.resolve("file2.txt"), "Another file content".getBytes());

        // Generate a key pair for testing
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        privateKey = keyPair.getPrivate();
        publicKey = keyPair.getPublic();
    }

    @AfterEach
    public void tearDown() throws IOException {
        // Delete the temporary directory and its contents
        FileUtils.deleteDirectory(tempDir.toFile());
    }

    @Test
    public void testFileSigning() throws Exception {
        // Save keys to temporary files
        Path privateKeyPath = savePrivateKey(privateKey);
        Path publicKeyPath = savePublicKey(publicKey);

        // Create a KeyProvider
        KeyProvider keyProvider = new FileKeyProvider(privateKeyPath.toString(), publicKeyPath.toString());

        // Sign the directory
        FileSigner.signDirectory(VERSION, tempDir.toString(), keyProvider);

        // Verify the manifest and files
        assertTrue(verifyManifestAndFiles(tempDir.toString(), publicKey, VERSION));
    }

    private boolean verifyManifestAndFiles(String directoryPath, PublicKey publicKey, String version) throws Exception {
        Path manifestPath = Paths.get(directoryPath, MANIFEST_FILENAME);
        Path manifestSignaturePath = Paths.get(directoryPath, MANIFEST_SIGNATURE_FILENAME);

        byte[] manifestContent = Files.readAllBytes(manifestPath);
        byte[] manifestSignature = Files.readAllBytes(manifestSignaturePath);

        boolean isValid = verifyManifestSignature(manifestContent, manifestSignature, publicKey, version);
        if (!isValid) {
            return false;
        }

        JSONObject manifest = new JSONObject(new String(manifestContent));
        return verifyManifest(directoryPath, manifest, publicKey);
    }

    private boolean verifyManifestSignature(byte[] manifestContent, byte[] manifestSignature, PublicKey publicKey, String version) throws Exception {
        byte[] manifestHash = hashWithVersion(manifestContent, version);
        byte[] signature = decodeHex(new String(manifestSignature));
        return verifySignature(manifestHash, signature, publicKey);
    }

    private boolean verifyManifest(String directoryPath, JSONObject manifest, PublicKey publicKey) throws Exception {
        try (Stream<Path> paths = Files.walk(Paths.get(directoryPath))) {
            List<Path> filePaths = paths.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals(MANIFEST_FILENAME))
                    .filter(path -> !path.getFileName().toString().equals(MANIFEST_SIGNATURE_FILENAME))
                    .collect(Collectors.toList());

            for (Path filePath : filePaths) {
                byte[] fileContent = Files.readAllBytes(filePath);
                byte[] hash = hash(fileContent);

                JSONObject fileEntry = manifest.getJSONObject(filePath.toString());
                String expectedHash = fileEntry.getString("hash");
                String signature = fileEntry.getString("signature");

                if (!expectedHash.equals(encodeHex(hash))) {
                    return false;
                }

                if (!verifySignature(hash, decodeHex(signature), publicKey)) {
                    return false;
                }
            }
        }
        return true;
    }

    private Path savePrivateKey(PrivateKey privateKey) throws IOException {
        Path keyPath = Files.createTempFile("private_key", ".der");
        Files.write(keyPath, privateKey.getEncoded());
        keyPath.toFile().deleteOnExit();
        return keyPath;
    }

    private Path savePublicKey(PublicKey publicKey) throws IOException {
        Path keyPath = Files.createTempFile("public_key", ".der");
        Files.write(keyPath, publicKey.getEncoded());
        keyPath.toFile().deleteOnExit();
        return keyPath;
    }

    private byte[] hash(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
        return digest.digest(data);
    }

    private byte[] hashWithVersion(byte[] data, String version) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
        digest.update(data);
        digest.update(version.getBytes());
        return digest.digest();
    }

    private boolean verifySignature(byte[] data, byte[] signature, PublicKey publicKey) throws Exception {
        Signature sig = Signature.getInstance(SIGNING_ALGORITHM);
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);
    }

    private String encodeHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private byte[] decodeHex(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}

package ca.weblite.tools.security;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.*;

public class FileSignerTest {

    private static final String VERSION = "1.0.0";
    private static final String MANIFEST_FILENAME = "jdeploy.mf";
    private static final String MANIFEST_SIGNATURE_FILENAME = "jdeploy.mf.sig";
    private static final String CERTIFICATE_FILENAME = "jdeploy.cert.pem";

    private Path tempDir;
    private PrivateKey privateKey;
    private X509Certificate certificate;

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

        // Generate a self-signed certificate for testing
        certificate = generateSelfSignedCertificate(keyPair);
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
        Path certificatePath = saveCertificate(certificate);

        // Create a KeyProvider
        KeyProvider keyProvider = new FileKeyProvider(privateKeyPath.toString(), certificatePath.toString());

        // Sign the directory
        FileSigner.signDirectory(VERSION, tempDir.toString(), keyProvider);

        // Create a CertificateVerifier
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("test", certificate);
        CertificateVerifier verifier = new SimpleCertificateVerifier(keyStore);

        // Verify the manifest and files using FileVerifier
        VerificationResult result = FileVerifier.verifyDirectory(VERSION, tempDir.toString(), verifier);
        assertEquals(VerificationResult.SIGNED_CORRECTLY, result, "Directory verification failed");
    }

    private Path savePrivateKey(PrivateKey privateKey) throws IOException {
        Path keyPath = Files.createTempFile("private_key", ".der");
        Files.write(keyPath, privateKey.getEncoded());
        keyPath.toFile().deleteOnExit();
        return keyPath;
    }

    private Path saveCertificate(Certificate certificate) throws IOException {
        Path certPath = Files.createTempFile("certificate", ".pem");
        try (FileWriter writer = new FileWriter(certPath.toFile())) {
            writer.write(encodeToPEM(certificate));
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
        certPath.toFile().deleteOnExit();
        return certPath;
    }

    private String encodeToPEM(Certificate certificate) throws IOException, CertificateEncodingException {
        String base64Encoded = Base64.getEncoder().encodeToString(certificate.getEncoded());
        return "-----BEGIN CERTIFICATE-----\n" + base64Encoded + "\n-----END CERTIFICATE-----";
    }

    private X509Certificate generateSelfSignedCertificate(KeyPair keyPair) throws Exception {
        long now = System.currentTimeMillis();
        Date startDate = new Date(now);

        X500Name dnName = new X500Name("CN=Test Certificate");
        BigInteger certSerialNumber = new BigInteger(Long.toString(now)); // Using current time as the certificate serial number
        Date endDate = new Date(now + 365 * 24 * 60 * 60 * 1000L); // 1 year validity

        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate());
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                dnName, certSerialNumber, startDate, endDate, dnName, keyPair.getPublic());

        JcaX509CertificateConverter certConverter = new JcaX509CertificateConverter();
        return certConverter.getCertificate(certBuilder.build(contentSigner));
    }
}

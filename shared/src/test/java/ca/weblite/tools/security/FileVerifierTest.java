package ca.weblite.tools.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Comparator;
import java.util.Date;
import java.util.stream.Stream;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.*;

public class FileVerifierTest {

    private static final String VERSION = "1.0.0";
    private static final String MANIFEST_FILENAME = "jdeploy.mf";
    private static final String MANIFEST_SIGNATURE_FILENAME = "jdeploy.mf.sig";

    private Path tempDir;
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private X509Certificate certificate;
    private CertificateVerifier certificateVerifier;

    @BeforeEach
    public void setUp() throws Exception {
        // Create a temporary directory
        tempDir = Files.createTempDirectory("testDir");

        // Create some temporary files in the directory
        Files.write(tempDir.resolve("file1.txt"), "Hello World".getBytes());
        Files.write(tempDir.resolve("file2.txt"), "Another file content".getBytes());

        // Generate a key pair and self-signed certificate for testing
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        privateKey = keyPair.getPrivate();
        publicKey = keyPair.getPublic();
        certificate = generateSelfSignedCertificate(keyPair);

        // Create a mock CertificateVerifier that trusts the generated certificate
        certificateVerifier = mock(CertificateVerifier.class);
        when(certificateVerifier.isTrusted(certificate)).thenReturn(true);
    }

    @AfterEach
    public void tearDown() throws Exception {
        // Delete the temporary directory and its contents
        try (Stream<Path> walk = Files.walk(tempDir)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    public void testVerifyDirectory() throws Exception {
        // Create a KeyProvider
        KeyProvider keyProvider = new KeyProvider() {
            @Override
            public PrivateKey getPrivateKey() {
                return privateKey;
            }

            @Override
            public Certificate getCertificate() {
                return certificate;
            }
        };

        // Sign the directory
        FileSigner.signDirectory(VERSION, tempDir.toString(), keyProvider);

        // Verify the directory
        VerificationResult result = FileVerifier.verifyDirectory(VERSION, tempDir.toString(), certificateVerifier);
        assertEquals(VerificationResult.SIGNED_CORRECTLY, result, "Directory verification failed");
    }

    @Test
    public void testVerifyDirectoryWithoutSigning() throws Exception {
        // Verify the directory without signing it
        VerificationResult result = FileVerifier.verifyDirectory(VERSION, tempDir.toString(), certificateVerifier);
        assertEquals(VerificationResult.NOT_SIGNED_AT_ALL, result, "Verification should fail for unsigned directory");
    }

    @Test
    public void testVerifyDirectoryWithModifiedFile() throws Exception {
        // Create a KeyProvider
        KeyProvider keyProvider = new KeyProvider() {
            @Override
            public PrivateKey getPrivateKey() {
                return privateKey;
            }

            @Override
            public Certificate getCertificate() {
                return certificate;
            }
        };

        // Sign the directory
        FileSigner.signDirectory(VERSION, tempDir.toString(), keyProvider);

        // Modify a file
        Files.write(tempDir.resolve("file1.txt"), "Tampered content".getBytes());

        // Verify the directory
        VerificationResult result = FileVerifier.verifyDirectory(VERSION, tempDir.toString(), certificateVerifier);
        assertEquals(VerificationResult.SIGNATURE_MISMATCH, result, "Verification should fail for directory with modified file");
    }

    @Test
    public void testVerifyDirectoryWithModifiedManifest() throws Exception {
        // Create a KeyProvider
        KeyProvider keyProvider = new KeyProvider() {
            @Override
            public PrivateKey getPrivateKey() {
                return privateKey;
            }

            @Override
            public Certificate getCertificate() {
                return certificate;
            }
        };

        // Sign the directory
        FileSigner.signDirectory(VERSION, tempDir.toString(), keyProvider);

        // Modify the manifest file
        Path manifestPath = tempDir.resolve(MANIFEST_FILENAME);
        Files.write(manifestPath, "Tampered manifest content".getBytes());

        // Verify the directory
        VerificationResult result = FileVerifier.verifyDirectory(VERSION, tempDir.toString(), certificateVerifier);
        assertEquals(VerificationResult.SIGNATURE_MISMATCH, result, "Verification should fail for directory with modified manifest");
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

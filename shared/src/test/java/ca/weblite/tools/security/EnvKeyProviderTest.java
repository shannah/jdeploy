package ca.weblite.tools.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.FileWriter;
import java.math.BigInteger;
import java.nio.file.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;

import ca.weblite.tools.env.EnvVarProvider;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;

public class EnvKeyProviderTest {

    private static final String PRIVATE_KEY_ENV = "JDEPLOY_PRIVATE_KEY";
    private static final String CERTIFICATE_ENV = "JDEPLOY_CERTIFICATE";

    private PrivateKey privateKey;
    private X509Certificate certificate;

    @BeforeEach
    public void setUp() throws Exception {
        // Generate a key pair for testing
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        // Generate a self-signed certificate for testing
        certificate = generateSelfSignedCertificate(keyPair);
    }

    @Test
    public void testGetPrivateKeyFromPEM() throws Exception {
        EnvVarProvider envVarProvider = mock(EnvVarProvider.class);
        when(envVarProvider.getEnv(PRIVATE_KEY_ENV))
                .thenReturn(encodeToPEM(privateKey, "PRIVATE KEY"));

        KeyProvider keyProvider = new EnvKeyProvider(envVarProvider);
        PrivateKey retrievedPrivateKey = keyProvider.getPrivateKey();
        assertNotNull(retrievedPrivateKey);
        assertArrayEquals(privateKey.getEncoded(), retrievedPrivateKey.getEncoded());
    }

    @Test
    public void testGetCertificateFromPEM() throws Exception {
        EnvVarProvider envVarProvider = mock(EnvVarProvider.class);
        when(envVarProvider.getEnv(CERTIFICATE_ENV)).thenReturn(encodeToPEM(certificate, "CERTIFICATE"));

        KeyProvider keyProvider = new EnvKeyProvider(envVarProvider);
        Certificate retrievedCertificate = keyProvider.getCertificate();
        assertNotNull(retrievedCertificate);
        assertArrayEquals(certificate.getEncoded(), retrievedCertificate.getEncoded());
    }

    @Test
    public void testGetPrivateKeyFromFile() throws Exception {
        // Save private key to a temporary file
        Path privateKeyPath = saveKeyToFile(privateKey, "private_key.der");

        EnvVarProvider envVarProvider = mock(EnvVarProvider.class);
        when(envVarProvider.getEnv(PRIVATE_KEY_ENV)).thenReturn(privateKeyPath.toString());

        KeyProvider keyProvider = new EnvKeyProvider(envVarProvider);
        PrivateKey retrievedPrivateKey = keyProvider.getPrivateKey();
        assertNotNull(retrievedPrivateKey);
        assertArrayEquals(privateKey.getEncoded(), retrievedPrivateKey.getEncoded());
    }

    @Test
    public void testGetCertificateFromFile() throws Exception {
        // Save certificate to a temporary file
        Path certificatePath = saveCertificateToFile(certificate, "certificate.pem");

        EnvVarProvider envVarProvider = mock(EnvVarProvider.class);
        when(envVarProvider.getEnv(CERTIFICATE_ENV)).thenReturn(certificatePath.toString());

        KeyProvider keyProvider = new EnvKeyProvider(envVarProvider);
        Certificate retrievedCertificate = keyProvider.getCertificate();
        assertNotNull(retrievedCertificate);
        assertArrayEquals(certificate.getEncoded(), retrievedCertificate.getEncoded());
    }

    private String encodeToPEM(Key key, String type) {
        String base64Encoded = Base64.getEncoder().encodeToString(key.getEncoded());
        return "-----BEGIN " + type + "-----\n" + base64Encoded + "\n-----END " + type + "-----";
    }

    private String encodeToPEM(Certificate certificate, String type) throws Exception {
        String base64Encoded = Base64.getEncoder().encodeToString(certificate.getEncoded());
        return "-----BEGIN " + type + "-----\n" + base64Encoded + "\n-----END " + type + "-----";
    }

    private Path saveKeyToFile(Key key, String fileName) throws Exception {
        Path keyPath = Files.createTempFile(fileName, null);
        Files.write(keyPath, key.getEncoded());
        keyPath.toFile().deleteOnExit();
        return keyPath;
    }

    private Path saveCertificateToFile(Certificate certificate, String fileName) throws Exception {
        Path certPath = Files.createTempFile(fileName, null);
        try (FileWriter writer = new FileWriter(certPath.toFile())) {
            writer.write(encodeToPEM(certificate, "CERTIFICATE"));
        }
        certPath.toFile().deleteOnExit();
        return certPath;
    }

    private X509Certificate generateSelfSignedCertificate(KeyPair keyPair) throws Exception {
        long now = System.currentTimeMillis();
        Date startDate = new Date(now);

        X500Principal dnName = new X500Principal("CN=Test Certificate");
        BigInteger certSerialNumber = new BigInteger(Long.toString(now)); // Using current time as the certificate serial number
        Date endDate = new Date(now + 365 * 24 * 60 * 60 * 1000L); // 1 year validity

        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate());
        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                dnName, certSerialNumber, startDate, endDate, dnName, keyPair.getPublic());

        JcaX509CertificateConverter certConverter = new JcaX509CertificateConverter();
        return certConverter.getCertificate(certBuilder.build(contentSigner));
    }
}

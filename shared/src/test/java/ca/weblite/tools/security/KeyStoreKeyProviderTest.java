package ca.weblite.tools.security;

import static org.junit.jupiter.api.Assertions.*;

import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.*;
import java.security.*;
import java.security.cert.Certificate;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.*;

public class KeyStoreKeyProviderTest {

    private static final String KEY_STORE_PATH = "test-keystore.jks";
    private static final String KEY_STORE_PASSWORD = "password";
    private static final String PRIVATE_KEY_ALIAS = "privateKeyAlias";
    private static final String PUBLIC_KEY_ALIAS = "publicKeyAlias";

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @BeforeEach
    public void setUp() throws Exception {
        // Generate a key pair for testing
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        privateKey = keyPair.getPrivate();
        publicKey = keyPair.getPublic();

        // Create a KeyStore and add the keys
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        Certificate cert = generateSelfSignedCertificate(keyPair);
        keyStore.setKeyEntry(PRIVATE_KEY_ALIAS, privateKey, KEY_STORE_PASSWORD.toCharArray(), new Certificate[]{cert});
        keyStore.setCertificateEntry(PUBLIC_KEY_ALIAS, cert);

        // Save the KeyStore to a file
        try (FileOutputStream fos = new FileOutputStream(KEY_STORE_PATH)) {
            keyStore.store(fos, KEY_STORE_PASSWORD.toCharArray());
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        // Delete the KeyStore file
        Files.deleteIfExists(Paths.get(KEY_STORE_PATH));
    }

    @Test
    public void testGetPrivateKey() throws Exception {
        KeyProvider keyProvider = new KeyStoreKeyProvider(KEY_STORE_PATH, KEY_STORE_PASSWORD, PRIVATE_KEY_ALIAS, PUBLIC_KEY_ALIAS);
        PrivateKey retrievedPrivateKey = keyProvider.getPrivateKey();
        assertNotNull(retrievedPrivateKey);
        assertArrayEquals(privateKey.getEncoded(), retrievedPrivateKey.getEncoded());
    }

    @Test
    public void testGetPublicKey() throws Exception {
        KeyProvider keyProvider = new KeyStoreKeyProvider(KEY_STORE_PATH, KEY_STORE_PASSWORD, PRIVATE_KEY_ALIAS, PUBLIC_KEY_ALIAS);
        PublicKey retrievedPublicKey = keyProvider.getPublicKey();
        assertNotNull(retrievedPublicKey);
        assertArrayEquals(publicKey.getEncoded(), retrievedPublicKey.getEncoded());
    }

    private Certificate generateSelfSignedCertificate(KeyPair keyPair) throws Exception {
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

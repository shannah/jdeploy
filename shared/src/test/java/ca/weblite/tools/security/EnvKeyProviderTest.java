package ca.weblite.tools.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.security.*;
import java.util.Base64;

import ca.weblite.tools.env.EnvVarProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EnvKeyProviderTest {

    private static final String PRIVATE_KEY_ENV = "JDEPLOY_PRIVATE_KEY";
    private static final String PUBLIC_KEY_ENV = "JDEPLOY_PUBLIC_KEY";

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
    public void testGetPublicKeyFromPEM() throws Exception {
        EnvVarProvider envVarProvider = mock(EnvVarProvider.class);
        when(envVarProvider.getEnv(PUBLIC_KEY_ENV)).thenReturn(encodeToPEM(publicKey, "PUBLIC KEY"));

        KeyProvider keyProvider = new EnvKeyProvider(envVarProvider);
        PublicKey retrievedPublicKey = keyProvider.getPublicKey();
        assertNotNull(retrievedPublicKey);
        assertArrayEquals(publicKey.getEncoded(), retrievedPublicKey.getEncoded());
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
    public void testGetPublicKeyFromFile() throws Exception {
        // Save public key to a temporary file
        Path publicKeyPath = saveKeyToFile(publicKey, "public_key.der");

        EnvVarProvider envVarProvider = mock(EnvVarProvider.class);
        when(envVarProvider.getEnv(PUBLIC_KEY_ENV)).thenReturn(publicKeyPath.toString());

        KeyProvider keyProvider = new EnvKeyProvider(envVarProvider);
        PublicKey retrievedPublicKey = keyProvider.getPublicKey();
        assertNotNull(retrievedPublicKey);
        assertArrayEquals(publicKey.getEncoded(), retrievedPublicKey.getEncoded());
    }

    private String encodeToPEM(Key key, String type) {
        String base64Encoded = Base64.getEncoder().encodeToString(key.getEncoded());
        return "-----BEGIN " + type + "-----\n" + base64Encoded + "\n-----END " + type + "-----";
    }

    private Path saveKeyToFile(Key key, String fileName) throws Exception {
        Path keyPath = Files.createTempFile(fileName, null);
        Files.write(keyPath, key.getEncoded());
        keyPath.toFile().deleteOnExit();
        return keyPath;
    }
}

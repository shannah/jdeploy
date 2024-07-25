package ca.weblite.tools.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.condition.OS.MAC;

import java.security.*;
import java.security.cert.Certificate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledOnOs;

@EnabledIfEnvironmentVariable(named = "JDEPLOY_TEST_CERTIFICATE_NAME", matches = ".+")
@EnabledOnOs(MAC)
public class MacKeyStoreKeyProviderTest {
    private String alias;

    @BeforeEach
    public void setUp() throws Exception {
        // Generate unique alias based on CN
        alias = System.getenv("JDEPLOY_TEST_CERTIFICATE_NAME");
    }

    @Test
    public void testGetPrivateKey() throws Exception {
        KeyProvider keyProvider = new MacKeyStoreKeyProvider(alias, null);
        PrivateKey retrievedPrivateKey = keyProvider.getPrivateKey();
        assertNotNull(retrievedPrivateKey);
    }

    @Test
    public void testGetCertificate() throws Exception {
        KeyProvider keyProvider = new MacKeyStoreKeyProvider(alias, null);
        Certificate retrievedCertificate = keyProvider.getCertificate();
        assertNotNull(retrievedCertificate);
    }
}

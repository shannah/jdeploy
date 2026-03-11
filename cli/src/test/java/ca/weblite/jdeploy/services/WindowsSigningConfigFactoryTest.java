package ca.weblite.jdeploy.services;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WindowsSigningConfigFactoryTest {

    private WindowsSigningConfigFactory createFactory(Map<String, String> env) {
        return new WindowsSigningConfigFactory() {
            @Override
            String getenv(String name) {
                return env.get(name);
            }
        };
    }

    @Test
    void createFromEnvironment_returnsNull_whenNoEnvVars() {
        WindowsSigningConfigFactory factory = createFactory(new HashMap<String, String>());
        assertNull(factory.createFromEnvironment());
    }

    @Test
    void createFromEnvironment_returnsConfig_withKeystorePath() {
        Map<String, String> env = new HashMap<>();
        env.put("JDEPLOY_WIN_KEYSTORE_PATH", "/path/to/keystore.pfx");
        env.put("JDEPLOY_WIN_KEYSTORE_PASSWORD", "secret");

        WindowsSigningConfig config = createFactory(env).createFromEnvironment();

        assertNotNull(config);
        assertEquals("/path/to/keystore.pfx", config.getKeystorePath());
        assertEquals("secret", config.getKeystorePassword());
        assertEquals("PKCS12", config.getKeystoreType());
    }

    @Test
    void createFromEnvironment_returnsPkcs11Config() {
        Map<String, String> env = new HashMap<>();
        env.put("JDEPLOY_WIN_KEYSTORE_TYPE", "PKCS11");
        env.put("JDEPLOY_WIN_PKCS11_CONFIG", "/path/to/pkcs11.cfg");
        env.put("JDEPLOY_WIN_KEYSTORE_PASSWORD", "1234");

        WindowsSigningConfig config = createFactory(env).createFromEnvironment();

        assertNotNull(config);
        assertTrue(config.isPkcs11());
        assertEquals("/path/to/pkcs11.cfg", config.getPkcs11ConfigPath());
    }

    @Test
    void createFromEnvironment_returnsNull_pkcs11WithoutConfig() {
        Map<String, String> env = new HashMap<>();
        env.put("JDEPLOY_WIN_KEYSTORE_TYPE", "PKCS11");

        assertNull(createFactory(env).createFromEnvironment());
    }

    @Test
    void createFromEnvironment_readsAllFields() {
        Map<String, String> env = new HashMap<>();
        env.put("JDEPLOY_WIN_KEYSTORE_PATH", "/keystore.pfx");
        env.put("JDEPLOY_WIN_KEYSTORE_PASSWORD", "pass");
        env.put("JDEPLOY_WIN_KEYSTORE_TYPE", "JKS");
        env.put("JDEPLOY_WIN_KEY_ALIAS", "myalias");
        env.put("JDEPLOY_WIN_KEY_PASSWORD", "keypass");
        env.put("JDEPLOY_WIN_TIMESTAMP_URL", "http://ts.example.com");
        env.put("JDEPLOY_WIN_HASH_ALGORITHM", "SHA-384");
        env.put("JDEPLOY_WIN_SIGN_DESCRIPTION", "My App");
        env.put("JDEPLOY_WIN_SIGN_URL", "http://example.com");

        WindowsSigningConfig config = createFactory(env).createFromEnvironment();

        assertNotNull(config);
        assertEquals("/keystore.pfx", config.getKeystorePath());
        assertEquals("pass", config.getKeystorePassword());
        assertEquals("JKS", config.getKeystoreType());
        assertEquals("myalias", config.getAlias());
        assertEquals("keypass", config.getKeyPassword());
        assertEquals("http://ts.example.com", config.getTimestampUrl());
        assertEquals("SHA-384", config.getHashAlgorithm());
        assertEquals("My App", config.getDescription());
        assertEquals("http://example.com", config.getUrl());
    }

    @Test
    void createFromEnvironment_usesDefaultTimestamp_whenNotSet() {
        Map<String, String> env = new HashMap<>();
        env.put("JDEPLOY_WIN_KEYSTORE_PATH", "/keystore.pfx");

        WindowsSigningConfig config = createFactory(env).createFromEnvironment();

        assertNotNull(config);
        assertEquals("http://timestamp.digicert.com", config.getTimestampUrl());
    }
}

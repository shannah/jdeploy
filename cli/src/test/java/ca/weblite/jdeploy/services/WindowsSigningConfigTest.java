package ca.weblite.jdeploy.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WindowsSigningConfigTest {

    @Test
    void validate_withKeystorePath_succeeds() {
        WindowsSigningConfig config = new WindowsSigningConfig();
        config.setKeystorePath("/path/to/keystore.pfx");
        assertDoesNotThrow(config::validate);
    }

    @Test
    void validate_withoutKeystorePath_throws() {
        WindowsSigningConfig config = new WindowsSigningConfig();
        assertThrows(IllegalStateException.class, config::validate);
    }

    @Test
    void validate_pkcs11WithConfig_succeeds() {
        WindowsSigningConfig config = new WindowsSigningConfig();
        config.setKeystoreType("PKCS11");
        config.setPkcs11ConfigPath("/path/to/pkcs11.cfg");
        assertDoesNotThrow(config::validate);
    }

    @Test
    void validate_pkcs11WithoutConfig_throws() {
        WindowsSigningConfig config = new WindowsSigningConfig();
        config.setKeystoreType("PKCS11");
        assertThrows(IllegalStateException.class, config::validate);
    }

    @Test
    void isPkcs11_returnsTrue_whenTypeIsPKCS11() {
        WindowsSigningConfig config = new WindowsSigningConfig();
        config.setKeystoreType("PKCS11");
        assertTrue(config.isPkcs11());
    }

    @Test
    void isPkcs11_returnsFalse_whenTypeIsPKCS12() {
        WindowsSigningConfig config = new WindowsSigningConfig();
        assertFalse(config.isPkcs11());
    }

    @Test
    void defaults_areCorrect() {
        WindowsSigningConfig config = new WindowsSigningConfig();
        assertEquals("PKCS12", config.getKeystoreType());
        assertEquals("SHA-256", config.getHashAlgorithm());
        assertEquals("http://timestamp.digicert.com", config.getTimestampUrl());
    }
}

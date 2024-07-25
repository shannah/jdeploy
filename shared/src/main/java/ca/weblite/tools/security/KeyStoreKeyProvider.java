package ca.weblite.tools.security;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;

public class KeyStoreKeyProvider implements KeyProvider {

    private final String keyStorePath;
    private final char[] keyStorePassword;
    private final String privateKeyAlias;
    private final String certificateAlias;

    public KeyStoreKeyProvider(String keyStorePath, char[] keyStorePassword, String privateKeyAlias, String certificateAlias) {
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
        this.privateKeyAlias = privateKeyAlias;
        this.certificateAlias = certificateAlias;
    }

    @Override
    public PrivateKey getPrivateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(keyStorePath)) {
            keyStore.load(fis, keyStorePassword);
        }
        return (PrivateKey) keyStore.getKey(privateKeyAlias, keyStorePassword);
    }

    @Override
    public Certificate getCertificate() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(keyStorePath)) {
            keyStore.load(fis, keyStorePassword);
        }
        Certificate cert = keyStore.getCertificate(certificateAlias);
        if (cert == null) {
            throw new Exception("No certificate found for alias: " + certificateAlias);
        }
        return cert;
    }
}

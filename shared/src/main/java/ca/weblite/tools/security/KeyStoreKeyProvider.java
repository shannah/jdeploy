package ca.weblite.tools.security;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;

public class KeyStoreKeyProvider implements KeyProvider {

    private final String keyStorePath;
    private final String keyStorePassword;
    private final String privateKeyAlias;
    private final String publicKeyAlias;

    public KeyStoreKeyProvider(String keyStorePath, String keyStorePassword, String privateKeyAlias, String publicKeyAlias) {
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
        this.privateKeyAlias = privateKeyAlias;
        this.publicKeyAlias = publicKeyAlias;
    }

    @Override
    public PrivateKey getPrivateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(keyStorePath)) {
            keyStore.load(fis, keyStorePassword.toCharArray());
        }
        return (PrivateKey) keyStore.getKey(privateKeyAlias, keyStorePassword.toCharArray());
    }

    @Override
    public PublicKey getPublicKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(keyStorePath)) {
            keyStore.load(fis, keyStorePassword.toCharArray());
        }
        Certificate cert = keyStore.getCertificate(publicKeyAlias);
        if (cert == null) {
            throw new Exception("No certificate found for alias: " + publicKeyAlias);
        }
        return cert.getPublicKey();
    }
}

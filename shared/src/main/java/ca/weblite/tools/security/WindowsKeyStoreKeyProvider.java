package ca.weblite.tools.security;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;

public class WindowsKeyStoreKeyProvider implements KeyProvider {

    private final String alias;
    private final char[] keyPassword;

    public WindowsKeyStoreKeyProvider(String alias, char[] keyPassword) {
        this.alias = alias;
        this.keyPassword = keyPassword;
    }

    @Override
    public PrivateKey getPrivateKey() throws Exception {
        // First try Windows-MY keystore
        PrivateKey privateKey = getPrivateKeyFromKeystore("Windows-MY");
        if (privateKey != null) {
            return privateKey;
        }

        // If not found, try Windows-ROOT keystore
        privateKey = getPrivateKeyFromKeystore("Windows-ROOT");
        if (privateKey != null) {
            return privateKey;
        }

        throw new Exception("No private key found for alias: " + alias);
    }

    @Override
    public Certificate getCertificate() throws Exception {
        // First try Windows-MY keystore
        Certificate cert = getCertificateFromKeystore("Windows-MY");
        if (cert != null) {
            return cert;
        }

        // If not found, try Windows-ROOT keystore
        cert = getCertificateFromKeystore("Windows-ROOT");
        if (cert != null) {
            return cert;
        }

        throw new Exception("No certificate found for alias: " + alias);
    }

    private PrivateKey getPrivateKeyFromKeystore(String keystoreType) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(keystoreType);
        keyStore.load(null, null);
        return (PrivateKey) keyStore.getKey(alias, keyPassword);
    }

    private Certificate getCertificateFromKeystore(String keystoreType) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(keystoreType);
        keyStore.load(null, null);
        return keyStore.getCertificate(alias);
    }
}

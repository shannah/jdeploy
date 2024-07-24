package ca.weblite.tools.security;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;

public class MacKeyStoreKeyProvider implements KeyProvider {

    private final String alias;
    private final char[] keyPassword;

    public MacKeyStoreKeyProvider(String alias, char[] keyPassword) {
        this.alias = alias;
        this.keyPassword = keyPassword;
    }

    @Override
    public PrivateKey getPrivateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("KeychainStore");
        keyStore.load(null, null);
        return (PrivateKey) keyStore.getKey(alias, keyPassword);
    }

    @Override
    public PublicKey getPublicKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("KeychainStore");
        keyStore.load(null, null);
        Certificate cert = keyStore.getCertificate(alias);
        if (cert == null) {
            throw new Exception("No certificate found for alias: " + alias);
        }
        return cert.getPublicKey();
    }
}

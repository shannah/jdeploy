package ca.weblite.tools.security;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WindowsKeyStoreKeyProvider implements KeyProvider {

    private final String alias;
    private final char[] keyPassword;
    private final String rootCertificateAlias;

    public WindowsKeyStoreKeyProvider(String alias, char[] keyPassword) {
        this(alias, keyPassword, null);
    }

    public WindowsKeyStoreKeyProvider(String alias, char[] keyPassword, String rootCertificateAlias) {
        this.alias = alias;
        this.keyPassword = keyPassword;
        this.rootCertificateAlias = rootCertificateAlias;
    }

    @Override
    public PrivateKey getSigningKey() throws Exception {
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
    public Certificate getSigningCertificate() throws Exception {
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

    @Override
    public List<Certificate> getSigningCertificateChain() throws Exception {
        // First try Windows-MY keystore
        List<Certificate> certChain = getCertificateChainFromKeystore("Windows-MY");
        if (certChain != null && !certChain.isEmpty()) {
            return certChain;
        }

        // If not found, try Windows-ROOT keystore
        certChain = getCertificateChainFromKeystore("Windows-ROOT");
        if (certChain != null && !certChain.isEmpty()) {
            return certChain;
        }

        throw new Exception("No certificate chain found for alias: " + alias);
    }

    @Override
    public List<Certificate> getTrustedCertificates() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("KeychainStore");
        keyStore.load(null, null);

        List<Certificate> trustedCerts = new ArrayList<>();
        trustedCerts.add(getSigningCertificate());

        if (rootCertificateAlias != null) {
            trustedCerts.add(getRootCertificate());
        }

        return trustedCerts;
    }

    private Certificate getRootCertificate() throws Exception {
        // First try Windows-MY keystore
        Certificate cert = getRootCertificateFromKeystore("Windows-MY");
        if (cert != null) {
            return cert;
        }

        // If not found, try Windows-ROOT keystore
        cert = getRootCertificateFromKeystore("Windows-ROOT");
        if (cert != null) {
            return cert;
        }

        throw new Exception("No certificate found for alias: " + rootCertificateAlias);
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

    private Certificate getRootCertificateFromKeystore(String keystoreType) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(keystoreType);
        keyStore.load(null, null);
        return keyStore.getCertificate(rootCertificateAlias);
    }

    private Certificate getCertificateFromKeystore(String keystoreType, String alias) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(keystoreType);
        keyStore.load(null, null);
        return keyStore.getCertificate(alias);
    }

    private List<Certificate> getCertificateChainFromKeystore(String keystoreType) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(keystoreType);
        keyStore.load(null, null);
        Certificate[] certChain = keyStore.getCertificateChain(alias);
        if (certChain == null || certChain.length == 0) {
            return Collections.emptyList();
        }
        List<Certificate> certChainList = new ArrayList<>();
        Collections.addAll(certChainList, certChain);
        return certChainList;
    }
}

package ca.weblite.tools.security;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MacKeyStoreKeyProvider implements KeyProvider {

    private final String alias;
    private final char[] keyPassword;
    private final String rootCertificateAlias;

    public MacKeyStoreKeyProvider(String alias, char[] keyPassword) {
        this(alias, keyPassword, null);
    }

    public MacKeyStoreKeyProvider(String alias, char[] keyPassword, String rootCertificateAlias) {
        this.alias = alias;
        this.keyPassword = keyPassword;
        this.rootCertificateAlias = rootCertificateAlias;
    }

    @Override
    public PrivateKey getSigningKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("KeychainStore");
        keyStore.load(null, null);
        return (PrivateKey) keyStore.getKey(alias, keyPassword);
    }

    @Override
    public Certificate getSigningCertificate() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("KeychainStore");
        keyStore.load(null, null);
        Certificate cert = keyStore.getCertificate(alias);
        if (cert == null) {
            throw new Exception("No certificate found for alias: " + alias);
        }
        return cert;
    }

    @Override
    public List<Certificate> getSigningCertificateChain() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("KeychainStore");
        keyStore.load(null, null);
        Certificate[] certChain = keyStore.getCertificateChain(alias);
        if (certChain == null || certChain.length == 0) {
            throw new Exception("No certificate chain found for alias: " + alias);
        }
        List<Certificate> certChainList = new ArrayList<>();
        Collections.addAll(certChainList, certChain);
        return certChainList;
    }

    @Override
    public List<Certificate> getTrustedCertificates() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("KeychainStore");
        keyStore.load(null, null);

        if (rootCertificateAlias == null) {
            // If the root alias is null, return the signing certificate as the root
            Certificate signingCert = keyStore.getCertificate(alias);
            if (signingCert == null) {
                throw new Exception("No certificate found for alias: " + alias);
            }
            return Collections.singletonList(signingCert);
        }

        Certificate rootCert = keyStore.getCertificate(rootCertificateAlias);
        if (rootCert == null) {
            throw new Exception("No root certificate found for alias: " + rootCertificateAlias);
        }
        return Collections.singletonList(rootCert);
    }
}

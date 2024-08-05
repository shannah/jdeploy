package ca.weblite.tools.security;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KeyStoreKeyProvider implements KeyProvider {

    private final String keyStorePath;
    private final char[] keyStorePassword;
    private final String privateKeyAlias;
    private final String certificateAlias;
    private final String rootCertificateAlias;

    public KeyStoreKeyProvider(
            String keyStorePath,
            char[] keyStorePassword,
            String privateKeyAlias,
            String certificateAlias
    ) {
        this(keyStorePath, keyStorePassword, privateKeyAlias, certificateAlias, null);
    }

    public KeyStoreKeyProvider(
            String keyStorePath,
            char[] keyStorePassword,
            String privateKeyAlias,
            String certificateAlias,
            String rootCertificateAlias
    ) {
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
        this.privateKeyAlias = privateKeyAlias;
        this.certificateAlias = certificateAlias;
        this.rootCertificateAlias = rootCertificateAlias;
    }

    @Override
    public PrivateKey getSigningKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(keyStorePath)) {
            keyStore.load(fis, keyStorePassword);
        }
        return (PrivateKey) keyStore.getKey(privateKeyAlias, keyStorePassword);
    }

    @Override
    public Certificate getSigningCertificate() throws Exception {
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

    @Override
    public List<Certificate> getSigningCertificateChain() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(keyStorePath)) {
            keyStore.load(fis, keyStorePassword);
        }
        Certificate[] certChain = keyStore.getCertificateChain(privateKeyAlias);
        if (certChain == null || certChain.length == 0) {
            throw new Exception("No certificate chain found for alias: " + privateKeyAlias);
        }
        List<Certificate> certChainList = new ArrayList<>();
        Collections.addAll(certChainList, certChain);
        return certChainList;
    }

    @Override
    public List<Certificate> getTrustedCertificates() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(keyStorePath)) {
            keyStore.load(fis, keyStorePassword);
        }

        if (rootCertificateAlias == null) {
            // If the root alias is null, return the signing certificate as the root
            Certificate signingCert = keyStore.getCertificate(certificateAlias);
            if (signingCert == null) {
                throw new Exception("No certificate found for alias: " + certificateAlias);
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

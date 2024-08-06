package ca.weblite.tools.security;

import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CompositeKeyProvider implements KeyProvider {

    private final List<KeyProvider> keyProviders = new ArrayList<>();
    private PrivateKey cachedPrivateKey;
    private Certificate cachedCertificate;

    private KeyProvider certificateProvider;
    private KeyProvider keyProvider;

    public void registerKeyProvider(KeyProvider keyProvider) {
        keyProviders.add(keyProvider);
    }

    @Override
    public PrivateKey getSigningKey() throws Exception {
        if (cachedPrivateKey != null) {
            return cachedPrivateKey;
        }
        for (KeyProvider provider : keyProviders) {
            try {
                PrivateKey privateKey = provider.getSigningKey();
                if (privateKey != null) {
                    cachedPrivateKey = privateKey;
                    keyProvider = provider;
                    return privateKey;
                }
            } catch (Exception e) {
                // Log and continue trying other providers
                System.err.println("Failed to get private key from provider: " + e.getMessage());
            }
        }
        throw new Exception("No available key provider could provide a private key.");
    }

    @Override
    public Certificate getSigningCertificate() throws Exception {
        if (cachedCertificate != null) {
            return cachedCertificate;
        }
        for (KeyProvider provider : keyProviders) {
            try {
                Certificate certificate = provider.getSigningCertificate();
                if (certificate != null) {
                    cachedCertificate = certificate;
                    certificateProvider = provider;
                    return certificate;
                }
            } catch (Exception e) {
                // Log and continue trying other providers
                System.err.println("Failed to get certificate from provider: " + e.getMessage());
            }
        }
        throw new Exception("No available key provider could provide a certificate.");
    }

    @Override
    public List<Certificate> getSigningCertificateChain() throws Exception {
        getSigningCertificate();
        if (certificateProvider != null) {
            return certificateProvider.getSigningCertificateChain();
        }

        return Collections.emptyList();
    }

    @Override
    public List<Certificate> getTrustedCertificates() throws Exception {
        getSigningCertificate();
        if (certificateProvider != null) {
            return certificateProvider.getTrustedCertificates();
        }

        return Collections.emptyList();
    }

    public void clearCache() {
        cachedPrivateKey = null;
        cachedCertificate = null;
        keyProvider = null;
        certificateProvider = null;
    }
}

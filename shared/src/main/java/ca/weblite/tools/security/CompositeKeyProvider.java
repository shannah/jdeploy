package ca.weblite.tools.security;

import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;

public class CompositeKeyProvider implements KeyProvider {

    private final List<KeyProvider> keyProviders = new ArrayList<>();
    private PrivateKey cachedPrivateKey;
    private Certificate cachedCertificate;

    public void registerKeyProvider(KeyProvider keyProvider) {
        keyProviders.add(keyProvider);
    }

    @Override
    public PrivateKey getPrivateKey() throws Exception {
        if (cachedPrivateKey != null) {
            return cachedPrivateKey;
        }
        for (KeyProvider provider : keyProviders) {
            try {
                PrivateKey privateKey = provider.getPrivateKey();
                if (privateKey != null) {
                    cachedPrivateKey = privateKey;
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
    public Certificate getCertificate() throws Exception {
        if (cachedCertificate != null) {
            return cachedCertificate;
        }
        for (KeyProvider provider : keyProviders) {
            try {
                Certificate certificate = provider.getCertificate();
                if (certificate != null) {
                    cachedCertificate = certificate;
                    return certificate;
                }
            } catch (Exception e) {
                // Log and continue trying other providers
                System.err.println("Failed to get certificate from provider: " + e.getMessage());
            }
        }
        throw new Exception("No available key provider could provide a certificate.");
    }

    public void clearCache() {
        cachedPrivateKey = null;
        cachedCertificate = null;
    }
}

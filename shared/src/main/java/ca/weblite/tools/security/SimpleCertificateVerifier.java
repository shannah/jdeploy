package ca.weblite.tools.security;

import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

public class SimpleCertificateVerifier implements CertificateVerifier {

    private final KeyStore keyStore;

    public SimpleCertificateVerifier(KeyStore keyStore) {
        this.keyStore = keyStore;
    }

    @Override
    public boolean isTrusted(List<Certificate> certificateChain) {
        try {
            if (!isValidChain(certificateChain)) {
                return false;
            }

            for (Certificate cert : certificateChain) {
                if (isCertificateTrusted(cert)) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean isValidChain(List<Certificate> certificateChain) {
        try {
            if (certificateChain.isEmpty()) {
                return false;
            }

            for (int i = 0; i < certificateChain.size() - 1; i++) {
                X509Certificate cert = (X509Certificate) certificateChain.get(i);
                X509Certificate issuer = (X509Certificate) certificateChain.get(i + 1);

                // Verify that cert was issued by issuer
                cert.verify(issuer.getPublicKey());
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean isCertificateTrusted(Certificate cert) throws Exception {
        if (keyStore == null) {
            return false;
        }

        for (String alias : Collections.list(keyStore.aliases())) {
            Certificate trustedCert = keyStore.getCertificate(alias);
            if (trustedCert != null && trustedCert.equals(cert)) {
                return true;
            }

            // Check if the certificate is part of a chain that can be validated against the trusted cert
            if (trustedCert instanceof X509Certificate) {
                X509Certificate x509Cert = (X509Certificate) trustedCert;
                try {
                    x509Cert.checkValidity();
                    cert.verify(x509Cert.getPublicKey());
                    return true;
                } catch (Exception e) {
                    // Verification failed, continue checking other certificates
                }
            }
        }

        return false;
    }
}

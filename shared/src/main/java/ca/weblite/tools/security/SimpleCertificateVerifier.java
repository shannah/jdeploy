package ca.weblite.tools.security;

import java.security.KeyStore;
import java.security.cert.*;
import java.util.*;

public class SimpleCertificateVerifier implements CertificateVerifier {

    private final KeyStore keyStore;

    public SimpleCertificateVerifier(KeyStore keyStore) {
        this.keyStore = keyStore;
    }

    @Override
    public boolean isTrusted(List<Certificate> certificateChain) {
        X509Certificate certificate = (X509Certificate) certificateChain.get(0);
        Set<X509Certificate> intermediates = new HashSet<>();
        for (int i = 1; i < certificateChain.size(); i++) {
            intermediates.add((X509Certificate) certificateChain.get(i));
        }
        try {
            if (
                    isCertificateTrusted(
                            certificate,
                            intermediates,
                            keyStore
                    )
            ) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static boolean isCertificateTrusted(
            X509Certificate certificate,
            Set<X509Certificate> intermediates,
            KeyStore rootKeyStore) throws Exception {
        // Create a CertPathValidator instance
        CertPathValidator validator = CertPathValidator.getInstance(CertPathValidator.getDefaultType());

        // Create a CertPath for the certificate chain
        List<X509Certificate> certChain = new ArrayList<>();
        certChain.add(certificate);  // Start with the leaf certificate
        certChain.addAll(intermediates);  // Add intermediate certificates

        CertPath certPath = CertificateFactory.getInstance("X.509").generateCertPath(certChain);

        // Create a TrustAnchor set from the root certificates in the KeyStore
        Set<TrustAnchor> trustAnchors = new HashSet<>();
        Enumeration<String> aliases = rootKeyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (rootKeyStore.isCertificateEntry(alias)) {
                X509Certificate rootCert = (X509Certificate) rootKeyStore.getCertificate(alias);
                trustAnchors.add(new TrustAnchor(rootCert, null));
            }
        }

        // Create PKIX parameters with the trust anchors and any additional constraints
        PKIXParameters pkixParams = new PKIXParameters(trustAnchors);
        pkixParams.setRevocationEnabled(false);  // Disable CRL checks for simplicity

        try {
            // Validate the certificate path
            validator.validate(certPath, pkixParams);
            return true;  // Certificate chain is trusted
        } catch (CertPathValidatorException e) {
            return false;  // Certificate chain is not trusted
        }
    }

}

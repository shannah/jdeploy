package ca.weblite.tools.security;

import java.security.cert.Certificate;

public class SimpleCertificateVerifier implements CertificateVerifier {

    private final Certificate trustedCertificate;

    public SimpleCertificateVerifier(Certificate trustedCertificate) {
        this.trustedCertificate = trustedCertificate;
    }

    @Override
    public boolean isTrusted(Certificate certificate) {
        return trustedCertificate.equals(certificate);
    }
}

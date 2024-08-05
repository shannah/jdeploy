package ca.weblite.tools.security;

import java.security.cert.Certificate;

public interface CertificateVerifier {
    boolean isTrusted(Certificate certificate) throws Exception;
}

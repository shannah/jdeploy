package ca.weblite.tools.security;

import java.security.cert.Certificate;
import java.util.List;

public interface CertificateVerifier {
    boolean isTrusted(List<Certificate> certificateChain) throws Exception;
}

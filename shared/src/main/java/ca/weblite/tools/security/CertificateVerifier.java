package ca.weblite.tools.security;

import java.security.cert.Certificate;
import java.util.Date;
import java.util.List;

public interface CertificateVerifier {
    boolean isTrusted(List<Certificate> certificateChain) throws Exception;

    /**
     * Whether the chain was trusted at {@code validAt}: each certificate's
     * validity is checked against that moment rather than the current time.
     * A package is verified at the time it was signed, so a release signed
     * while its signing certificate was valid keeps verifying after that
     * certificate expires.
     *
     * <p>The default ignores {@code validAt} and checks against the current
     * time, for implementations written before this method existed.</p>
     */
    default boolean isTrusted(List<Certificate> certificateChain, Date validAt) throws Exception {
        return isTrusted(certificateChain);
    }
}

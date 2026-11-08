package ca.weblite.tools.security;

import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds the signing chain and the pinned (trusted) certificate list for key
 * providers that take an optional root certificate alongside the signing
 * certificate.
 */
final class SigningChains {

    private SigningChains() {
    }

    /**
     * The signing certificate, then the root (when there is one and it is not
     * the signing certificate itself).
     */
    static List<Certificate> chain(Certificate signing, Certificate root) throws Exception {
        if (root == null || root.equals(signing)) {
            return Collections.singletonList(signing);
        }
        requireIssuedBy(signing, root);
        List<Certificate> chain = new ArrayList<>();
        chain.add(signing);
        chain.add(root);
        return chain;
    }

    /**
     * The signing certificate and the root. Installers pin every certificate in
     * this list, and a package verifies when its chain leads to any of them.
     */
    static List<Certificate> trusted(Certificate signing, Certificate root) throws Exception {
        return chain(signing, root);
    }

    private static void requireIssuedBy(Certificate signing, Certificate root) throws Exception {
        try {
            signing.verify(root.getPublicKey());
        } catch (Exception ex) {
            throw new Exception(
                    "The root certificate did not issue the signing certificate, so packages signed with it "
                            + "would not verify against the pinned root. Check the configured root certificate "
                            + "(JDEPLOY_ROOT_CERTIFICATE).",
                    ex
            );
        }
    }
}

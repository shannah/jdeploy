package ca.weblite.tools.security;

import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.List;

public interface KeyProvider {
    PrivateKey getSigningKey() throws Exception;
    Certificate getSigningCertificate() throws Exception;
    List<Certificate> getSigningCertificateChain() throws Exception;

    /**
     * Trusted certificates are embedded into the signed app bundles and are treated as trust-worthy.
     * The default scenario is for there to be a single trusted certificate - the signing certificate.
     * This method, however, allows us to add additional trusted certificates that may have a longer lifefime
     * than the signing certificate.
     *
     * E.g. You may want your signing certificate to expire every 30 days since it will include the private key.
     * But you want your app bundle to live longer than 30 days, so you might create a Root certificate with a 10 year
     * lifespan, but which you don't need the private key to sign every release.  In this case you would include
     * both the signing certificate and the root certificate in the trusted certificates.  You sign the package files
     * with a signing certificate that is issued by the root certificate.  The package includes the certificate chain
     * in the package.  The app bundle includes the signing certificate and root certificate in the trusted certificates.
     * That way you can still validate packages signed by future signing certificates.
     * @return
     * @throws Exception
     */
    List<Certificate> getTrustedCertificates() throws Exception;
}


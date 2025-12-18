package ca.weblite.tools.security;

import java.security.cert.X509Certificate;

public interface CertificateAliasProvider {
    public String getCertificateAlias(X509Certificate certificate);
}

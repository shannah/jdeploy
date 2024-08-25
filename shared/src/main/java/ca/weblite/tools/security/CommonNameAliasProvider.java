package ca.weblite.tools.security;

import java.security.cert.X509Certificate;

public class CommonNameAliasProvider implements CertificateAliasProvider {
    @Override
    public String getCertificateAlias(X509Certificate certificate) {
        String commonName =  certificate.getSubjectX500Principal().getName();
        if (commonName.startsWith("CN=")) {
            return commonName.substring(3);
        } else {
            return commonName;
        }
    }
}

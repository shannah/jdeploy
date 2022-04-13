package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.helpers.SelfSignedCertificateGenerator;
import ca.weblite.jdeploy.models.DeveloperIdentity;

import java.security.KeyPair;
import java.security.cert.Certificate;

public class CertificateGenerator {
    public Certificate[] generateCertificates(DeveloperIdentity identity, KeyPair keyPair) {
        return SelfSignedCertificateGenerator.getCerts(identity, keyPair);
    }
}

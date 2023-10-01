package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.helpers.KeyPairGenerator;
import ca.weblite.jdeploy.models.DeveloperIdentity;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import static ca.weblite.jdeploy.tests.helpers.DeveloperIdentityTestHelper.createMockIdentity;
import static org.junit.jupiter.api.Assertions.*;

class CertificateGeneratorTest {

    @Test
    @Disabled
    void generateCertificates() throws Exception {
        CertificateGenerator generator = new CertificateGenerator();
        KeyPair keyPair = KeyPairGenerator.generateKeyPair();
        DeveloperIdentity identity = createMockIdentity();
        Certificate[] certs = generator.generateCertificates(identity, keyPair);
        assertEquals(1, certs.length);
        assertEquals(keyPair.getPublic(), certs[0].getPublicKey());

    }
}
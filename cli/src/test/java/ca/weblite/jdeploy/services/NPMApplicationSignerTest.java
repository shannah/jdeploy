package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.helpers.KeyPairGenerator;
import ca.weblite.jdeploy.models.DeveloperIdentity;
import ca.weblite.jdeploy.models.NPMApplication;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static ca.weblite.jdeploy.tests.helpers.DeveloperIdentityTestHelper.createMockIdentity;
import static ca.weblite.jdeploy.tests.helpers.NPMApplicationTestHelper.createMockNPMApplication;
import static org.junit.jupiter.api.Assertions.*;

class NPMApplicationSignerTest {

    @Test
    public void testSignNPMApplication() throws Exception {
        NPMApplication app = createMockNPMApplication();
        DeveloperIdentity identity = createMockIdentity();
        NPMApplicationSigner signer = new NPMApplicationSigner();
        KeyPair keyPair = KeyPairGenerator.generateKeyPair();
        DeveloperIdentitySigner identitySigner = new DeveloperIdentitySigner();
        identitySigner.sign(identity, keyPair);

        signer.sign(keyPair, app, identity);

        DeveloperIdentityVerifier identityVerifier = new DeveloperIdentityVerifier();
        assertTrue(identityVerifier.verify(identity, app));

        String oldRegistryUrl = app.getNpmRegistryUrl();
        app.setNpmRegistryUrl("https://foo.example.com");
        assertFalse(identityVerifier.verify(identity, app));
        app.setNpmRegistryUrl(oldRegistryUrl);
        assertTrue(identityVerifier.verify(identity, app));

        DeveloperIdentity developerIdentity2 = createMockIdentity();
        // Unsigned identity should fail to verify
        assertFalse(identityVerifier.verify(developerIdentity2, app));
        identitySigner.sign(developerIdentity2, keyPair);

        // Different but identical identity signed with same keypair should verify
        assertTrue(identityVerifier.verify(developerIdentity2, app));

        KeyPair keyPair2 = KeyPairGenerator.generateKeyPair();
        identitySigner.sign(developerIdentity2, keyPair2);

        // Identity identity signed with different keypair should fail to verify
        assertFalse(identityVerifier.verify(developerIdentity2, app));



    }

}
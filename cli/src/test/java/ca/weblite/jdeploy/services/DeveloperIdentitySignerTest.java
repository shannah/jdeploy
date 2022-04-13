package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.helpers.KeyPairGenerator;
import ca.weblite.jdeploy.models.DeveloperIdentity;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static ca.weblite.jdeploy.tests.helpers.DeveloperIdentityTestHelper.createMockIdentity;
import static org.junit.jupiter.api.Assertions.*;

class DeveloperIdentitySignerTest {

    @Test
    public void testSignDeveloperIdentity() throws Exception {
        DeveloperIdentitySigner signer = new DeveloperIdentitySigner();
        KeyPair keyPair = KeyPairGenerator.generateKeyPair();
        DeveloperIdentity identity = createMockIdentity();
        signer.sign(identity, keyPair);

        DeveloperIdentityVerifier verifier = new DeveloperIdentityVerifier();
        assertTrue(verifier.verify(identity));
        byte[] badSignature = new byte[identity.getSignature().length];
        System.arraycopy(identity.getSignature(), 0, badSignature, 0, badSignature.length);
        badSignature[5]++; // MOdify the signature
        identity.setSignature(badSignature);
        assertFalse(verifier.verify(identity));
    }

}
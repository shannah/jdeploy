package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.DeveloperIdentity;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyPair;

import static ca.weblite.jdeploy.helpers.KeyPairGenerator.generateKeyPair;
import static ca.weblite.jdeploy.tests.helpers.DeveloperIdentityTestHelper.createMockIdentity;
import static org.junit.jupiter.api.Assertions.*;

public class DeveloperIdentityLoaderTest {

    @Test
    public void testLoadAndVerifyIdentity() throws Exception {

        // First let's generate an identity
        DeveloperIdentity identity = createMockIdentity();
        KeyPair keyPair = generateKeyPair();
        DeveloperIdentitySigner signer = new DeveloperIdentitySigner();
        DeveloperIdentityVerifier verifier = new DeveloperIdentityVerifier();
        signer.sign(identity, keyPair);

        DeveloperIdentityJSONWriter writer = new DeveloperIdentityJSONWriter();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writer.writeIdentity(identity, keyPair, baos);

        DeveloperIdentityURLLoader loader = new DeveloperIdentityURLLoader();
        loader.setUrlLoader(url -> {
            return new ByteArrayInputStream(baos.toByteArray());
        });

        DeveloperIdentity id2 = new DeveloperIdentity();
        loader.loadIdentityFromURL(id2, identity.getIdentityUrl());
        assertTrue(verifier.verify(id2));

        IdentityValidationFailureException ex = null;
        try {
            DeveloperIdentity id3 = new DeveloperIdentity();
            // Should fail because the URL won't match the identity URL
            loader.loadIdentityFromURL(id3, "https://foo.different.com/id.json");
        } catch (IdentityValidationFailureException ex2) {
            ex = ex2;
        }
        assertNotNull(ex);
        ex = null;
        try {
            DeveloperIdentity id3 = new DeveloperIdentity();
            loader.loadIdentityFromURL(id3, identity.getIdentityUrl());
            assertTrue(verifier.verify(id3));
        } catch (IdentityValidationFailureException ex2) {
            ex = ex2;
        }



    }
}

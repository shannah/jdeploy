package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.DeveloperIdentity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;

import static ca.weblite.jdeploy.helpers.DeveloperIdentitySignatureHelper.updateSignature;

/**
 * Services to sign a developer identity.
 */
public class DeveloperIdentitySigner {
    public byte[] sign(
            DeveloperIdentity identity,
            KeyPair keyPair)
            throws NoSuchAlgorithmException,
            InvalidKeyException,
            IOException,
            SignatureException {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(keyPair.getPrivate());

        updateSignature(identity, sig);
        byte[] signature = sig.sign();
        identity.setSignature(signature);
        identity.setPublicKey(keyPair.getPublic());
        return signature;
    }


}

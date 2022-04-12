package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.DeveloperIdentity;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.*;

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

    public void updateSignature(DeveloperIdentity identity, Signature sig) throws IOException, SignatureException {
        sig.update("name=".getBytes(StandardCharsets.UTF_8));
        sig.update(identity.getName().getBytes("UTF-8"));
        sig.update("\n".getBytes(StandardCharsets.UTF_8));
        sig.update("identityUrl=".getBytes(StandardCharsets.UTF_8));
        sig.update(identity.getIdentityUrl().getBytes("UTF-8"));
        sig.update("\n".getBytes(StandardCharsets.UTF_8));
        for (String aliasUrl : identity.getAliasUrls()) {
            sig.update("aliasUrl=".getBytes(StandardCharsets.UTF_8));
            sig.update(aliasUrl.getBytes(StandardCharsets.UTF_8));
            sig.update("\n".getBytes(StandardCharsets.UTF_8));
        }
    }
}

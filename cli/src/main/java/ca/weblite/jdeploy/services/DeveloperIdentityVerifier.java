package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.DeveloperIdentities;
import ca.weblite.jdeploy.models.DeveloperIdentity;
import ca.weblite.jdeploy.models.NPMApplication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;

/**
 * A service that will verify a developer identity
 */
public class DeveloperIdentityVerifier {
    private DeveloperIdentitySigner signer = new DeveloperIdentitySigner();
    private NPMApplicationSigner appSigner = new NPMApplicationSigner();
    public boolean verify(DeveloperIdentity identity)
            throws
            NoSuchAlgorithmException,
            InvalidKeyException,
            SignatureException,
            IOException {
        Signature sig = Signature.getInstance("SHA256withRSA");
        PublicKey pubKey = identity.getPublicKey();
        sig.initVerify(pubKey);
        signer.updateSignature(identity, sig);
        return sig.verify(identity.getSignature());
    }

    public boolean verify(DeveloperIdentity identity, NPMApplication app)
            throws
            NoSuchAlgorithmException,
            InvalidKeyException,
            SignatureException,
            IOException {
        Signature sig = Signature.getInstance("SHA256withRSA");
        PublicKey pubKey = identity.getPublicKey();
        sig.initVerify(pubKey);
        byte[] appSignatureForIDentity = app.getSignature(identity);
        if (appSignatureForIDentity == null) return false;
        appSigner.updateAppSignature(app, sig);
        return sig.verify(appSignatureForIDentity);
    }

    public DeveloperIdentities getVerifiedIdentities(NPMApplication app)
            throws NoSuchAlgorithmException, SignatureException, IOException, InvalidKeyException {
        DeveloperIdentities out = new DeveloperIdentities();
        for (DeveloperIdentity identity : app.getDeveloperIdentities()) {
            if (verify(identity, app)) {
                out.add(identity);
            }
        }
        return out;
    }


}

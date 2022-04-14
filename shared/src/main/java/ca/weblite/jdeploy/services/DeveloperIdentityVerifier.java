package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.DeveloperIdentities;
import ca.weblite.jdeploy.models.DeveloperIdentity;
import ca.weblite.jdeploy.models.NPMApplication;

import java.io.IOException;
import java.security.*;

import static ca.weblite.jdeploy.helpers.DeveloperIdentitySignatureHelper.updateSignature;
import static ca.weblite.jdeploy.helpers.NPMApplicationSignatureHelper.updateAppVersionSignature;

/**
 * A service that will verify a developer identity
 */
public class DeveloperIdentityVerifier {


    /**
     * Verifies a developer identity.  NOTE: This only verifies that the signature matches the
     * public key in the identity.  It doesn't actually verify that the identity is valid.
     * Verification that the identity is valid is performed when the DeveloperIdentity is loaded in
     * {@link DeveloperIdentityURLLoader}, as it verifies that the identity is indeed from the
     * URL that was loaded.
     * @param identity
     * @return
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     * @throws SignatureException
     * @throws IOException
     */
    public boolean verify(DeveloperIdentity identity)
            throws
            NoSuchAlgorithmException,
            InvalidKeyException,
            SignatureException,
            IOException {
        Signature sig = Signature.getInstance("SHA256withRSA");
        PublicKey pubKey = identity.getPublicKey();
        sig.initVerify(pubKey);
        updateSignature(identity, sig);
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
        if (pubKey == null) {
            return false;
        }
        sig.initVerify(pubKey);
        byte[] appSignatureForIDentity = app.getSignature(identity);
        if (appSignatureForIDentity == null) return false;
        updateAppVersionSignature(app, sig);
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

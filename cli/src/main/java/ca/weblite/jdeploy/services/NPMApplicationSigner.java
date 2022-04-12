package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.DeveloperIdentity;
import ca.weblite.jdeploy.models.NPMApplication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Objects;

public class NPMApplicationSigner {
    private DeveloperIdentityKeyStore keyStore = new DeveloperIdentityKeyStore();

    public void sign(NPMApplication app, DeveloperIdentity... developerIdentities) throws NoSuchAlgorithmException,
            InvalidKeyException,
            IOException,
            SignatureException {
        for (DeveloperIdentity identity : developerIdentities) {
            signWithIdentity(app, identity);
        }
    }



    private void signWithIdentity(NPMApplication app, DeveloperIdentity identity) throws NoSuchAlgorithmException,
            InvalidKeyException,
            IOException,
            SignatureException {
        Signature sig = Signature.getInstance("SHA256withRSA");
        KeyPair keyPair = keyStore.getKeyPair(identity);
        if (keyPair == null) {
            throw new IOException("The keystore could not find key for development identity "+identity.getIdentityUrl());
        }
        if (identity.getPublicKey() == null) {
            throw new IllegalStateException("The identity "+identity.getName()+" is incomplete.  It has no public key.");
        }
        if (!Objects.equals(identity.getPublicKey().getEncoded(), keyPair.getPublic().getEncoded())) {
            throw new IllegalStateException("The identity "+identity.getName()+" public key doesn't match the one from the keystore.");
        }
        if (identity.getSignature() == null) {
            throw new IllegalStateException("The identity "+identity.getName()+" is incomplete. It has no signature.");
        }

        sig.initSign(keyPair.getPrivate());
        updateAppSignature(app, sig);

        byte[] signature = sig.sign();
        app.addSignature(identity, signature);

    }

    public void updateAppSignature(NPMApplication app, Signature sig) throws SignatureException {
        sig.update("registryUrl=".getBytes(StandardCharsets.UTF_8));
        sig.update(app.getNpmRegistryUrl().getBytes(StandardCharsets.UTF_8));
        sig.update("\npackageName=".getBytes(StandardCharsets.UTF_8));
        sig.update(app.getPackageName().getBytes(StandardCharsets.UTF_8));
        sig.update("\nversion=".getBytes(StandardCharsets.UTF_8));
        sig.update(app.getPackageVersion().getBytes(StandardCharsets.UTF_8));
    }
}

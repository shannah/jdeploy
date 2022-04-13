package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.DeveloperIdentity;
import ca.weblite.jdeploy.models.NPMApplication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Objects;

import static ca.weblite.jdeploy.helpers.NPMApplicationSignatureHelper.updateAppSignature;

public class NPMApplicationSigner {
    private DeveloperIdentityKeyStore keyStore = new DeveloperIdentityKeyStore();

    public void sign(KeyPair keyPair, NPMApplication app, DeveloperIdentity... developerIdentities) throws NoSuchAlgorithmException,
            InvalidKeyException,
            IOException,
            SignatureException, UnrecoverableKeyException, CertificateException, KeyStoreException, NoSuchProviderException {
        for (DeveloperIdentity identity : developerIdentities) {
            signWithIdentity(keyPair, app, identity);
        }
    }



    private void signWithIdentity(KeyPair keyPair, NPMApplication app, DeveloperIdentity identity) throws NoSuchAlgorithmException,
            InvalidKeyException,
            IOException,
            SignatureException, UnrecoverableKeyException, CertificateException, KeyStoreException, NoSuchProviderException {
        Signature sig = Signature.getInstance("SHA256withRSA");
        //KeyPair keyPair = keyStore.getKeyPair(identity, true);
        if (keyPair == null) {
            throw new IllegalArgumentException("The keystore could not find key for development identity "+identity.getIdentityUrl());
        }

        sig.initSign(keyPair.getPrivate());
        updateAppSignature(app, sig);

        byte[] signature = sig.sign();
        app.addSignature(identity, signature);

    }


}

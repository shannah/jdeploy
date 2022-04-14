package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.helpers.NPMApplicationSignatureHelper;
import ca.weblite.jdeploy.models.DeveloperIdentity;
import ca.weblite.jdeploy.models.NPMApplication;

import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Base64;

import static ca.weblite.jdeploy.helpers.NPMApplicationSignatureHelper.updateAppVersionSignature;

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
        updateAppVersionSignature(app, sig);

        byte[] signature = sig.sign();
        app.addSignature(identity, signature);
        app.setVersionSignature(toHexString(getSHA(signature)));
        app.setDeveloperSignature(toHexString(getSHA(keyPair.getPublic().getEncoded())));
        app.setDeveloperPublicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(keyPair.getPrivate());
        NPMApplicationSignatureHelper.updateAppSignature(app, sig);

        byte[] appSignature = sig.sign();
        // Signature for general app.  This will be the same for all releases of this package.
        app.setAppSignature(toHexString(getSHA(appSignature)));




    }

    private static byte[] getSHA(byte[] input) throws NoSuchAlgorithmException
    {
        // Static getInstance method is called with hashing SHA
        MessageDigest md = MessageDigest.getInstance("SHA-256");

        // digest() method called
        // to calculate message digest of an input
        // and return array of byte
        return md.digest(input);
    }
    private static String toHexString(byte[] hash)
    {
        // Convert byte array into signum representation
        BigInteger number = new BigInteger(1, hash);

        // Convert message digest into hex value
        StringBuilder hexString = new StringBuilder(number.toString(16));

        // Pad with leading zeros
        while (hexString.length() < 32)
        {
            hexString.insert(0, '0');
        }

        return hexString.toString();
    }




}

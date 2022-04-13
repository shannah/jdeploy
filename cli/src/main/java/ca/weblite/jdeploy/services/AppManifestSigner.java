package ca.weblite.jdeploy.services;
import ca.weblite.jdeploy.models.AppManifest;

import java.io.*;

import java.security.*;


public class AppManifestSigner {
    private AppManifestSerializer serializer = new AppManifestSerializer();

    public byte[] signManifest(AppManifest appManifest, PrivateKey key) throws IOException, SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(key);
        sig.update(serializer.serializeManifest(appManifest).getBytes("UTF-8"));
        byte[] signature = sig.sign();
        appManifest.setSignature(signature);
        return signature;
    }




}

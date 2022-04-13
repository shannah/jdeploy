package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.DeveloperIdentity;
import ca.weblite.tools.security.CertificateUtil;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Base64;

public class DeveloperIdentityJSONWriter {
    private DeveloperIdentitySigner signer = new DeveloperIdentitySigner();

    /**
     * Writes a developer identity to an output file and signs it with the provided keypair.
     * @param identity The identity to write.
     * @param keyPair The keypair to use to sign it.
     * @param outputFile The file where the json identity file should be written.
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws SignatureException
     * @throws InvalidKeyException
     */
    public void writeIdentity(DeveloperIdentity identity, KeyPair keyPair, File outputFile) throws IOException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            writeIdentity(identity, keyPair, fos);
        }
    }

    /**
     * Writes a developer identity to an output file and signs it with the provided keypair.
     * @param identity The identity to write.
     * @param keyPair The keypair to use to sign it.
     * @param outputStream The OutputStream where the json identity file should be written.
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws SignatureException
     * @throws InvalidKeyException
     */
    public void writeIdentity(DeveloperIdentity identity, KeyPair keyPair, OutputStream outputStream) throws IOException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        signer.sign(identity, keyPair);
        JSONObject json = new JSONObject();
        json.put("identityUrl", identity.getIdentityUrl());
        json.put("name", identity.getName());
        json.put("organization", identity.getOrganization());
        json.put("countryCode", identity.getCountryCode());
        json.put("city", identity.getCity());
        JSONArray aliasUrls = new JSONArray();
        json.put("aliasUrls", aliasUrls);
        for (String u : identity.getAliasUrls()) {
            aliasUrls.put(u);
        }
        json.put("publicKey", CertificateUtil.toPemEncodedString(identity.getPublicKey()));
        json.put("signature", Base64.getEncoder().encodeToString(identity.getSignature()));
        outputStream.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
    }
}

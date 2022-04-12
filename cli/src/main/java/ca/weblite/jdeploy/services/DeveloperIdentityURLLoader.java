package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.DeveloperIdentity;
import ca.weblite.tools.io.IOUtil;
import ca.weblite.tools.io.URLUtil;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

public class DeveloperIdentityURLLoader {
    private DeveloperIdentityVerifier developerIdentityVerifier = new DeveloperIdentityVerifier();
    public void loadIdentityFromURL(DeveloperIdentity identity, String url) throws Exception {
        if (!url.startsWith("https://")) {
            throw new IOException("Identities can only be loaded over https.  Attempted to load from "+url);
        }
        try (InputStream input = URLUtil.openStream(new URL(url))) {
            String contents = IOUtil.readToString(input);
            JSONObject json = new JSONObject(contents);

            loadIdentityFromJSON(identity, json, url);
        }
    }

    private void loadIdentityFromJSON(DeveloperIdentity identity, JSONObject json, String url) throws Exception {
        identity.setIdentityUrl(json.getString("identityUrl"));
        identity.setName(json.getString(json.getString("name")));
        if (json.has("aliasUrls")) {
            JSONArray aliasUrls = json.getJSONArray("aliasUrls");
            int len = aliasUrls.length();
            for (int i=0; i<len; i++) {
                identity.addAliasUrl(aliasUrls.getString(i));
            }
        }
        if (identity.matchesUrl(url)) {
            throw new IOException("Failed to load identity from url "+url+" because the identity at that location does not match the URL");
        }
        String signatureBase64 = json.getString("signature");
        byte[] signature = Base64.decodeBase64(signatureBase64);
        identity.setSignature(signature);

        if (json.has("publicKey")) {
            String publicKeyBase64 = json.getString("publicKey");
            identity.setPublicKey(getPublicKeyFromPem(publicKeyBase64));

        }
        developerIdentityVerifier.verify(identity);


    }

    private static PublicKey getPublicKeyFromPem(String publicKeyPEM) throws CertificateException, NoSuchAlgorithmException, InvalidKeySpecException {
        String temp = publicKeyPEM;

        if(temp.contains("-----BEGIN PUBLIC KEY-----"))
        {
            publicKeyPEM = temp
                    .replace("-----BEGIN PUBLIC KEY-----\n", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .trim();
        }
        else if(temp.contains("-----BEGIN RSA PUBLIC KEY-----"))
        {
            publicKeyPEM = temp
                    .replace("-----BEGIN RSA PUBLIC KEY-----\n", "")
                    .replace("-----END RSA PUBLIC KEY-----", "")
                    .trim();
        }
        else if(temp.contains("-----BEGIN CERTIFICATE-----"))
        {
            CertificateFactory fact = CertificateFactory.getInstance("X.509");
            X509Certificate cer = (X509Certificate) fact.generateCertificate(new ByteArrayInputStream(temp.getBytes(StandardCharsets.UTF_8)));
            return cer.getPublicKey();
        }

        byte[] decoded = Base64.decodeBase64(publicKeyPEM);

        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }
}

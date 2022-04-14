package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.DeveloperIdentity;
import ca.weblite.tools.security.CertificateUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

public class DeveloperIdentityJSONReader {
    private DeveloperIdentityVerifier developerIdentityVerifier = new DeveloperIdentityVerifier();



    public void loadIdentityFromJSON(DeveloperIdentity identity, JSONObject json, String url) throws IOException, CertificateException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, InvalidKeyException {
        identity.setIdentityUrl(json.getString("identityUrl"));
        identity.setName(json.getString("name"));
        if (json.has("organization")) {
            identity.setOrganization(json.getString("organization"));
        }
        if (json.has("countryCode")) {
            identity.setCountryCode(json.getString("countryCode"));
        }
        if (json.has("city")) {
            identity.setCity(json.getString("city"));
        }
        if (json.has("aliasUrls")) {
            JSONArray aliasUrls = json.getJSONArray("aliasUrls");
            int len = aliasUrls.length();
            for (int i=0; i<len; i++) {
                identity.addAliasUrl(aliasUrls.getString(i));
            }
        }
        if (!identity.matchesUrl(url)) {
            throw new IdentityValidationFailureException("Failed to load identity from url "+url+" because the identity at that location does not match the URL", identity);
        }
        String signatureBase64 = json.getString("signature");
        byte[] signature = Base64.getDecoder().decode(signatureBase64);
        identity.setSignature(signature);

        if (json.has("publicKey")) {
            String publicKeyBase64 = json.getString("publicKey");
            identity.setPublicKey(CertificateUtil.getPublicKeyFromPem(publicKeyBase64));

        }

        identity.setWebsiteURL(url.substring(0, url.lastIndexOf("/")));

        if (!developerIdentityVerifier.verify(identity)) {
            throw new IdentityValidationFailureException("Failed to verify the identity.", identity);
        }


    }
}

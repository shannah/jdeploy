package ca.weblite.jdeploy.services;
import ca.weblite.tools.io.IOUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Singleton
public class GithubSecretSetter {

    private static final String GITHUB_API_URL = "https://api.github.com";

    private final SealedBoxUtility sealedBoxUtility;

    @Inject
    public GithubSecretSetter(SealedBoxUtility sealedBoxUtility) {
        this.sealedBoxUtility = sealedBoxUtility;
    }


    public void setSecret(String owner, String repo, String token, String secretName, String secretValue) throws Exception {
        // Step 1: Get the public key
        PublicKeyInfo publicKey = getPublicKey(owner, repo, token);

        // Step 2: Encrypt the secret (This is a placeholder, implement encryption with libsodium)
        String encryptedSecret = encryptSecret(secretValue, publicKey);

        // Step 3: Create or update the secret
        createOrUpdateSecret(owner, repo, token, secretName, encryptedSecret, publicKey);
    }

    private PublicKeyInfo getPublicKey(String owner, String repo, String token) throws Exception {
        URL url = new URL(GITHUB_API_URL + "/repos/" + owner + "/" + repo + "/actions/secrets/public-key");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Authorization", "token " + token);
        con.setRequestProperty("Accept", "application/vnd.github.v3+json");
        int responseCode = con.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("Failed to get public key: GitHub API responded with code " + responseCode);
        }

        // Read the response
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        JSONObject jsonResponse = new JSONObject(response.toString());
        String publicKey = jsonResponse.getString("key");
        String keyId = jsonResponse.getString("key_id");
        return new PublicKeyInfo(publicKey, keyId);
    }


    private String encryptSecret(String secret, PublicKeyInfo publicKeyInfo) throws Exception {
        // Decode the public key
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyInfo.getKey());

        return Base64.getEncoder().encodeToString(
                sealedBoxUtility.crypto_box_seal(secret.getBytes(), publicKeyBytes)
        );
    }


    private void createOrUpdateSecret(
            String owner,
            String repo,
            String token,
            String secretName,
            String encryptedSecret,
            PublicKeyInfo publicKeyInfo
    ) throws Exception {
        URL url = new URL(GITHUB_API_URL + "/repos/" + owner + "/" + repo + "/actions/secrets/" + secretName);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("PUT");
        con.setRequestProperty("Authorization", "token " + token);
        con.setRequestProperty("Accept", "application/vnd.github.v3+json");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);

        String jsonPayload = "{\"encrypted_value\":\"" + encryptedSecret + "\", \"key_id\":\"" + publicKeyInfo.getKeyId() + "\"}";
        try (OutputStream os = con.getOutputStream()) {
            byte[] input = jsonPayload.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        // Handle the response (omitted for brevity)
        int responseCode = con.getResponseCode();
        if (responseCode < HttpURLConnection.HTTP_OK || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
            // Read error message from response (if any)
            String errorMessage = IOUtil.readToString(con.getErrorStream());
            throw new RuntimeException("Failed to set secret: HTTP error code: " + responseCode + " - " + errorMessage);
        }
    }

    private static class PublicKeyInfo {
        private final String key;
        private final String keyId;

        public PublicKeyInfo(String key, String keyId) {
            this.key = key;
            this.keyId = keyId;
        }

        // Getters
        public String getKey() {
            return key;
        }

        public String getKeyId() {
            return keyId;
        }
    }
}


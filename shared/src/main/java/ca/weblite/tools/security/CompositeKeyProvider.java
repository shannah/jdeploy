package ca.weblite.tools.security;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public class CompositeKeyProvider implements KeyProvider {

    private final List<KeyProvider> keyProviders = new ArrayList<>();

    public void registerKeyProvider(KeyProvider keyProvider) {
        keyProviders.add(keyProvider);
    }

    @Override
    public PrivateKey getPrivateKey() throws Exception {
        for (KeyProvider provider : keyProviders) {
            try {
                PrivateKey privateKey = provider.getPrivateKey();
                if (privateKey != null) {
                    return privateKey;
                }
            } catch (Exception e) {
                // Log and continue trying other providers
                System.err.println("Failed to get private key from provider: " + e.getMessage());
            }
        }
        throw new Exception("No available key provider could provide a private key.");
    }

    @Override
    public PublicKey getPublicKey() throws Exception {
        for (KeyProvider provider : keyProviders) {
            try {
                PublicKey publicKey = provider.getPublicKey();
                if (publicKey != null) {
                    return publicKey;
                }
            } catch (Exception e) {
                // Log and continue trying other providers
                System.err.println("Failed to get public key from provider: " + e.getMessage());
            }
        }
        throw new Exception("No available key provider could provide a public key.");
    }
}


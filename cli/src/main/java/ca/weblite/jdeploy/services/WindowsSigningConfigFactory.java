package ca.weblite.jdeploy.services;

/**
 * Factory that builds {@link WindowsSigningConfig} from environment variables.
 *
 * <p>Environment variables:</p>
 * <ul>
 *   <li>{@code JDEPLOY_WIN_KEYSTORE_PATH} — path to PFX/JKS keystore file</li>
 *   <li>{@code JDEPLOY_WIN_KEYSTORE_PASSWORD} — keystore password</li>
 *   <li>{@code JDEPLOY_WIN_KEYSTORE_TYPE} — {@code PKCS12} (default), {@code JKS}, or {@code PKCS11}</li>
 *   <li>{@code JDEPLOY_WIN_KEY_ALIAS} — key alias within keystore</li>
 *   <li>{@code JDEPLOY_WIN_KEY_PASSWORD} — private key password (defaults to keystore password)</li>
 *   <li>{@code JDEPLOY_WIN_TIMESTAMP_URL} — RFC 3161 timestamp server URL</li>
 *   <li>{@code JDEPLOY_WIN_HASH_ALGORITHM} — hash algorithm ({@code SHA-256} default)</li>
 *   <li>{@code JDEPLOY_WIN_PKCS11_CONFIG} — path to PKCS#11 provider config file</li>
 *   <li>{@code JDEPLOY_WIN_SIGN_DESCRIPTION} — Authenticode signature description</li>
 *   <li>{@code JDEPLOY_WIN_SIGN_URL} — Authenticode signature URL</li>
 * </ul>
 */
public class WindowsSigningConfigFactory {

    /**
     * Creates a {@link WindowsSigningConfig} from environment variables.
     *
     * @return a populated config, or {@code null} if no signing configuration is available
     *         (i.e., neither keystore path nor PKCS#11 config is set)
     */
    public WindowsSigningConfig createFromEnvironment() {
        String keystorePath = getenv("JDEPLOY_WIN_KEYSTORE_PATH");
        String pkcs11Config = getenv("JDEPLOY_WIN_PKCS11_CONFIG");
        String keystoreType = getenv("JDEPLOY_WIN_KEYSTORE_TYPE");

        boolean isPkcs11 = "PKCS11".equalsIgnoreCase(keystoreType);

        if (!isPkcs11 && isEmpty(keystorePath)) {
            return null;
        }
        if (isPkcs11 && isEmpty(pkcs11Config)) {
            return null;
        }

        WindowsSigningConfig config = new WindowsSigningConfig();
        config.setKeystorePath(keystorePath);
        config.setKeystorePassword(getenv("JDEPLOY_WIN_KEYSTORE_PASSWORD"));
        if (keystoreType != null) {
            config.setKeystoreType(keystoreType);
        }
        config.setAlias(getenv("JDEPLOY_WIN_KEY_ALIAS"));
        config.setKeyPassword(getenv("JDEPLOY_WIN_KEY_PASSWORD"));
        config.setPkcs11ConfigPath(pkcs11Config);

        String timestampUrl = getenv("JDEPLOY_WIN_TIMESTAMP_URL");
        if (timestampUrl != null) {
            config.setTimestampUrl(timestampUrl);
        }

        String hashAlgorithm = getenv("JDEPLOY_WIN_HASH_ALGORITHM");
        if (hashAlgorithm != null) {
            config.setHashAlgorithm(hashAlgorithm);
        }

        config.setDescription(getenv("JDEPLOY_WIN_SIGN_DESCRIPTION"));
        config.setUrl(getenv("JDEPLOY_WIN_SIGN_URL"));

        return config;
    }

    String getenv(String name) {
        return System.getenv(name);
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
}

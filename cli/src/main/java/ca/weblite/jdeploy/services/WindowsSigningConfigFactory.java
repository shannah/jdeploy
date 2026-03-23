package ca.weblite.jdeploy.services;

/**
 * Factory that builds {@link WindowsSigningConfig} from environment variables.
 *
 * <p>Environment variables:</p>
 * <ul>
 *   <li>{@code JDEPLOY_WIN_KEYSTORE_PATH} — path to PFX/JKS keystore file</li>
 *   <li>{@code JDEPLOY_WIN_KEYSTORE_PASSWORD} — keystore password</li>
 *   <li>{@code JDEPLOY_WIN_KEYSTORE_TYPE} — {@code PKCS12} (default), {@code JKS}, {@code PKCS11}, or {@code DIGICERTONE}</li>
 *   <li>{@code JDEPLOY_WIN_KEY_ALIAS} — key alias within keystore</li>
 *   <li>{@code JDEPLOY_WIN_KEY_PASSWORD} — private key password (defaults to keystore password)</li>
 *   <li>{@code JDEPLOY_WIN_TIMESTAMP_URL} — RFC 3161 timestamp server URL</li>
 *   <li>{@code JDEPLOY_WIN_HASH_ALGORITHM} — hash algorithm ({@code SHA-256} default)</li>
 *   <li>{@code JDEPLOY_WIN_PKCS11_CONFIG} — path to PKCS#11 provider config file</li>
 *   <li>{@code JDEPLOY_WIN_SIGN_DESCRIPTION} — Authenticode signature description</li>
 *   <li>{@code JDEPLOY_WIN_SIGN_URL} — Authenticode signature URL</li>
 *   <li>{@code SM_API_KEY} — DigiCert ONE API key</li>
 *   <li>{@code SM_CLIENT_CERT_FILE} — path to DigiCert ONE client auth certificate (P12)</li>
 *   <li>{@code SM_CLIENT_CERT_PASSWORD} — password for the client auth certificate</li>
 *   <li>{@code SM_HOST} — DigiCert ONE API host URL</li>
 * </ul>
 */
public class WindowsSigningConfigFactory {

    /**
     * Creates a {@link WindowsSigningConfig} from environment variables.
     *
     * @return a populated config, or {@code null} if no signing configuration is available
     *         (i.e., neither keystore path, PKCS#11 config, nor DigiCert ONE API key is set)
     */
    public WindowsSigningConfig createFromEnvironment() {
        String keystorePath = getenv("JDEPLOY_WIN_KEYSTORE_PATH");
        String pkcs11Config = getenv("JDEPLOY_WIN_PKCS11_CONFIG");
        String keystoreType = getenv("JDEPLOY_WIN_KEYSTORE_TYPE");

        boolean isPkcs11 = "PKCS11".equalsIgnoreCase(keystoreType);
        boolean isDigiCertOne = "DIGICERTONE".equalsIgnoreCase(keystoreType);

        if (isDigiCertOne) {
            String smApiKey = getenv("SM_API_KEY");
            if (isEmpty(smApiKey)) {
                return null;
            }
        } else if (isPkcs11) {
            if (isEmpty(pkcs11Config)) {
                return null;
            }
        } else {
            if (isEmpty(keystorePath)) {
                return null;
            }
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

        // DigiCert ONE / KeyLocker fields
        config.setSmApiKey(getenv("SM_API_KEY"));
        config.setSmClientCertFile(getenv("SM_CLIENT_CERT_FILE"));
        config.setSmClientCertPassword(getenv("SM_CLIENT_CERT_PASSWORD"));
        config.setSmHost(getenv("SM_HOST"));

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

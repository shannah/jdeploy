package ca.weblite.jdeploy.services;

/**
 * Configuration for Windows Authenticode signing.
 *
 * Supports local keystores (PFX/JKS), PKCS#11 HSM tokens, and is designed
 * to be extended for cloud HSM services (Azure Key Vault, AWS CloudHSM, etc.).
 */
public class WindowsSigningConfig {

    private static final String DEFAULT_TIMESTAMP_URL = "http://timestamp.digicert.com";
    private static final String DEFAULT_HASH_ALGORITHM = "SHA-256";
    private static final String DEFAULT_KEYSTORE_TYPE = "PKCS12";

    private String keystorePath;
    private String keystorePassword;
    private String keystoreType = DEFAULT_KEYSTORE_TYPE;
    private String alias;
    private String keyPassword;
    private String timestampUrl = DEFAULT_TIMESTAMP_URL;
    private String hashAlgorithm = DEFAULT_HASH_ALGORITHM;
    private String pkcs11ConfigPath;
    private String description;
    private String url;

    public String getKeystorePath() {
        return keystorePath;
    }

    public void setKeystorePath(String keystorePath) {
        this.keystorePath = keystorePath;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public String getKeystoreType() {
        return keystoreType;
    }

    public void setKeystoreType(String keystoreType) {
        this.keystoreType = keystoreType;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getKeyPassword() {
        return keyPassword;
    }

    public void setKeyPassword(String keyPassword) {
        this.keyPassword = keyPassword;
    }

    public String getTimestampUrl() {
        return timestampUrl;
    }

    public void setTimestampUrl(String timestampUrl) {
        this.timestampUrl = timestampUrl;
    }

    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    public void setHashAlgorithm(String hashAlgorithm) {
        this.hashAlgorithm = hashAlgorithm;
    }

    public String getPkcs11ConfigPath() {
        return pkcs11ConfigPath;
    }

    public void setPkcs11ConfigPath(String pkcs11ConfigPath) {
        this.pkcs11ConfigPath = pkcs11ConfigPath;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isPkcs11() {
        return "PKCS11".equalsIgnoreCase(keystoreType);
    }

    /**
     * Validates that the minimum required configuration is present.
     *
     * @throws IllegalStateException if required fields are missing
     */
    public void validate() {
        if (isPkcs11()) {
            if (pkcs11ConfigPath == null || pkcs11ConfigPath.isEmpty()) {
                throw new IllegalStateException(
                        "PKCS#11 config path is required when keystoreType is PKCS11"
                );
            }
        } else {
            if (keystorePath == null || keystorePath.isEmpty()) {
                throw new IllegalStateException(
                        "Keystore path is required for keystore type " + keystoreType
                );
            }
        }
    }
}

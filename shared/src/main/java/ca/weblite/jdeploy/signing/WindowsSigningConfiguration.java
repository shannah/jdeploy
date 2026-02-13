package ca.weblite.jdeploy.signing;

import java.io.File;

/**
 * Configuration model for Windows code signing.
 *
 * This class holds all the configuration needed to sign a Windows executable:
 * - Certificate path and password
 * - Timestamp server URL
 * - Publisher name for display
 * - Signing algorithm
 */
public class WindowsSigningConfiguration {

    private static final String DEFAULT_TIMESTAMP_SERVER = "http://timestamp.digicert.com";
    private static final String DEFAULT_ALGORITHM = "SHA-256";

    private String certificatePath;
    private String certificatePassword;
    private String timestampServer = DEFAULT_TIMESTAMP_SERVER;
    private String publisherName;
    private String algorithm = DEFAULT_ALGORITHM;

    /**
     * Creates an empty configuration.
     * Use the builder for fluent construction.
     */
    public WindowsSigningConfiguration() {
    }

    /**
     * Creates a configuration from environment variables.
     *
     * Environment variables:
     * - JDEPLOY_WINDOWS_CERTIFICATE_PATH
     * - JDEPLOY_WINDOWS_CERTIFICATE_PASSWORD
     * - JDEPLOY_WINDOWS_TIMESTAMP_SERVER (optional)
     * - JDEPLOY_WINDOWS_PUBLISHER_NAME (optional)
     *
     * @return configuration populated from environment
     */
    public static WindowsSigningConfiguration fromEnvironment() {
        return builder()
                .certificatePath(System.getenv("JDEPLOY_WINDOWS_CERTIFICATE_PATH"))
                .certificatePassword(System.getenv("JDEPLOY_WINDOWS_CERTIFICATE_PASSWORD"))
                .timestampServer(getEnvOrDefault("JDEPLOY_WINDOWS_TIMESTAMP_SERVER", DEFAULT_TIMESTAMP_SERVER))
                .publisherName(System.getenv("JDEPLOY_WINDOWS_PUBLISHER_NAME"))
                .build();
    }

    private static String getEnvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    /**
     * Validates that this configuration has all required fields.
     *
     * @throws SigningConfigurationException if validation fails
     */
    public void validate() throws SigningConfigurationException {
        if (certificatePath == null || certificatePath.isEmpty()) {
            throw new SigningConfigurationException("Certificate path is required for Windows signing");
        }

        File certFile = new File(certificatePath);
        if (!certFile.exists()) {
            throw new SigningConfigurationException("Certificate file not found: " + certificatePath);
        }

        if (!certFile.canRead()) {
            throw new SigningConfigurationException("Certificate file is not readable: " + certificatePath);
        }

        // Password can be empty for some certificate types, so we just check for null
        if (certificatePassword == null) {
            throw new SigningConfigurationException("Certificate password is required");
        }
    }

    /**
     * Checks if this configuration appears to be valid without throwing.
     *
     * @return true if basic validation passes
     */
    public boolean isValid() {
        try {
            validate();
            return true;
        } catch (SigningConfigurationException e) {
            return false;
        }
    }

    // ==================== Getters and Setters ====================

    public String getCertificatePath() {
        return certificatePath;
    }

    public void setCertificatePath(String certificatePath) {
        this.certificatePath = certificatePath;
    }

    public String getCertificatePassword() {
        return certificatePassword;
    }

    public void setCertificatePassword(String certificatePassword) {
        this.certificatePassword = certificatePassword;
    }

    public String getTimestampServer() {
        return timestampServer;
    }

    public void setTimestampServer(String timestampServer) {
        this.timestampServer = timestampServer != null ? timestampServer : DEFAULT_TIMESTAMP_SERVER;
    }

    public String getPublisherName() {
        return publisherName;
    }

    public void setPublisherName(String publisherName) {
        this.publisherName = publisherName;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm != null ? algorithm : DEFAULT_ALGORITHM;
    }

    // ==================== Builder ====================

    /**
     * Creates a new builder for WindowsSigningConfiguration.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for WindowsSigningConfiguration.
     */
    public static class Builder {
        private final WindowsSigningConfiguration config = new WindowsSigningConfiguration();

        public Builder certificatePath(String path) {
            config.setCertificatePath(path);
            return this;
        }

        public Builder certificatePassword(String password) {
            config.setCertificatePassword(password);
            return this;
        }

        public Builder timestampServer(String server) {
            config.setTimestampServer(server);
            return this;
        }

        public Builder publisherName(String name) {
            config.setPublisherName(name);
            return this;
        }

        public Builder algorithm(String algorithm) {
            config.setAlgorithm(algorithm);
            return this;
        }

        public WindowsSigningConfiguration build() {
            return config;
        }
    }

    @Override
    public String toString() {
        return "WindowsSigningConfiguration{" +
                "certificatePath='" + certificatePath + '\'' +
                ", timestampServer='" + timestampServer + '\'' +
                ", publisherName='" + publisherName + '\'' +
                ", algorithm='" + algorithm + '\'' +
                '}';
    }
}

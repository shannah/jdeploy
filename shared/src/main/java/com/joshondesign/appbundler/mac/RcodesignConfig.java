package com.joshondesign.appbundler.mac;

import java.io.File;
import java.io.IOException;

/**
 * Configuration and availability detection for rcodesign, a cross-platform
 * macOS code signing and notarization tool.
 *
 * <p>When running on non-macOS platforms, rcodesign can be used as a fallback
 * for Apple's native codesign and notarytool commands.</p>
 *
 * <h3>Environment Variables</h3>
 * <ul>
 *   <li>{@code JDEPLOY_RCODESIGN_P12_FILE} - Path to PKCS#12 (.p12) certificate file for signing</li>
 *   <li>{@code JDEPLOY_RCODESIGN_P12_PASSWORD} - Password for the P12 file</li>
 *   <li>{@code JDEPLOY_RCODESIGN_API_KEY_PATH} - Path to App Store Connect API key JSON file for notarization</li>
 *   <li>{@code JDEPLOY_RCODESIGN_API_ISSUER} - App Store Connect API issuer UUID (alternative to API key file)</li>
 *   <li>{@code JDEPLOY_RCODESIGN_API_KEY} - App Store Connect API key ID (alternative to API key file)</li>
 * </ul>
 */
public class RcodesignConfig {

    static final String ENV_P12_FILE = "JDEPLOY_RCODESIGN_P12_FILE";
    static final String ENV_P12_PASSWORD = "JDEPLOY_RCODESIGN_P12_PASSWORD";
    static final String ENV_API_KEY_PATH = "JDEPLOY_RCODESIGN_API_KEY_PATH";
    static final String ENV_API_ISSUER = "JDEPLOY_RCODESIGN_API_ISSUER";
    static final String ENV_API_KEY = "JDEPLOY_RCODESIGN_API_KEY";

    private RcodesignConfig() {
    }

    /**
     * Checks whether the rcodesign binary is available on the system PATH.
     */
    public static boolean isRcodesignAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("rcodesign", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /**
     * Returns the path to the P12 certificate file, or null if not configured.
     */
    public static String getP12File() {
        return getEnv(ENV_P12_FILE);
    }

    /**
     * Returns the P12 password, or null if not configured.
     */
    public static String getP12Password() {
        return getEnv(ENV_P12_PASSWORD);
    }

    /**
     * Returns the path to the App Store Connect API key JSON file, or null if not configured.
     */
    public static String getApiKeyPath() {
        return getEnv(ENV_API_KEY_PATH);
    }

    /**
     * Returns the App Store Connect API issuer UUID, or null if not configured.
     */
    public static String getApiIssuer() {
        return getEnv(ENV_API_ISSUER);
    }

    /**
     * Returns the App Store Connect API key ID, or null if not configured.
     */
    public static String getApiKey() {
        return getEnv(ENV_API_KEY);
    }

    /**
     * Whether signing credentials are configured for rcodesign.
     * Requires at minimum a P12 file.
     */
    public static boolean isSigningConfigured() {
        String p12File = getP12File();
        return p12File != null && !p12File.isEmpty() && new File(p12File).exists();
    }

    /**
     * Whether notarization credentials are configured for rcodesign.
     * Requires either an API key JSON file, or both an API issuer and API key ID.
     */
    public static boolean isNotarizationConfigured() {
        String apiKeyPath = getApiKeyPath();
        if (apiKeyPath != null && !apiKeyPath.isEmpty() && new File(apiKeyPath).exists()) {
            return true;
        }
        String apiIssuer = getApiIssuer();
        String apiKey = getApiKey();
        return apiIssuer != null && !apiIssuer.isEmpty()
                && apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Whether rcodesign can be used for signing: binary is available and signing credentials are set.
     */
    public static boolean canSign() {
        return isRcodesignAvailable() && isSigningConfigured();
    }

    /**
     * Whether rcodesign can be used for notarization: binary is available and notarization credentials are set.
     */
    public static boolean canNotarize() {
        return isRcodesignAvailable() && isNotarizationConfigured();
    }

    private static String getEnv(String name) {
        String value = System.getenv(name);
        if (value != null && value.isEmpty()) {
            return null;
        }
        return value;
    }
}

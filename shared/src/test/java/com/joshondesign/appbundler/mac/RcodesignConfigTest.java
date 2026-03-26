package com.joshondesign.appbundler.mac;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RcodesignConfig} environment variable reading and validation.
 *
 * <p>Note: These tests verify the static accessor methods. Since they read from
 * {@code System.getenv()}, full integration testing requires setting environment
 * variables externally. These tests verify behavior when env vars are not set
 * (the default case).</p>
 */
public class RcodesignConfigTest {

    @Test
    void signingNotConfiguredByDefault() {
        // Unless the CI environment happens to have these vars set,
        // signing should not be configured by default
        String p12 = System.getenv(RcodesignConfig.ENV_P12_FILE);
        if (p12 == null || p12.isEmpty()) {
            assertFalse(RcodesignConfig.isSigningConfigured(),
                    "Signing should not be configured when JDEPLOY_RCODESIGN_P12_FILE is not set");
        }
    }

    @Test
    void notarizationNotConfiguredByDefault() {
        String apiKeyPath = System.getenv(RcodesignConfig.ENV_API_KEY_PATH);
        String apiIssuer = System.getenv(RcodesignConfig.ENV_API_ISSUER);
        String apiKey = System.getenv(RcodesignConfig.ENV_API_KEY);
        if ((apiKeyPath == null || apiKeyPath.isEmpty())
                && (apiIssuer == null || apiIssuer.isEmpty() || apiKey == null || apiKey.isEmpty())) {
            assertFalse(RcodesignConfig.isNotarizationConfigured(),
                    "Notarization should not be configured when API credentials are not set");
        }
    }

    @Test
    void canSignReturnsFalseWhenNotConfigured() {
        String p12 = System.getenv(RcodesignConfig.ENV_P12_FILE);
        if (p12 == null || p12.isEmpty()) {
            assertFalse(RcodesignConfig.canSign(),
                    "canSign() should return false when signing is not configured");
        }
    }

    @Test
    void canNotarizeReturnsFalseWhenNotConfigured() {
        String apiKeyPath = System.getenv(RcodesignConfig.ENV_API_KEY_PATH);
        String apiIssuer = System.getenv(RcodesignConfig.ENV_API_ISSUER);
        if ((apiKeyPath == null || apiKeyPath.isEmpty())
                && (apiIssuer == null || apiIssuer.isEmpty())) {
            assertFalse(RcodesignConfig.canNotarize(),
                    "canNotarize() should return false when notarization is not configured");
        }
    }

    @Test
    void gettersReturnNullWhenEnvVarsNotSet() {
        // These will return null when the env vars aren't set
        // We can't guarantee they're unset, so we just verify they don't throw
        RcodesignConfig.getP12File();
        RcodesignConfig.getP12Password();
        RcodesignConfig.getApiKeyPath();
        RcodesignConfig.getApiIssuer();
        RcodesignConfig.getApiKey();
    }
}

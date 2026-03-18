package com.joshondesign.appbundler.mac;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Notarizes macOS application bundles using rcodesign as a cross-platform
 * alternative to Apple's native {@code xcrun notarytool} and {@code xcrun stapler}.
 *
 * <p>Requires App Store Connect API credentials configured via environment variables.
 * Either {@code JDEPLOY_RCODESIGN_API_KEY_PATH} (JSON file) or both
 * {@code JDEPLOY_RCODESIGN_API_ISSUER} and {@code JDEPLOY_RCODESIGN_API_KEY}.</p>
 */
public class RcodesignNotaryTool {

    /**
     * Notarizes a macOS .app bundle using rcodesign.
     *
     * <p>This creates a ZIP of the app, submits it for notarization,
     * waits for completion, and staples the ticket.</p>
     *
     * @param appPath path to the .app directory
     * @throws IOException if notarization credentials are missing or the process fails
     * @throws InterruptedException if the process is interrupted
     */
    public void notarizeApp(String appPath) throws IOException, InterruptedException {
        if (!(new File(appPath).isDirectory())) {
            throw new IllegalArgumentException("appPath must be a directory");
        }

        if (!RcodesignConfig.isNotarizationConfigured()) {
            throw new IOException("rcodesign notarization credentials are not configured. " +
                    "Set JDEPLOY_RCODESIGN_API_KEY_PATH or both JDEPLOY_RCODESIGN_API_ISSUER and JDEPLOY_RCODESIGN_API_KEY.");
        }

        // Submit for notarization and wait
        System.out.println("Submitting app for notarization via rcodesign: " + appPath);
        List<String> submitCommand = new ArrayList<>();
        submitCommand.add("rcodesign");
        submitCommand.add("notary-submit");
        addApiCredentials(submitCommand);
        submitCommand.add("--wait");
        submitCommand.add(appPath);

        runProcess(new ProcessBuilder(submitCommand));

        // Staple the notarization ticket
        System.out.println("Stapling notarization ticket via rcodesign");
        List<String> stapleCommand = new ArrayList<>();
        stapleCommand.add("rcodesign");
        stapleCommand.add("staple");
        stapleCommand.add(appPath);

        runProcess(new ProcessBuilder(stapleCommand));
    }

    private void addApiCredentials(List<String> command) {
        String apiKeyPath = RcodesignConfig.getApiKeyPath();
        if (apiKeyPath != null && !apiKeyPath.isEmpty() && new File(apiKeyPath).exists()) {
            command.add("--api-key-path");
            command.add(apiKeyPath);
        } else {
            String apiIssuer = RcodesignConfig.getApiIssuer();
            String apiKey = RcodesignConfig.getApiKey();
            command.add("--api-issuer");
            command.add(apiIssuer);
            command.add("--api-key");
            command.add(apiKey);
        }
    }

    private static void runProcess(ProcessBuilder processBuilder) throws IOException, InterruptedException {
        processBuilder.inheritIO();
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("rcodesign process failed with exit code: " + exitCode);
        }
    }
}

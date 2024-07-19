package com.joshondesign.appbundler.mac;

import java.io.File;
import java.io.IOException;

public class NotaryTool {

    private static final String XCRUN = "/usr/bin/xcrun";

    private static final String DITTO = "/usr/bin/ditto";

    public void notarizeApp(
            String appPath,
            String appleId,
            String apple2faPassword,
            String appleTeamId
    ) throws IOException, InterruptedException {
        if (!(new File(appPath).isDirectory())) {
            throw new IllegalArgumentException("appPath must be a directory");
        }
        if (appleId == null || appleId.isEmpty()) {
            throw new IllegalArgumentException("appleId must be provided");
        }
        if (apple2faPassword == null || apple2faPassword.isEmpty()) {
            throw new IllegalArgumentException("apple2faPassword must be provided");
        }
        String zipPath = appPath + ".zip";

        // Create a ZIP archive suitable for notarization.
        ProcessBuilder dittoBuilder = new ProcessBuilder(
                DITTO,
                "-c",
                "-k",
                "--keepParent",
                appPath,
                zipPath
        );
        try {
            runProcess(dittoBuilder);

            // Store credentials with notarytool
            ProcessBuilder credentialsBuilder;
            if (appleTeamId != null && !appleTeamId.isEmpty()) {
                System.out.println("Storing credentials for appleId: " + appleId + ", teamId: " + appleTeamId);
                //xcrun notarytool store-credentials "AC_PASSWORD" --apple-id "$APPLE_ID" $TEAM_ID_FLAG --password "$APPLE_2FA_PASSWORD"
                credentialsBuilder = new ProcessBuilder(
                        XCRUN,
                        "notarytool",
                        "store-credentials",
                        "AC_PASSWORD",
                        "--apple-id", appleId,
                        "--team-id", appleTeamId,
                        "--password", apple2faPassword
                );
            } else {
                System.out.println("Storing credentials for appleId: " + appleId);
                credentialsBuilder = new ProcessBuilder(
                        XCRUN,
                        "notarytool",
                        "store-credentials",
                        "AC_PASSWORD",
                        "--apple-id", appleId,
                        "--password", apple2faPassword
                );
            }
            runProcess(credentialsBuilder);

            // Submit the app for notarization
            System.out.println("Submitting app for notarization");
            ProcessBuilder submitBuilder = new ProcessBuilder(
                    XCRUN,
                    "notarytool",
                    "submit",
                    zipPath,
                    "--keychain-profile",
                    "AC_PASSWORD",
                    "--wait"
            );
            runProcess(submitBuilder);

            // Staple the notarization ticket to the app
            ProcessBuilder staplerBuilder = new ProcessBuilder(XCRUN, "stapler", "staple", appPath);
            runProcess(staplerBuilder);

            // Validate the stapling
            ProcessBuilder validateBuilder = new ProcessBuilder(XCRUN, "stapler", "validate", appPath);
            runProcess(validateBuilder);
        } finally {
            File zipFile = new File(zipPath);
            if (zipFile.exists()) {
                zipFile.delete();
            }
        }

    }

    private static void runProcess(ProcessBuilder processBuilder) throws IOException, InterruptedException {
        processBuilder.inheritIO();
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Process failed with exit code: " + exitCode);
        }
    }
}

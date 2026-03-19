package ca.weblite.jdeploy.installer.win;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * Adds certificates to the current user's trust store on Windows.
 * Uses certutil with the -user flag so no admin elevation is required.
 */
public class CertificateTrustService {

    /**
     * Adds a certificate file to the current user's trusted root certificate store.
     * This does not require admin privileges.
     *
     * @param certFile the .cer certificate file to add
     * @return true if the certificate was successfully added
     */
    public boolean addToUserTrustStore(File certFile) {
        if (certFile == null || !certFile.exists()) {
            return false;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "certutil.exe", "-user", "-addstore", "Root", certFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("certutil failed (exit code " + exitCode + "): " + output);
            }
            return exitCode == 0;
        } catch (Exception e) {
            System.err.println("Failed to add certificate to user trust store: " + e.getMessage());
            return false;
        }
    }
}

package ca.weblite.jdeploy.installer.win;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Checks Authenticode signatures on Windows executables using PowerShell.
 * Determines if an exe is signed and whether its certificate is trusted.
 */
public class AuthenticodeSignatureChecker {

    /**
     * Result of an Authenticode signature check.
     */
    public static class SignatureCheckResult {
        private final boolean signed;
        private final boolean trusted;
        private final String subject;
        private final String issuer;
        private final String thumbprint;
        private final String validFrom;
        private final String validTo;
        private final String statusMessage;

        public SignatureCheckResult(
                boolean signed,
                boolean trusted,
                String subject,
                String issuer,
                String thumbprint,
                String validFrom,
                String validTo,
                String statusMessage
        ) {
            this.signed = signed;
            this.trusted = trusted;
            this.subject = subject;
            this.issuer = issuer;
            this.thumbprint = thumbprint;
            this.validFrom = validFrom;
            this.validTo = validTo;
            this.statusMessage = statusMessage;
        }

        public boolean isSigned() { return signed; }
        public boolean isTrusted() { return trusted; }
        public String getSubject() { return subject; }
        public String getIssuer() { return issuer; }
        public String getThumbprint() { return thumbprint; }
        public String getValidFrom() { return validFrom; }
        public String getValidTo() { return validTo; }
        public String getStatusMessage() { return statusMessage; }

        /**
         * Returns true if the exe is signed but the certificate is not trusted
         * (i.e., self-signed or signed with an untrusted CA).
         */
        public boolean isSignedButUntrusted() {
            return signed && !trusted;
        }
    }

    /**
     * Checks the Authenticode signature of a Windows executable.
     *
     * @param exeFile the executable file to check
     * @return the signature check result (never null)
     * @throws IllegalArgumentException if exeFile is null, does not exist, or is not an .exe file
     * @throws IOException if the PowerShell process fails or returns a non-zero exit code
     */
    public SignatureCheckResult checkSignature(File exeFile) throws IOException {
        if (exeFile == null) {
            throw new IllegalArgumentException("exeFile must not be null");
        }
        if (!exeFile.exists()) {
            throw new IllegalArgumentException("File does not exist: " + exeFile.getAbsolutePath());
        }
        if (!exeFile.getName().endsWith(".exe")) {
            throw new IllegalArgumentException("File is not an .exe: " + exeFile.getName());
        }

        String script = buildPowerShellScript(exeFile);
        ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script
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

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("PowerShell Get-AuthenticodeSignature failed with exit code " + exitCode + ": " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for PowerShell process", e);
        }

        return parseResult(output.toString());
    }

    private String buildPowerShellScript(File exeFile) {
        String escapedPath = exeFile.getAbsolutePath().replace("'", "''");
        return "$sig = Get-AuthenticodeSignature -FilePath '" + escapedPath + "'; " +
                "if ($sig.SignerCertificate -ne $null) { " +
                "  $cert = $sig.SignerCertificate; " +
                "  Write-Output \"SIGNED=true\"; " +
                "  Write-Output \"STATUS=$($sig.Status)\"; " +
                "  Write-Output \"STATUS_MESSAGE=$($sig.StatusMessage)\"; " +
                "  Write-Output \"SUBJECT=$($cert.Subject)\"; " +
                "  Write-Output \"ISSUER=$($cert.Issuer)\"; " +
                "  Write-Output \"THUMBPRINT=$($cert.Thumbprint)\"; " +
                "  Write-Output \"VALID_FROM=$($cert.NotBefore.ToString('yyyy-MM-dd'))\"; " +
                "  Write-Output \"VALID_TO=$($cert.NotAfter.ToString('yyyy-MM-dd'))\" " +
                "} else { " +
                "  Write-Output \"SIGNED=false\" " +
                "}";
    }

    private SignatureCheckResult parseResult(String output) {
        boolean signed = false;
        boolean trusted = false;
        String subject = "";
        String issuer = "";
        String thumbprint = "";
        String validFrom = "";
        String validTo = "";
        String statusMessage = "";
        String status = "";

        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.startsWith("SIGNED=")) {
                signed = "true".equals(line.substring("SIGNED=".length()));
            } else if (line.startsWith("STATUS=")) {
                status = line.substring("STATUS=".length());
            } else if (line.startsWith("STATUS_MESSAGE=")) {
                statusMessage = line.substring("STATUS_MESSAGE=".length());
            } else if (line.startsWith("SUBJECT=")) {
                subject = line.substring("SUBJECT=".length());
            } else if (line.startsWith("ISSUER=")) {
                issuer = line.substring("ISSUER=".length());
            } else if (line.startsWith("THUMBPRINT=")) {
                thumbprint = line.substring("THUMBPRINT=".length());
            } else if (line.startsWith("VALID_FROM=")) {
                validFrom = line.substring("VALID_FROM=".length());
            } else if (line.startsWith("VALID_TO=")) {
                validTo = line.substring("VALID_TO=".length());
            }
        }

        if (!signed) {
            return new SignatureCheckResult(false, false, "", "", "", "", "", "Not signed");
        }

        // "Valid" status means the certificate chain is trusted
        trusted = "Valid".equals(status);

        return new SignatureCheckResult(signed, trusted, subject, issuer, thumbprint, validFrom, validTo, statusMessage);
    }

    /**
     * Exports the signing certificate from an exe to a temporary .cer file.
     *
     * @param exeFile the signed executable
     * @return the temporary .cer file (never null)
     * @throws IllegalArgumentException if exeFile is null or does not exist
     * @throws IOException if the certificate could not be exported
     */
    public File exportCertificate(File exeFile) throws IOException {
        if (exeFile == null) {
            throw new IllegalArgumentException("exeFile must not be null");
        }
        if (!exeFile.exists()) {
            throw new IllegalArgumentException("File does not exist: " + exeFile.getAbsolutePath());
        }

        File certFile = File.createTempFile("jdeploy_cert_", ".cer");
        certFile.deleteOnExit();

        String escapedExePath = exeFile.getAbsolutePath().replace("'", "''");
        String escapedCertPath = certFile.getAbsolutePath().replace("'", "''");

        String script = "$sig = Get-AuthenticodeSignature -FilePath '" + escapedExePath + "'; " +
                "if ($sig.SignerCertificate -ne $null) { " +
                "  [System.IO.File]::WriteAllBytes('" + escapedCertPath + "', $sig.SignerCertificate.RawData); " +
                "  Write-Output 'OK' " +
                "} else { " +
                "  Write-Output 'NO_CERT' " +
                "}";

        ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            certFile.delete();
            throw new IOException("Interrupted while waiting for PowerShell process", e);
        }

        if (output.toString().trim().equals("OK") && certFile.length() > 0) {
            return certFile;
        }

        certFile.delete();
        throw new IOException("Failed to export certificate from " + exeFile.getName()
                + ": " + output.toString().trim());
    }
}

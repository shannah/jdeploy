package com.joshondesign.appbundler.mac;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Signs macOS application bundles using rcodesign as a cross-platform
 * alternative to Apple's native {@code /usr/bin/codesign}.
 *
 * <p>Requires a PKCS#12 (.p12) certificate file configured via the
 * {@code JDEPLOY_RCODESIGN_P12_FILE} environment variable.</p>
 */
public class RcodesignSigner {

    /**
     * Signs a single file or bundle with rcodesign.
     *
     * @param targetPath      path to the file or .app bundle to sign
     * @param entitlementsFile optional entitlements plist file (may be null)
     * @throws IOException if rcodesign is not configured or signing fails
     * @throws InterruptedException if the process is interrupted
     */
    public void sign(String targetPath, File entitlementsFile) throws IOException, InterruptedException {
        String p12File = RcodesignConfig.getP12File();
        if (p12File == null || p12File.isEmpty()) {
            throw new IOException("JDEPLOY_RCODESIGN_P12_FILE environment variable is not set");
        }

        List<String> command = new ArrayList<>();
        command.add("rcodesign");
        command.add("sign");
        command.add("--p12-file");
        command.add(p12File);

        String p12Password = RcodesignConfig.getP12Password();
        if (p12Password != null && !p12Password.isEmpty()) {
            command.add("--p12-password");
            command.add(p12Password);
        }

        if (entitlementsFile != null && entitlementsFile.exists()) {
            command.add("--entitlements-xml-path");
            command.add(entitlementsFile.getAbsolutePath());
        }

        command.add("--code-signature-flags");
        command.add("runtime");

        command.add(targetPath);

        System.out.println("Signing with rcodesign: " + targetPath);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        Process p = pb.start();
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("rcodesign sign failed with exit code " + exitCode);
        }
    }
}

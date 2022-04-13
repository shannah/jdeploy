package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.AppManifest;
import ca.weblite.tools.io.MD5;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;

/**
 * Generates an {@link ca.weblite.jdeploy.models.AppManifest} by crawling the files
 * in an app bundle.
 */
public class AppManifestGenerator {
    public AppManifest generateManifest(AppManifest appManifest, File appRoot, String identity, PrivateKey privateKey) throws IOException, SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        appRoot = appRoot.getCanonicalFile();
        appManifest.setAppRoot(appRoot);
        crawl(appManifest, appRoot);

        if (privateKey != null) {
            AppManifestSigner signer = new AppManifestSigner();
            signer.signManifest(appManifest, privateKey);
        }
        if (identity != null) {
            appManifest.setIdentity(identity);
        }
        return appManifest;
    }

    private void crawl(AppManifest appManifest, File directory) throws IOException {
        for (File child : directory.listFiles()) {
            if (child.getAbsolutePath().equals(appManifest.getManifestFile().getAbsolutePath())) {
                // We don't add the app manifest itself because it would
                // screw up the signature
                continue;
            }
            if (child.isDirectory()) {
                crawl(appManifest, child);
            } else if (child.isFile()) {
                String md5 = MD5.getMD5Checksum(child);
                appManifest.addFile(child, md5);
            }
        }
    }
}

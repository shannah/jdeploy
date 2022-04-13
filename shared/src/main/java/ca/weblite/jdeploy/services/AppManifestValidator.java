package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.AppManifest;
import ca.weblite.tools.io.FileUtil;
import ca.weblite.tools.io.MD5;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates the contents of an app package against an app manifest
 * to make sure none of the files have changed.
 *
 * By default this only fails if files are changed or removed.  It allows files to be added.
 * This is to try to prevent false positives caused by OS adding hidden files like .DS_Store
 *
 */
public class AppManifestValidator {
    private AppManifestSerializer serializer = new AppManifestSerializer();

    private boolean failIfFilesAdded;
    private boolean failIfFilesRemoved = true;
    private boolean failIfFilesChanged = true;
    private boolean validateSignature = false;

    /**
     * Validates an app manifest against the root directory to make sure
     * files haven't changed.
     * @param appManifest The appManifest to check
     * @return True if it validates (i.e. files haven't changed).  False otherwise
     * @throws IOException If there is a problem with IO.  This is not thrown on a mere validation failure.
     */
    public boolean validate(AppManifest appManifest, PublicKey pubKey) throws IOException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        return validateNoFilesAdded(appManifest) &&
                validateNoFilesChanged(appManifest) && verifyManifestSignature(appManifest, pubKey);

    }

    private boolean validateNoFilesChanged(AppManifest appManifest) throws IOException {
        if (!failIfFilesChanged && !failIfFilesRemoved) {
            // If the failIfFilesChanged flag isn't set,then this always returns true
            return true;
        }
        for (AppManifest.FileDescription fileDescription : appManifest.getFiles()) {
            File file = fileDescription.toFile();
            if (!file.exists()) {
                if (failIfFilesRemoved) {
                    return false;
                } else {
                    continue;
                }
            }
            String md5 = MD5.getMD5Checksum(file);
            if (failIfFilesChanged && !md5.equals(fileDescription.getMD5())) {
                return false;
            }
        }
        return true;
    }

    private boolean validateNoFilesAdded(AppManifest appManifest) throws IOException {
        if (!failIfFilesAdded) {
            return true;
        }
        Set<String> filesInManifest = new HashSet<String>();
        for (AppManifest.FileDescription fileDescription : appManifest.getFiles()) {
            filesInManifest.add(fileDescription.getFile());
        }
        return validateNoFilesAdded(filesInManifest, appManifest.getAppRoot(), appManifest.getAppRoot());
    }

    private boolean validateNoFilesAdded(Set<String> filesInManifest, File appRoot, File directory) throws IOException {
        if (!failIfFilesAdded) {
            return true;
        }
        for (File child : directory.listFiles()) {
            if (child.isDirectory()) {
                boolean result = validateNoFilesAdded(filesInManifest, appRoot, child);
                if (!result) return false;
            } else if (child.isFile()) {
                if (!filesInManifest.contains(FileUtil.getRelativePath(appRoot, child))) {
                    return false;
                }
            }
        }

        return true;
    }

    public boolean verifyManifestSignature(AppManifest appManifest, PublicKey pubKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, UnsupportedEncodingException {

        if (appManifest.getSignature() == null) throw new IllegalStateException("AppManifest has no signature");
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(pubKey);
        sig.update(serializer.serializeManifest(appManifest).getBytes("UTF-8"));
        return sig.verify(appManifest.getSignature());
    }


    public boolean isFailIfFilesAdded() {
        return failIfFilesAdded;
    }

    public void setFailIfFilesAdded(boolean failIfFilesAdded) {
        this.failIfFilesAdded = failIfFilesAdded;
    }

    public boolean isFailIfFilesRemoved() {
        return failIfFilesRemoved;
    }

    public void setFailIfFilesRemoved(boolean failIfFilesRemoved) {
        this.failIfFilesRemoved = failIfFilesRemoved;
    }

    public boolean isFailIfFilesChanged() {
        return failIfFilesChanged;
    }

    public void setFailIfFilesChanged(boolean failIfFilesChanged) {
        this.failIfFilesChanged = failIfFilesChanged;
    }
}

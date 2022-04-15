package ca.weblite.jdeploy.models;

import ca.weblite.jdeploy.data.VerificationStatus;

import java.util.ArrayList;
import java.util.List;

public class NPMApplication {
    public static final String DEFAULT_NPM_REGISTRY = "https://registry.npmjs.org";
    private String npmRegistryUrl = DEFAULT_NPM_REGISTRY;
    private String packageName;
    private String packageVersion;
    private String timeStampString;
    private String appSignature;
    private String versionSignature;
    private String developerSignature;
    private String developerPublicKey;
    private VerificationStatus homepageVerificationStatus = VerificationStatus.UNKNOWN;
    private String homepage;

    private List<AppSignature> signatures = new ArrayList<AppSignature>();

    public String getNpmRegistryUrl() {
        return npmRegistryUrl;
    }

    public void setNpmRegistryUrl(String npmRegistryUrl) {
        this.npmRegistryUrl = npmRegistryUrl;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getPackageVersion() {
        return packageVersion;
    }

    public void setPackageVersion(String packageVersion) {
        this.packageVersion = packageVersion;
    }

    public String getTimeStampString() {
        return timeStampString;
    }

    public void setTimeStampString(String timeStampString) {
        this.timeStampString = timeStampString;
    }

    public String getAppSignature() {
        return appSignature;
    }

    public void setAppSignature(String appSignature) {
        this.appSignature = appSignature;
    }

    public String getVersionSignature() {
        return versionSignature;
    }

    public void setVersionSignature(String versionSignature) {
        this.versionSignature = versionSignature;
    }

    public String getDeveloperSignature() {
        return developerSignature;
    }

    public void setDeveloperSignature(String developerSignature) {
        this.developerSignature = developerSignature;
    }

    public String getDeveloperPublicKey() {
        return developerPublicKey;
    }

    public void setDeveloperPublicKey(String developerPublicKey) {
        this.developerPublicKey = developerPublicKey;
    }

    public List<AppSignature> getSignatures() {
        return signatures;
    }

    public void setSignatures(List<AppSignature> signatures) {
        this.signatures = signatures;
    }

    public boolean isHomepageVerified() {
        return homepageVerificationStatus == VerificationStatus.SUCCEEDED;
    }

    public void setHomepageVerified(boolean homepageVerified) {
        homepageVerificationStatus = homepageVerified ? VerificationStatus.SUCCEEDED : VerificationStatus.FAILED;
    }

    public VerificationStatus getHomepageVerificationStatus() {
        return homepageVerificationStatus;
    }

    public String getHomepage() {
        return homepage;
    }

    public void setHomepage(String homepage) {
        this.homepage = homepage;
    }

    private class AppSignature {
        DeveloperIdentity developerIdentity;
        byte[] signature;

        AppSignature(DeveloperIdentity identity, byte[] signature) {
            this.developerIdentity = identity;
            this.signature = signature;
        }
    }

    public void addSignature(DeveloperIdentity identity, byte[] signature) {
        signatures.add(new AppSignature(identity, signature));
    }

    public byte[] getSignature(DeveloperIdentity identity) {
        for (AppSignature id : signatures) {
            if (id.developerIdentity.getIdentityUrl().equals(identity.getIdentityUrl())) {
                return id.signature;
            }
        }
        return null;
    }

    public DeveloperIdentities getDeveloperIdentities() {
        DeveloperIdentities out = new DeveloperIdentities();
        for (AppSignature sig : signatures) {
            out.add(sig.developerIdentity);
        }
        return out;
    }

    public boolean containsAnySignature(String text) {
        // Should only check for appSignature and versionSignature because they
        // are verified by npm.  The developer signature could potentially just be added
        // to match an official webpage.
        return _containsAnySignature(text, appSignature, versionSignature);
    }

    private static boolean containsSignature(String text, String sig) {
        return sig != null && !sig.isEmpty() && text.contains(sig);
    }

    private static boolean _containsAnySignature(String text, String... signatures) {
        if (signatures.length == 0) return false;
        for (String sig :signatures) {
            if (containsSignature(text, sig)) return true;
        }
        return false;
    }



}

package ca.weblite.jdeploy.models;

import java.util.ArrayList;
import java.util.List;

public class NPMApplication {
    private String npmRegistryUrl;
    private String packageName;
    private String packageVersion;
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


}

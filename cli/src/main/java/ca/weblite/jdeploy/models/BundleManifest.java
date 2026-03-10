package ca.weblite.jdeploy.models;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Collection of bundle artifacts produced during publish-time bundling.
 * Provides helpers for querying artifacts and generating package.json entries.
 */
public class BundleManifest {

    private final List<BundleArtifact> artifacts;

    public BundleManifest(List<BundleArtifact> artifacts) {
        this.artifacts = new ArrayList<>(artifacts);
    }

    public List<BundleArtifact> getArtifacts() {
        return Collections.unmodifiableList(artifacts);
    }

    public List<BundleArtifact> getArtifactsForPlatform(String platform, String arch) {
        return artifacts.stream()
                .filter(a -> a.getPlatform().equals(platform) && a.getArch().equals(arch))
                .collect(Collectors.toList());
    }

    /**
     * Builds the jdeploy.bundles JSONObject for package.json.
     * Each key is {platform}-{arch}. Values contain url, sha256, and optional cli sub-object.
     */
    public JSONObject toPackageJsonBundles() {
        JSONObject bundles = new JSONObject();

        for (BundleArtifact artifact : artifacts) {
            String key = artifact.getPlatformKey();

            if (artifact.isCli()) {
                // Add as cli sub-object under the platform key
                JSONObject platformObj = bundles.optJSONObject(key);
                if (platformObj == null) {
                    platformObj = new JSONObject();
                    bundles.put(key, platformObj);
                }
                JSONObject cliObj = new JSONObject();
                cliObj.put("url", artifact.getUrl());
                cliObj.put("sha256", artifact.getSha256());
                platformObj.put("cli", cliObj);
            } else {
                // Add as main entry under the platform key
                JSONObject platformObj = bundles.optJSONObject(key);
                if (platformObj == null) {
                    platformObj = new JSONObject();
                    bundles.put(key, platformObj);
                }
                platformObj.put("url", artifact.getUrl());
                platformObj.put("sha256", artifact.getSha256());
            }
        }

        return bundles;
    }

    public boolean isEmpty() {
        return artifacts.isEmpty();
    }
}

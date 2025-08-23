package ca.weblite.jdeploy.downloadPage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class DownloadPageSettingsJsonReader {
    public void readJson(DownloadPageSettings settings, JSONObject jsonObject) {
        // Read enabled platforms from the JSON object
        if (settings != null && jsonObject != null) {
            if (jsonObject.has("platforms")) {
                JSONArray platformsArray = jsonObject.getJSONArray("platforms");
                Set<DownloadPageSettings.BundlePlatform> enabledPlatforms = new LinkedHashSet<>();

                for (int i = 0; i < platformsArray.length(); i++) {
                    String platformStr = platformsArray.optString(i, null);
                    if (platformStr != null) {
                        try {
                            DownloadPageSettings.BundlePlatform platform = DownloadPageSettings.BundlePlatform.fromString(platformStr);
                            enabledPlatforms.add(platform);
                        } catch (IllegalArgumentException e) {
                            // Ignore unknown platforms
                        }
                    }
                }
                settings.setEnabledPlatforms(enabledPlatforms);
            }
        }
    }
}

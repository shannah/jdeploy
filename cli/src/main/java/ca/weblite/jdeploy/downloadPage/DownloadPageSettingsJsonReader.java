package ca.weblite.jdeploy.downloadPage;

import org.json.JSONObject;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class DownloadPageSettingsJsonReader {
    public void readJson(DownloadPageSettings settings, JSONObject jsonObject) {
        // Read enabled platforms from the JSON object
        if (settings != null && jsonObject != null) {
            if (jsonObject.has("platforms")) {
                Object platformsObj = jsonObject.get("platforms");
                Set<DownloadPageSettings.BundlePlatform> enabledPlatforms = new LinkedHashSet<>();
                if (platformsObj instanceof Collection) {
                    Collection<?> platforms = (Collection<?>) platformsObj;
                    for (Object platform : platforms) {
                        if (platform instanceof String) {
                            enabledPlatforms.add(DownloadPageSettings.BundlePlatform.fromString((String)platform));
                        }
                    }
                }
                settings.setEnabledPlatforms(enabledPlatforms);
            }
        }
    }
}

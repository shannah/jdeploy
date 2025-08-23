package ca.weblite.jdeploy.downloadPage;

import org.json.JSONObject;

public class DownloadPageSettingsJsonWriter {
    public void write(DownloadPageSettings settings, JSONObject jsonObject) {
        // Write enabled platforms to the JSON object
        if (settings != null && jsonObject != null) {
            if (jsonObject.has("platforms")) {
                jsonObject.remove("platforms");
            }
            for (DownloadPageSettings.BundlePlatform platform : settings.getEnabledPlatforms()) {
                writePlatform(platform, jsonObject);
            }
        }
    }

    private void writePlatform(DownloadPageSettings.BundlePlatform platform, JSONObject jsonObject) {
        if (platform != null) {
            jsonObject.append("platforms", platform.getPlatformName());
        }
    }
}

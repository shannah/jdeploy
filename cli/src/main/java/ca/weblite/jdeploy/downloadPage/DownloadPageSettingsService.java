package ca.weblite.jdeploy.downloadPage;

import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;

@Singleton
public class DownloadPageSettingsService {

    private final DownloadPageSettingsJsonReader jsonReader;
    private final DownloadPageSettingsJsonWriter jsonWriter;
    @Inject
    public DownloadPageSettingsService(
            DownloadPageSettingsJsonReader jsonReader,
            DownloadPageSettingsJsonWriter jsonWriter
    ) {
        this.jsonReader = jsonReader;
         this.jsonWriter = jsonWriter;
    }

    public DownloadPageSettings read(File packageJsonFile) {
        // Read the package JSON file and return a DownloadPageSettings object.
        if (packageJsonFile == null || !packageJsonFile.exists()) {
            throw new IllegalArgumentException("packageJsonFile cannot be null or does not exist");
        }
        JSONObject packageJson = new JSONObject(packageJsonFile);
        return read(packageJson);
    }

    public DownloadPageSettings read(JSONObject packageJson) {
        // This method should parse the packageJson string and return a DownloadPageSettings object.
        // For now, we will return a new instance with default settings.
        DownloadPageSettings settings = new DownloadPageSettings();
        if (packageJson != null && packageJson.has("downloadPage")) {
            jsonReader.readJson(settings, packageJson.getJSONObject("downloadPage"));
        }
        return settings;
    }

    public void write(DownloadPageSettings settings, JSONObject packageJson) {
        // This method should convert the DownloadPageSettings object to a JSON object and update the packageJson.
        if (packageJson == null) {
            throw new IllegalArgumentException("packageJson cannot be null");
        }
        JSONObject downloadPageJson = packageJson.has("downloadPage")
            ? packageJson.getJSONObject("downloadPage")
                : null;

        if (downloadPageJson == null) {
            downloadPageJson = new JSONObject();
            packageJson.put("downloadPage", downloadPageJson);
        }

        jsonWriter.write(settings, downloadPageJson);

    }
}

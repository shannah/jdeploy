package ca.weblite.jdeploy.downloadPage;

import org.json.JSONObject;
import org.apache.commons.io.FileUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
        try {
            String content = FileUtils.readFileToString(packageJsonFile, StandardCharsets.UTF_8);
            JSONObject packageJson = new JSONObject(content);
            return read(packageJson);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + packageJsonFile.getPath(), e);
        }
    }

    public DownloadPageSettings read(JSONObject packageJson) {
        // This method should parse the packageJson string and return a DownloadPageSettings object.
        // For now, we will return a new instance with default settings.
        DownloadPageSettings settings = new DownloadPageSettings();
        JSONObject jdeploy = packageJson.has("jdeploy")
            ? packageJson.getJSONObject("jdeploy")
                : null;

        if (jdeploy == null) {
            return settings;
        }

        if (jdeploy.has("downloadPage")) {
            jsonReader.readJson(settings, jdeploy.getJSONObject("downloadPage"));
        }
        return settings;
    }

    public void write(DownloadPageSettings settings, JSONObject packageJson) {
        // This method should convert the DownloadPageSettings object to a JSON object and update the packageJson.
        if (packageJson == null) {
            throw new IllegalArgumentException("packageJson cannot be null");
        }

        JSONObject jdeploy = packageJson.has("jdeploy")
            ? packageJson.getJSONObject("jdeploy")
                : null;

        if (jdeploy == null) {
            throw new IllegalArgumentException("packageJson must have a jdeploy section");
        }

        JSONObject downloadPageJson = jdeploy.has("downloadPage")
            ? jdeploy.getJSONObject("downloadPage")
                : null;

        if (downloadPageJson == null) {
            downloadPageJson = new JSONObject();
            jdeploy.put("downloadPage", downloadPageJson);
        }

        jsonWriter.write(settings, downloadPageJson);

    }
}

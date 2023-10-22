package ca.weblite.jdeploy.helpers.filemergers;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class JSONFileMerger implements FileMerger {

    public void merge(File packageJson, File patch) throws Exception {
        JSONObject baseJson = readJsonFromFile(packageJson);
        JSONObject patchJson = readJsonFromFile(patch);

        JSONObject mergedJson = mergeRecursively(baseJson, patchJson);

        // Write the merged JSON back to the original file
        try (FileOutputStream fos = new FileOutputStream(packageJson)) {
            fos.write(mergedJson.toString(4).getBytes());  // Indented with 4 spaces
        }
    }

    @Override
    public boolean isApplicableTo(File base, File patch) {
        return base.getName().endsWith(".json") && patch.getName().equals(base.getName());
    }

    private JSONObject mergeRecursively(JSONObject baseJson, JSONObject patchJson) {
        for (String key : JSONObject.getNames(patchJson)) {
            if (baseJson.has(key) && baseJson.get(key) instanceof JSONObject && patchJson.get(key) instanceof JSONObject) {
                // If the key leads to another JSONObject in both baseJson and patchJson, merge those JSONObjects
                baseJson.put(key, mergeRecursively(baseJson.getJSONObject(key), patchJson.getJSONObject(key)));
            } else {
                // Otherwise, just replace the value in baseJson with the one from patchJson
                baseJson.put(key, patchJson.get(key));
            }
        }
        return baseJson;
    }

    private JSONObject readJsonFromFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return new JSONObject(new JSONTokener(fis));
        }
    }
}

package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.AppManifest;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

/**
 * Writes an {@link AppManifest} to a JSON file.
 */
public class AppManifestJSONWriter {
    public void writeManifest(AppManifest manifest, File outputFile) throws IOException {
        JSONObject json = new JSONObject();
        JSONObject files = new JSONObject();
        json.put("files", files);

        if (manifest.getSignature() != null) {
            json.put("signature", Base64.encodeBase64String(manifest.getSignature()));
        }
        if (manifest.getIdentity() != null) {
            json.put("identity", manifest.getIdentity());
        }

        for (AppManifest.FileDescription file : manifest.getFiles()) {
            JSONObject props = new JSONObject();
            props.put("md5", file.getMD5());
            files.put(file.getFile(), props);
        }

        FileUtils.writeStringToFile(outputFile, json.toString(2), "UTF-8");


    }
}

package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.AppManifest;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;

/**
 * Reads an {@link AppManifest} from a JSON file.
 */
public class AppManifestJSONReader {
    public AppManifest loadManifest(AppManifest manifest, File manifestFile) throws IOException {
        manifest.setAppRoot(manifestFile.getCanonicalFile().getParentFile());

        JSONObject manifestJson = new JSONObject(FileUtils.readFileToString(manifestFile, "UTF-8"));
        if (manifestJson.has("signature")) {
            String signatureString = manifestJson.getString("signature");
            if (signatureString != null && !signatureString.isEmpty()) {
                manifest.setSignature(Base64.getDecoder().decode(signatureString));
            }
        }
        if (manifestJson.has("identity")) {
            manifest.setIdentity(manifestJson.getString("identity"));
        }
        JSONObject files = manifestJson.getJSONObject("files");
        Iterator<String> keyIterator = files.keys();
        while (keyIterator.hasNext()) {
            String fileRelativePath = keyIterator.next();
            JSONObject props = files.getJSONObject(fileRelativePath);
            String md5 = props.getString("md5");
            manifest.addFile(fileRelativePath, md5);
        }

        return manifest;
    }
}

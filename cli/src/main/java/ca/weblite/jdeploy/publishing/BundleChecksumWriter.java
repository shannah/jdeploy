package ca.weblite.jdeploy.publishing;

import ca.weblite.jdeploy.models.BundleManifest;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;

/**
 * Writes bundle URLs and SHA-256 hashes into the publish copy of package.json.
 * Only modifies the publish copy, not the source package.json.
 */
@Singleton
public class BundleChecksumWriter {

    @Inject
    public BundleChecksumWriter() {
    }

    /**
     * Writes the bundles section into the given package.json file.
     *
     * @param publishPackageJson the publish copy of package.json
     * @param manifest           the bundle manifest with URLs set
     */
    public void writeBundles(File publishPackageJson, BundleManifest manifest) throws IOException {
        if (manifest == null || manifest.isEmpty()) {
            return;
        }

        String content = FileUtils.readFileToString(publishPackageJson, "UTF-8");
        JSONObject packageJson = new JSONObject(content);

        JSONObject jdeployObj = packageJson.optJSONObject("jdeploy");
        if (jdeployObj == null) {
            jdeployObj = new JSONObject();
            packageJson.put("jdeploy", jdeployObj);
        }

        jdeployObj.put("artifacts", manifest.toPackageJsonBundles());

        FileUtils.writeStringToFile(publishPackageJson, packageJson.toString(), "UTF-8");
    }
}

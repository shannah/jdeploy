package ca.weblite.jdeploy.installer.npm;

import ca.weblite.tools.io.IOUtil;
import ca.weblite.tools.io.URLUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.beans.Encoder;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class NPMRegistry {
    private static final String REGISTRY_URL="https://registry.npmjs.org/";

    public NPMPackage loadPackage(String packageName) throws IOException {
        try (InputStream inputStream = URLUtil.openStream(
                new URL(REGISTRY_URL+ URLEncoder.encode(packageName, "UTF-8")))) {
            String str = IOUtil.readToString(inputStream);
            return new NPMPackage(new JSONObject(str));
        }
    }

}

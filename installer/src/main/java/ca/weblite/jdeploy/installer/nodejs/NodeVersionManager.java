package ca.weblite.jdeploy.installer.nodejs;

import ca.weblite.tools.io.IOUtil;
import ca.weblite.tools.io.URLUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class NodeVersionManager implements NodeConstants {
    JSONArray nodeVersionsJSON;

    public void load() throws IOException {
        try (InputStream input = URLUtil.openStream(getIndexURL())) {
            nodeVersionsJSON = new JSONArray(IOUtil.readToString(input));
        }
    }



    private URL getIndexURL() {
        try {
            return new URL(INDEX_URL);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

}

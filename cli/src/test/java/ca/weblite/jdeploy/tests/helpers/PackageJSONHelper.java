package ca.weblite.jdeploy.tests.helpers;

import org.json.JSONArray;
import org.json.JSONObject;

public class PackageJSONHelper {
    public static JSONObject createMockPackageJSON() {
        JSONObject out = new JSONObject();
        out.put("name", "test-app");
        out.put("version", "1.0");
        out.put("homepage", "https://www.example.com");

        JSONObject jdeploy = new JSONObject();
        out.put("jdeploy", jdeploy);

        jdeploy.put("javaVersion", "11");
        jdeploy.put("javafx", true);

        JSONArray identities = new JSONArray();
        jdeploy.put("identities", identities);
        identities.put("https://example.com/id.json");

        return out;
    }
}

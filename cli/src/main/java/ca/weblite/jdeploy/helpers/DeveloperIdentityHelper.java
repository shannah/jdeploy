package ca.weblite.jdeploy.helpers;

import ca.weblite.jdeploy.models.DeveloperIdentity;
import org.json.JSONArray;
import org.json.JSONObject;

public class DeveloperIdentityHelper {

    public static DeveloperIdentity createFromPackageJSON(JSONObject packageJSON) {
        DeveloperIdentity id = new DeveloperIdentity();
        if (packageJSON.has("name")) {
            id.setName(packageJSON.getString("name"));
        } else {
            id.setName("jdeploy");
        }

        JSONObject jdeploy = packageJSON.has("jdeploy") ? packageJSON.getJSONObject("jdeploy") : new JSONObject();

        if (jdeploy.has("identities")) {
            JSONArray identitiesJson = jdeploy.getJSONArray("identities");
            int len = identitiesJson.length();
            for (int i=0; i<len; i++) {
                if (id.getIdentityUrl() == null) {
                    id.setIdentityUrl(identitiesJson.getString(i));
                } else {
                    id.addAliasUrl(identitiesJson.getString(i));
                }
            }
        } else {
            if (packageJSON.has("homepage")) {

                String homepage = packageJSON.getString("homepage");
                String origHomepage = homepage;
                if (homepage.contains("#")) {
                    homepage = homepage.substring(0, homepage.indexOf("#"));
                }
                if (homepage.contains("?")) {
                    homepage = homepage.substring(0, homepage.indexOf("?"));
                }
                if (homepage.lastIndexOf("/") > 8) {
                    homepage = homepage.substring(0, homepage.lastIndexOf("/"));
                }

                if (homepage.startsWith("https://")) {
                    id.setIdentityUrl(homepage + "/.jdeploy/id.json");
                    id.addAliasUrl(origHomepage);
                }
            }
        }

        return id;

    }
}

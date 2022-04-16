package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.DeveloperIdentity;
import ca.weblite.jdeploy.models.NPMApplication;
import ca.weblite.tools.io.IOUtil;
import ca.weblite.tools.io.URLUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;

public class NPMApplicationLoader {
    private DeveloperIdentityURLLoader identityLoader = new DeveloperIdentityURLLoader();
    public void load(NPMApplication app, String registryUrl, String packageName, String packageVersion) throws IOException {
        String url = registryUrl;
        if (!url.endsWith("/")) url = url + "/";
        url += packageName;
        JSONObject json = null;
        try (InputStream input = URLUtil.openStream(new URL(url))) {
            String jsonString = IOUtil.readToString(input);
            json = new JSONObject(jsonString);
        }

        app.setPackageName(packageName);
        app.setNpmRegistryUrl(registryUrl);
        app.setPackageVersion(packageVersion);

        JSONObject versionsJson = json.getJSONObject("versions");
        JSONObject versionJson = versionsJson.getJSONObject(packageVersion);
        JSONObject jdeployJson = versionJson.getJSONObject("jdeploy");
        JSONObject signatures = jdeployJson.has("signatures") ? jdeployJson.getJSONObject("signatures") : new JSONObject();



        if (jdeployJson.has("identities")) {
            JSONArray identitiesJson = jdeployJson.getJSONArray("identities");
            int len = identitiesJson.length();
            for (int i=0; i<len; i++) {
                Object identity = identitiesJson.get(i);
                if ((identity instanceof String) && ((String)identity).startsWith("https://")) {
                    String identityUrl = (String)identity;
                    DeveloperIdentity developerIdentity = new DeveloperIdentity();
                    try {
                        identityLoader.loadIdentityFromURL(developerIdentity, (String) identity);
                    } catch (Exception ex) {
                        System.err.println("Failed to load identity from "+(String)identity);
                        ex.printStackTrace();
                        continue;
                    }

                    if (signatures.has(identityUrl)) {
                        app.addSignature(developerIdentity, Base64.getDecoder().decode(signatures.getString(identityUrl)));
                    }

                }
            }
        }

    }

    public DeveloperIdentityURLLoader getIdentityLoader() {
        return identityLoader;
    }

    public void setIdentityLoader(DeveloperIdentityURLLoader identityLoader) {
        this.identityLoader = identityLoader;
    }
}

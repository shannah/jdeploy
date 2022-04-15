package ca.weblite.jdeploy.helpers;

import ca.weblite.jdeploy.models.NPMApplication;

import org.json.JSONObject;

import java.util.Date;

public class NPMApplicationHelper {
    public static NPMApplication createFromPackageJSON(JSONObject packageJSON) {
        NPMApplication out = new NPMApplication();
        out.setPackageName(packageJSON.getString("name"));
        out.setPackageVersion(packageJSON.getString("version"));
        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
        String timestamp = jdeploy.has("timestamp") ? jdeploy.getString("timestamp") : new Date().getTime()+"";
        out.setTimeStampString(timestamp);

        if (jdeploy.has("appSignature")) {
            out.setAppSignature(jdeploy.getString("appSignature"));
        }
        if (jdeploy.has("versionSignature")) {
            out.setVersionSignature(jdeploy.getString("versionSignature"));
        }
        if (jdeploy.has("developerSignature")) {
            out.setDeveloperSignature(jdeploy.getString("developerSignature"));
        }
        if (jdeploy.has("developerPublicKey")) {
            out.setDeveloperPublicKey(jdeploy.getString("developerPublicKey"));
        }
        if (packageJSON.has("homepage")) {
            out.setHomepage(packageJSON.getString("homepage"));
        }

        return out;
    }

    public static void updatePackageJSONSignatures(NPMApplication app, JSONObject packageJSON) {
        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
        jdeploy.put("appSignature", app.getAppSignature());
        jdeploy.put("developerSignature", app.getDeveloperSignature());
        jdeploy.put("versionSignature", app.getVersionSignature());
        jdeploy.put("developerPublicKey", app.getDeveloperPublicKey());
        jdeploy.put("timestamp", app.getTimeStampString());

    }


}

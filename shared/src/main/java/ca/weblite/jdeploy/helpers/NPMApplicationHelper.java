package ca.weblite.jdeploy.helpers;

import ca.weblite.jdeploy.models.NPMApplication;

import ca.weblite.tools.security.CertificateUtil;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

public class NPMApplicationHelper {
    public static NPMApplication createFromPackageJSON(JSONObject packageJSON) {
        NPMApplication out = new NPMApplication();
        out.setPackageName(packageJSON.getString("name"));
        out.setPackageVersion(packageJSON.getString("version"));
        if (packageJSON.has("source")) {
            out.setSource(packageJSON.getString("source"));
        }
        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");

        if (packageJSON.has("homepage")) {
            out.setHomepage(packageJSON.getString("homepage"));
        }

        return out;
    }

    public static String getApplicationSha256Hash(NPMApplication app) throws NoSuchAlgorithmException {
        return CertificateUtil.getSHA256String(getApplicationKey(app).getBytes(StandardCharsets.UTF_8));
    }

    public static String getVersionSha256Hash(NPMApplication app) throws NoSuchAlgorithmException {
        return CertificateUtil.getSHA256String(getVersionKey(app).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gets a unique key for the application.
     * @param app
     * @return
     */
    public static String getApplicationKey(NPMApplication app) {
        StringBuilder sb = new StringBuilder();
        sb.append(app.getNpmRegistryUrl()).append("\n").append(app.getPackageName()).append("\n").append(app.getHomepage());
        return sb.toString();
    }

    public static String getVersionKey(NPMApplication app) {
        return getApplicationKey(app)+"\n" + app.getPackageVersion();
    }






}

package ca.weblite.jdeploy.installer.npm;

import ca.weblite.jdeploy.helpers.NPMApplicationHelper;
import ca.weblite.jdeploy.models.DocumentTypeAssociation;
import ca.weblite.jdeploy.models.NPMApplication;
import ca.weblite.jdeploy.app.permissions.PermissionRequest;
import ca.weblite.jdeploy.app.permissions.PermissionRequestService;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class NPMPackageVersion {
    private static final String DEFAULT_JAVA_VERSION = "11";
    private final NPMPackage npmPackage;
    private final String version;
    private final JSONObject packageJson;

    NPMPackageVersion(NPMPackage pkg, String version, JSONObject packageJson) {
        this.npmPackage = pkg;
        this.version = version;
        this.packageJson = packageJson;
    }


    public NPMApplication toNPMApplication() {
        return NPMApplicationHelper.createFromPackageJSON(packageJson);
    }

    public NPMPackage getNpmPackage() {
        return npmPackage;
    }

    public String getDescription() {
        return packageJson.getString("description");
    }

    public String getVersion() {
        return version;
    }

    public String getJavaVersion() {
        if (jdeploy().has("javaVersion")) {
            return jdeploy().getString("javaVersion");
        }
        return DEFAULT_JAVA_VERSION;
    }

    private JSONObject jdeploy() {
        return packageJson.getJSONObject("jdeploy");
    }

    public Iterable<DocumentTypeAssociation> getDocumentTypeAssociations() {
        ArrayList<DocumentTypeAssociation> out = new ArrayList<>();
        if (jdeploy().has("documentTypes")) {
            JSONArray documentTypes = jdeploy().getJSONArray("documentTypes");
            int len = documentTypes.length();
            for (int i=0; i<len; i++) {
                JSONObject docType = documentTypes.getJSONObject(i);
                String ext = docType.has("extension") ? docType.getString("extension") : null;
                if (ext == null) continue;
                String mimetype = docType.has("mimetype") ? docType.getString("mimetype") : null;
                if (mimetype == null) continue;
                String icon = docType.has("icon") ? docType.getString("icon") : null;
                boolean editor = docType.has("editor") && docType.getBoolean("editor");
                DocumentTypeAssociation docTypeObj = new DocumentTypeAssociation(
                        ext,
                        mimetype,
                        icon,
                        editor
                );
                out.add(docTypeObj);
            }
        }
        return out;
    }

    public Iterable<String> getUrlSchemes() {
        ArrayList<String> out = new ArrayList<>();
        if (jdeploy().has("urlSchemes")) {
            JSONArray schemes = jdeploy().getJSONArray("urlSchemes");
            int len = schemes.length();
            for (int i=0; i<len; i++) {
                String scheme = schemes.getString(i);
                out.add(scheme);
            }
        }
        return out;
    }

    public String getInstallerTheme() {
        if (jdeploy().has("installerTheme")) {
            return jdeploy().getString("installerTheme");
        }
        return null;
    }

    public Map<PermissionRequest, String> getPermissionRequests() {
        return new PermissionRequestService().getPermissionRequests(packageJson);
    }

    public String getMainClass() {
        if (jdeploy().has("mainClass")) {
            return jdeploy().getString("mainClass");
        }
        return null;
    }

    public String getWmClassName() {
        if (jdeploy().has("linux") && jdeploy().getJSONObject("linux").has("wmClassName")) {
            return jdeploy().getJSONObject("linux").getString("wmClassName");
        }
        return null;
    }
}

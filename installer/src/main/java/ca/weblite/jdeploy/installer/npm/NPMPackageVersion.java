package ca.weblite.jdeploy.installer.npm;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class NPMPackageVersion {
    private final NPMPackage npmPackage;
    private final String version;
    private final JSONObject packageJson;

    NPMPackageVersion(NPMPackage pkg, String version, JSONObject packageJson) {
        this.npmPackage = pkg;
        this.version = version;
        this.packageJson = packageJson;
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

    public class DocumentTypeAssociation {
        private String extension, mimetype, iconPath;
        private boolean editor;

        private DocumentTypeAssociation(String extension, String mimetype, String iconPath, boolean editor) {
            this.extension = extension;
            this.mimetype = mimetype;
            this.iconPath = iconPath;

        }

        public String getExtension() {
            return extension;
        }

        public String getMimetype() {
            return mimetype;
        }

        public String getIconPath() {
            return iconPath;
        }

        public boolean isEditor() {
            return editor;
        }
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





}

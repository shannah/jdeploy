package ca.weblite.jdeploy.helpers;

import ca.weblite.jdeploy.models.DocumentTypeAssociation;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class FileAssociationsHelper {



    public static Iterable<DocumentTypeAssociation> getDocumentTypeAssociationsFromPackageJSON(JSONObject packageJSON) {
        ArrayList<DocumentTypeAssociation> out = new ArrayList<>();
        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
        if (jdeploy.has("documentTypes")) {
            JSONArray documentTypes = jdeploy.getJSONArray("documentTypes");
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
}

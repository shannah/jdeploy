package ca.weblite.jdeploy.models;

public class DocumentTypeAssociation {
    private String extension, mimetype, iconPath;
    private boolean editor;

    public DocumentTypeAssociation(String extension, String mimetype, String iconPath, boolean editor) {
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

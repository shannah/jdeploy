package ca.weblite.jdeploy.models;

public class DocumentTypeAssociation {
    private String extension, mimetype, iconPath;
    private boolean editor;

    // Directory support fields
    private boolean isDirectory;
    private String role;
    private String description;

    /**
     * Constructor for file extension associations (existing behavior).
     */
    public DocumentTypeAssociation(String extension, String mimetype, String iconPath, boolean editor) {
        this.extension = extension;
        this.mimetype = mimetype;
        this.iconPath = iconPath;
        this.editor = editor;
        this.isDirectory = false;
    }

    /**
     * Constructor for directory associations.
     * @param role "Editor" or "Viewer"
     * @param description Human-readable description for context menus
     * @param iconPath Optional path to custom icon
     */
    public DocumentTypeAssociation(String role, String description, String iconPath) {
        this.isDirectory = true;
        this.role = role;
        this.description = description;
        this.iconPath = iconPath;
        this.editor = "Editor".equalsIgnoreCase(role);
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

    /**
     * @return true if this is a directory association, false if it's a file extension association
     */
    public boolean isDirectory() {
        return isDirectory;
    }

    /**
     * @return the role for directory associations ("Editor" or "Viewer"), or inferred from editor flag for files
     */
    public String getRole() {
        if (isDirectory) {
            return role != null ? role : "Viewer";
        }
        return editor ? "Editor" : "Viewer";
    }

    /**
     * @return human-readable description for directory associations
     */
    public String getDescription() {
        return description;
    }

    /**
     * Validates that this association has the required fields for its type.
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        if (isDirectory) {
            return role != null && !role.isEmpty();
        } else {
            return extension != null && mimetype != null;
        }
    }
}

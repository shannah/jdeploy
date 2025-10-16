# Directory Association Support Implementation Plan

## Overview
This document outlines the implementation plan for adding directory association support to jDeploy. Currently, jDeploy only supports file extension associations through the `documentTypes` configuration. This enhancement will allow applications to register as handlers for directories, enabling features like:
- Drag-and-drop directories onto application icons
- Right-click context menu "Open with..." on directories (Windows/Linux)
- Opening directories as projects/workspaces in the application

## Current State Analysis

### Existing File Association Support
**Configuration** (`package.json`):
```json
"jdeploy": {
  "documentTypes": [
    {
      "extension": "txt",
      "mimetype": "text/plain",
      "editor": true,
      "icon": "path/to/icon"
    }
  ]
}
```

**Model**: `DocumentTypeAssociation.java` (shared module)
- Fields: `extension`, `mimetype`, `iconPath`, `editor`
- Designed specifically for file extensions

**Windows Registry Implementation**: `InstallWindowsRegistry.java` (installer module)
- Registers under `HKEY_CURRENT_USER\Software\Classes\.{extension}`
- Uses `OpenWithProgIds` for file associations
- Command pattern: `"exe_path" "%1"`

**macOS Info.plist Implementation**: `MacBundler.java` (shared module)
- Uses `CFBundleDocumentTypes` with `CFBundleTypeExtensions`
- Standard file extension handling only

### Limitations
1. `DocumentTypeAssociation` model only has `extension` field (no directory concept)
2. Windows registry code only handles `Software\Classes\.{extension}` pattern
3. macOS bundler only uses file-based `CFBundleDocumentTypes`
4. No code paths exist for directory handling

## Proposed Solution

### Design Principles
1. **Backward Compatible**: Existing file associations continue to work unchanged
2. **Explicit Configuration**: Directories must be explicitly requested (not automatic)
3. **Platform Consistency**: Behavior should be similar across Windows, macOS, and Linux
4. **Minimal API Surface**: Simple, clear configuration schema

### Configuration Schema Extension

Add support for directory associations in `package.json`:

```json
"jdeploy": {
  "documentTypes": [
    {
      "extension": "txt",
      "mimetype": "text/plain",
      "editor": true
    },
    {
      "type": "directory",
      "role": "Editor",
      "description": "Open folder as project"
    }
  ]
}
```

**Field Descriptions**:
- `type`: `"directory"` indicates this is a directory association (mutually exclusive with `extension`)
- `role`: Optional. "Editor" or "Viewer" (default: "Viewer")
- `description`: Optional. Human-readable description for context menus
- `icon`: Optional. Custom icon path for directory associations

## Implementation Phases

## Phase 1: Model and Schema Updates (Priority: High)

### 1.1 Update DocumentTypeAssociation Model
**Location**: `/shared/src/main/java/ca/weblite/jdeploy/models/DocumentTypeAssociation.java`

**Changes Required**:
```java
public class DocumentTypeAssociation {
    private String extension;        // Existing - for file extensions
    private String mimetype;         // Existing
    private String iconPath;         // Existing
    private boolean editor;          // Existing

    // NEW: Directory support
    private boolean isDirectory;     // True if this is a directory association
    private String role;             // "Editor" or "Viewer"
    private String description;      // Human-readable description

    // Existing constructor
    public DocumentTypeAssociation(String extension, String mimetype, String iconPath, boolean editor) {
        this.extension = extension;
        this.mimetype = mimetype;
        this.iconPath = iconPath;
        this.editor = editor;
        this.isDirectory = false;
    }

    // NEW: Directory constructor
    public DocumentTypeAssociation(String role, String description, String iconPath) {
        this.isDirectory = true;
        this.role = role;
        this.description = description;
        this.iconPath = iconPath;
        this.editor = "Editor".equalsIgnoreCase(role);
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public String getRole() {
        return role != null ? role : "Viewer";
    }

    public String getDescription() {
        return description;
    }

    // Validation
    public boolean isValid() {
        if (isDirectory) {
            return role != null && !role.isEmpty();
        } else {
            return extension != null && mimetype != null;
        }
    }
}
```

**Testing**:
- Unit tests for both constructors
- Validation logic tests
- Backward compatibility tests

### 1.2 Update FileAssociationsHelper Parser
**Location**: `/shared/src/main/java/ca/weblite/jdeploy/helpers/FileAssociationsHelper.java`

**Current Code** (lines 13-37):
```java
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
```

**Updated Code**:
```java
public static Iterable<DocumentTypeAssociation> getDocumentTypeAssociationsFromPackageJSON(JSONObject packageJSON) {
    ArrayList<DocumentTypeAssociation> out = new ArrayList<>();
    JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
    if (jdeploy.has("documentTypes")) {
        JSONArray documentTypes = jdeploy.getJSONArray("documentTypes");
        int len = documentTypes.length();
        for (int i=0; i<len; i++) {
            JSONObject docType = documentTypes.getJSONObject(i);

            // Check if this is a directory association
            boolean isDirectory = docType.has("type") &&
                                 "directory".equalsIgnoreCase(docType.getString("type"));

            DocumentTypeAssociation docTypeObj;

            if (isDirectory) {
                // Directory association
                String role = docType.has("role") ? docType.getString("role") : "Viewer";
                String description = docType.has("description") ? docType.getString("description") : null;
                String icon = docType.has("icon") ? docType.getString("icon") : null;
                docTypeObj = new DocumentTypeAssociation(role, description, icon);
            } else {
                // File extension association (existing behavior)
                String ext = docType.has("extension") ? docType.getString("extension") : null;
                if (ext == null) continue;
                String mimetype = docType.has("mimetype") ? docType.getString("mimetype") : null;
                if (mimetype == null) continue;
                String icon = docType.has("icon") ? docType.getString("icon") : null;
                boolean editor = docType.has("editor") && docType.getBoolean("editor");
                docTypeObj = new DocumentTypeAssociation(ext, mimetype, icon, editor);
            }

            if (docTypeObj.isValid()) {
                out.add(docTypeObj);
            }
        }
    }
    return out;
}
```

**Testing**:
- Test parsing file associations (existing behavior)
- Test parsing directory associations (new)
- Test mixed configurations
- Test invalid configurations

### 1.3 Update AppInfo Model
**Location**: `/shared/src/main/java/ca/weblite/jdeploy/app/AppInfo.java`

**Changes Required**:
- Add `hasDirectoryAssociations()` method
- Add `getDirectoryAssociations()` method to filter directory-only associations

```java
public boolean hasDirectoryAssociations() {
    if (documentMimetypes == null) return false;
    // Check if any DocumentTypeAssociation in the collection is a directory
    // This would require storing DocumentTypeAssociation objects directly
    // OR storing a separate flag - needs design decision
    return false; // TODO: Implement based on storage approach
}
```

**Design Decision Required**:
Currently `AppInfo` stores separate maps for extensions, mimetypes, and icons. For directory support, we have two options:

**Option A**: Keep parallel storage (current approach)
- Add separate fields: `hasDirectoryAssociation`, `directoryRole`, `directoryDescription`, `directoryIcon`
- Pro: Minimal changes to existing code
- Con: Assumes only ONE directory association per app

**Option B**: Store DocumentTypeAssociation objects directly
- Replace the separate maps with `List<DocumentTypeAssociation>`
- Pro: More flexible, supports multiple directory types in future
- Con: Larger refactor, affects all existing code

**Recommendation**: Use **Option A** for MVP (simpler, faster), plan for Option B in future if multiple directory types are needed.

### 1.4 Update NPMPackageVersion Parser
**Location**: `/installer/src/main/java/ca/weblite/jdeploy/installer/npm/NPMPackageVersion.java`

**Current Code** (lines 55-78):
Already uses `FileAssociationsHelper` indirectly through `DocumentTypeAssociation` parsing.

**Changes Required**:
None directly - the helper update in 1.2 will automatically flow through. Just need to verify compatibility.

**Testing**:
- Integration test to ensure installer module picks up directory associations correctly

## Phase 2: Windows Implementation (Priority: High)

### 2.1 Update InstallWindowsRegistry for Directory Support
**Location**: `/installer/src/main/java/ca/weblite/jdeploy/installer/win/InstallWindowsRegistry.java`

**New Methods Required**:

```java
/**
 * Registers directory associations in the Windows registry.
 * Creates context menu entries for "Open with {AppName}" on directories.
 */
private void registerDirectoryAssociations() {
    if (!appInfo.hasDirectoryAssociations()) {
        return;
    }

    String progId = getProgId();
    String appTitle = appInfo.getTitle();

    // Register in HKEY_CURRENT_USER\Software\Classes\Directory\shell
    String directoryShellKey = "Software\\Classes\\Directory\\shell\\" + progId;

    if (!registryKeyExists(HKEY_CURRENT_USER, directoryShellKey)) {
        registryCreateKey(HKEY_CURRENT_USER, directoryShellKey);
    }

    // Set the display name for the context menu
    String menuText = "Open with " + appTitle;
    registrySetStringValue(HKEY_CURRENT_USER, directoryShellKey, null, menuText);

    // Set icon if available
    if (icon != null && icon.exists()) {
        registrySetStringValue(HKEY_CURRENT_USER, directoryShellKey, "Icon",
            icon.getAbsolutePath() + ",0");
    }

    // Create command key
    String commandKey = directoryShellKey + "\\command";
    if (!registryKeyExists(HKEY_CURRENT_USER, commandKey)) {
        registryCreateKey(HKEY_CURRENT_USER, commandKey);
    }

    // Set command to launch app with directory path
    registrySetStringValue(HKEY_CURRENT_USER, commandKey, null,
        "\"" + exe.getAbsolutePath() + "\" \"%1\"");

    // Also register for Directory\Background\shell for "Open folder with..." in empty space
    String backgroundShellKey = "Software\\Classes\\Directory\\Background\\shell\\" + progId;
    if (!registryKeyExists(HKEY_CURRENT_USER, backgroundShellKey)) {
        registryCreateKey(HKEY_CURRENT_USER, backgroundShellKey);
    }
    registrySetStringValue(HKEY_CURRENT_USER, backgroundShellKey, null, menuText);
    if (icon != null && icon.exists()) {
        registrySetStringValue(HKEY_CURRENT_USER, backgroundShellKey, "Icon",
            icon.getAbsolutePath() + ",0");
    }

    String backgroundCommandKey = backgroundShellKey + "\\command";
    if (!registryKeyExists(HKEY_CURRENT_USER, backgroundCommandKey)) {
        registryCreateKey(HKEY_CURRENT_USER, backgroundCommandKey);
    }
    registrySetStringValue(HKEY_CURRENT_USER, backgroundCommandKey, null,
        "\"" + exe.getAbsolutePath() + "\" \"%V\"");
}

/**
 * Unregisters directory associations from Windows registry.
 */
private void unregisterDirectoryAssociations() {
    if (!appInfo.hasDirectoryAssociations()) {
        return;
    }

    String progId = getProgId();

    String directoryShellKey = "Software\\Classes\\Directory\\shell\\" + progId;
    if (registryKeyExists(HKEY_CURRENT_USER, directoryShellKey)) {
        deleteKeyRecursive(directoryShellKey);
    }

    String backgroundShellKey = "Software\\Classes\\Directory\\Background\\shell\\" + progId;
    if (registryKeyExists(HKEY_CURRENT_USER, backgroundShellKey)) {
        deleteKeyRecursive(backgroundShellKey);
    }
}
```

**Update Existing Methods**:

Update `register()` method (around line 649):
```java
public void register() throws IOException {
    // ... existing code ...

    if (appInfo.hasDocumentTypes() || appInfo.hasUrlSchemes()) {
        registerFileExtensions();
        registerFileTypeEntry();
    }

    if (appInfo.hasUrlSchemes()) {
        registerUrlSchemes();
    }

    // NEW: Register directory associations
    if (appInfo.hasDirectoryAssociations()) {
        registerDirectoryAssociations();
    }

    // ... rest of existing code ...
}
```

Update `unregister()` method (around line 574):
```java
public void unregister(File backupLogFile) throws IOException {
    if (appInfo.hasDocumentTypes()) {
        unregisterFileExtensions();
    }

    if (appInfo.hasUrlSchemes()) {
        unregisterUrlSchemes();
    }

    // NEW: Unregister directory associations
    if (appInfo.hasDirectoryAssociations()) {
        unregisterDirectoryAssociations();
    }

    deleteUninstallEntry();
    deleteRegistryKey();

    if (backupLogFile != null && backupLogFile.exists()) {
        new WinRegistry().regImport(backupLogFile);
    }
}
```

**Registry Keys Used**:
- `HKEY_CURRENT_USER\Software\Classes\Directory\shell\{ProgId}` - Right-click on folder
- `HKEY_CURRENT_USER\Software\Classes\Directory\Background\shell\{ProgId}` - Right-click in folder empty space

**Testing**:
- Test directory right-click context menu appears
- Test "Open with..." launches app with directory path
- Test uninstall removes registry entries
- Test with and without icon
- Test backup/restore functionality

## Phase 3: macOS Implementation (Priority: High)

### 3.1 Update MacBundler for Directory Support
**Location**: `/shared/src/main/java/com/joshondesign/appbundler/mac/MacBundler.java`

**Current Code** (around line 518):
```java
for(String ext : app.getExtensions()) {
    String role = "Viewer";
    if (app.isEditableExtension(ext)) {
        role = "Editor";
    }
    out.start("key").text("CFBundleDocumentTypes").end();
    out.start("array").start("dict");
        out.start("key").text("CFBundleTypeExtensions").end();
        out.start("array").start("string").text(ext).end().end();
        // ...
    out.end().end();
}
```

**New Code - Add Directory Handling**:
```java
// Existing file extensions loop
for(String ext : app.getExtensions()) {
    String role = "Viewer";
    if (app.isEditableExtension(ext)) {
        role = "Editor";
    }
    out.start("key").text("CFBundleDocumentTypes").end();
    out.start("array").start("dict");
        out.start("key").text("CFBundleTypeExtensions").end();
        out.start("array").start("string").text(ext).end().end();
        out.start("key").text("CFBundleTypeName").end();
        out.start("string").text(ext).end();
        out.start("key").text("CFBundleTypeMIMETypes").end();
        out.start("array").start("string").text(app.getExtensionMimetype(ext)).end().end();
        out.start("key").text("CFBundleTypeRole").end();
        out.start("string").text(role).end();
        String icon = app.getExtensionIcon(ext);
        if (icon != null) {
            out.start("key").text("CFBundleTypeIconFile").end();
            out.start("string").text(icon).end();
        }
    out.end().end();
}

// NEW: Directory associations
if (app.hasDirectoryAssociations()) {
    String role = app.getDirectoryRole(); // "Editor" or "Viewer"

    out.start("key").text("CFBundleDocumentTypes").end();
    out.start("array").start("dict");
        // Use LSItemContentTypes for folder handling
        out.start("key").text("LSItemContentTypes").end();
        out.start("array");
            out.start("string").text("public.folder").end();
        out.end();

        out.start("key").text("CFBundleTypeName").end();
        out.start("string").text("Folder").end();

        out.start("key").text("CFBundleTypeRole").end();
        out.start("string").text(role).end();

        // Optional: Custom icon for folders
        String dirIcon = app.getDirectoryIcon();
        if (dirIcon != null) {
            out.start("key").text("CFBundleTypeIconFile").end();
            out.start("string").text(dirIcon).end();
        }
    out.end().end();
}
```

**App Interface Updates Required**:
The `App` interface/class needs these new methods:
```java
boolean hasDirectoryAssociations();
String getDirectoryRole();
String getDirectoryIcon();
```

**Info.plist Result**:
```xml
<key>CFBundleDocumentTypes</key>
<array>
    <dict>
        <key>LSItemContentTypes</key>
        <array>
            <string>public.folder</string>
        </array>
        <key>CFBundleTypeName</key>
        <string>Folder</string>
        <key>CFBundleTypeRole</key>
        <string>Editor</string>
    </dict>
</array>
```

**Testing**:
- Test drag-and-drop folder onto app icon
- Test right-click "Open With" on folders
- Test both Editor and Viewer roles
- Test with custom folder icon

### 3.2 Update App Interface
**Location**: Find the `App` interface used by MacBundler

Need to:
1. Add directory association methods to interface
2. Implement in concrete App class
3. Wire through from AppInfo

## Phase 4: Linux Implementation (Priority: Medium)

### 4.1 Update Linux Desktop File Generation

**Location**: Search for `.desktop` file generation code

**Desktop Entry Specification**:
Linux uses `.desktop` files for application associations. For directories:

```desktop
[Desktop Entry]
Name=MyApp
Exec=myapp %U
Type=Application
MimeType=inode/directory;
Categories=Development;
```

**Implementation**:
- Add `inode/directory` to MimeType field when directory associations are configured
- Ensure desktop file is installed to appropriate location
- Run `update-desktop-database` after installation (if available)

**Testing**:
- Test on Ubuntu/Debian with GNOME
- Test on Fedora with GNOME
- Test on KDE Plasma
- Verify right-click "Open With" shows application

## Phase 5: GUI Updates (Priority: Medium)

### Overview
Add UI controls to JDeployProjectEditor for configuring directory associations alongside existing file associations. The UI should be intuitive, consistent with existing patterns, and provide appropriate validation.

### 5.1 Add DirectoryAssociationFields Inner Class

**Location**: `/cli/src/main/java/ca/weblite/jdeploy/gui/JDeployProjectEditor.java`

**After line 277** (after `DoctypeFields` class):

```java
private class DirectoryAssociationFields {
    private JCheckBox enableCheckbox;        // Enable directory associations
    private JComboBox<String> roleComboBox;  // Editor/Viewer dropdown
    private JTextField descriptionField;     // Description for context menu
    private JButton selectIconButton;        // Browse for custom icon
    private JLabel iconPathLabel;            // Display selected icon path
    private String iconPath;                 // Store icon path
}
```

**Add field to editor class** (around line 84):
```java
private DirectoryAssociationFields directoryAssociationFields;
```

### 5.2 Create Directory Association UI Panel

**New Method** (add around line 650, after `createDocTypeRow` methods):

```java
private JPanel createDirectoryAssociationPanel() {
    directoryAssociationFields = new DirectoryAssociationFields();

    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(BorderFactory.createTitledBorder("Directory Association"));
    panel.setOpaque(false);

    // Enable checkbox
    directoryAssociationFields.enableCheckbox = new JCheckBox("Allow opening directories/folders");
    directoryAssociationFields.enableCheckbox.setOpaque(false);
    directoryAssociationFields.enableCheckbox.addActionListener(evt -> {
        boolean enabled = directoryAssociationFields.enableCheckbox.isSelected();
        directoryAssociationFields.roleComboBox.setEnabled(enabled);
        directoryAssociationFields.descriptionField.setEnabled(enabled);
        directoryAssociationFields.selectIconButton.setEnabled(enabled);
        setModified();
    });

    // Role dropdown
    JPanel rolePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    rolePanel.setOpaque(false);
    rolePanel.add(new JLabel("Role:"));
    directoryAssociationFields.roleComboBox = new JComboBox<>(new String[]{"Editor", "Viewer"});
    directoryAssociationFields.roleComboBox.setEnabled(false);
    directoryAssociationFields.roleComboBox.addActionListener(evt -> setModified());
    rolePanel.add(directoryAssociationFields.roleComboBox);

    // Description field
    JPanel descPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    descPanel.setOpaque(false);
    descPanel.add(new JLabel("Description:"));
    directoryAssociationFields.descriptionField = new JTextField(30);
    directoryAssociationFields.descriptionField.setEnabled(false);
    directoryAssociationFields.descriptionField.setToolTipText(
        "Text shown in context menu (e.g., 'Open folder as project')"
    );
    addChangeListenerTo(directoryAssociationFields.descriptionField, this::setModified);
    descPanel.add(directoryAssociationFields.descriptionField);

    // Icon selection
    JPanel iconPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    iconPanel.setOpaque(false);
    iconPanel.add(new JLabel("Icon:"));
    directoryAssociationFields.selectIconButton = new JButton("Browse...");
    directoryAssociationFields.selectIconButton.setEnabled(false);
    directoryAssociationFields.selectIconButton.addActionListener(evt -> selectDirectoryIcon());
    iconPanel.add(directoryAssociationFields.selectIconButton);

    directoryAssociationFields.iconPathLabel = new JLabel("(none)");
    directoryAssociationFields.iconPathLabel.setForeground(Color.GRAY);
    iconPanel.add(directoryAssociationFields.iconPathLabel);

    // Add components to panel
    panel.add(directoryAssociationFields.enableCheckbox);
    panel.add(Box.createVerticalStrut(10));
    panel.add(rolePanel);
    panel.add(descPanel);
    panel.add(iconPanel);

    return panel;
}

private void selectDirectoryIcon() {
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Select Directory Icon");
    fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
        @Override
        public boolean accept(File f) {
            if (f.isDirectory()) return true;
            String name = f.getName().toLowerCase();
            return name.endsWith(".png") || name.endsWith(".jpg") ||
                   name.endsWith(".jpeg") || name.endsWith(".gif") ||
                   name.endsWith(".icns") || name.endsWith(".ico");
        }

        @Override
        public String getDescription() {
            return "Image Files (*.png, *.jpg, *.jpeg, *.gif, *.icns, *.ico)";
        }
    });

    if (directoryAssociationFields.iconPath != null) {
        fileChooser.setSelectedFile(new File(directoryAssociationFields.iconPath));
    } else {
        fileChooser.setCurrentDirectory(packageJSONFile.getParentFile());
    }

    int result = fileChooser.showOpenDialog(frame);
    if (result == JFileChooser.APPROVE_OPTION) {
        File selected = fileChooser.getSelectedFile();
        try {
            // Make path relative to package.json if possible
            String relativePath = packageJSONFile.getParentFile()
                .toPath()
                .relativize(selected.toPath())
                .toString();
            directoryAssociationFields.iconPath = relativePath;
            directoryAssociationFields.iconPathLabel.setText(relativePath);
            directoryAssociationFields.iconPathLabel.setForeground(Color.BLACK);
            setModified();
        } catch (Exception ex) {
            // Use absolute path if relative path fails
            directoryAssociationFields.iconPath = selected.getAbsolutePath();
            directoryAssociationFields.iconPathLabel.setText(selected.getName());
            directoryAssociationFields.iconPathLabel.setForeground(Color.BLACK);
            setModified();
        }
    }
}
```

### 5.3 Integrate into File Associations Tab

**Locate the File Associations Section** - Find where the document types (file associations) are displayed. This is likely in a scrollable panel or tab.

**Add directory association panel within the same container**:

```java
// Within the file associations section/tab
Box doctypesPanel = Box.createVerticalBox();
doctypesPanel.setOpaque(false);

// ... existing file association rows code ...

// NEW: Add directory association panel at the bottom of file associations
doctypesPanel.add(Box.createVerticalStrut(20)); // Spacing

JPanel dirAssocPanel = createDirectoryAssociationPanel();
dirAssocPanel.setMaximumSize(new Dimension(1000, dirAssocPanel.getPreferredSize().height));
dirAssocPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
doctypesPanel.add(dirAssocPanel); // Add within same container as file associations

// Continue with rest of doctypesPanel setup
```

**Alternative: If using tabs** - If file associations are in a dedicated tab, the directory association panel should be in the same tab, at the bottom after all file association entries.

### 5.4 Load Directory Association from package.json

**Update `load()` or data loading method** (around line 1100-1200):

```java
// After loading document types
if (jdeploy.has("documentTypes")) {
    JSONArray docTypes = jdeploy.getJSONArray("documentTypes");
    // ... existing file association loading code ...

    // NEW: Look for directory association
    for (int i = 0; i < docTypes.length(); i++) {
        JSONObject docType = docTypes.getJSONObject(i);
        if (docType.has("type") && "directory".equalsIgnoreCase(docType.getString("type"))) {
            // Found directory association
            directoryAssociationFields.enableCheckbox.setSelected(true);

            String role = docType.optString("role", "Viewer");
            directoryAssociationFields.roleComboBox.setSelectedItem(role);
            directoryAssociationFields.roleComboBox.setEnabled(true);

            String description = docType.optString("description", "");
            directoryAssociationFields.descriptionField.setText(description);
            directoryAssociationFields.descriptionField.setEnabled(true);

            String icon = docType.optString("icon", null);
            if (icon != null && !icon.isEmpty()) {
                directoryAssociationFields.iconPath = icon;
                directoryAssociationFields.iconPathLabel.setText(icon);
                directoryAssociationFields.iconPathLabel.setForeground(Color.BLACK);
            }
            directoryAssociationFields.selectIconButton.setEnabled(true);

            break; // Only one directory association supported
        }
    }
}
```

### 5.5 Save Directory Association to package.json

**Update `save()` method** (around line 1120-1150):

```java
// In the save method, after saving file associations
if (!jdeploy.has("documentTypes")) {
    jdeploy.put("documentTypes", new JSONArray());
}
JSONArray docTypes = jdeploy.getJSONArray("documentTypes");

// Remove existing directory association (if any)
for (int i = docTypes.length() - 1; i >= 0; i--) {
    JSONObject docType = docTypes.getJSONObject(i);
    if (docType.has("type") && "directory".equalsIgnoreCase(docType.getString("type"))) {
        docTypes.remove(i);
    }
}

// Add new directory association if enabled
if (directoryAssociationFields.enableCheckbox.isSelected()) {
    JSONObject dirAssoc = new JSONObject();
    dirAssoc.put("type", "directory");
    dirAssoc.put("role", directoryAssociationFields.roleComboBox.getSelectedItem().toString());

    String description = directoryAssociationFields.descriptionField.getText().trim();
    if (!description.isEmpty()) {
        dirAssoc.put("description", description);
    }

    if (directoryAssociationFields.iconPath != null &&
        !directoryAssociationFields.iconPath.isEmpty()) {
        dirAssoc.put("icon", directoryAssociationFields.iconPath);
    }

    docTypes.put(dirAssoc);
}
```

### 5.6 Add Validation

**New validation method**:

```java
private String validateDirectoryAssociation() {
    if (!directoryAssociationFields.enableCheckbox.isSelected()) {
        return null; // Not enabled, no validation needed
    }

    // Validate description is not empty (good UX practice)
    String description = directoryAssociationFields.descriptionField.getText().trim();
    if (description.isEmpty()) {
        return "Directory association description should not be empty. " +
               "This text appears in the context menu.";
    }

    // Validate icon path if provided
    if (directoryAssociationFields.iconPath != null &&
        !directoryAssociationFields.iconPath.isEmpty()) {
        File iconFile = new File(packageJSONFile.getParentFile(),
                                 directoryAssociationFields.iconPath);
        if (!iconFile.exists()) {
            return "Directory icon file not found: " + directoryAssociationFields.iconPath;
        }
    }

    return null; // Valid
}
```

**Call validation before save**:

```java
// In save() method, before writing to file
String validationError = validateDirectoryAssociation();
if (validationError != null) {
    int result = JOptionPane.showConfirmDialog(
        frame,
        validationError + "\n\nContinue saving anyway?",
        "Validation Warning",
        JOptionPane.YES_NO_OPTION,
        JOptionPane.WARNING_MESSAGE
    );
    if (result != JOptionPane.YES_OPTION) {
        return; // Don't save
    }
}
```

### 5.7 UI Polish and Accessibility

**Additional enhancements**:

1. **Tooltips**:
   - Role combo: "Editor: allows modifying folder contents. Viewer: read-only access"
   - Description: "Text shown in 'Open With' context menu"
   - Icon: "Optional custom icon for folder associations (PNG, ICO recommended)"

2. **Help Text**:
   Add an info icon/label near the checkbox:
   ```java
   JLabel helpLabel = new JLabel("ⓘ");
   helpLabel.setForeground(Color.BLUE);
   helpLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
   helpLabel.setToolTipText(
       "<html>When enabled, users can:<br>" +
       "• Right-click folders to open them with your app<br>" +
       "• Drag folders onto your app icon<br>" +
       "• Use 'Open With' menu for folders</html>"
   );
   ```

3. **Keyboard Navigation**: Ensure proper tab order and keyboard shortcuts

4. **Visual Consistency**: Match the styling of existing file association rows

### 5.8 Testing the GUI

**Manual Testing Checklist**:

- [ ] Directory association panel appears in editor
- [ ] Enable checkbox toggles all controls correctly
- [ ] Role dropdown has correct options (Editor, Viewer)
- [ ] Description field accepts input and triggers modified state
- [ ] Icon browse button opens file chooser
- [ ] Selected icon path displays correctly
- [ ] Relative paths are preferred over absolute paths
- [ ] Save writes correct JSON to package.json
- [ ] Load reads existing directory associations correctly
- [ ] Validation warns about missing descriptions
- [ ] Validation warns about missing icon files
- [ ] Removing directory association (unchecking) removes from JSON
- [ ] Modified flag works correctly
- [ ] UI matches existing style and patterns

### Implementation Notes

**Placement in UI**: The directory association panel should appear:
- **Within the file associations section/tab** (not as a separate section)
- At the bottom of the file association entries
- Use a titled border to visually separate it from individual file entries
- This placement makes logical sense since directories are document types stored in the same `documentTypes` array

**Singleton Pattern**: Only one directory association is supported per application (matching the implementation design). The UI reflects this with a single panel rather than a list.

**Default Values**:
- Role: "Viewer" (safer default)
- Description: Empty (user must provide)
- Icon: None (uses app icon by default)

**Future Enhancements** (out of scope for MVP):
- Preview button to show how context menu will look
- Multiple directory types (e.g., different for Git repos vs. general folders)
- Platform-specific configuration (different descriptions per OS)

## Phase 6: Testing (Priority: High)

### 6.1 Unit Tests

**FileAssociationsHelperTest**:
- Test parsing directory associations
- Test parsing mixed file + directory configs
- Test invalid configurations
- Test backward compatibility with old configs

**DocumentTypeAssociationTest**:
- Test directory constructor
- Test file constructor (existing)
- Test validation logic
- Test isDirectory() flag

**InstallWindowsRegistryTest**:
- Mock registry operations
- Test directory registration
- Test directory unregistration
- Test backup/restore with directories

### 6.2 Integration Tests

Create test project: **DirectoryAssociationTest**
**Location**: `/tests/projects/DirectoryAssociationTest/`

**package.json**:
```json
{
  "name": "jdeploy-test-directory-app",
  "version": "1.0.0",
  "jdeploy": {
    "jar": "target/test-app.jar",
    "javaVersion": "11",
    "title": "Directory Test App",
    "documentTypes": [
      {
        "type": "directory",
        "role": "Editor",
        "description": "Open folder as project"
      }
    ]
  }
}
```

**Test Java App**:
Simple app that prints command-line arguments to verify directory path is passed correctly.

**Test Script**:
```bash
#!/bin/bash
# Build and install app
jdeploy install

# On Windows:
# - Check registry keys exist
# - Check context menu appears
# - Test opening directory

# On macOS:
# - Check Info.plist has correct entries
# - Test drag-and-drop
# - Test "Open With"

# On Linux:
# - Check .desktop file has inode/directory
# - Test context menu
```

### 6.3 Manual Testing Checklist

**Windows**:
- [ ] Right-click folder → Context menu shows "Open with {AppName}"
- [ ] Selecting context menu opens app with folder path
- [ ] Right-click in empty folder space → Shows option
- [ ] App receives correct directory path as argument
- [ ] Icon appears correctly in context menu
- [ ] Uninstall removes registry entries completely

**macOS**:
- [ ] Drag folder onto app icon → App opens with folder path
- [ ] Right-click folder → "Open With" shows app
- [ ] App receives correct directory path as argument
- [ ] Custom icon appears (if configured)
- [ ] Works with Editor role
- [ ] Works with Viewer role

**Linux**:
- [ ] Right-click folder → "Open With" shows app (GNOME)
- [ ] Right-click folder → "Open With" shows app (KDE)
- [ ] App receives correct directory path as argument
- [ ] .desktop file MimeType includes inode/directory

### 6.4 Backward Compatibility Tests
- [ ] Existing apps with only file associations still work
- [ ] Apps with no documentTypes still work
- [ ] Mixed file + directory associations work
- [ ] Old package.json format still works

## Phase 7: Documentation (Priority: Medium)

### 7.1 Update User Documentation
- Add directory association examples to README
- Update package.json schema documentation
- Add platform-specific behavior notes
- Add troubleshooting section

### 7.2 Update Developer Documentation
- Document AppInfo API changes
- Document registry patterns on Windows
- Document Info.plist patterns on macOS
- Document .desktop file patterns on Linux

### 7.3 Add Examples
Create example project showing:
- Basic directory association
- Mixed file + directory associations
- Custom icons for directories
- Editor vs Viewer roles

## Implementation Timeline

### Sprint 1 (Week 1-2): Core Model & Parsing
- [ ] Phase 1.1: Update DocumentTypeAssociation model
- [ ] Phase 1.2: Update FileAssociationsHelper parser
- [ ] Phase 1.3: Design AppInfo storage approach
- [ ] Phase 1.4: Update AppInfo model
- [ ] Unit tests for Phase 1

### Sprint 2 (Week 3-4): Windows Implementation
- [ ] Phase 2.1: Implement Windows registry changes
- [ ] Unit tests for Windows registry code
- [ ] Manual testing on Windows 10/11

### Sprint 3 (Week 5-6): macOS Implementation
- [ ] Phase 3.1: Update MacBundler
- [ ] Phase 3.2: Update App interface
- [ ] Manual testing on macOS (Intel & ARM)

### Sprint 4 (Week 7): Linux Implementation
- [ ] Phase 4.1: Update desktop file generation
- [ ] Manual testing on Ubuntu, Fedora

### Sprint 5 (Week 8): GUI & Polish
- [ ] Phase 5.1: Update GUI editor
- [ ] Phase 5.2: Add validation
- [ ] Integration tests
- [ ] Documentation updates

### Sprint 6 (Week 9): Testing & Documentation
- [ ] Phase 6: Complete test suite
- [ ] Phase 7: Complete documentation
- [ ] Final cross-platform validation

## Risk Assessment

### High Risk
1. **Platform Behavior Differences**: Directory handling varies significantly between platforms
   - *Mitigation*: Extensive testing on each platform, clear documentation of differences

2. **Registry Permissions on Windows**: Some registry operations may require elevation
   - *Mitigation*: Use HKEY_CURRENT_USER instead of HKEY_LOCAL_MACHINE

3. **macOS Sandboxing**: Sandboxed apps have restrictions on folder access
   - *Mitigation*: Document limitations, require appropriate entitlements

### Medium Risk
1. **Linux Desktop Environment Variations**: Different behavior on GNOME vs KDE vs others
   - *Mitigation*: Test on major desktop environments

2. **Backward Compatibility**: Changes to AppInfo model could break existing code
   - *Mitigation*: Comprehensive regression testing

### Low Risk
1. **Icon Display Issues**: Custom icons may not display correctly on all platforms
   - *Mitigation*: Use standard icon formats, provide fallbacks

## Open Questions

1. **Multiple Directory Types**: Should we support registering for specific directory types (e.g., Git repos, Maven projects)?
   - *Decision*: Not in MVP, but design should allow future extension

2. **Subdirectory Filtering**: Should apps be able to register for directories containing specific files?
   - *Decision*: Not in MVP, too complex for initial implementation

3. **Network Directories**: Should directory associations work with network/remote directories?
   - *Decision*: Platform-dependent, don't explicitly block but don't guarantee

4. **AppInfo Storage**: Option A (separate fields) vs Option B (List<DocumentTypeAssociation>)?
   - *Decision*: **Implemented with Option C** - Store single `DocumentTypeAssociation directoryAssociation` field instead of separate primitives. Cleaner than Option A, simpler than Option B. Treats directories as document types (which they are) without duplication.

## Success Criteria

1. **Functional**:
   - Users can configure directory associations via package.json
   - Applications appear in "Open With" menus for directories
   - Drag-and-drop directories onto app icons works
   - Directory paths are correctly passed to applications

2. **Quality**:
   - Zero breaking changes to existing file associations
   - 90%+ unit test coverage for new code
   - Manual testing passes on Windows 10/11, macOS 12+, Ubuntu 22.04+

3. **Documentation**:
   - Complete API documentation
   - User guide with examples
   - Platform-specific notes for all supported OSes

## Status Tracking

- [x] Phase 1: Model & Schema Updates
  - [x] DocumentTypeAssociation model extended with directory support
  - [x] FileAssociationsHelper updated to parse directory associations
  - [x] AppInfo refactored to use DocumentTypeAssociation object
  - [x] AppDescription refactored to use DocumentTypeAssociation object
  - [x] Installer Main.java updated to handle directory associations

- [x] Phase 2: Windows Implementation
  - [x] registerDirectoryAssociations() - Context menu entries
  - [x] unregisterDirectoryAssociations() - Cleanup
  - [x] Both Directory\shell and Directory\Background\shell support
  - [x] Integrated into register() and unregister() flow

- [x] Phase 3: macOS Implementation
  - [x] MacBundler updated with CFBundleDocumentTypes for folders
  - [x] Uses LSItemContentTypes with "public.folder"
  - [x] AppDescription updated with directory association support
  - [x] Bundler.java passes DocumentTypeAssociation directly

- [x] Phase 4: Linux Implementation
  - [x] Desktop file generation adds inode/directory to MimeType
  - [x] Works with existing file association infrastructure

- [x] Phase 5: GUI Updates
  - [x] 5.1: Add DirectoryAssociationFields inner class
  - [x] 5.2: Create directory association UI panel
  - [x] 5.3: Integrate into file associations section (within tab, not separate)
  - [x] 5.4: Load directory association from package.json
  - [x] 5.5: Save directory association to package.json
  - [x] 5.6: Add validation (description required, icon path verified)
  - [x] 5.7: Test GUI changes (compilation successful, no unit tests needed)
- [x] Phase 6: Testing
  - [x] Unit tests for DocumentTypeAssociation (11 tests, all passing)
  - [x] Unit tests for FileAssociationsHelper (9 tests, all passing)
  - [x] Integration test project created (`tests/projects/DirectoryAssociationTest`)
  - [ ] Manual testing on Windows (pending)
  - [ ] Manual testing on macOS (pending)
  - [ ] Manual testing on Linux (pending)
- [ ] Phase 7: Documentation
  - [ ] User guide
  - [ ] API documentation
  - [ ] Platform-specific notes

**Last Updated**: 2025-10-14
**Status**: Implementation & GUI Complete (Manual testing and docs pending)
**Owner**: Claude Code

## Implementation Notes

### Key Changes Made

**1. DocumentTypeAssociation Model** (`/shared/src/main/java/ca/weblite/jdeploy/models/DocumentTypeAssociation.java`)
- Added directory support fields: `isDirectory`, `role`, `description`
- Two constructors: one for files, one for directories
- `isValid()` method validates based on type
- `getRole()` returns appropriate role for both files and directories

**2. AppInfo & AppDescription**
- Stores directory association as single `DocumentTypeAssociation directoryAssociation` field
- Convenience methods delegate to the association object
- `setDirectoryAssociation()` validates that only directory types are stored
- Clean API: `hasDirectoryAssociation()`, `getDirectoryAssociation()`, `getDirectoryRole()`, etc.

**3. Windows Registry** (`/installer/src/main/java/ca/weblite/jdeploy/installer/win/InstallWindowsRegistry.java`)
- `registerDirectoryAssociations()`: Creates two registry entries
  - `Software\Classes\Directory\shell\{ProgId}` - Right-click on folders
  - `Software\Classes\Directory\Background\shell\{ProgId}` - Right-click in empty folder space
- `unregisterDirectoryAssociations()`: Cleans up both keys
- Uses backup log for rollback safety
- Uninstaller calls `unregister()` which checks `hasDirectoryAssociation()` ✅

**4. macOS Info.plist** (`/shared/src/main/java/com/joshondesign/appbundler/mac/MacBundler.java`)
- Adds `CFBundleDocumentTypes` entry with `LSItemContentTypes = ["public.folder"]`
- Respects role (Editor/Viewer)
- Supports optional custom folder icon

**5. Linux Desktop Files** (`/installer/src/main/java/ca/weblite/jdeploy/installer/Main.java`)
- Adds `inode/directory` to MimeType field
- Works alongside existing file extensions and URL schemes
- Uses same desktop file generation infrastructure

### Design Decisions Finalized

1. **Unified Model**: Directories ARE document types, not a separate concept
2. **Single Field**: `DocumentTypeAssociation directoryAssociation` instead of 4 separate primitives
3. **Type Safety**: `isDirectory()` flag + validation prevents mixing types
4. **Backward Compatible**: All existing file associations unchanged
5. **Uninstaller**: Uses existing `unregister()` flow, depends on package.json being available (Option A)

### Files Modified

- `/shared/src/main/java/ca/weblite/jdeploy/models/DocumentTypeAssociation.java`
- `/shared/src/main/java/ca/weblite/jdeploy/helpers/FileAssociationsHelper.java`
- `/shared/src/main/java/ca/weblite/jdeploy/app/AppInfo.java`
- `/shared/src/main/java/ca/weblite/jdeploy/appbundler/AppDescription.java`
- `/shared/src/main/java/ca/weblite/jdeploy/appbundler/Bundler.java`
- `/shared/src/main/java/com/joshondesign/appbundler/mac/MacBundler.java`
- `/installer/src/main/java/ca/weblite/jdeploy/installer/Main.java`
- `/installer/src/main/java/ca/weblite/jdeploy/installer/win/InstallWindowsRegistry.java`

### Testing Summary

**Unit Tests Created**:
1. `DocumentTypeAssociationTest.java` - 11 tests covering:
   - File association constructor and properties
   - Directory association constructor and properties
   - Role handling (Editor/Viewer, case-insensitive)
   - Validation logic for both types
   - Null value handling

2. `FileAssociationsHelperTest.java` - 9 tests covering:
   - Parsing file associations from package.json
   - Parsing directory associations from package.json
   - Mixed file + directory associations
   - Case-insensitive type matching
   - Default role assignment
   - Invalid association filtering
   - Empty/missing documentTypes handling

**All tests passing**: ✅ 20/20 tests pass

**Integration Test Project**:
- Location: `/tests/projects/DirectoryAssociationTest/`
- Simple Swing app that displays command-line arguments
- Configured with both directory and file associations
- Includes comprehensive manual testing checklist
- Ready for building installers and manual platform testing

### Next Steps

1. **Manual Testing**: Build installers for DirectoryAssociationTest and verify on Windows, macOS, Linux
2. **Documentation**: Add examples to README and user guide
3. **Release**: Merge changes and publish updated jDeploy version

### Phase 5 Implementation Estimate

**Complexity**: Medium
**Estimated Time**: 4-6 hours
**Dependencies**: None (core functionality already complete)

**Breakdown**:
- Inner class and field setup: 30 minutes
- UI panel creation: 1-2 hours
- Load/Save integration: 1 hour
- Validation: 30 minutes
- Polish and accessibility: 1 hour
- Testing: 1-2 hours

### GUI Implementation Summary (Phase 5) - COMPLETED 2025-10-14

**File Modified**: `/cli/src/main/java/ca/weblite/jdeploy/gui/JDeployProjectEditor.java`

**Changes Made**:

1. **DirectoryAssociationFields Inner Class** (lines 283-290)
   - Holds UI components for directory association panel
   - Fields: enableCheckbox, roleComboBox, descriptionField, selectIconButton, iconPathLabel, iconPath

2. **createDirectoryAssociationPanel() Method** (lines 608-681)
   - Creates UI panel with titled border "Directory Association"
   - Enable checkbox with help tooltip explaining functionality
   - Role dropdown (Editor/Viewer) with tooltip
   - Description text field (30 columns)
   - Icon browse button with file chooser integration
   - All fields disabled by default, enabled when checkbox is selected
   - Integrated with setModified() for tracking unsaved changes

3. **selectDirectoryIcon() Method** (lines 683-732)
   - File chooser dialog for selecting icon files
   - Filters for PNG and ICO files
   - Converts absolute paths to relative paths (relative to package.json directory)
   - Updates icon path label with selected file

4. **Integration into File Associations Section** (lines 1256-1261)
   - Panel added to bottom of existing file associations panel
   - Placed within the doctypesPanel container (not separate)
   - Follows user feedback to integrate with existing file associations

5. **Load Functionality** (lines 1263-1291)
   - Reads directory association from jdeploy.documentTypes array
   - Finds entry with type="directory"
   - Populates UI fields: role, description, icon path
   - Enables/disables fields based on whether association exists

6. **Save Functionality** (lines 2269-2289, within handleSave())
   - Removes existing directory associations from documentTypes array
   - Creates new directory association if checkbox is enabled
   - Stores role, description (if not empty), and icon path (if set)
   - Adds to documentTypes array before saving package.json

7. **Validation Method** (lines 2244-2268)
   - validateDirectoryAssociation() checks:
     - If not enabled, no validation needed
     - Description must not be empty (UX best practice)
     - Icon file must exist if path is specified
   - Returns error message or null if valid
   - Called before save with confirmation dialog if validation fails
   - User can choose to save anyway despite validation warnings

**Build Status**:
- ✅ CLI module compiles successfully
- ✅ All existing tests pass
- ✅ No compilation errors
- ✅ Ready for manual testing

**Testing Notes**:
- No unit tests created for GUI code (standard practice for Swing UI)
- GUI functionality can be tested manually by:
  1. Running jDeploy GUI editor
  2. Opening a project with package.json
  3. Navigating to file associations section
  4. Testing directory association panel controls
  5. Saving and verifying package.json updates correctly

**User Feedback Incorporated**:
- Placed panel within file associations tab (not separate section)
- Used existing DocumentTypeAssociation model (no separate fields in AppInfo)
- Followed existing UI patterns and styles
- Matched tooltip style from implementation plan

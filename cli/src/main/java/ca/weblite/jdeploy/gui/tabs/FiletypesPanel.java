package ca.weblite.jdeploy.gui.tabs;

import io.codeworth.panelmatic.PanelMatic;
import io.codeworth.panelmatic.util.Groupings;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.kordamp.ikonli.material.Material;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Objects;

/**
 * Panel for managing file type associations (document types) and directory associations.
 * Combines document type and directory association UI into a single, cohesive panel.
 *
 * Follows the panel pattern: getRoot(), load(JSONObject), save(JSONObject), addChangeListener()
 */
public class FiletypesPanel extends JPanel {
    private final File projectDirectory;
    private ActionListener changeListener;
    
    private Box doctypesPanel;
    private DoctypeFields[] doctypeFieldsArray;
    private DirectoryAssociationFields directoryAssociationFields;
    private JSONArray documentTypesArray;

    public FiletypesPanel(File projectDirectory) {
        this.projectDirectory = Objects.requireNonNull(projectDirectory, "projectDirectory");
        doctypeFieldsArray = new DoctypeFields[0];
        initializeUI();
    }

    private void initializeUI() {
        setOpaque(false);
        setLayout(new BorderLayout());
        
        JPanel contentPanel = createContentPanel();
        JScrollPane scroller = new JScrollPane(contentPanel);
        scroller.setOpaque(false);
        scroller.getViewport().setOpaque(false);
        scroller.setBorder(new EmptyBorder(0, 0, 0, 0));
        
        add(scroller, BorderLayout.CENTER);
    }

    private JPanel createContentPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        
        // Directory association section
        JPanel dirAssocSection = createDirectoryAssociationSection();
        
        // File types section
        Box fileTypesSection = createFileTypesSection();
        
        // Combined panel
        JPanel combined = new JPanel(new BorderLayout());
        combined.setOpaque(false);
        combined.add(dirAssocSection, BorderLayout.NORTH);
        combined.add(fileTypesSection, BorderLayout.CENTER);
        
        panel.add(combined, BorderLayout.NORTH);
        return panel;
    }

    private JPanel createDirectoryAssociationSection() {
        directoryAssociationFields = new DirectoryAssociationFields();

        // Enable checkbox
        directoryAssociationFields.enableCheckbox = new JCheckBox("Allow opening directories/folders");
        directoryAssociationFields.enableCheckbox.setOpaque(false);
        directoryAssociationFields.enableCheckbox.setToolTipText(
            "<html>When enabled, users can:<br>" +
            "• Right-click folders to open them with your app<br>" +
            "• Drag folders onto your app icon<br>" +
            "• Use 'Open With' menu for folders</html>"
        );
        directoryAssociationFields.enableCheckbox.addActionListener(evt -> {
            boolean enabled = directoryAssociationFields.enableCheckbox.isSelected();
            directoryAssociationFields.roleComboBox.setEnabled(enabled);
            directoryAssociationFields.descriptionField.setEnabled(enabled);
            fireChangeEvent();
        });

        // Role dropdown
        directoryAssociationFields.roleComboBox = new JComboBox<>(new String[]{"Editor", "Viewer"});
        directoryAssociationFields.roleComboBox.setEnabled(false);
        directoryAssociationFields.roleComboBox.setToolTipText(
            "Editor: allows modifying folder contents. Viewer: read-only access"
        );
        directoryAssociationFields.roleComboBox.addActionListener(evt -> fireChangeEvent());

        // Description field
        directoryAssociationFields.descriptionField = new JTextField();
        directoryAssociationFields.descriptionField.setEnabled(false);
        directoryAssociationFields.descriptionField.setToolTipText(
            "Text shown in context menu (e.g., 'Open folder as project')"
        );
        addChangeListenerTo(directoryAssociationFields.descriptionField, this::fireChangeEvent);

        // Use plain GridBagLayout
        JPanel rowPanel = new JPanel(new GridBagLayout());
        rowPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        rowPanel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 0, 5, 0);
        rowPanel.add(directoryAssociationFields.enableCheckbox, gbc);

        // Role
        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(5, 0, 5, 5);
        rowPanel.add(new JLabel("Role:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.insets = new Insets(5, 0, 5, 0);
        rowPanel.add(directoryAssociationFields.roleComboBox, gbc);

        // Description
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(5, 0, 5, 5);
        rowPanel.add(new JLabel("Description:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(5, 0, 5, 0);
        rowPanel.add(directoryAssociationFields.descriptionField, gbc);

        // Wrap in a titled section
        JPanel section = new JPanel(new BorderLayout());
        section.setOpaque(false);
        
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BorderLayout());
        header.add(new JLabel("<html><b>Directory Association</b></html>"), BorderLayout.WEST);
        
        section.add(header, BorderLayout.NORTH);
        section.add(rowPanel, BorderLayout.CENTER);
        
        return section;
    }

    private Box createFileTypesSection() {
        // Create the panel to hold document type rows
        doctypesPanel = Box.createVerticalBox();
        doctypesPanel.setOpaque(false);

        // Add toolbar with "+" button
        JButton addDocType = new JButton(FontIcon.of(Material.ADD));
        addDocType.setToolTipText("Add document type association");
        addDocType.addActionListener(evt -> {
            // Ensure documentTypesArray is initialized
            if (documentTypesArray == null) {
                documentTypesArray = new JSONArray();
            }
            JSONObject row = new JSONObject();
            int index = documentTypesArray.length();
            documentTypesArray.put(index, row);
            createDocTypeRow(documentTypesArray, index, doctypesPanel);
            doctypesPanel.revalidate();
            doctypesPanel.repaint();
            fireChangeEvent();
        });

        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setOpaque(false);
        tb.add(addDocType);

        JPanel fileAssocHeader = new JPanel();
        fileAssocHeader.setOpaque(false);
        fileAssocHeader.setLayout(new BorderLayout());
        fileAssocHeader.add(tb, BorderLayout.WEST);

        Box section = Box.createVerticalBox();
        section.setOpaque(false);
        section.add(fileAssocHeader);
        section.add(Box.createVerticalStrut(5));
        section.add(doctypesPanel);
        section.add(Box.createVerticalGlue());
        
        return section;
    }

    private void createDocTypeRow(JSONArray docTypes, int index, Box container) {
        DoctypeFields rowDocTypeFields = new DoctypeFields();
        JSONObject row = docTypes.getJSONObject(index);
        JPanel rowPanel = new JPanel();
        rowPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        rowPanel.setOpaque(false);
        initDoctypeFields(rowDocTypeFields, row, rowPanel);
        JPanel rowWrapper = new JPanel();
        rowWrapper.setOpaque(false);

        rowWrapper.setLayout(new BorderLayout());
        rowWrapper.add(rowPanel, BorderLayout.CENTER);

        JButton removeRow = new JButton(FontIcon.of(Material.DELETE));
        removeRow.setOpaque(false);

        removeRow.addActionListener(evt -> {
            int rowIndex = -1;
            int l = docTypes.length();
            for (int j = 0; j < l; j++) {
                if (docTypes.getJSONObject(j) == row) {
                    rowIndex = j;
                }
            }
            if (rowIndex >= 0) {
                docTypes.remove(rowIndex);
            }
            container.remove(rowWrapper);
            container.revalidate();
            container.repaint();
            fireChangeEvent();
        });
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.add(removeRow);
        rowWrapper.add(buttons, BorderLayout.EAST);
        Component filler = null;
        if (container.getComponentCount() > 0) {
            filler = container.getComponent(container.getComponentCount() - 1);
            container.remove(filler);
        } else {
            filler = new Box.Filler(
                    new Dimension(0, 0),
                    new Dimension(0, 0),
                    new Dimension(1000, 1000)
            );
            ((JComponent) filler).setOpaque(false);
        }
        rowWrapper.setMaximumSize(new Dimension(1000, rowWrapper.getPreferredSize().height));
        rowWrapper.setBorder(new MatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
        container.add(rowWrapper);
        container.add(filler);
    }

    private void initDoctypeFields(DoctypeFields fields, JSONObject docTypeRow, Container cnt) {
        fields.extension = new JTextField();
        fields.extension.setColumns(8);
        fields.extension.setMaximumSize(
                new Dimension(fields.extension.getPreferredSize().width, fields.extension.getPreferredSize().height)
        );
        fields.extension.setToolTipText("Enter the file extension.  E.g. txt");
        if (docTypeRow.has("extension")) {
            fields.extension.setText(docTypeRow.getString("extension"));
        }
        addChangeListenerTo(fields.extension, () -> {
            String extVal = fields.extension.getText();
            if (extVal.startsWith(".")) {
                extVal = extVal.substring(1);
                fields.extension.setText(extVal);
                docTypeRow.put("extension", fields.extension.getText());
                fireChangeEvent();
            } else {
                docTypeRow.put("extension", fields.extension.getText());
                fireChangeEvent();
            }
        });

        fields.mimetype = new JTextField();
        if (docTypeRow.has("mimetype")) {
            fields.mimetype.setText(docTypeRow.getString("mimetype"));
        }
        addChangeListenerTo(fields.mimetype, () -> {
            docTypeRow.put("mimetype", fields.mimetype.getText());
            fireChangeEvent();
        });
        
        fields.editor = new JCheckBox("Editor");
        fields.editor.setToolTipText(
                "Check this box if the app can edit this document type.  " +
                        "Leave unchecked if it can only view documents of this type"
        );
        if (docTypeRow.has("editor") && docTypeRow.getBoolean("editor")) {
            fields.editor.setSelected(true);
        }
        fields.editor.addActionListener(evt -> {
            docTypeRow.put("editor", fields.editor.isSelected());
            fireChangeEvent();
        });

        fields.custom = new JCheckBox("Custom");
        fields.custom.setToolTipText(
                "Check this box if this is a custom extension for your app, and should be added to the system " +
                        "mimetypes registry."
        );
        if (docTypeRow.has("custom") && docTypeRow.getBoolean("custom")) {
            fields.custom.setSelected(true);
        }
        fields.custom.addActionListener(evt -> {
            docTypeRow.put("custom", fields.custom.isSelected());
            fireChangeEvent();
        });

        cnt.removeAll();
        cnt.setLayout(new BoxLayout(cnt, BoxLayout.Y_AXIS));
        JComponent pmPane = PanelMatic.begin()
                .add(Groupings.lineGroup(
                        new JLabel("Extension:"), fields.extension,
                        (JComponent) Box.createHorizontalStrut(10),
                        new JLabel("Mimetype:"), fields.mimetype))
                .add(Groupings.lineGroup(fields.editor, fields.custom)).get();
        setOpaqueRecursive(pmPane, false);

        cnt.add(pmPane);
    }

    private static void addChangeListenerTo(JTextComponent textField, Runnable r) {
        textField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        });
    }

    private void setOpaqueRecursive(JComponent cnt, boolean opaque) {
        cnt.setOpaque(opaque);
        if (cnt.getComponentCount() > 0) {
            int len = cnt.getComponentCount();
            for (int i = 0; i < len; i++) {
                Component cmp = cnt.getComponent(i);
                if (cmp instanceof JComponent) {
                    setOpaqueRecursive((JComponent) cmp, opaque);
                }
            }
        }
    }

    public JPanel getRoot() {
        return this;
    }

    /**
     * Loads file type and directory association configuration from the jdeploy JSONObject.
     */
    public void load(JSONObject jdeploy) {
        // Clear existing document type rows
        doctypesPanel.removeAll();
        
        // Initialize or load the documentTypes array
        if (jdeploy.has("documentTypes")) {
            documentTypesArray = jdeploy.getJSONArray("documentTypes");
        } else {
            documentTypesArray = new JSONArray();
        }
        
        JSONArray docTypes = documentTypesArray;
        
        // Load directory association if it exists
        if (docTypes.length() > 0) {
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

                    break; // Only one directory association supported
                }
            }
        }

        // Add file associations (skip directory associations)
        int len = documentTypesArray.length();
        for (int i = 0; i < len; i++) {
            JSONObject docType = documentTypesArray.getJSONObject(i);
            // Skip directory associations in the file list
            if (docType.has("type") && "directory".equalsIgnoreCase(docType.getString("type"))) {
                continue;
            }
            createDocTypeRow(documentTypesArray, i, doctypesPanel);
        }
        
        // Add the filler at the end
        if (doctypesPanel.getComponentCount() == 0) {
            Component filler = new Box.Filler(
                    new Dimension(0, 0),
                    new Dimension(0, 0),
                    new Dimension(1000, 1000)
            );
            ((JComponent) filler).setOpaque(false);
            doctypesPanel.add(filler);
        }
        
        doctypesPanel.revalidate();
        doctypesPanel.repaint();
    }

    /**
     * Saves file type and directory association configuration to the jdeploy JSONObject.
     */
    public void save(JSONObject jdeploy) {
        // Validate directory association
        String validationError = validateDirectoryAssociation();
        if (validationError != null) {
            throw new IllegalArgumentException(validationError);
        }

        // Ensure documentTypesArray is initialized
        if (documentTypesArray == null) {
            documentTypesArray = new JSONArray();
        }

        // Remove existing directory associations from the array
        for (int i = documentTypesArray.length() - 1; i >= 0; i--) {
            JSONObject docType = documentTypesArray.getJSONObject(i);
            if (docType.has("type") && "directory".equalsIgnoreCase(docType.getString("type"))) {
                documentTypesArray.remove(i);
            }
        }

        // Add new directory association if enabled
        if (directoryAssociationFields.enableCheckbox.isSelected()) {
            JSONObject dirAssoc = new JSONObject();
            dirAssoc.put("type", "directory");
            dirAssoc.put("role", directoryAssociationFields.roleComboBox.getSelectedItem());

            String description = directoryAssociationFields.descriptionField.getText().trim();
            if (!description.isEmpty()) {
                dirAssoc.put("description", description);
            }

            documentTypesArray.put(dirAssoc);
        }

        // Write the documentTypes array to jdeploy
        // Remove old documentTypes first
        if (jdeploy.has("documentTypes")) {
            jdeploy.remove("documentTypes");
        }

        // Only add documentTypes if the array is not empty
        if (documentTypesArray.length() > 0) {
            jdeploy.put("documentTypes", documentTypesArray);
        }
    }

    /**
     * Validates the directory association configuration.
     * @return null if valid, or an error message if invalid
     */
    public String validateDirectoryAssociation() {
        if (directoryAssociationFields == null ||
            !directoryAssociationFields.enableCheckbox.isSelected()) {
            return null; // Not enabled, no validation needed
        }

        // Validate description is not empty (good UX practice)
        String description = directoryAssociationFields.descriptionField.getText().trim();
        if (description.isEmpty()) {
            return "Directory association description should not be empty. " +
                   "This text appears in the context menu.";
        }

        return null; // Valid
    }

    /**
     * Registers a change listener to be notified when document types or directory associations change.
     */
    public void addChangeListener(ActionListener listener) {
        this.changeListener = listener;
    }

    private void fireChangeEvent() {
        if (changeListener != null) {
            changeListener.actionPerformed(new java.awt.event.ActionEvent(this, 0, "changed"));
        }
    }

    /**
     * Inner class to hold document type field references.
     */
    private static class DoctypeFields {
        JTextField extension;
        JTextField mimetype;
        JCheckBox editor;
        JCheckBox custom;
    }

    /**
     * Inner class to hold directory association field references.
     */
    private static class DirectoryAssociationFields {
        JCheckBox enableCheckbox;
        JComboBox<String> roleComboBox;
        JTextField descriptionField;
    }

    /**
     * Test helper: enables directory association programmatically.
     * This is used only in tests to set up the panel state.
     */
    protected void enableDirectoryAssociation(String role, String description) {
        if (directoryAssociationFields != null) {
            directoryAssociationFields.enableCheckbox.setSelected(true);
            directoryAssociationFields.roleComboBox.setEnabled(true);
            directoryAssociationFields.descriptionField.setEnabled(true);
            directoryAssociationFields.roleComboBox.setSelectedItem(role);
            directoryAssociationFields.descriptionField.setText(description);
        }
    }

    /**
     * Test helper: disables directory association programmatically.
     */
    protected void disableDirectoryAssociation() {
        if (directoryAssociationFields != null) {
            directoryAssociationFields.enableCheckbox.setSelected(false);
            directoryAssociationFields.roleComboBox.setEnabled(false);
            directoryAssociationFields.descriptionField.setEnabled(false);
        }
    }

    /**
     * Test helper: gets current directory association state.
     */
    protected boolean isDirectoryAssociationEnabled() {
        return directoryAssociationFields != null && directoryAssociationFields.enableCheckbox.isSelected();
    }

    /**
     * Test helper: gets current directory association description.
     */
    protected String getDirectoryAssociationDescription() {
        return directoryAssociationFields != null ? directoryAssociationFields.descriptionField.getText() : "";
    }
}

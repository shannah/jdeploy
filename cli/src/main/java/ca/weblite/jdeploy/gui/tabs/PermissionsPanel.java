package ca.weblite.jdeploy.gui.tabs;

import ca.weblite.jdeploy.app.permissions.PermissionRequest;
import ca.weblite.jdeploy.app.permissions.PermissionRequestService;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Panel for editing application permissions in the jDeploy project editor.
 */
public class PermissionsPanel extends JPanel {
    
    private final PermissionRequestService permissionService;
    private final Map<PermissionRequest, JCheckBox> permissionCheckboxes;
    private final Map<PermissionRequest, JTextField> descriptionFields;
    private ActionListener changeListener;

    // Run as Administrator controls
    private JComboBox<String> runAsAdministratorComboBox;
    
    public PermissionsPanel() {
        this.permissionService = new PermissionRequestService();
        this.permissionCheckboxes = new HashMap<>();
        this.descriptionFields = new HashMap<>();
        
        initializeUI();
    }
    
    private void initializeUI() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Header panel
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel titleLabel = new JLabel("Application Permissions");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(titleLabel);
        
        JLabel subtitleLabel = new JLabel(
            "<html><body style='width: 600px'>" +
            "Configure the system permissions your application needs. " +
            "These will be used by platform-specific installers (e.g., macOS Info.plist entries). " +
            "Permissions not supported by a platform will be ignored." +
            "</body></html>"
        );
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(11f));
        subtitleLabel.setForeground(Color.GRAY);
        
        JPanel headerWrapper = new JPanel(new BorderLayout());
        headerWrapper.add(headerPanel, BorderLayout.NORTH);
        headerWrapper.add(subtitleLabel, BorderLayout.CENTER);
        headerWrapper.setBorder(new EmptyBorder(0, 0, 10, 0));
        
        add(headerWrapper, BorderLayout.NORTH);
        
        // Main content panel with scroll
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        
        createPermissionSections(mainPanel);
        
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        add(scrollPane, BorderLayout.CENTER);
    }

    private void createRunAsAdministratorSection(JPanel parent) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(new TitledBorder("Run as Administrator"));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel settingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        settingPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel("Privilege Escalation:");
        runAsAdministratorComboBox = new JComboBox<>(new String[]{
            "disabled", "allowed", "required"
        });
        runAsAdministratorComboBox.setSelectedItem("disabled");
        runAsAdministratorComboBox.addActionListener(e -> fireChangeEvent());

        settingPanel.add(label);
        settingPanel.add(Box.createHorizontalStrut(5));
        settingPanel.add(runAsAdministratorComboBox);

        JLabel descriptionLabel = new JLabel(
            "<html><body style='width: 600px'>" +
            "<b>disabled</b>: Application runs with normal user privileges (default)<br/>" +
            "<b>allowed</b>: Creates both normal and \"Run as administrator\" launchers<br/>" +
            "<b>required</b>: All launchers require administrator privileges" +
            "</body></html>"
        );
        descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(11f));
        descriptionLabel.setForeground(Color.GRAY);
        descriptionLabel.setBorder(new EmptyBorder(5, 10, 10, 10));

        section.add(settingPanel);
        section.add(descriptionLabel);

        parent.add(section);
        parent.add(Box.createVerticalStrut(15));
    }

    private void createPermissionSections(JPanel parent) {
        // Add Run as Administrator section at the top
        createRunAsAdministratorSection(parent);

        // Group permissions by category
        createPermissionGroup(parent, "Media Access", new PermissionRequest[]{
            PermissionRequest.CAMERA,
            PermissionRequest.MICROPHONE,
            PermissionRequest.PHOTOS,
            PermissionRequest.PHOTOS_ADD,
            PermissionRequest.MEDIA_LIBRARY
        });
        
        createPermissionGroup(parent, "Location Services", new PermissionRequest[]{
            PermissionRequest.LOCATION,
            PermissionRequest.LOCATION_WHEN_IN_USE,
            PermissionRequest.LOCATION_ALWAYS
        });
        
        createPermissionGroup(parent, "Personal Data", new PermissionRequest[]{
            PermissionRequest.CONTACTS,
            PermissionRequest.CONTACTS_FULL_ACCESS,
            PermissionRequest.CALENDARS,
            PermissionRequest.CALENDARS_WRITE,
            PermissionRequest.REMINDERS,
            PermissionRequest.REMINDERS_WRITE,
            PermissionRequest.HEALTH_SHARE,
            PermissionRequest.HEALTH_UPDATE
        });
        
        createPermissionGroup(parent, "System Access", new PermissionRequest[]{
            PermissionRequest.DESKTOP_FOLDER,
            PermissionRequest.DOCUMENTS_FOLDER,
            PermissionRequest.DOWNLOADS_FOLDER,
            PermissionRequest.NETWORK_VOLUMES,
            PermissionRequest.REMOVABLE_VOLUMES,
            PermissionRequest.SYSTEM_ADMINISTRATION,
            PermissionRequest.APPLE_EVENTS,
            PermissionRequest.SCREEN_CAPTURE
        });
        
        createPermissionGroup(parent, "Device Features", new PermissionRequest[]{
            PermissionRequest.BLUETOOTH,
            PermissionRequest.BLUETOOTH_ALWAYS,
            PermissionRequest.MOTION,
            PermissionRequest.FACE_ID,
            PermissionRequest.SPEECH_RECOGNITION,
            PermissionRequest.LOCAL_NETWORK,
            PermissionRequest.NFC_READER,
            PermissionRequest.IDENTITY
        });
        
        createPermissionGroup(parent, "Notifications & App Features", new PermissionRequest[]{
            PermissionRequest.USER_NOTIFICATIONS,
            PermissionRequest.SIRI,
            PermissionRequest.FOCUS_STATUS,
            PermissionRequest.USER_TRACKING,
            PermissionRequest.WILLINGNESS_TO_RATE
        });
        
        createPermissionGroup(parent, "Services & Entertainment", new PermissionRequest[]{
            PermissionRequest.HOMEKIT,
            PermissionRequest.MUSIC,
            PermissionRequest.TV_PROVIDER,
            PermissionRequest.VIDEO_SUBSCRIBER,
            PermissionRequest.FILE_PROVIDER_PRESENCE,
            PermissionRequest.GAMEKIT_FRIEND_REQUEST,
            PermissionRequest.LOOK_AROUND
        });
    }
    
    private void createPermissionGroup(JPanel parent, String groupTitle, PermissionRequest[] permissions) {
        JPanel groupPanel = new JPanel();
        groupPanel.setLayout(new BoxLayout(groupPanel, BoxLayout.Y_AXIS));
        groupPanel.setBorder(new TitledBorder(groupTitle));
        groupPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        for (PermissionRequest permission : permissions) {
            JPanel permissionPanel = createPermissionRow(permission);
            groupPanel.add(permissionPanel);
        }
        
        parent.add(groupPanel);
        parent.add(Box.createVerticalStrut(10));
    }
    
    private JPanel createPermissionRow(PermissionRequest permission) {
        JPanel rowPanel = new JPanel();
        rowPanel.setLayout(new BoxLayout(rowPanel, BoxLayout.Y_AXIS));
        rowPanel.setBorder(new EmptyBorder(5, 10, 5, 10));
        rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Top panel with checkbox and macOS key
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        topPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Checkbox for enabling/disabling permission
        JCheckBox checkbox = new JCheckBox(getPermissionDisplayName(permission));
        
        // macOS key label (for reference)
        JLabel macOSKeyLabel = new JLabel("(" + permission.getMacOSKey() + ")");
        macOSKeyLabel.setFont(macOSKeyLabel.getFont().deriveFont(10f));
        macOSKeyLabel.setForeground(Color.GRAY);
        
        topPanel.add(checkbox);
        topPanel.add(Box.createHorizontalStrut(10));
        topPanel.add(macOSKeyLabel);
        
        // Description field panel (initially hidden)
        JPanel descPanel = new JPanel(new BorderLayout());
        descPanel.setBorder(new EmptyBorder(5, 25, 0, 0)); // Left indent to align under checkbox text
        descPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel descLabel = new JLabel("Description: ");
        JTextField descriptionField = new JTextField();
        descriptionField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { fireChangeEvent(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { fireChangeEvent(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { fireChangeEvent(); }
        });
        
        // Set placeholder text
        String placeholderText = generateGenericDescription(permission);
        descriptionField.setToolTipText("Leave empty to use default: " + placeholderText);
        
        descPanel.add(descLabel, BorderLayout.WEST);
        descPanel.add(descriptionField, BorderLayout.CENTER);
        descPanel.setVisible(false); // Initially hidden
        
        // Store references
        permissionCheckboxes.put(permission, checkbox);
        descriptionFields.put(permission, descriptionField);
        
        // Checkbox action listener to show/hide description field
        checkbox.addActionListener(e -> {
            boolean enabled = checkbox.isSelected();
            descPanel.setVisible(enabled);
            rowPanel.revalidate();
            rowPanel.repaint();
            fireChangeEvent();
        });
        
        // Add components to row panel
        rowPanel.add(topPanel);
        rowPanel.add(descPanel);
        
        return rowPanel;
    }
    
    private String getPermissionDisplayName(PermissionRequest permission) {
        String name = permission.name().toLowerCase().replace("_", " ");
        // Capitalize first letter of each word
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (Character.isWhitespace(c)) {
                capitalizeNext = true;
                result.append(c);
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
    
    private String generateGenericDescription(PermissionRequest permission) {
        String friendlyName = permission.name().toLowerCase().replace("_", " ");
        return "The " + friendlyName + " permission is required for functionality of this application.";
    }
    
    /**
     * Loads permissions from the package.json data
     */
    public void loadPermissions(JSONObject packageJson) {
        Map<PermissionRequest, String> permissions = permissionService.getPermissionRequests(packageJson);

        // Load run as administrator setting
        if (packageJson.has("jdeploy") && packageJson.getJSONObject("jdeploy").has("runAsAdministrator")) {
            String runAsAdmin = packageJson.getJSONObject("jdeploy").getString("runAsAdministrator");
            runAsAdministratorComboBox.setSelectedItem(runAsAdmin);
        } else {
            runAsAdministratorComboBox.setSelectedItem("disabled");
        }

        // Clear all checkboxes and descriptions, hide all description panels
        permissionCheckboxes.values().forEach(cb -> cb.setSelected(false));
        descriptionFields.values().forEach(field -> {
            field.setText("");
            field.getParent().setVisible(false); // Hide description panel
        });

        // Set loaded permissions
        for (Map.Entry<PermissionRequest, String> entry : permissions.entrySet()) {
            PermissionRequest permission = entry.getKey();
            String description = entry.getValue();

            JCheckBox checkbox = permissionCheckboxes.get(permission);
            JTextField descField = descriptionFields.get(permission);

            if (checkbox != null && descField != null) {
                checkbox.setSelected(true);
                descField.getParent().setVisible(true); // Show description panel

                // Only set description if it's not the generic one
                String genericDesc = generateGenericDescription(permission);
                if (!genericDesc.equals(description)) {
                    descField.setText(description);
                }
            }
        }

        // Refresh the UI
        revalidate();
        repaint();
    }
    
    /**
     * Saves current permissions to the package.json data
     */
    public void savePermissions(JSONObject packageJson) {
        Map<PermissionRequest, String> permissions = new HashMap<>();

        for (Map.Entry<PermissionRequest, JCheckBox> entry : permissionCheckboxes.entrySet()) {
            PermissionRequest permission = entry.getKey();
            JCheckBox checkbox = entry.getValue();

            if (checkbox.isSelected()) {
                JTextField descField = descriptionFields.get(permission);
                String description = descField != null ? descField.getText().trim() : "";

                // If description is empty, use generic description
                if (description.isEmpty()) {
                    description = generateGenericDescription(permission);
                }

                permissions.put(permission, description);
            }
        }

        permissionService.savePermissionRequests(packageJson, permissions);

        // Save run as administrator setting
        if (!packageJson.has("jdeploy")) {
            packageJson.put("jdeploy", new JSONObject());
        }
        JSONObject jdeployObj = packageJson.getJSONObject("jdeploy");
        String runAsAdmin = (String) runAsAdministratorComboBox.getSelectedItem();

        // Only save if not default value
        if ("disabled".equals(runAsAdmin)) {
            if (jdeployObj.has("runAsAdministrator")) {
                jdeployObj.remove("runAsAdministrator");
            }
        } else {
            jdeployObj.put("runAsAdministrator", runAsAdmin);
        }
    }
    
    /**
     * Gets the current permissions as a map
     */
    public Map<PermissionRequest, String> getPermissions() {
        return permissionCheckboxes.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            JTextField descField = descriptionFields.get(entry.getKey());
                            String description = descField != null ? descField.getText().trim() : "";
                            return description.isEmpty() ? 
                                generateGenericDescription(entry.getKey()) : description;
                        }
                ));
    }
    
    /**
     * Adds a change listener that will be called when permissions are modified
     */
    public void addChangeListener(ActionListener listener) {
        this.changeListener = listener;
    }
    
    private void fireChangeEvent() {
        if (changeListener != null) {
            changeListener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "change"));
        }
    }
}
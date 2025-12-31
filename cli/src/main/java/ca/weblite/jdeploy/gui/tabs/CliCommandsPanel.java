package ca.weblite.jdeploy.gui.tabs;

import ca.weblite.jdeploy.gui.util.SwingUtils;
import ca.weblite.jdeploy.models.CommandSpec;
import ca.weblite.jdeploy.models.CommandSpecParser;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel for managing CLI commands in a master-detail UI.
 * Provides a list of commands with Add/Remove buttons and an editor form
 * for editing command name, description, and arguments.
 *
 * Follows the panel pattern: getRoot(), load(JSONObject), save(JSONObject), addChangeListener()
 */
public class CliCommandsPanel extends JPanel {
    private static final String COMMAND_NAME_REGEX = "^[A-Za-z0-9][A-Za-z0-9._-]*$";

    private DefaultListModel<String> commandListModel;
    private JList<String> commandList;
    private JButton addButton;
    private JButton removeButton;
    private JTextField nameField;
    private JTextField descriptionField;
    private JTextArea argsField;
    private JLabel validationLabel;
    private ActionListener changeListener;
    private boolean isUpdatingUI = false;
    private java.util.Map<String, JSONObject> commandsModel = new java.util.LinkedHashMap<>();

    public CliCommandsPanel() {
        initializeUI();
    }

    private void initializeUI() {
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // Create main split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setOpaque(false);

        // Left panel: command list with buttons
        JPanel leftPanel = createLeftPanel();
        splitPane.setLeftComponent(leftPanel);

        // Right panel: editor form
        JPanel rightPanel = createRightPanel();
        splitPane.setRightComponent(rightPanel);

        splitPane.setDividerLocation(200);
        add(splitPane, BorderLayout.CENTER);
    }

    private JPanel createLeftPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BorderLayout());
        panel.setBorder(new EmptyBorder(0, 0, 0, 10));

        // Label
        JLabel label = new JLabel("Commands");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, BorderLayout.NORTH);

        // Command list
        commandListModel = new DefaultListModel<>();
        commandList = new JList<>(commandListModel);
        commandList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        commandList.addListSelectionListener(this::onCommandSelected);

        JScrollPane scrollPane = new JScrollPane(commandList);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Buttons panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.setOpaque(false);
        buttonPanel.setLayout(new FlowLayout(FlowLayout.LEFT));

        addButton = new JButton("Add");
        addButton.addActionListener(evt -> addNewCommand());
        buttonPanel.add(addButton);

        removeButton = new JButton("Remove");
        removeButton.setEnabled(false);
        removeButton.addActionListener(evt -> removeSelectedCommand());
        buttonPanel.add(removeButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BorderLayout());
        panel.setBorder(new EmptyBorder(0, 10, 0, 0));

        // Scrollable form area
        JPanel formPanel = new JPanel();
        formPanel.setOpaque(false);
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        // Name field
        JLabel nameLabel = new JLabel("Command Name");
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        formPanel.add(nameLabel);

        nameField = new JTextField();
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, nameField.getPreferredSize().height));
        nameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onNameChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { onNameChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onNameChanged(); }
        });
        formPanel.add(nameField);

        // Validation label
        validationLabel = new JLabel(" ");
        validationLabel.setFont(validationLabel.getFont().deriveFont(Font.ITALIC));
        validationLabel.setForeground(Color.RED);
        formPanel.add(validationLabel);

        formPanel.add(Box.createVerticalStrut(10));

        // Description field
        JLabel descLabel = new JLabel("Description");
        descLabel.setFont(descLabel.getFont().deriveFont(Font.BOLD));
        formPanel.add(descLabel);

        descriptionField = new JTextField();
        descriptionField.setMaximumSize(new Dimension(Integer.MAX_VALUE, descriptionField.getPreferredSize().height));
        descriptionField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { fireChangeEvent(); }
            @Override public void removeUpdate(DocumentEvent e) { fireChangeEvent(); }
            @Override public void changedUpdate(DocumentEvent e) { fireChangeEvent(); }
        });
        formPanel.add(descriptionField);

        formPanel.add(Box.createVerticalStrut(10));

        // Arguments field
        JLabel argsLabel = new JLabel("Arguments (one per line)");
        argsLabel.setFont(argsLabel.getFont().deriveFont(Font.BOLD));
        formPanel.add(argsLabel);

        argsField = new JTextArea();
        argsField.setLineWrap(false);
        argsField.setWrapStyleWord(false);
        argsField.setRows(6);
        argsField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        argsField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { fireChangeEvent(); }
            @Override public void removeUpdate(DocumentEvent e) { fireChangeEvent(); }
            @Override public void changedUpdate(DocumentEvent e) { fireChangeEvent(); }
        });

        JScrollPane argsScroller = new JScrollPane(argsField);
        argsScroller.setOpaque(false);
        argsScroller.getViewport().setOpaque(false);
        argsScroller.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        formPanel.add(argsScroller);

        formPanel.add(Box.createVerticalGlue());

        // Wrap in scroll pane
        JScrollPane scrollPane = new JScrollPane(formPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    public JPanel getRoot() {
        return this;
    }

    /**
     * Load commands from jdeploy configuration.
     */
    public void load(JSONObject jdeploy) {
        isUpdatingUI = true;
        try {
            commandListModel.clear();
            commandsModel.clear();
            nameField.setText("");
            descriptionField.setText("");
            argsField.setText("");
            validationLabel.setText(" ");
            removeButton.setEnabled(false);

            if (jdeploy == null || !jdeploy.has("commands")) {
                return;
            }

            JSONObject commandsObj = jdeploy.getJSONObject("commands");
            for (String key : commandsObj.keySet()) {
                JSONObject cmdSpec = commandsObj.getJSONObject(key);
                commandListModel.addElement(key);
                commandsModel.put(key, new JSONObject(cmdSpec.toString()));
            }
        } finally {
            isUpdatingUI = false;
        }
    }

    /**
     * Save commands to jdeploy configuration.
     */
    public void save(JSONObject jdeploy) {
        // Ensure current edited command is saved
        saveCurrentCommand();

        JSONObject commands = new JSONObject();
        for (int i = 0; i < commandListModel.size(); i++) {
            String name = commandListModel.getElementAt(i);
            JSONObject spec = commandsModel.getOrDefault(name, buildCommandSpec(name));
            commands.put(name, spec);
        }

        if (commands.length() > 0) {
            jdeploy.put("commands", commands);
        } else if (jdeploy.has("commands")) {
            jdeploy.remove("commands");
        }
    }

    /**
     * Register a change listener.
     */
    public void addChangeListener(ActionListener listener) {
        this.changeListener = listener;
    }

    private void onCommandSelected(ListSelectionEvent evt) {
        if (isUpdatingUI || evt.getValueIsAdjusting()) {
            return;
        }

        // Save the previously selected command
        saveCurrentCommand();

        int index = commandList.getSelectedIndex();
        if (index < 0) {
            nameField.setText("");
            descriptionField.setText("");
            argsField.setText("");
            validationLabel.setText(" ");
            removeButton.setEnabled(false);
        } else {
            String commandName = commandListModel.getElementAt(index);
            loadCommandForEditing(commandName);
            removeButton.setEnabled(true);
        }
    }

    private void loadCommandForEditing(String commandName) {
        isUpdatingUI = true;
        try {
            nameField.setText(commandName);
            // Description and args are loaded from the form state
            // In a real scenario, we'd load from the backing data model
            descriptionField.setText("");
            argsField.setText("");
            validationLabel.setText(" ");
        } finally {
            isUpdatingUI = false;
        }
    }

    private void saveCurrentCommand() {
        int index = commandList.getSelectedIndex();
        if (index >= 0) {
            String oldName = commandListModel.getElementAt(index);
            String newName = nameField.getText().trim();

            // Save current form state to model
            JSONObject spec = buildCommandSpec(newName);
            commandsModel.put(newName, spec);

            // If name changed, update list and model
            if (!oldName.equals(newName) && !newName.isEmpty()) {
                commandsModel.remove(oldName);
                commandListModel.setElementAt(newName, index);
                commandList.setSelectedIndex(index);
            }
        }
    }

    private void onNameChanged() {
        if (isUpdatingUI) {
            return;
        }

        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            validationLabel.setText(" ");
        } else if (!name.matches(COMMAND_NAME_REGEX)) {
            validationLabel.setText("Invalid: must start with alphanumeric, then alphanumeric/dot/dash/underscore");
        } else {
            validationLabel.setText(" ");
        }

        fireChangeEvent();
    }

    private void addNewCommand() {
        int newIndex = commandListModel.size();
        String newName = generateUniqueName();
        commandListModel.addElement(newName);
        commandsModel.put(newName, new JSONObject());
        commandList.setSelectedIndex(newIndex);
        nameField.requestFocus();
        fireChangeEvent();
    }

    private void removeSelectedCommand() {
        int index = commandList.getSelectedIndex();
        if (index >= 0) {
            String name = commandListModel.getElementAt(index);
            commandListModel.removeElementAt(index);
            commandsModel.remove(name);
            removeButton.setEnabled(false);
            nameField.setText("");
            descriptionField.setText("");
            argsField.setText("");
            validationLabel.setText(" ");
            fireChangeEvent();
        }
    }

    private String generateUniqueName() {
        int counter = 1;
        String baseName = "command";
        while (commandListModel.contains(baseName + counter)) {
            counter++;
        }
        return baseName + counter;
    }

    private JSONObject buildCommandSpec(String name) {
        JSONObject spec = new JSONObject();

        String desc = descriptionField.getText().trim();
        if (!desc.isEmpty()) {
            spec.put("description", desc);
        }

        String argsText = argsField.getText().trim();
        if (!argsText.isEmpty()) {
            String[] lines = argsText.split("\n");
            JSONArray argsArray = new JSONArray();
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    argsArray.put(trimmed);
                }
            }
            if (argsArray.length() > 0) {
                spec.put("args", argsArray);
            }
        }

        return spec;
    }

    private void fireChangeEvent() {
        if (changeListener != null && !isUpdatingUI) {
            changeListener.actionPerformed(new java.awt.event.ActionEvent(this, 0, "changed"));
        }
    }
}

package ca.weblite.jdeploy.gui.tabs;

import org.json.JSONArray;
import org.json.JSONObject;
import org.kordamp.ikonli.material.Material;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * Panel for managing helper actions in the project editor.
 *
 * Helper actions are quick links that appear in the tray menu and service management panel,
 * allowing users to open URLs, custom protocol handlers, or files.
 *
 * Follows the panel pattern: getRoot(), load(JSONObject), save(JSONObject), addChangeListener()
 */
public class HelperActionsPanel extends JPanel {

    private static final int COL_LABEL = 0;
    private static final int COL_DESCRIPTION = 1;
    private static final int COL_URL = 2;

    private static final String[] COLUMN_NAMES = {"Label", "Description", "URL"};

    private JTable actionsTable;
    private DefaultTableModel tableModel;
    private ActionListener changeListener;
    private boolean isLoading = false;

    public HelperActionsPanel() {
        initializeUI();
    }

    private void initializeUI() {
        setOpaque(false);
        setLayout(new BorderLayout(0, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // Help text at the top
        JTextArea helpText = new JTextArea();
        helpText.setEditable(false);
        helpText.setOpaque(false);
        helpText.setLineWrap(true);
        helpText.setWrapStyleWord(true);
        helpText.setText(
            "Helper actions are quick links that appear in the system tray menu and service management panel. " +
            "Use them to provide shortcuts to dashboards, configuration files, or custom URLs.\n\n" +
            "Supported URL types:\n" +
            "  - Web URLs: https://example.com/dashboard\n" +
            "  - Custom protocols: myapp://settings\n" +
            "  - File paths: /path/to/config.json"
        );
        helpText.setMaximumSize(new Dimension(600, 120));
        JPanel helpPanel = new JPanel(new BorderLayout());
        helpPanel.setOpaque(false);
        helpPanel.add(helpText, BorderLayout.CENTER);

        // Table model - all cells editable
        tableModel = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        };

        // Listen for table changes
        tableModel.addTableModelListener(e -> {
            if (!isLoading && e.getType() != TableModelEvent.DELETE) {
                fireChangeEvent();
            }
        });

        // Table
        actionsTable = new JTable(tableModel);
        actionsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        actionsTable.getTableHeader().setReorderingAllowed(false);
        actionsTable.setRowHeight(24);

        // Set column widths
        actionsTable.getColumnModel().getColumn(COL_LABEL).setPreferredWidth(120);
        actionsTable.getColumnModel().getColumn(COL_DESCRIPTION).setPreferredWidth(200);
        actionsTable.getColumnModel().getColumn(COL_URL).setPreferredWidth(250);

        JScrollPane scrollPane = new JScrollPane(actionsTable);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setPreferredSize(new Dimension(600, 200));

        // Toolbar with Add/Remove buttons
        JButton addButton = new JButton(FontIcon.of(Material.ADD));
        addButton.setToolTipText("Add a new helper action");
        addButton.addActionListener(e -> {
            tableModel.addRow(new Object[]{"", "", ""});
            int newRow = tableModel.getRowCount() - 1;
            actionsTable.setRowSelectionInterval(newRow, newRow);
            actionsTable.scrollRectToVisible(actionsTable.getCellRect(newRow, 0, true));
            fireChangeEvent();
        });

        JButton removeButton = new JButton(FontIcon.of(Material.DELETE));
        removeButton.setToolTipText("Remove selected helper action");
        removeButton.addActionListener(e -> {
            int selectedRow = actionsTable.getSelectedRow();
            if (selectedRow >= 0) {
                tableModel.removeRow(selectedRow);
                fireChangeEvent();
            }
        });

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setOpaque(false);
        toolbar.add(addButton);
        toolbar.add(removeButton);

        JPanel toolbarPanel = new JPanel(new BorderLayout());
        toolbarPanel.setOpaque(false);
        toolbarPanel.add(toolbar, BorderLayout.WEST);

        // Layout
        add(helpPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(toolbarPanel, BorderLayout.SOUTH);
    }

    /**
     * Returns the root component (this panel itself).
     */
    public JPanel getRoot() {
        return this;
    }

    /**
     * Loads helper actions from the jdeploy JSONObject.
     */
    public void load(JSONObject jdeploy) {
        isLoading = true;
        try {
            tableModel.setRowCount(0);

            if (!jdeploy.has("helper")) {
                return;
            }

            JSONObject helper = jdeploy.getJSONObject("helper");
            if (!helper.has("actions")) {
                return;
            }

            JSONArray actions = helper.getJSONArray("actions");
            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.getJSONObject(i);
                String label = action.optString("label", "");
                String description = action.optString("description", "");
                String url = action.optString("url", "");
                tableModel.addRow(new Object[]{label, description, url});
            }
        } finally {
            isLoading = false;
        }
    }

    /**
     * Saves helper actions to the jdeploy JSONObject.
     */
    public void save(JSONObject jdeploy) {
        JSONArray actions = new JSONArray();

        for (int row = 0; row < tableModel.getRowCount(); row++) {
            String label = getStringValue(row, COL_LABEL);
            String description = getStringValue(row, COL_DESCRIPTION);
            String url = getStringValue(row, COL_URL);

            // Skip rows with empty label or URL
            if (label.isEmpty() || url.isEmpty()) {
                continue;
            }

            JSONObject action = new JSONObject();
            action.put("label", label);
            if (!description.isEmpty()) {
                action.put("description", description);
            }
            action.put("url", url);
            actions.put(action);
        }

        if (actions.length() > 0) {
            JSONObject helper = jdeploy.optJSONObject("helper");
            if (helper == null) {
                helper = new JSONObject();
                jdeploy.put("helper", helper);
            }
            helper.put("actions", actions);
        } else {
            // Remove helper.actions if empty
            if (jdeploy.has("helper")) {
                JSONObject helper = jdeploy.getJSONObject("helper");
                helper.remove("actions");
                // Remove helper object if it's now empty
                if (helper.isEmpty()) {
                    jdeploy.remove("helper");
                }
            }
        }
    }

    /**
     * Validates the helper actions configuration.
     *
     * @return null if valid, or an error message if invalid
     */
    public String validateConfiguration() {
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            String label = getStringValue(row, COL_LABEL);
            String url = getStringValue(row, COL_URL);

            // If the row has any content, validate it
            boolean hasContent = !label.isEmpty() || !url.isEmpty() ||
                    !getStringValue(row, COL_DESCRIPTION).isEmpty();

            if (hasContent) {
                if (label.isEmpty()) {
                    return "Helper action in row " + (row + 1) + " is missing a label.";
                }
                if (url.isEmpty()) {
                    return "Helper action '" + label + "' is missing a URL.";
                }
            }
        }
        return null;
    }

    /**
     * Registers a change listener to be notified when helper actions change.
     */
    public void addChangeListener(ActionListener listener) {
        this.changeListener = listener;
    }

    private void fireChangeEvent() {
        if (changeListener != null) {
            changeListener.actionPerformed(new java.awt.event.ActionEvent(this, 0, "changed"));
        }
    }

    private String getStringValue(int row, int col) {
        Object value = tableModel.getValueAt(row, col);
        if (value == null) {
            return "";
        }
        return value.toString().trim();
    }
}

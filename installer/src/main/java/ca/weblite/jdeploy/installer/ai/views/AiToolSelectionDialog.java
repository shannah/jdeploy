package ca.weblite.jdeploy.installer.ai.views;

import ca.weblite.jdeploy.ai.models.AIToolType;
import ca.weblite.jdeploy.ai.models.AiIntegrationConfig;
import ca.weblite.jdeploy.ai.services.AiToolDetector;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Dialog for selecting which AI tools should be configured with the application's
 * MCP server, skills, and agents.
 */
public class AiToolSelectionDialog extends JDialog {

    private final AiIntegrationConfig config;
    private final AiToolDetector toolDetector;
    private final Map<AIToolType, JCheckBox> toolCheckboxes = new LinkedHashMap<>();
    private boolean confirmed = false;
    private Set<AIToolType> selectedTools = new HashSet<>();

    public AiToolSelectionDialog(Frame parent, AiIntegrationConfig config, AiToolDetector toolDetector) {
        super(parent, "Select AI Tools for Integration", true);
        this.config = config;
        this.toolDetector = toolDetector;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Header panel
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBorder(new EmptyBorder(15, 15, 10, 15));

        JLabel headerLabel = new JLabel("Select AI Tools for Integration");
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.add(headerLabel);

        headerPanel.add(Box.createVerticalStrut(8));

        JLabel descLabel = new JLabel("<html>The following AI tools were detected on your system.<br>" +
                "Select which ones should be configured:</html>");
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.add(descLabel);

        add(headerPanel, BorderLayout.NORTH);

        // Tools list panel
        JPanel toolsPanel = new JPanel();
        toolsPanel.setLayout(new BoxLayout(toolsPanel, BoxLayout.Y_AXIS));
        toolsPanel.setBorder(new EmptyBorder(5, 15, 5, 15));

        List<AIToolType> installedTools = toolDetector.getInstalledTools();

        for (AIToolType toolType : AIToolType.values()) {
            boolean isInstalled = installedTools.contains(toolType);

            JPanel toolRow = createToolRow(toolType, isInstalled);
            toolRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            toolsPanel.add(toolRow);
            toolsPanel.add(Box.createVerticalStrut(5));
        }

        // Add note about manual setup tools
        toolsPanel.add(Box.createVerticalStrut(10));
        JLabel noteLabel = new JLabel("<html><i>\u2139 Warp and JetBrains IDEs require manual setup.<br>" +
                "Configuration will be copied to clipboard.</i></html>");
        noteLabel.setForeground(Color.GRAY);
        noteLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        toolsPanel.add(noteLabel);

        JScrollPane scrollPane = new JScrollPane(toolsPanel);
        scrollPane.setBorder(null);
        scrollPane.setPreferredSize(new Dimension(450, 350));
        add(scrollPane, BorderLayout.CENTER);

        // Buttons panel
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonsPanel.setBorder(new EmptyBorder(10, 15, 15, 15));

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> {
            confirmed = false;
            dispose();
        });
        buttonsPanel.add(cancelButton);

        JButton installButton = new JButton("Install");
        installButton.addActionListener(e -> {
            confirmed = true;
            collectSelectedTools();
            dispose();
        });
        buttonsPanel.add(installButton);

        add(buttonsPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(getParent());
    }

    private JPanel createToolRow(AIToolType toolType, boolean isInstalled) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)),
                new EmptyBorder(8, 0, 8, 0)
        ));

        // Tool checkbox row
        JPanel checkboxRow = new JPanel();
        checkboxRow.setLayout(new BoxLayout(checkboxRow, BoxLayout.X_AXIS));
        checkboxRow.setOpaque(false);
        checkboxRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JCheckBox checkbox = new JCheckBox(toolType.getDisplayName());
        checkbox.setOpaque(false);
        checkbox.setSelected(isInstalled && toolType.supportsAutoInstall());
        checkbox.setEnabled(isInstalled);

        if (!isInstalled) {
            checkbox.setText(toolType.getDisplayName() + " (not detected)");
            checkbox.setForeground(Color.GRAY);
        }

        toolCheckboxes.put(toolType, checkbox);
        checkboxRow.add(checkbox);
        checkboxRow.add(Box.createHorizontalGlue());

        row.add(checkboxRow);

        // Features sub-items
        JPanel featuresPanel = new JPanel();
        featuresPanel.setLayout(new BoxLayout(featuresPanel, BoxLayout.Y_AXIS));
        featuresPanel.setOpaque(false);
        featuresPanel.setBorder(new EmptyBorder(2, 25, 0, 0));
        featuresPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (toolType.supportsMcp() && config.hasMcpConfig()) {
            JLabel mcpLabel = new JLabel("\u2514 MCP Server");
            mcpLabel.setFont(mcpLabel.getFont().deriveFont(11f));
            mcpLabel.setForeground(isInstalled ? new Color(80, 80, 80) : Color.GRAY);
            featuresPanel.add(mcpLabel);
        }

        if (toolType.supportsSkills() && config.hasSkills()) {
            JLabel skillsLabel = new JLabel("\u2514 Skills (" + config.getSkillNames().size() + " skill" +
                    (config.getSkillNames().size() == 1 ? "" : "s") + ")");
            skillsLabel.setFont(skillsLabel.getFont().deriveFont(11f));
            skillsLabel.setForeground(isInstalled ? new Color(80, 80, 80) : Color.GRAY);
            featuresPanel.add(skillsLabel);
        }

        if (toolType.supportsAgents() && config.hasAgents()) {
            JLabel agentsLabel = new JLabel("\u2514 Agents (" + config.getAgentNames().size() + " agent" +
                    (config.getAgentNames().size() == 1 ? "" : "s") + ")");
            agentsLabel.setFont(agentsLabel.getFont().deriveFont(11f));
            agentsLabel.setForeground(isInstalled ? new Color(80, 80, 80) : Color.GRAY);
            featuresPanel.add(agentsLabel);
        }

        if (toolType.requiresManualSetup() && isInstalled) {
            JLabel manualLabel = new JLabel("\u2514 (Copy to clipboard)");
            manualLabel.setFont(manualLabel.getFont().deriveFont(Font.ITALIC, 11f));
            manualLabel.setForeground(new Color(100, 100, 100));
            featuresPanel.add(manualLabel);
        }

        if (featuresPanel.getComponentCount() > 0) {
            row.add(featuresPanel);
        }

        return row;
    }

    private void collectSelectedTools() {
        selectedTools.clear();
        for (Map.Entry<AIToolType, JCheckBox> entry : toolCheckboxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selectedTools.add(entry.getKey());
            }
        }
    }

    /**
     * Returns true if the user confirmed the selection.
     */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Returns the set of selected AI tools.
     */
    public Set<AIToolType> getSelectedTools() {
        return new HashSet<>(selectedTools);
    }

    /**
     * Shows the dialog and returns the selected tools, or null if cancelled.
     */
    public static Set<AIToolType> showDialog(Frame parent, AiIntegrationConfig config, AiToolDetector toolDetector) {
        AiToolSelectionDialog dialog = new AiToolSelectionDialog(parent, config, toolDetector);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            return dialog.getSelectedTools();
        }
        return null;
    }
}

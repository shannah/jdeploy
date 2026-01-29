package ca.weblite.jdeploy.gui.tabs;

import ca.weblite.jdeploy.ai.models.AiIntegrationConfig;
import ca.weblite.jdeploy.ai.models.McpServerConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel for configuring AI integrations (MCP servers, skills, agents) in the jDeploy Project Editor.
 *
 * This panel allows developers to:
 * - Configure MCP server settings (command, args, default enabled)
 * - View the status of skills in .jdeploy/skills/
 * - View the status of agents in .jdeploy/agents/
 */
public class AiIntegrationsPanel extends JPanel {
    private static final String ARGS_PLACEHOLDER = "--verbose\n--config default";

    private final File projectDir;
    private ActionListener changeListener;
    private boolean isUpdatingUI = false;

    // MCP Configuration components
    private JComboBox<String> commandComboBox;
    private JTextArea argsField;
    private JCheckBox defaultEnabledCheckbox;

    // Status display components
    private JLabel skillsStatusLabel;
    private JLabel agentsStatusLabel;

    // Cached commands from jdeploy.commands
    private List<String> availableCommands = new ArrayList<>();

    public AiIntegrationsPanel(File projectDir) {
        this.projectDir = projectDir;
        initializeUI();
    }

    private void initializeUI() {
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel mainPanel = new JPanel();
        mainPanel.setOpaque(false);
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // Instructions panel
        JPanel instructionsPanel = createInstructionsPanel();
        instructionsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(instructionsPanel);
        mainPanel.add(Box.createVerticalStrut(20));

        // MCP Configuration section
        JPanel mcpSection = createMcpSection();
        mcpSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(mcpSection);
        mainPanel.add(Box.createVerticalStrut(20));

        // Skills & Agents Status section
        JPanel statusSection = createStatusSection();
        statusSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(statusSection);

        mainPanel.add(Box.createVerticalGlue());

        // Wrap in scroll pane
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createInstructionsPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(true);
        panel.setBackground(new Color(245, 245, 250));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 210), 1),
            new EmptyBorder(10, 10, 10, 10)
        ));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("AI Integrations");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(5));

        String instructionsText = "Configure your application as an MCP (Model Context Protocol) server " +
                "that can be used by AI tools like Claude Desktop, Claude Code, Cursor, and others.\n\n" +
                "When users install your app with AI integrations enabled, your MCP server will be " +
                "automatically registered with their installed AI tools.\n\n" +
                "Skills and agents can be added by placing folders with SKILL.md files in:\n" +
                "  \u2022 .jdeploy/skills/ - For Claude Code and Codex CLI skills\n" +
                "  \u2022 .jdeploy/agents/ - For Claude Code agents";

        JTextArea instructionsArea = new JTextArea(instructionsText);
        instructionsArea.setEditable(false);
        instructionsArea.setOpaque(false);
        instructionsArea.setLineWrap(true);
        instructionsArea.setWrapStyleWord(true);
        instructionsArea.setFont(instructionsArea.getFont().deriveFont(11f));
        instructionsArea.setBorder(null);
        instructionsArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        instructionsArea.setFocusable(false);
        instructionsArea.setPreferredSize(new Dimension(500, 140));
        instructionsArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));

        panel.add(instructionsArea);

        return panel;
    }

    private JPanel createMcpSection() {
        JPanel section = new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));

        // Section header
        JLabel sectionHeader = new JLabel("MCP Server Configuration");
        sectionHeader.setFont(sectionHeader.getFont().deriveFont(Font.BOLD, 12f));
        sectionHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(sectionHeader);
        section.add(Box.createVerticalStrut(10));

        // Command dropdown
        JPanel commandPanel = new JPanel();
        commandPanel.setOpaque(false);
        commandPanel.setLayout(new BoxLayout(commandPanel, BoxLayout.X_AXIS));
        commandPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel commandLabel = new JLabel("Command: ");
        commandLabel.setPreferredSize(new Dimension(120, 25));
        commandPanel.add(commandLabel);
        commandPanel.add(Box.createHorizontalStrut(5));
        commandPanel.add(createInfoIcon("<html>Select a command from jdeploy.commands.<br>" +
                "This command will be invoked when AI tools start your MCP server.</html>"));
        commandPanel.add(Box.createHorizontalStrut(10));

        commandComboBox = new JComboBox<>();
        commandComboBox.setEditable(true);
        commandComboBox.setMaximumSize(new Dimension(300, 30));
        commandComboBox.setPreferredSize(new Dimension(300, 30));
        commandComboBox.addActionListener(e -> {
            if (!isUpdatingUI) {
                fireChangeEvent();
            }
        });
        commandPanel.add(commandComboBox);
        commandPanel.add(Box.createHorizontalGlue());

        section.add(commandPanel);
        section.add(Box.createVerticalStrut(10));

        // Args field
        JPanel argsLabelPanel = new JPanel();
        argsLabelPanel.setOpaque(false);
        argsLabelPanel.setLayout(new BoxLayout(argsLabelPanel, BoxLayout.X_AXIS));
        argsLabelPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel argsLabel = new JLabel("Additional Arguments (one per line):");
        argsLabelPanel.add(argsLabel);
        argsLabelPanel.add(Box.createHorizontalStrut(5));
        argsLabelPanel.add(createInfoIcon("<html>Additional command-line arguments to pass to the MCP server.<br>" +
                "These are appended after --jdeploy:command=&lt;command&gt;</html>"));
        argsLabelPanel.add(Box.createHorizontalGlue());

        section.add(argsLabelPanel);
        section.add(Box.createVerticalStrut(5));

        argsField = new JTextArea();
        argsField.setLineWrap(false);
        argsField.setWrapStyleWord(false);
        argsField.setRows(4);
        argsField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        argsField.setForeground(Color.GRAY);
        argsField.setText(ARGS_PLACEHOLDER);

        // Placeholder behavior
        argsField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                if (argsField.getText().equals(ARGS_PLACEHOLDER) && argsField.getForeground().equals(Color.GRAY)) {
                    argsField.setText("");
                    argsField.setForeground(Color.BLACK);
                }
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (argsField.getText().trim().isEmpty()) {
                    argsField.setForeground(Color.GRAY);
                    argsField.setText(ARGS_PLACEHOLDER);
                }
            }
        });

        argsField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onFieldChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { onFieldChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onFieldChanged(); }
        });

        JScrollPane argsScroller = new JScrollPane(argsField);
        argsScroller.setAlignmentX(Component.LEFT_ALIGNMENT);
        argsScroller.setMaximumSize(new Dimension(500, 100));
        argsScroller.setPreferredSize(new Dimension(500, 100));
        section.add(argsScroller);
        section.add(Box.createVerticalStrut(10));

        // Default enabled checkbox
        defaultEnabledCheckbox = new JCheckBox("Enable by default in installer");
        defaultEnabledCheckbox.setOpaque(false);
        defaultEnabledCheckbox.setSelected(true);
        defaultEnabledCheckbox.setToolTipText("When checked, the 'Install AI Integrations' checkbox will be checked by default in the installer");
        defaultEnabledCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        defaultEnabledCheckbox.addActionListener(e -> {
            if (!isUpdatingUI) {
                fireChangeEvent();
            }
        });
        section.add(defaultEnabledCheckbox);

        return section;
    }

    private JPanel createStatusSection() {
        JPanel section = new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));

        // Section header
        JLabel sectionHeader = new JLabel("Skills & Agents Status");
        sectionHeader.setFont(sectionHeader.getFont().deriveFont(Font.BOLD, 12f));
        sectionHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(sectionHeader);
        section.add(Box.createVerticalStrut(10));

        // Skills status
        JPanel skillsPanel = new JPanel();
        skillsPanel.setOpaque(false);
        skillsPanel.setLayout(new BoxLayout(skillsPanel, BoxLayout.X_AXIS));
        skillsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel skillsLabel = new JLabel("Skills: ");
        skillsLabel.setPreferredSize(new Dimension(80, 25));
        skillsPanel.add(skillsLabel);

        skillsStatusLabel = new JLabel("Checking...");
        skillsStatusLabel.setForeground(Color.GRAY);
        skillsPanel.add(skillsStatusLabel);
        skillsPanel.add(Box.createHorizontalGlue());

        section.add(skillsPanel);
        section.add(Box.createVerticalStrut(5));

        // Agents status
        JPanel agentsPanel = new JPanel();
        agentsPanel.setOpaque(false);
        agentsPanel.setLayout(new BoxLayout(agentsPanel, BoxLayout.X_AXIS));
        agentsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel agentsLabel = new JLabel("Agents: ");
        agentsLabel.setPreferredSize(new Dimension(80, 25));
        agentsPanel.add(agentsLabel);

        agentsStatusLabel = new JLabel("Checking...");
        agentsStatusLabel.setForeground(Color.GRAY);
        agentsPanel.add(agentsStatusLabel);
        agentsPanel.add(Box.createHorizontalGlue());

        section.add(agentsPanel);
        section.add(Box.createVerticalStrut(10));

        // Refresh button
        JButton refreshButton = new JButton("Refresh Status");
        refreshButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        refreshButton.addActionListener(e -> refreshStatus());
        section.add(refreshButton);

        return section;
    }

    private JLabel createInfoIcon(String tooltipText) {
        JLabel infoIcon = new JLabel("\u24D8"); // Unicode circled letter i
        infoIcon.setForeground(new Color(100, 149, 237)); // Cornflower blue
        infoIcon.setFont(infoIcon.getFont().deriveFont(Font.BOLD, 14f));
        infoIcon.setToolTipText(tooltipText);
        infoIcon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return infoIcon;
    }

    public JPanel getRoot() {
        return this;
    }

    /**
     * Load AI integration configuration from jdeploy object.
     */
    public void load(JSONObject jdeploy) {
        isUpdatingUI = true;
        try {
            // Load available commands from jdeploy.commands
            loadAvailableCommands(jdeploy);

            // Load MCP configuration
            McpServerConfig mcpConfig = null;
            if (jdeploy != null && jdeploy.has("ai")) {
                JSONObject ai = jdeploy.getJSONObject("ai");
                if (ai.has("mcp")) {
                    mcpConfig = McpServerConfig.fromJson(ai.getJSONObject("mcp"));
                }
            }

            if (mcpConfig != null && mcpConfig.isValid()) {
                commandComboBox.setSelectedItem(mcpConfig.getCommand());

                List<String> args = mcpConfig.getArgs();
                if (args != null && !args.isEmpty()) {
                    argsField.setForeground(Color.BLACK);
                    argsField.setText(String.join("\n", args));
                } else {
                    argsField.setForeground(Color.GRAY);
                    argsField.setText(ARGS_PLACEHOLDER);
                }

                defaultEnabledCheckbox.setSelected(mcpConfig.isDefaultEnabled());
            } else {
                commandComboBox.setSelectedItem("");
                argsField.setForeground(Color.GRAY);
                argsField.setText(ARGS_PLACEHOLDER);
                defaultEnabledCheckbox.setSelected(true);
            }

            // Refresh skills/agents status
            refreshStatus();

        } finally {
            isUpdatingUI = false;
        }
    }

    private void loadAvailableCommands(JSONObject jdeploy) {
        availableCommands.clear();
        commandComboBox.removeAllItems();
        commandComboBox.addItem(""); // Empty option

        if (jdeploy == null) {
            System.err.println("[AiIntegrationsPanel] jdeploy object is null");
            return;
        }

        System.err.println("[AiIntegrationsPanel] jdeploy keys: " + jdeploy.keySet());

        if (jdeploy.has("commands")) {
            JSONObject commands = jdeploy.getJSONObject("commands");
            System.err.println("[AiIntegrationsPanel] Found commands: " + commands.keySet());
            for (String key : commands.keySet()) {
                availableCommands.add(key);
                commandComboBox.addItem(key);
            }
        } else {
            System.err.println("[AiIntegrationsPanel] No 'commands' key found in jdeploy");
        }
    }

    /**
     * Save AI integration configuration to jdeploy object.
     */
    public void save(JSONObject jdeploy) {
        String command = (String) commandComboBox.getSelectedItem();
        if (command == null || command.trim().isEmpty()) {
            // No MCP configured - remove ai section if it exists
            if (jdeploy.has("ai")) {
                JSONObject ai = jdeploy.getJSONObject("ai");
                ai.remove("mcp");
                if (ai.isEmpty()) {
                    jdeploy.remove("ai");
                }
            }
            return;
        }

        // Build MCP config
        McpServerConfig.Builder builder = McpServerConfig.builder()
                .command(command.trim())
                .defaultEnabled(defaultEnabledCheckbox.isSelected());

        // Parse args
        String argsText = argsField.getText().trim();
        boolean isPlaceholder = argsText.equals(ARGS_PLACEHOLDER.trim()) && argsField.getForeground().equals(Color.GRAY);

        if (!argsText.isEmpty() && !isPlaceholder) {
            String[] lines = argsText.split("\n");
            List<String> args = new ArrayList<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    args.add(trimmed);
                }
            }
            if (!args.isEmpty()) {
                builder.args(args);
            }
        }

        McpServerConfig config = builder.build();

        // Ensure ai object exists
        if (!jdeploy.has("ai")) {
            jdeploy.put("ai", new JSONObject());
        }
        JSONObject ai = jdeploy.getJSONObject("ai");

        // Save MCP config
        ai.put("mcp", config.toJson());
    }

    /**
     * Register a change listener.
     */
    public void addChangeListener(ActionListener listener) {
        this.changeListener = listener;
    }

    private void refreshStatus() {
        if (projectDir == null) {
            skillsStatusLabel.setText("Project directory not available");
            skillsStatusLabel.setForeground(Color.GRAY);
            agentsStatusLabel.setText("Project directory not available");
            agentsStatusLabel.setForeground(Color.GRAY);
            return;
        }

        // Check skills
        File skillsDir = new File(projectDir, ".jdeploy/skills");
        if (skillsDir.isDirectory()) {
            int count = 0;
            File[] skillDirs = skillsDir.listFiles(File::isDirectory);
            if (skillDirs != null) {
                for (File skillDir : skillDirs) {
                    if (new File(skillDir, "SKILL.md").exists()) {
                        count++;
                    }
                }
            }
            if (count > 0) {
                skillsStatusLabel.setText(count + " skill" + (count == 1 ? "" : "s") + " found in .jdeploy/skills/");
                skillsStatusLabel.setForeground(new Color(0, 128, 0)); // Green
            } else {
                skillsStatusLabel.setText("No skills with SKILL.md found in .jdeploy/skills/");
                skillsStatusLabel.setForeground(Color.GRAY);
            }
        } else {
            skillsStatusLabel.setText("No .jdeploy/skills/ directory");
            skillsStatusLabel.setForeground(Color.GRAY);
        }

        // Check agents
        File agentsDir = new File(projectDir, ".jdeploy/agents");
        if (agentsDir.isDirectory()) {
            int count = 0;
            File[] agentDirs = agentsDir.listFiles(File::isDirectory);
            if (agentDirs != null) {
                for (File agentDir : agentDirs) {
                    if (new File(agentDir, "SKILL.md").exists()) {
                        count++;
                    }
                }
            }
            if (count > 0) {
                agentsStatusLabel.setText(count + " agent" + (count == 1 ? "" : "s") + " found in .jdeploy/agents/");
                agentsStatusLabel.setForeground(new Color(0, 128, 0)); // Green
            } else {
                agentsStatusLabel.setText("No agents with SKILL.md found in .jdeploy/agents/");
                agentsStatusLabel.setForeground(Color.GRAY);
            }
        } else {
            agentsStatusLabel.setText("No .jdeploy/agents/ directory");
            agentsStatusLabel.setForeground(Color.GRAY);
        }
    }

    private void onFieldChanged() {
        if (!isUpdatingUI) {
            fireChangeEvent();
        }
    }

    private void fireChangeEvent() {
        if (changeListener != null) {
            changeListener.actionPerformed(new java.awt.event.ActionEvent(this, 0, "changed"));
        }
    }
}

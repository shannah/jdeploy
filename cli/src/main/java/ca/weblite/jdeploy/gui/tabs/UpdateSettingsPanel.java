package ca.weblite.jdeploy.gui.tabs;

import ca.weblite.jdeploy.gui.util.SwingUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

import org.json.JSONObject;

/**
 * Editor panel for application update settings stored in the {@code jdeploy} object
 * of package.json.
 *
 * <p>Exposes:</p>
 * <ul>
 *   <li><b>appUpdateMode</b> — {@code "auto"} (default, silent update on launch) or
 *       {@code "prompt"} (ask the user before updating).</li>
 *   <li><b>minLauncherInitialAppVersion</b> / <b>minLauncherInitialAppVersionMode</b> —
 *       the minimum initial app version required to run new releases. Either an explicit
 *       version, or the sentinel mode {@code "latest"} which jDeploy resolves to the
 *       published version at publish time.</li>
 *   <li><b>requireLauncherUpdate</b> — force a full launcher update for users on an older
 *       launcher.</li>
 * </ul>
 *
 * <p>In the UI these are framed as <b>App-Only Updates</b> ({@code appUpdateMode} — updates
 * that replace only the app's jar files, applied seamlessly on launch) and <b>Full Updates</b>
 * ({@code minLauncherInitialAppVersion} / {@code requireLauncherUpdate} — updates that run the
 * installer again to update the native launcher along with the app).</p>
 *
 * <p>Follows the established remove-when-default convention: keys are only written when
 * they differ from the default, and removed otherwise.</p>
 */
public class UpdateSettingsPanel extends JPanel {

    static final String KEY_APP_UPDATE_MODE = "appUpdateMode";
    static final String KEY_MIN_INITIAL_VERSION = "minLauncherInitialAppVersion";
    static final String KEY_MIN_INITIAL_VERSION_MODE = "minLauncherInitialAppVersionMode";
    static final String KEY_REQUIRE_LAUNCHER_UPDATE = "requireLauncherUpdate";

    static final String UPDATE_MODE_AUTO = "auto";
    static final String UPDATE_MODE_PROMPT = "prompt";
    static final String MIN_VERSION_MODE_LATEST = "latest";

    private final JRadioButton autoUpdateRadio = new JRadioButton("Update automatically on launch (default)");
    private final JRadioButton promptUpdateRadio = new JRadioButton("Prompt the user before updating");

    private final JRadioButton minNoneRadio = new JRadioButton("Never require a full update (default)");
    private final JRadioButton minLatestRadio = new JRadioButton("Require a full update for every new release");
    private final JRadioButton minExplicitRadio = new JRadioButton("Require a full update for installations older than:");
    private final JTextField minExplicitField = new PlaceholderTextField("App version, e.g. 1.2.0", 14);

    private final JCheckBox requireLauncherUpdateCheckbox =
            new JCheckBox("Don't allow the app to run until the full update is completed");

    private static final String APP_ONLY_HELP =
            "<html><p style='width:400px'>App-only updates replace just your app's jar files, "
                    + "leaving the native launcher untouched. They are fast, and can be applied "
                    + "seamlessly in the background when the user launches your app.</p>"
                    + "<p style='width:400px'>App-only updates are generally sufficient for "
                    + "distributing new releases. If a release depends on newer jDeploy features, "
                    + "or on features of your app that require running the installer again, use "
                    + "Full Updates to require a full update.</p></html>";

    private static final String FULL_UPDATE_HELP =
            "<html><p style='width:400px'>A full update runs through the installation wizard "
                    + "again, updating the native launcher (the harness that hosts your app) in "
                    + "addition to the app's jar files. Full updates are slower than app-only "
                    + "updates and require interaction from the user, so they should only be "
                    + "required when necessary.</p>"
                    + "<p style='width:400px'>A full update is sometimes required — for example, "
                    + "when a release depends on jDeploy features that weren't available in "
                    + "previous versions of your app, or when you've added features (such as new "
                    + "file associations or services) that require a full install.</p>"
                    + "<p style='width:400px'>The version threshold refers to the version the "
                    + "user first installed: users whose original installation is older than the "
                    + "threshold are asked to download a new installer and perform a full update "
                    + "before they can run newer releases.</p></html>";

    private ActionListener changeListener;

    public UpdateSettingsPanel() {
        // Anchor the content at the top: BorderLayout.NORTH keeps the sections at
        // their preferred heights instead of stretching them (and the rows inside
        // them) to fill the editor's content area.
        setLayout(new BorderLayout());
        buildUi();
        initializeChangeListeners();
        updateEnabledState();
    }

    private void buildUi() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // --- App-only updates (jar files only, applied seamlessly on launch) ---
        JPanel modePanel = new JPanel();
        modePanel.setLayout(new BoxLayout(modePanel, BoxLayout.Y_AXIS));
        modePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        modePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("App-Only Updates"),
                BorderFactory.createEmptyBorder(4, 8, 8, 8)));
        modePanel.add(wrapLeft(createHelpLink("App-Only Updates", APP_ONLY_HELP)));
        modePanel.add(wrapLeft(new JLabel(
                "<html><p style='width:420px'>App-only updates replace your app's jar files, "
                        + "and can be applied seamlessly when the user launches your app. "
                        + "Choose what happens when one is available.</p></html>")));
        modePanel.add(Box.createVerticalStrut(6));
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(autoUpdateRadio);
        modeGroup.add(promptUpdateRadio);
        modePanel.add(wrapLeft(autoUpdateRadio));
        modePanel.add(wrapLeft(promptUpdateRadio));
        content.add(modePanel);

        content.add(Box.createVerticalStrut(10));

        // --- Full updates (minimum initial app version + require launcher update) ---
        JPanel minPanel = new JPanel();
        minPanel.setLayout(new BoxLayout(minPanel, BoxLayout.Y_AXIS));
        minPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        minPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Full Updates"),
                BorderFactory.createEmptyBorder(4, 8, 8, 8)));
        minPanel.add(wrapLeft(createHelpLink("Full Updates", FULL_UPDATE_HELP)));
        minPanel.add(wrapLeft(new JLabel(
                "<html><p style='width:420px'>A full update runs the installer again, updating "
                        + "the native launcher along with the app. Choose when users are "
                        + "required to perform one.</p></html>")));
        minPanel.add(Box.createVerticalStrut(6));
        ButtonGroup minGroup = new ButtonGroup();
        minGroup.add(minNoneRadio);
        minGroup.add(minLatestRadio);
        minGroup.add(minExplicitRadio);
        minPanel.add(wrapLeft(minNoneRadio));
        minPanel.add(wrapLeft(minLatestRadio));
        minExplicitField.setToolTipText(
                "<html><p style='width:300px'>The minimum version of <i>your app</i> (not "
                        + "jDeploy). Users who first installed a version older than this will be "
                        + "required to perform a full update.</p></html>");
        JPanel explicitRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        explicitRow.add(minExplicitRadio);
        explicitRow.add(Box.createHorizontalStrut(6));
        explicitRow.add(minExplicitField);
        minPanel.add(wrapLeft(explicitRow));
        minPanel.add(wrapLeft(requireLauncherUpdateCheckbox));
        content.add(minPanel);

        add(content, BorderLayout.NORTH);
    }

    private static JComponent wrapLeft(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(c);
        return row;
    }

    /**
     * Creates a small link-styled label that pops up a fuller description of a section
     * when clicked.
     */
    private JComponent createHelpLink(String title, String helpHtml) {
        JLabel link = new JLabel("<html><u>What are " + title.toLowerCase() + "?</u></html>");
        link.setForeground(new Color(100, 149, 237)); // Cornflower blue
        link.setFont(link.getFont().deriveFont(link.getFont().getSize2D() - 1f));
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                JOptionPane.showMessageDialog(
                        UpdateSettingsPanel.this,
                        new JLabel(helpHtml),
                        title,
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });
        return link;
    }

    private void initializeChangeListeners() {
        autoUpdateRadio.addItemListener(e -> fireChangeEvent());
        promptUpdateRadio.addItemListener(e -> fireChangeEvent());

        minNoneRadio.addItemListener(e -> { updateEnabledState(); fireChangeEvent(); });
        minLatestRadio.addItemListener(e -> { updateEnabledState(); fireChangeEvent(); });
        minExplicitRadio.addItemListener(e -> { updateEnabledState(); fireChangeEvent(); });
        SwingUtils.addChangeListenerTo(minExplicitField, this::fireChangeEvent);

        requireLauncherUpdateCheckbox.addItemListener(e -> fireChangeEvent());
    }

    private void updateEnabledState() {
        minExplicitField.setEnabled(minExplicitRadio.isSelected());
        // The "require full launcher update" flag only matters when a minimum is in effect.
        requireLauncherUpdateCheckbox.setEnabled(!minNoneRadio.isSelected());
    }

    public JPanel getRoot() {
        return this;
    }

    public void load(JSONObject jdeploy) {
        // Auto-update mode
        String mode = jdeploy == null ? UPDATE_MODE_AUTO
                : jdeploy.optString(KEY_APP_UPDATE_MODE, UPDATE_MODE_AUTO);
        if (UPDATE_MODE_PROMPT.equals(mode)) {
            promptUpdateRadio.setSelected(true);
        } else {
            autoUpdateRadio.setSelected(true);
        }

        // Minimum initial app version
        String explicit = jdeploy == null ? "" : jdeploy.optString(KEY_MIN_INITIAL_VERSION, "");
        String minMode = jdeploy == null ? "" : jdeploy.optString(KEY_MIN_INITIAL_VERSION_MODE, "");
        if (MIN_VERSION_MODE_LATEST.equals(minMode)) {
            minLatestRadio.setSelected(true);
            minExplicitField.setText("");
        } else if (explicit != null && !explicit.isEmpty()) {
            minExplicitRadio.setSelected(true);
            minExplicitField.setText(explicit);
        } else {
            minNoneRadio.setSelected(true);
            minExplicitField.setText("");
        }

        // Require full launcher update
        boolean requireUpdate = jdeploy != null && jdeploy.optBoolean(KEY_REQUIRE_LAUNCHER_UPDATE, false);
        requireLauncherUpdateCheckbox.setSelected(requireUpdate);

        updateEnabledState();
    }

    public void save(JSONObject jdeploy) {
        if (jdeploy == null) {
            return;
        }

        // Auto-update mode: only persist the non-default "prompt" value.
        if (promptUpdateRadio.isSelected()) {
            jdeploy.put(KEY_APP_UPDATE_MODE, UPDATE_MODE_PROMPT);
        } else {
            jdeploy.remove(KEY_APP_UPDATE_MODE);
        }

        // Minimum initial app version (the two keys are mutually exclusive).
        if (minLatestRadio.isSelected()) {
            jdeploy.put(KEY_MIN_INITIAL_VERSION_MODE, MIN_VERSION_MODE_LATEST);
            jdeploy.remove(KEY_MIN_INITIAL_VERSION);
        } else if (minExplicitRadio.isSelected() && !minExplicitField.getText().trim().isEmpty()) {
            jdeploy.put(KEY_MIN_INITIAL_VERSION, minExplicitField.getText().trim());
            jdeploy.remove(KEY_MIN_INITIAL_VERSION_MODE);
        } else {
            jdeploy.remove(KEY_MIN_INITIAL_VERSION);
            jdeploy.remove(KEY_MIN_INITIAL_VERSION_MODE);
        }

        // Require full launcher update: only meaningful when a minimum is set.
        boolean minimumSet = minLatestRadio.isSelected()
                || (minExplicitRadio.isSelected() && !minExplicitField.getText().trim().isEmpty());
        if (minimumSet && requireLauncherUpdateCheckbox.isSelected()) {
            jdeploy.put(KEY_REQUIRE_LAUNCHER_UPDATE, true);
        } else {
            jdeploy.remove(KEY_REQUIRE_LAUNCHER_UPDATE);
        }
    }

    public void addChangeListener(ActionListener listener) {
        this.changeListener = listener;
    }

    private void fireChangeEvent() {
        if (changeListener != null) {
            changeListener.actionPerformed(new java.awt.event.ActionEvent(this, 0, "changed"));
        }
    }

    /**
     * A text field that paints a grayed-out placeholder hint while it is empty.
     */
    private static class PlaceholderTextField extends JTextField {

        private final String placeholder;

        PlaceholderTextField(String placeholder, int columns) {
            super(columns);
            this.placeholder = placeholder;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(
                        RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(Color.GRAY);
                g2.setFont(getFont().deriveFont(Font.ITALIC));
                Insets insets = getInsets();
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(placeholder, insets.left + 2,
                        (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        }
    }
}

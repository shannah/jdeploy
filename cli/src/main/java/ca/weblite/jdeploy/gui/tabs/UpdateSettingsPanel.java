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
    private final JRadioButton promptUpdateRadio = new JRadioButton("Prompt me before updating");

    private final JRadioButton minNoneRadio = new JRadioButton("No minimum");
    private final JRadioButton minLatestRadio = new JRadioButton("Auto-set to the published version on each publish");
    private final JRadioButton minExplicitRadio = new JRadioButton("Require at least version:");
    private final JTextField minExplicitField = new JTextField(12);

    private final JCheckBox requireLauncherUpdateCheckbox =
            new JCheckBox("Force a full launcher update for users below the minimum");

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

        // --- Auto-update behaviour ---
        JPanel modePanel = new JPanel();
        modePanel.setLayout(new BoxLayout(modePanel, BoxLayout.Y_AXIS));
        modePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        modePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Auto-Update Behaviour"),
                BorderFactory.createEmptyBorder(4, 8, 8, 8)));
        modePanel.add(wrapLeft(new JLabel(
                "<html><p style='width:420px'>Choose what happens when an update is available "
                        + "the next time the user launches your app.</p></html>")));
        modePanel.add(Box.createVerticalStrut(6));
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(autoUpdateRadio);
        modeGroup.add(promptUpdateRadio);
        modePanel.add(wrapLeft(autoUpdateRadio));
        modePanel.add(wrapLeft(promptUpdateRadio));
        content.add(modePanel);

        content.add(Box.createVerticalStrut(10));

        // --- Minimum initial app version ---
        JPanel minPanel = new JPanel();
        minPanel.setLayout(new BoxLayout(minPanel, BoxLayout.Y_AXIS));
        minPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        minPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Minimum Initial App Version"),
                BorderFactory.createEmptyBorder(4, 8, 8, 8)));
        minPanel.add(wrapLeft(new JLabel(
                "<html><p style='width:420px'>The initial app version is the version a user "
                        + "first installed. Setting a minimum forces users whose initial app "
                        + "version is older to download a new installer and perform a full update "
                        + "before they can run newer releases.</p></html>")));
        minPanel.add(Box.createVerticalStrut(6));
        ButtonGroup minGroup = new ButtonGroup();
        minGroup.add(minNoneRadio);
        minGroup.add(minLatestRadio);
        minGroup.add(minExplicitRadio);
        minPanel.add(wrapLeft(minNoneRadio));
        minPanel.add(wrapLeft(minLatestRadio));
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
}

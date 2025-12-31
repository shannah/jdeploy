package ca.weblite.jdeploy.gui.tabs;

import ca.weblite.jdeploy.factories.PublishTargetFactory;
import ca.weblite.jdeploy.gui.util.SwingUtils;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetServiceInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

public class PublishSettingsPanel extends JPanel {

    /**
     * Callback interface for delegating error display to the parent.
     */
    public interface OnErrorCallback {
        void onError(String message, Exception exception);
    }

    private final PublishTargetFactory publishTargetFactory;
    private final PublishTargetServiceInterface publishTargetService;
    private final OnErrorCallback onErrorCallback;

    private final JCheckBox npmCheckbox;
    private final JCheckBox githubCheckbox;
    private final JTextField githubRepositoryField;

    private ActionListener changeListener;
    private boolean isLoadingFromJson = false;

    public PublishSettingsPanel(PublishTargetFactory publishTargetFactory,
                               PublishTargetServiceInterface publishTargetService,
                               OnErrorCallback onErrorCallback) {
        this.publishTargetFactory = publishTargetFactory;
        this.publishTargetService = publishTargetService;
        this.onErrorCallback = onErrorCallback;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        // npm Section
        JPanel npmPanel = new JPanel();
        npmPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        npmPanel.setBorder(BorderFactory.createTitledBorder("npm"));
        npmCheckbox = new JCheckBox("Publish releases on npm");
        npmPanel.add(npmCheckbox);

        // GitHub Section
        JPanel githubPanel = new JPanel();
        githubPanel.setLayout(new GridBagLayout());
        githubPanel.setBorder(BorderFactory.createTitledBorder("GitHub"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        githubCheckbox = new JCheckBox("Publish releases on GitHub");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        githubPanel.add(githubCheckbox, gbc);

        JLabel githubRepositoryLabel = new JLabel("Release Repository URL:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        githubPanel.add(githubRepositoryLabel, gbc);

        githubRepositoryField = new JTextField(20);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        githubPanel.add(githubRepositoryField, gbc);

        // Add panels to the main panel
        add(npmPanel);
        add(Box.createVerticalStrut(10)); // Add some spacing between sections
        add(githubPanel);

        // Initialize change listeners
        initializeChangeListeners();
    }

    /**
     * Initialize internal listeners for checkbox and text field changes.
     */
    private void initializeChangeListeners() {
        npmCheckbox.addItemListener(e -> onFieldChanged());
        githubCheckbox.addItemListener(e -> onFieldChanged());
        SwingUtils.addChangeListenerTo(githubRepositoryField, this::onFieldChanged);
    }

    /**
     * Called when any field changes. Updates publish targets and fires change event.
     */
    private void onFieldChanged() {
        if (isLoadingFromJson) {
            return;
        }

        try {
            updatePublishTargetsFromUI();
            fireChangeEvent();
        } catch (Exception e) {
            if (onErrorCallback != null) {
                onErrorCallback.onError("Error updating publish targets: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Load publish settings from package.json.
     * Reads npm and GitHub publish targets and populates checkboxes and repository field.
     */
    public void load(JSONObject packageJSON) {
        isLoadingFromJson = true;
        try {
            // Clear existing values
            npmCheckbox.setSelected(false);
            githubCheckbox.setSelected(false);
            githubRepositoryField.setText("");

            if (packageJSON == null) {
                return;
            }

            // Get publish targets from the service
            List<PublishTargetInterface> targets = publishTargetService.getTargetsForPackageJson(packageJSON, false);

            // Process targets to populate UI
            for (PublishTargetInterface target : targets) {
                if (target.getType() == PublishTargetType.NPM) {
                    npmCheckbox.setSelected(true);
                } else if (target.getType() == PublishTargetType.GITHUB) {
                    githubCheckbox.setSelected(true);
                    String url = target.getUrl();
                    if (url != null && !url.isEmpty()) {
                        githubRepositoryField.setText(url);
                    }
                }
            }
        } catch (Exception e) {
            if (onErrorCallback != null) {
                onErrorCallback.onError("Error loading publish settings: " + e.getMessage(), e);
            }
        } finally {
            isLoadingFromJson = false;
        }
    }

    /**
     * Update publish targets in the service based on current UI state.
     */
    private void updatePublishTargetsFromUI() {
        List<PublishTargetInterface> targets = new ArrayList<>();

        if (npmCheckbox.isSelected()) {
            targets.add(publishTargetFactory.createWithUrlAndName("", "npm"));
        }

        if (githubCheckbox.isSelected()) {
            String repositoryUrl = githubRepositoryField.getText().trim();
            targets.add(publishTargetFactory.createWithUrlAndName(repositoryUrl, "github"));
        }

        // Note: The service will handle persisting these targets to the package.json
        // via updatePublishTargetsForPackageJson in the broader context
    }

    /**
     * Add a change listener that fires when any publish setting changes.
     */
    public void addChangeListener(ActionListener listener) {
        this.changeListener = listener;
    }

    /**
     * Fire a change event to notify listeners of modifications.
     */
    private void fireChangeEvent() {
        if (changeListener != null) {
            changeListener.actionPerformed(new java.awt.event.ActionEvent(this, 0, "publishSettingsChanged"));
        }
    }

    // Accessors for UI components
    public JCheckBox getNpmCheckbox() {
        return npmCheckbox;
    }

    public JCheckBox getGithubCheckbox() {
        return githubCheckbox;
    }

    public JTextField getGithubRepositoryField() {
        return githubRepositoryField;
    }

    /**
     * Get the current list of publish targets as they appear in the UI.
     */
    public List<PublishTargetInterface> getPublishTargets() {
        List<PublishTargetInterface> targets = new ArrayList<>();

        if (npmCheckbox.isSelected()) {
            targets.add(publishTargetFactory.createWithUrlAndName("", "npm"));
        }

        if (githubCheckbox.isSelected()) {
            String repositoryUrl = githubRepositoryField.getText().trim();
            targets.add(publishTargetFactory.createWithUrlAndName(repositoryUrl, "github"));
        }

        return targets;
    }
}

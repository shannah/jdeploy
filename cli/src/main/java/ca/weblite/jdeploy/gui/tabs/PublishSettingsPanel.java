package ca.weblite.jdeploy.gui.tabs;

import javax.swing.*;
import java.awt.*;

public class PublishSettingsPanel extends JPanel {

    private final JCheckBox npmCheckbox;
    private final JCheckBox githubCheckbox;
    private final JTextField githubRepositoryField;

    public PublishSettingsPanel() {
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
    }

    // Accessors
    public JCheckBox getNpmCheckbox() {
        return npmCheckbox;
    }

    public JCheckBox getGithubCheckbox() {
        return githubCheckbox;
    }

    public JTextField getGithubRepositoryField() {
        return githubRepositoryField;
    }
}

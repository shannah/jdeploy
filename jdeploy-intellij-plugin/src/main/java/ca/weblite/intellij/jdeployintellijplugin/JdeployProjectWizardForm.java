package ca.weblite.intellij.jdeployintellijplugin;

import javax.swing.*;

public class JdeployProjectWizardForm {
    private JComboBox projectTemplate;
    private JTextField githubUser;
    private JPasswordField githubToken;
    private JPanel root;
    private JCheckBox privateRepository;

    public JPanel getRoot() {
        return root;
    }

    public JComboBox getProjectTemplate() {
        return projectTemplate;
    }

    public JTextField getGithubUser() {
        return githubUser;
    }

    public JPasswordField getGithubToken() {
        return githubToken;
    }

    public JCheckBox getPrivateRepository() {
        return privateRepository;
    }
}

package ca.weblite.jdeploy.gui.tabs;

import ca.weblite.jdeploy.gui.util.SwingUtils;

import javax.swing.*;
import java.awt.event.ActionListener;

import org.json.JSONObject;

public class CheerpJSettingsPanel extends JPanel {
    private CheerpJSettings cheerpJSettings;
    private ActionListener changeListener;

    public CheerpJSettingsPanel() {
        cheerpJSettings = new CheerpJSettings();
        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
        add(cheerpJSettings.getRoot());
        initializeChangeListeners();
    }

    private void initializeChangeListeners() {
        // Add change listener to checkbox
        cheerpJSettings.getEnableCheerpJ().addItemListener(e -> fireChangeEvent());
        
        // Add change listeners to text fields
        SwingUtils.addChangeListenerTo(cheerpJSettings.getGithubPagesBranch(), this::fireChangeEvent);
        SwingUtils.addChangeListenerTo(cheerpJSettings.getGithubPagesBranchPath(), this::fireChangeEvent);
        SwingUtils.addChangeListenerTo(cheerpJSettings.getGithubPagesTagPath(), this::fireChangeEvent);
        SwingUtils.addChangeListenerTo(cheerpJSettings.getGithubPagesPath(), this::fireChangeEvent);
    }

    public JPanel getRoot() {
        return this;
    }

    public CheerpJSettings getCheerpJSettings() {
        return cheerpJSettings;
    }

    public void load(JSONObject jdeploy) {
        if (jdeploy == null || !jdeploy.has("cheerpj")) {
            cheerpJSettings.getEnableCheerpJ().setSelected(false);
            cheerpJSettings.getGithubPagesBranch().setText("");
            cheerpJSettings.getGithubPagesBranchPath().setText("");
            cheerpJSettings.getGithubPagesTagPath().setText("");
            cheerpJSettings.getGithubPagesPath().setText("");
            return;
        }

        JSONObject cheerpj = jdeploy.getJSONObject("cheerpj");

        // Load enabled flag
        if (cheerpj.has("enabled")) {
            cheerpJSettings.getEnableCheerpJ().setSelected(cheerpj.getBoolean("enabled"));
        } else {
            cheerpJSettings.getEnableCheerpJ().setSelected(false);
        }

        // Load githubPages settings
        if (cheerpj.has("githubPages")) {
            JSONObject githubPages = cheerpj.getJSONObject("githubPages");

            if (githubPages.has("branch")) {
                cheerpJSettings.getGithubPagesBranch().setText(githubPages.getString("branch"));
            } else {
                cheerpJSettings.getGithubPagesBranch().setText("");
            }

            if (githubPages.has("branchPath")) {
                cheerpJSettings.getGithubPagesBranchPath().setText(githubPages.getString("branchPath"));
            } else {
                cheerpJSettings.getGithubPagesBranchPath().setText("");
            }

            if (githubPages.has("tagPath")) {
                cheerpJSettings.getGithubPagesTagPath().setText(githubPages.getString("tagPath"));
            } else {
                cheerpJSettings.getGithubPagesTagPath().setText("");
            }

            if (githubPages.has("path")) {
                cheerpJSettings.getGithubPagesPath().setText(githubPages.getString("path"));
            } else {
                cheerpJSettings.getGithubPagesPath().setText("");
            }
        } else {
            cheerpJSettings.getGithubPagesBranch().setText("");
            cheerpJSettings.getGithubPagesBranchPath().setText("");
            cheerpJSettings.getGithubPagesTagPath().setText("");
            cheerpJSettings.getGithubPagesPath().setText("");
        }
    }

    public void save(JSONObject jdeploy) {
        if (jdeploy == null) {
            return;
        }

        boolean enabled = cheerpJSettings.getEnableCheerpJ().isSelected();
        String branch = cheerpJSettings.getGithubPagesBranch().getText().trim();
        String branchPath = cheerpJSettings.getGithubPagesBranchPath().getText().trim();
        String tagPath = cheerpJSettings.getGithubPagesTagPath().getText().trim();
        String path = cheerpJSettings.getGithubPagesPath().getText().trim();

        // Only save cheerpj if enabled
        if (!enabled && (branch.isEmpty() && branchPath.isEmpty() && tagPath.isEmpty() && path.isEmpty())) {
            if (jdeploy.has("cheerpj")) {
                jdeploy.remove("cheerpj");
            }
            return;
        }

        JSONObject cheerpj = new JSONObject();
        cheerpj.put("enabled", enabled);

        JSONObject githubPages = new JSONObject();

        if (!branch.isEmpty()) {
            githubPages.put("branch", branch);
        }
        if (!branchPath.isEmpty()) {
            githubPages.put("branchPath", branchPath);
        }
        if (!tagPath.isEmpty()) {
            githubPages.put("tagPath", tagPath);
        }
        if (!path.isEmpty()) {
            githubPages.put("path", path);
        }

        if (githubPages.length() > 0) {
            cheerpj.put("githubPages", githubPages);
        }

        jdeploy.put("cheerpj", cheerpj);
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

package ca.weblite.jdeploy.gui.controllers;

import ca.weblite.jdeploy.services.GithubWorkflowGenerator;

import javax.swing.*;
import java.awt.*;

public class EditGithubWorkflowController implements Runnable {

    private final GithubWorkflowGenerator githubWorkflowGenerator;

    private final JFrame parentFrame;

    public EditGithubWorkflowController(JFrame parentFrame, GithubWorkflowGenerator githubWorkflowGenerator) {
        this.parentFrame = parentFrame;
        this.githubWorkflowGenerator = githubWorkflowGenerator;
    }
    @Override
    public void run() {
        if (!githubWorkflowGenerator.getGithubWorkflowFile().exists()) {
            JOptionPane.showMessageDialog(parentFrame, "The workflow file was not found.", "Not found", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().edit(githubWorkflowGenerator.getGithubWorkflowFile());
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(parentFrame, "Failed to open workflow file.\nMessage: " + ex.getMessage(), "Not found", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}

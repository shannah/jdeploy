package ca.weblite.jdeploy.gui.controllers;

import ca.weblite.jdeploy.JDeploy;
import ca.weblite.jdeploy.models.NPMApplication;
import ca.weblite.jdeploy.services.GithubWorkflowGenerator;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class GenerateGithubWorkflowController implements Runnable {
    private final JFrame parentFrame;
    private final int javaVersion;

    private final String jdeployVersion;

    private final GithubWorkflowGenerator githubWorkflowGenerator;

    public GenerateGithubWorkflowController(JFrame parentFrame, int javaVersion, String jdeployVersion, GithubWorkflowGenerator githubWorkflowGenerator) {
        this.parentFrame = parentFrame;
        this.javaVersion = javaVersion;
        this.jdeployVersion = jdeployVersion;
        this.githubWorkflowGenerator = githubWorkflowGenerator;
    }

    @Override
    public void run() {
        if (githubWorkflowGenerator.getGithubWorkflowFile().exists()) {
            JOptionPane.showMessageDialog(
                    parentFrame,
                    "A workflow already exists at " + githubWorkflowGenerator.getGithubWorkflowFile()+".\n  " +
                            "Please remove this workflow file and then again", "Workflow Already Exists",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }
        final int result = JOptionPane.showConfirmDialog(parentFrame, new JLabel("<html><p style='width:400px'>" +
                        "Would you like to generate a workflow to automatically build jDeploy releases using Github actions?</p>" +
                        "<p style='width:400px'>\" +\n" +
                        "This will create a workflow file at .github/workflows/jdeploy.yml, which you can then customize to suit your needs.</p>" +
                        "</html>"),
                "Publish to NPM?",
                JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.NO_OPTION) {
            return;
        }
        try {
            githubWorkflowGenerator.generateGithubWorkflow(javaVersion, jdeployVersion);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().edit(githubWorkflowGenerator.getGithubWorkflowFile());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                    parentFrame,
                    "Workflow generation failed.\nMessage: " + ex.getMessage(), "Generation Failed",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }
}

package ca.weblite.jdeploy.gui.controllers;

import ca.weblite.jdeploy.interop.DesktopInterop;
import ca.weblite.jdeploy.services.GithubWorkflowGenerator;

import javax.swing.*;

public class EditGithubWorkflowController implements Runnable {

    private final GithubWorkflowGenerator githubWorkflowGenerator;
    private final JFrame parentFrame;

    private final DesktopInterop desktopInterop;

    public EditGithubWorkflowController(JFrame parentFrame,
                                 GithubWorkflowGenerator githubWorkflowGenerator
    ) {
        this(parentFrame, githubWorkflowGenerator, new DesktopInterop());
    }

    public EditGithubWorkflowController(
            JFrame parentFrame,
            GithubWorkflowGenerator githubWorkflowGenerator,
            DesktopInterop desktopInterop
    ) {
        this.desktopInterop = desktopInterop;
        this.parentFrame = parentFrame;
        this.githubWorkflowGenerator = githubWorkflowGenerator;
    }
    @Override
    public void run() {
        if (!githubWorkflowGenerator.getGithubWorkflowFile().exists()) {
            JOptionPane.showMessageDialog(parentFrame, "The workflow file was not found.", "Not found", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (desktopInterop.isDesktopSupported()) {
            try {
                desktopInterop.edit(githubWorkflowGenerator.getGithubWorkflowFile());
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(parentFrame, "Failed to open workflow file.\nMessage: " + ex.getMessage(), "Not found", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}

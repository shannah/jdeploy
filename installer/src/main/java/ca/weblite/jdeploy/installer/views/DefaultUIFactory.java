package ca.weblite.jdeploy.installer.views;

import ca.weblite.jdeploy.installer.models.InstallationSettings;

import javax.swing.*;

public class DefaultUIFactory implements UIFactory {
    private UI ui = new DefaultUI();
    @Override
    public UI getUI() {
        return ui;
    }

    @Override
    public InstallationForm createInstallationForm(InstallationSettings settings) {
        return new DefaultInstallationForm(settings);
    }

    public void showModalInfoDialog(InstallationForm installationForm, String message, String title) {
        JOptionPane.showMessageDialog((JFrame)installationForm, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    public void showModalErrorDialog(InstallationForm installationForm, String message, String title) {
        JOptionPane.showMessageDialog((JFrame)installationForm, message, title, JOptionPane.ERROR_MESSAGE);
    }

}

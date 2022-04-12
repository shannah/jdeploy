package ca.weblite.jdeploy.installer.views;

import ca.weblite.jdeploy.installer.models.InstallationSettings;

public interface UIFactory {
    public UI getUI();
    public InstallationForm createInstallationForm(InstallationSettings settings);
    public void showModalInfoDialog(InstallationForm installationForm, String message, String title);
    public void showModalErrorDialog(InstallationForm installationForm, String message, String title);

}

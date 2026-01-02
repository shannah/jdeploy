package ca.weblite.jdeploy.installer.views;

import ca.weblite.jdeploy.installer.events.InstallationFormEventDispatcher;
import ca.weblite.jdeploy.installer.events.InstallationFormEventListener;

public interface InstallationForm {
    public void showInstallationCompleteDialog();
    public void showUninstallCompleteDialog();
    public void showInstallationForm();
    public void showTrustConfirmationDialog();
    public void setEventDispatcher(InstallationFormEventDispatcher dispatcher);
    public InstallationFormEventDispatcher getEventDispatcher();

    public void setInProgress(boolean inProgress, String message);
    public void setAppAlreadyInstalled(boolean installed);

}

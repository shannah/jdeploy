package ca.weblite.jdeploy.installer.events;

import ca.weblite.jdeploy.installer.views.InstallationForm;

public class InstallationFormEvent {
    private InstallationForm installationForm;
    private boolean consumed;
    private Type type;
    public InstallationFormEvent(Type type) {
        this.type = type;
    }
    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public void setConsumed(boolean consumed) {
        this.consumed = consumed;
    }

    public InstallationForm getInstallationForm() {
        return installationForm;
    }

    public void setInstallationForm(InstallationForm installationForm) {
        this.installationForm = installationForm;
    }

    public static enum Type {
        InstallClicked,
        InstallCompleteOpenApp,
        InstallCompleteRevealApp,
        InstallCompleteCloseInstaller,
        VisitSoftwareHomepage,
        CancelInstallation,
        ProceedWithInstallation
    }

}

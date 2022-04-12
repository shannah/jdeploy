package ca.weblite.jdeploy.themes.fxtheme;

import ca.weblite.jdeploy.installer.events.InstallationFormEvent;
import ca.weblite.jdeploy.installer.events.InstallationFormEventDispatcher;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.views.InstallationForm;
import javafx.scene.control.Alert;

public class FXInstallationForm implements InstallationForm {
    private InstallationFormEventDispatcher dispatcher;
    private InstallationSettings installationSettings;
    FXApplication app;
    public FXInstallationForm(InstallationSettings settings) {
        this.installationSettings = settings;
        FXApplication.installationForm = this;
    }

    @Override
    public void showInstallationCompleteDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Installation Complete");
        alert.setHeaderText("Installation Complete");
        alert.setContentText("Installation is complete");
        alert.showAndWait();
        getEventDispatcher().fireEvent(InstallationFormEvent.Type.InstallCompleteCloseInstaller);
    }

    @Override
    public void showInstallationForm() {

        FXApplication.launch(FXApplication.class);
    }

    @Override
    public void setEventDispatcher(InstallationFormEventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public InstallationFormEventDispatcher getEventDispatcher() {
        return dispatcher;
    }

    @Override
    public void setInProgress(boolean inProgress, String message) {
        app.setInProgress(inProgress, message);
    }
}

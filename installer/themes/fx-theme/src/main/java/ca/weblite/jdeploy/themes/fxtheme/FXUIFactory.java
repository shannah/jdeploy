package ca.weblite.jdeploy.themes.fxtheme;

import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.views.InstallationForm;
import ca.weblite.jdeploy.installer.views.UI;
import ca.weblite.jdeploy.installer.views.UIFactory;
import javafx.scene.control.Alert;

public class FXUIFactory implements UIFactory {
    private final UI ui = new FXUI();
    @Override
    public UI getUI() {
        return ui;
    }

    @Override
    public InstallationForm createInstallationForm(InstallationSettings settings) {
        return new FXInstallationForm(settings);
    }

    @Override
    public void showModalInfoDialog(InstallationForm installationForm, String message, String title) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public void showModalErrorDialog(InstallationForm installationForm, String message, String title) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

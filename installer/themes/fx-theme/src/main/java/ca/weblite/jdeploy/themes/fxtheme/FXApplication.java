package ca.weblite.jdeploy.themes.fxtheme;

import ca.weblite.jdeploy.installer.events.InstallationFormEvent;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class FXApplication extends Application {
    static FXInstallationForm installationForm;
    private Button install;
    @Override
    public void start(Stage stage) throws Exception {
        installationForm.app = this;
        FXUI.initialized = true;
        install = new Button("Install");
        install.setOnAction(evt->installationForm.getEventDispatcher().fireEvent(InstallationFormEvent.Type.InstallClicked));
        var scene = new Scene(new StackPane(install), 640, 480);
        stage.setScene(scene);
        stage.show();
    }

    void setInProgress(boolean inProgress, String message) {
        if (inProgress) {
            install.setDisable(true);
        } else {
            install.setDisable(false);
        }
    }

}

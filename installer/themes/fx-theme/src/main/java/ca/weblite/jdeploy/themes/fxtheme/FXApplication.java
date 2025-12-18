package ca.weblite.jdeploy.themes.fxtheme;

import ca.weblite.jdeploy.installer.events.InstallationFormEvent;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.File;

/**
 * The FXApplication class used to launch JavaFX.
 */
public class FXApplication extends Application {

    static FXInstallationForm installationForm;
    private Button install;
    @Override
    public void start(Stage stage) throws Exception {
        // Set a back-link to the application instance so that
        // the installationForm can interact with the it.
        installationForm.app = this;

        // We set this flag to inform the UI that JavaFX is initialized.
        // The Platform.runLater() method will throw an exception if used
        // before JavaFX is initialized.  This flag is used to ensure that
        // we don't use JavaFX until it is ready.
        FXUI.initialized = true;

        // Set window icon
        File iconFile = installationForm.installationSettings.getApplicationIcon();
        if (iconFile != null && iconFile.exists()) {
            try {
                Image icon = new Image(iconFile.toURI().toURL().toExternalForm());
                stage.getIcons().add(icon);
            } catch (Exception ex) {
                // Log but don't fail - fall back to default icon
                System.err.println("Warning: Could not load application icon for installer window: " + ex.getMessage());
            }
        }

        // Create an "Install" button that the user presses to proceed with installation.
        install = new Button("Install");

        // Trigger an InstallClicked event when install is pressed.  This will
        // tell the Installer to do its thing.
        install.setOnAction(evt->installationForm.getEventDispatcher().fireEvent(InstallationFormEvent.Type.InstallClicked));

        var scene = new Scene(new StackPane(install), 640, 480);
        stage.setScene(scene);
        stage.show();
    }

    void setInProgress(boolean inProgress, String message) {
        // While installation is in progress, we disable the install button
        // so that it can't be triggered twice.
        if (inProgress) {
            install.setDisable(true);
        } else {
            install.setDisable(false);
        }
    }

}

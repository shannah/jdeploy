package ca.weblite.jdeploy.themes.fxtheme;

import ca.weblite.jdeploy.installer.events.InstallationFormEvent;
import ca.weblite.jdeploy.installer.events.InstallationFormEventDispatcher;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.views.InstallationForm;
import javafx.scene.control.Alert;

/**
 * The InstallationForm instance that wraps the JavaFX installation form. The {@link #showInstallationForm()}}
 * method is what actually launches JavaFX and displays the form, but it also includes other methods
 * that the Installer needs to use to interact with the UI.
 */
public class FXInstallationForm implements InstallationForm {

    // The event dispatcher used for notifying the installer of important
    // events, like when the user presses "Install"
    private InstallationFormEventDispatcher dispatcher;

    // The installation settings.  You should be able to get all of the information
    // you need about the app being installed, and the install environment from this
    // object.
    private InstallationSettings installationSettings;

    // Reference to the FXApplication app.
    FXApplication app;

    /**
     * Creates a new InstallationForm
     * @param settings The pre-populated installation settings.
     */
    public FXInstallationForm(InstallationSettings settings) {
        this.installationSettings = settings;
        FXApplication.installationForm = this;
    }

    /**
     * Displays the dialog when installation is successful and complete.  Should
     * give the user the option to open the app, reveal the app in the explorer/finder,
     * or simply close the installer.
     *
     *
     */
    @Override
    public void showInstallationCompleteDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Installation Complete");
        alert.setHeaderText("Installation Complete");
        alert.setContentText("Installation is complete");
        alert.showAndWait();
        // In this base-bones implementation we simply close the installer on complete.
        getEventDispatcher().fireEvent(InstallationFormEvent.Type.InstallCompleteCloseInstaller);

        // Full implementations should give the user the option to open the app
        // or reveal the app in the explorer
        // getEventDispatcher().fireEvent(InstallationFormEvent.Type.InstallCompleteOpenApp);
        // getEventDispatcher().fireEvent(InstallationFormEvent.Type.InstallCompleteRevealApp);
    }

    /**
     * Shows the installation form.  In this case, it initializes JavaFX also.
     */
    @Override
    public void showInstallationForm() {
        // Show the installation form.
        FXApplication.launch(FXApplication.class);
    }

    @Override
    public void showTrustConfirmationDialog() {

    }

    /**
     * Sets the event dispatcher.  The Installer will use this to set the event dispatcher in this
     * installer as soon as it is instantiated.
     * @param dispatcher The dispatcher to use to send event notifications to the Installer.
     * @see InstallationFormEvent.Type For types of events to send.
     */
    @Override
    public void setEventDispatcher(InstallationFormEventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * Gets the event dispatcher.
     * @return
     */
    @Override
    public InstallationFormEventDispatcher getEventDispatcher() {
        return dispatcher;
    }

    /**
     * Sets and unsets the in-progress status.
     * @param inProgress True if installation is in progress and the UI should show some sort of progress indicator.
     * @param message Text that can be displayed describing the progress.
     */
    @Override
    public void setInProgress(boolean inProgress, String message) {
        // Sets whether installation is in progress
        // Usually this is only a couple of seconds, but you can show an indicator
        // while in progress is true.
        app.setInProgress(inProgress, message);
    }
}

package ca.weblite.jdeploy.installer.views;

import ca.weblite.jdeploy.installer.events.InstallationFormEventDispatcher;
import ca.weblite.jdeploy.installer.events.InstallationFormEventListener;
import ca.weblite.jdeploy.installer.win.AuthenticodeSignatureChecker;

public interface InstallationForm {
    public void showInstallationCompleteDialog();
    public void showUninstallCompleteDialog();
    public void showInstallationForm();
    public void showTrustConfirmationDialog();
    public void setEventDispatcher(InstallationFormEventDispatcher dispatcher);
    public InstallationFormEventDispatcher getEventDispatcher();

    public void setInProgress(boolean inProgress, String message);
    public void setAppAlreadyInstalled(boolean installed);

    /**
     * Shows a dialog asking the user if they want to trust a self-signed certificate.
     * Only called on Windows when the installed exe is signed with an untrusted certificate.
     *
     * @param result the signature check result containing certificate details
     * @return true if the user chose to add the certificate to their trust store
     */
    default boolean showCertificateTrustPrompt(AuthenticodeSignatureChecker.SignatureCheckResult result) {
        return false;
    }

}

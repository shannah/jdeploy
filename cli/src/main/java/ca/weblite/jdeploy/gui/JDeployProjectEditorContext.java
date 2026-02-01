package ca.weblite.jdeploy.gui;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.interop.DesktopInterop;
import ca.weblite.jdeploy.interop.FileChooserInterop;
import ca.weblite.tools.platform.Platform;

import java.awt.*;
import java.io.File;
import java.net.URI;

public class JDeployProjectEditorContext {
    private String npmToken = null;
    private String githubToken = null;

    public DesktopInterop getDesktopInterop() {
        return DIContext.getInstance().getInstance(DesktopInterop.class);
    }

    public FileChooserInterop getFileChooserInterop() {
        return DIContext.getInstance().getInstance(FileChooserInterop.class);
    }

    public boolean shouldDisplayExitMenu() {
        return !Platform.getSystemPlatform().isMac();
    }

    public boolean shouldDisplayApplyButton() {
        return false;
    }

    public boolean shouldDisplayCancelButton() {
        return false;
    }

    public boolean shouldShowPublishButton() {
        return true;
    }

    public boolean shouldDisplayMenuBar() {
        return true;
    }

    public boolean shouldDisplayCheerpJPanel() {
        return false;
    }

    public void edit(File file) throws Exception {
        getDesktopInterop().edit(file);
    }

    public void browse(URI url) throws Exception {
        getDesktopInterop().browse(url);
    }

    public Component getParentFrame() {
        return null;
    }

    /**
     * Can be overridden by subclasses to perform refresh after a file is modified.
     * @param file
     */
    public void onFileUpdated(File file) {

    }

    public boolean useManagedNode() {
        return false;
    }

    public boolean promptForNpmToken(Object parent) {
        return true;
    }

    public void setNpmToken(String npmToken) {
        this.npmToken = npmToken;
    }

    public String getNpmToken() {
        if (npmToken != null) {
            return npmToken;
        }

        return System.getenv("NPM_TOKEN");
    }

    public boolean promptForGithubToken(Object parent) {
        return true;
    }

    public void setGithubToken(String githubToken) {
        this.githubToken = githubToken;
    }

    public String getGithubToken() {
        if (githubToken != null) {
            return githubToken;
        }

        return System.getenv("GITHUB_TOKEN");
    }

    public boolean shouldDisplayPublishSettingsTab() {
        return false;
    }

    public boolean isWebPreviewSupported() {
        return false;
    }

    /**
     * Called before publishing begins. Subclasses can override to show
     * confirmation dialogs or intercept the publish flow.
     * @param parent the parent frame for any dialogs
     * @return true to proceed with publishing, false to cancel
     */
    public boolean confirmPublish(Object parent) {
        return true;
    }

    public void showWebPreview(Frame projectEditorFrame) {
        // Do nothing
    }

    /**
     * Returns whether the project editor should use side panel navigation (IntelliJ-style)
     * instead of tabbed navigation. Defaults to true.
     * 
     * Subclasses can override this method to return false for backward compatibility
     * with the previous tabbed navigation UI.
     * 
     * @return true to use side navigation, false to use tabbed navigation
     */
    public boolean useSideNavigation() {
        return true;
    }
}

package ca.weblite.jdeploy.gui;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.interop.DesktopInterop;
import ca.weblite.jdeploy.interop.FileChooserInterop;
import ca.weblite.tools.platform.Platform;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.net.URI;

public class JDeployProjectEditorContext {
    private String npmToken = null;

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

    public void promptForNpmToken(Object parent) {

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

}

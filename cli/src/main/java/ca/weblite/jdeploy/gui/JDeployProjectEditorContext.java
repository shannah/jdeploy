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
}

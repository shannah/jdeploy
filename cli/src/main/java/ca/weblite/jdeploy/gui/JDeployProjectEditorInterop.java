package ca.weblite.jdeploy.gui;

import ca.weblite.tools.platform.Platform;

import javax.inject.Singleton;
import java.awt.*;
import java.io.File;
import java.net.URI;

@Singleton
public class JDeployProjectEditorInterop {
    public boolean shouldDisplayExitMenu() {
        return !Platform.getSystemPlatform().isMac();
    }

    public boolean shouldShowPublishButton() {
        return true;
    }

    public void edit(File file) throws Exception {
        Desktop.getDesktop().edit(file);
    }

    public void browse(URI url) throws Exception {
        Desktop.getDesktop().browse(url);
    }
}

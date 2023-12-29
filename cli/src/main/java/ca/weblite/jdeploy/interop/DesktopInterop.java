package ca.weblite.jdeploy.interop;

import javax.inject.Singleton;
import java.awt.*;
import java.io.File;
import java.net.URI;

@Singleton
public class DesktopInterop {
    public void edit(File file) throws Exception {
        Desktop.getDesktop().edit(file);
    }

    public void browse(URI url) throws Exception {
        Desktop.getDesktop().browse(url);
    }

    public boolean isDesktopSupported() {
        return Desktop.isDesktopSupported();
    }
}

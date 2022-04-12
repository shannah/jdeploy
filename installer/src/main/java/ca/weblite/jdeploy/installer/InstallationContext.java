package ca.weblite.jdeploy.installer;

import ca.weblite.jdeploy.installer.models.InstallationSettings;
import org.w3c.dom.Document;

import java.io.File;
import java.io.IOException;

public interface InstallationContext {
    public File findInstallFilesDir();
    public void applyContext(InstallationSettings settings);
    public File findAppXml();
    public Document getAppXMLDocument() throws IOException;
}

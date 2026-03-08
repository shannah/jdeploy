package ca.weblite.jdeploy.installer;

import ca.weblite.jdeploy.installer.models.InstallationSettings;

/**
 * Installation settings for local development/testing mode.
 *
 * Unlike HeadlessInstallationSettings (for headless server environments),
 * this enables all GUI integrations (dock, desktop, start menu, programs menu)
 * so developers can test the full installation experience locally.
 *
 * This is used by `jdeploy install` command for local testing.
 */
public class LocalInstallationSettings extends InstallationSettings {

    public LocalInstallationSettings() {
        super();
        // Enable all GUI integrations for full installation experience testing
        // The defaults from InstallationSettings already enable these, but we
        // explicitly set them here for clarity.
        setAddToDock(true);
        setAddToDesktop(true);
        setAddToStartMenu(true);
        setAddToPrograms(true);
    }
}

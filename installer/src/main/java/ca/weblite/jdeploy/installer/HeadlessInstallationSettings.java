package ca.weblite.jdeploy.installer;

import ca.weblite.jdeploy.installer.models.InstallationSettings;

/**
 * Installation settings for headless (CLI) mode.
 * Disables GUI-related features like dock, desktop shortcuts, and start menu items.
 */
public class HeadlessInstallationSettings extends InstallationSettings {

    public HeadlessInstallationSettings() {
        super();
        // Disable dock/desktop/start menu items in headless mode
        // These require GUI interaction or cause screen flashes
        setAddToDock(false);
        setAddToDesktop(false);
        setAddToStartMenu(false);
        setAddToPrograms(false);
    }
}

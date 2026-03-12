package ca.weblite.jdeploy.installer.services;

import ca.weblite.jdeploy.installer.models.AutoUpdateSettings;
import ca.weblite.jdeploy.installer.models.InstallationSettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class InstallerPreferencesService {

    private static final String PREFERENCES_DIR = ".jdeploy" + File.separator + "preferences";
    private static final String PREFERENCES_FILE = "preferences.properties";

    private static final String KEY_VERSION = "version";
    private static final String KEY_PRERELEASE = "prerelease";

    private final File preferencesFile;

    public InstallerPreferencesService(String fullyQualifiedPackageName) {
        File dir = new File(
                System.getProperty("user.home"),
                PREFERENCES_DIR + File.separator + fullyQualifiedPackageName
        );
        this.preferencesFile = new File(dir, PREFERENCES_FILE);
    }

    /**
     * Saves the version and prerelease values as they would appear in app.xml.
     *
     * @param version the computed version string (e.g., "latest", "^1", "~1.2", "1.2.3")
     * @param prerelease the prerelease flag
     */
    public void save(String version, boolean prerelease) {
        Properties props = new Properties();
        props.setProperty(KEY_VERSION, version);
        props.setProperty(KEY_PRERELEASE, String.valueOf(prerelease));

        preferencesFile.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(preferencesFile)) {
            props.store(out, "jDeploy Installer Preferences");
        } catch (IOException e) {
            System.err.println("Warning: Failed to save installer preferences: " + e.getMessage());
        }
    }

    /**
     * Loads saved preferences and applies them to the installation settings.
     * Maps the stored version string back to an AutoUpdateSettings enum value:
     * <ul>
     *   <li>"latest" → Stable</li>
     *   <li>"^..." → MinorOnly</li>
     *   <li>"~..." → PatchesOnly</li>
     *   <li>exact version → Off</li>
     * </ul>
     */
    public void applyTo(InstallationSettings settings) {
        if (!preferencesFile.exists()) {
            return;
        }

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(preferencesFile)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("Warning: Failed to load installer preferences: " + e.getMessage());
            return;
        }

        String version = props.getProperty(KEY_VERSION);
        if (version != null) {
            settings.setAutoUpdate(versionToAutoUpdateSettings(version));
        }

        String prerelease = props.getProperty(KEY_PRERELEASE);
        if (prerelease != null) {
            settings.setPrerelease(Boolean.parseBoolean(prerelease));
        }
    }

    public void delete() {
        if (preferencesFile.exists()) {
            preferencesFile.delete();
        }
        File dir = preferencesFile.getParentFile();
        if (dir.exists() && dir.isDirectory()) {
            String[] children = dir.list();
            if (children == null || children.length == 0) {
                dir.delete();
            }
        }
    }

    public boolean exists() {
        return preferencesFile.exists();
    }

    private static AutoUpdateSettings versionToAutoUpdateSettings(String version) {
        if ("latest".equals(version)) {
            return AutoUpdateSettings.Stable;
        } else if (version.startsWith("^")) {
            return AutoUpdateSettings.MinorOnly;
        } else if (version.startsWith("~")) {
            return AutoUpdateSettings.PatchesOnly;
        } else {
            return AutoUpdateSettings.Off;
        }
    }
}

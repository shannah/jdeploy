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

    private static final String KEY_AUTO_UPDATE = "autoUpdate";
    private static final String KEY_PRERELEASE = "prerelease";

    private final File preferencesFile;

    public InstallerPreferencesService(String fullyQualifiedPackageName) {
        File dir = new File(
                System.getProperty("user.home"),
                PREFERENCES_DIR + File.separator + fullyQualifiedPackageName
        );
        this.preferencesFile = new File(dir, PREFERENCES_FILE);
    }

    public void save(InstallationSettings settings) {
        Properties props = new Properties();
        props.setProperty(KEY_AUTO_UPDATE, settings.getAutoUpdate().name());
        props.setProperty(KEY_PRERELEASE, String.valueOf(settings.isPrerelease()));

        preferencesFile.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(preferencesFile)) {
            props.store(out, "jDeploy Installer Preferences");
        } catch (IOException e) {
            System.err.println("Warning: Failed to save installer preferences: " + e.getMessage());
        }
    }

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

        String autoUpdate = props.getProperty(KEY_AUTO_UPDATE);
        if (autoUpdate != null) {
            try {
                settings.setAutoUpdate(AutoUpdateSettings.valueOf(autoUpdate));
            } catch (IllegalArgumentException e) {
                // Invalid value, keep default
            }
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
}

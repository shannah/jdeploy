package ca.weblite.jdeploy.packaging;

import java.util.prefs.Preferences;

public class PackagingPreferencesService {

    private static final String BUILD_PROJECT_KEY = "buildProjectBeforePackaging";

    public PackagingPreferences getPackagingPreferences(String packageName) {
        Preferences prefs = Preferences.userNodeForPackage(PackagingPreferencesService.class);
        Preferences packagePrefs = prefs.node(packageName);
        boolean buildOnPackage = packagePrefs.getBoolean(BUILD_PROJECT_KEY, false);

        return new PackagingPreferences(
                packageName,
                buildOnPackage
        );
    }

    public void setPackagingPreferences(PackagingPreferences packagingPreferences) {
        Preferences prefs = Preferences.userNodeForPackage(PackagingPreferencesService.class);
        Preferences packagePrefs = prefs.node(packagingPreferences.getPackageName());
        packagePrefs.putBoolean(BUILD_PROJECT_KEY, packagingPreferences.isBuildProjectBeforePackaging());

        try {
            packagePrefs.flush();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save packaging preferences", e);
        }
    }
}

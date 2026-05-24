package ca.weblite.jdeploy.installer.services;

import ca.weblite.jdeploy.installer.models.AutoUpdateSettings;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link InstallerPreferencesService}.
 *
 * <p>Verifies that the auto-update version string the installer writes (e.g.
 * {@code "latest"}, {@code "^1"}, {@code "~1.2"}, {@code "1.2.3"}) round-trips
 * correctly back into the corresponding {@link AutoUpdateSettings} when the
 * installer is re-run for the same package.</p>
 */
public class InstallerPreferencesServiceTest {

    private static final String FQPN = "com.example.testapp";

    @TempDir
    File tempHome;

    private String originalUserHome;
    private InstallerPreferencesService service;

    @BeforeEach
    public void setUp() {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempHome.getAbsolutePath());
        service = new InstallerPreferencesService(FQPN);
    }

    @AfterEach
    public void tearDown() {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }
    }

    private File expectedPreferencesFile() {
        return new File(
                tempHome,
                ".jdeploy" + File.separator + "preferences" + File.separator
                        + FQPN + File.separator + "preferences.properties"
        );
    }

    // --- save() ---

    @Test
    public void save_writesFileAtExpectedPath() {
        service.save("latest", false);
        assertTrue(expectedPreferencesFile().exists(),
                "preferences.properties should be created under ~/.jdeploy/preferences/<fqpn>/");
    }

    @Test
    public void save_persistsVersionAndPrereleaseFlag() throws Exception {
        service.save("^1", true);

        Properties props = new Properties();
        try (java.io.FileInputStream in = new java.io.FileInputStream(expectedPreferencesFile())) {
            props.load(in);
        }
        assertEquals("^1", props.getProperty("version"));
        assertEquals("true", props.getProperty("prerelease"));
    }

    // --- save / applyTo round-trip for each AutoUpdateSettings ---

    @Test
    public void roundTrip_latest_mapsToStable() {
        service.save("latest", false);

        InstallationSettings settings = new InstallationSettings();
        settings.setAutoUpdate(AutoUpdateSettings.Off); // distinct from default to prove apply happened
        service.applyTo(settings);

        assertEquals(AutoUpdateSettings.Stable, settings.getAutoUpdate());
    }

    @Test
    public void roundTrip_caretPrefix_mapsToMinorOnly() {
        service.save("^1", false);

        InstallationSettings settings = new InstallationSettings();
        service.applyTo(settings);

        assertEquals(AutoUpdateSettings.MinorOnly, settings.getAutoUpdate());
    }

    @Test
    public void roundTrip_tildePrefix_mapsToPatchesOnly() {
        service.save("~1.2", false);

        InstallationSettings settings = new InstallationSettings();
        service.applyTo(settings);

        assertEquals(AutoUpdateSettings.PatchesOnly, settings.getAutoUpdate());
    }

    @Test
    public void roundTrip_exactVersion_mapsToOff() {
        service.save("1.2.3", false);

        InstallationSettings settings = new InstallationSettings();
        service.applyTo(settings);

        assertEquals(AutoUpdateSettings.Off, settings.getAutoUpdate());
    }

    @Test
    public void roundTrip_prereleaseFlag_preserved() {
        service.save("latest", true);

        InstallationSettings settings = new InstallationSettings();
        assertFalse(settings.isPrerelease(), "sanity: default should be false");
        service.applyTo(settings);

        assertTrue(settings.isPrerelease());
    }

    // --- applyTo() when no file exists ---

    @Test
    public void applyTo_noFile_leavesSettingsUnchanged() {
        assertFalse(service.exists(), "precondition: no preferences file yet");

        InstallationSettings settings = new InstallationSettings();
        AutoUpdateSettings before = settings.getAutoUpdate();
        boolean prereleaseBefore = settings.isPrerelease();

        service.applyTo(settings);

        assertEquals(before, settings.getAutoUpdate());
        assertEquals(prereleaseBefore, settings.isPrerelease());
    }

    // --- exists() and delete() ---

    @Test
    public void exists_falseBeforeSave_trueAfterSave() {
        assertFalse(service.exists());
        service.save("latest", false);
        assertTrue(service.exists());
    }

    @Test
    public void delete_removesFileAndEmptyParentDir() {
        service.save("latest", false);
        File file = expectedPreferencesFile();
        File parent = file.getParentFile();
        assertTrue(file.exists());
        assertTrue(parent.exists());

        service.delete();

        assertFalse(file.exists(), "preferences.properties should be deleted");
        assertFalse(parent.exists(),
                "empty per-package preferences dir should be cleaned up");
    }

    @Test
    public void delete_isNoOp_whenFileMissing() {
        assertFalse(service.exists());
        // Should not throw.
        service.delete();
        assertFalse(service.exists());
    }

    // --- Per-package isolation ---

    @Test
    public void differentPackages_useSeparateFiles() {
        InstallerPreferencesService other = new InstallerPreferencesService("com.example.otherapp");

        service.save("latest", false);

        assertTrue(service.exists());
        assertFalse(other.exists(),
                "saving for one package must not affect a different package's preferences");

        InstallationSettings settings = new InstallationSettings();
        settings.setAutoUpdate(AutoUpdateSettings.Off);
        other.applyTo(settings);
        assertEquals(AutoUpdateSettings.Off, settings.getAutoUpdate(),
                "applyTo for a package with no saved prefs must leave settings unchanged");
    }

    @Test
    public void save_overwritesPreviousValue() {
        service.save("1.2.3", false);
        service.save("latest", true);

        InstallationSettings settings = new InstallationSettings();
        service.applyTo(settings);

        assertEquals(AutoUpdateSettings.Stable, settings.getAutoUpdate());
        assertTrue(settings.isPrerelease());

        // Sanity: ensure expected file is the one being read.
        assertNotNull(expectedPreferencesFile());
        assertTrue(expectedPreferencesFile().exists());
    }
}

package ca.weblite.jdeploy.installer.services;

import ca.weblite.jdeploy.installer.models.AutoUpdateSettings;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A branch install and a stable install of the same package share one fully qualified package
 * name, and so one preferences file. A branch version must never leak into the stable install.
 */
public class InstallerPreferencesServiceTest {

    private static final String FQPN = "7e6bf55704a3a2fbf352f44ddfe3b5c1.xataworks";

    @TempDir
    File home;

    private String originalHome;

    @BeforeEach
    public void setUp() {
        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", home.getAbsolutePath());
    }

    @AfterEach
    public void tearDown() {
        System.setProperty("user.home", originalHome);
    }

    private File preferencesFile() {
        return new File(home, ".jdeploy/preferences/" + FQPN + "/preferences.properties");
    }

    @Test
    public void savingABranchInstallLeavesTheStablePreferencesUntouched() throws Exception {
        InstallerPreferencesService service = new InstallerPreferencesService(FQPN);
        service.save("latest", false);
        String before = new String(Files.readAllBytes(preferencesFile().toPath()), StandardCharsets.UTF_8);

        service.save("0.0.0-main", true);

        String after = new String(Files.readAllBytes(preferencesFile().toPath()), StandardCharsets.UTF_8);
        assertEquals(before, after);
    }

    @Test
    public void savingABranchInstallWritesNothingWhenThereAreNoPreferences() {
        new InstallerPreferencesService(FQPN).save("0.0.0-main", true);

        assertFalse(preferencesFile().exists());
    }

    @Test
    public void aBranchVersionAlreadyInThePreferencesIsIgnored() throws Exception {
        File f = preferencesFile();
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), "version=0.0.0-main\nprerelease=true\n".getBytes(StandardCharsets.UTF_8));
        InstallationSettings settings = new InstallationSettings();
        AutoUpdateSettings autoUpdate = settings.getAutoUpdate();
        boolean prerelease = settings.isPrerelease();

        new InstallerPreferencesService(FQPN).applyTo(settings);

        assertEquals(autoUpdate, settings.getAutoUpdate());
        assertEquals(prerelease, settings.isPrerelease());
    }

    @Test
    public void aStableVersionIsStillApplied() {
        new InstallerPreferencesService(FQPN).save("~1.2", true);
        InstallationSettings settings = new InstallationSettings();

        new InstallerPreferencesService(FQPN).applyTo(settings);

        assertEquals(AutoUpdateSettings.PatchesOnly, settings.getAutoUpdate());
        assertTrue(settings.isPrerelease());
    }
}

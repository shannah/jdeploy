package ca.weblite.jdeploy.appbundler.mac;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DmgSettingsTest {

    @Test
    public void testDefaults(@TempDir File projectDirectory) {
        DmgSettings settings = DmgSettings.fromPackageJson(new HashMap(), projectDirectory);
        assertNull(settings.getBackground());
        assertEquals(500, settings.getWindowWidth());
        assertEquals(350, settings.getWindowHeight());
        assertEquals(100, settings.getIconSize());
        assertEquals(100, settings.getAppIconX());
        assertEquals(150, settings.getAppIconY());
        assertEquals(400, settings.getApplicationsIconX());
        assertEquals(150, settings.getApplicationsIconY());
    }

    @Test
    public void testConfiguredBackgroundAndGeometry(@TempDir File projectDirectory) throws Exception {
        File background = new File(projectDirectory, "art/background.png");
        background.getParentFile().mkdirs();
        Files.createFile(background.toPath());

        Map dmg = new HashMap();
        dmg.put("background", "art/background.png");
        dmg.put("windowSize", Arrays.asList(600.0, 400.0));
        dmg.put("iconSize", 80.0);
        dmg.put("appIconPosition", Arrays.asList(150, 200));
        dmg.put("applicationsIconPosition", Arrays.asList(450, 200));
        Map jdeploy = new HashMap();
        jdeploy.put("dmg", dmg);

        DmgSettings settings = DmgSettings.fromPackageJson(jdeploy, projectDirectory);
        assertEquals(background, settings.getBackground());
        assertEquals(600, settings.getWindowWidth());
        assertEquals(400, settings.getWindowHeight());
        assertEquals(80, settings.getIconSize());
        assertEquals(150, settings.getAppIconX());
        assertEquals(200, settings.getAppIconY());
        assertEquals(450, settings.getApplicationsIconX());
        assertEquals(200, settings.getApplicationsIconY());
    }

    @Test
    public void testBackgroundFileConvention(@TempDir File projectDirectory) throws Exception {
        File background = new File(projectDirectory, "dmgbackground.tiff");
        Files.createFile(background.toPath());
        assertEquals(background, DmgSettings.fromPackageJson(new HashMap(), projectDirectory).getBackground());
    }

    @Test
    public void testMissingBackgroundFails(@TempDir File projectDirectory) {
        Map dmg = new HashMap();
        dmg.put("background", "nope.tiff");
        Map jdeploy = new HashMap();
        jdeploy.put("dmg", dmg);
        assertThrows(
                IllegalArgumentException.class,
                () -> DmgSettings.fromPackageJson(jdeploy, projectDirectory)
        );
    }

    @Test
    public void testDirectoryBackgroundFails(@TempDir File projectDirectory) {
        new File(projectDirectory, "art").mkdirs();
        Map dmg = new HashMap();
        dmg.put("background", "art");
        Map jdeploy = new HashMap();
        jdeploy.put("dmg", dmg);
        assertThrows(
                IllegalArgumentException.class,
                () -> DmgSettings.fromPackageJson(jdeploy, projectDirectory)
        );
    }

    @Test
    public void testMalformedWindowSizeFails(@TempDir File projectDirectory) {
        Map dmg = new HashMap();
        dmg.put("windowSize", 500.0);
        Map jdeploy = new HashMap();
        jdeploy.put("dmg", dmg);
        assertThrows(
                IllegalArgumentException.class,
                () -> DmgSettings.fromPackageJson(jdeploy, projectDirectory)
        );
    }
}

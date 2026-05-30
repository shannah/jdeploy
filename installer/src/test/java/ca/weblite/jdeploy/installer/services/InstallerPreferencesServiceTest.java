package ca.weblite.jdeploy.installer.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that {@link InstallerPreferencesService} writes the {@code app-update-mode}
 * preference (consumed by the launcher) to preferences.properties.
 */
public class InstallerPreferencesServiceTest {

    private String originalUserHome;
    private File tempHome;

    @BeforeEach
    public void setUp() throws Exception {
        originalUserHome = System.getProperty("user.home");
        tempHome = File.createTempFile("jdeploy-prefs-test", "");
        assertTrue(tempHome.delete());
        assertTrue(tempHome.mkdirs());
        System.setProperty("user.home", tempHome.getAbsolutePath());
    }

    @AfterEach
    public void tearDown() {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }
    }

    private File prefsFileFor(String fqpn) {
        return new File(tempHome, ".jdeploy" + File.separator + "preferences"
                + File.separator + fqpn + File.separator + "preferences.properties");
    }

    private Properties read(File f) throws Exception {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(f)) {
            props.load(in);
        }
        return props;
    }

    @Test
    public void testWritesAppUpdateModePrompt() throws Exception {
        String fqpn = "my-app";
        new InstallerPreferencesService(fqpn).save("latest", false, "prompt");

        File f = prefsFileFor(fqpn);
        assertTrue(f.exists(), "preferences.properties should be created");

        Properties props = read(f);
        assertEquals("prompt", props.getProperty("app-update-mode"));
        assertEquals("latest", props.getProperty("version"));
        assertEquals("false", props.getProperty("prerelease"));
    }

    @Test
    public void testWritesAppUpdateModeAuto() throws Exception {
        String fqpn = "my-app";
        new InstallerPreferencesService(fqpn).save("latest", true, "auto");

        Properties props = read(prefsFileFor(fqpn));
        assertEquals("auto", props.getProperty("app-update-mode"));
        assertEquals("true", props.getProperty("prerelease"));
    }

    @Test
    public void testNullModeDoesNotWriteKey() throws Exception {
        String fqpn = "my-app";
        new InstallerPreferencesService(fqpn).save("latest", false, null);

        Properties props = read(prefsFileFor(fqpn));
        assertNull(props.getProperty("app-update-mode"));
    }

    @Test
    public void testEmptyModeDoesNotWriteKey() throws Exception {
        String fqpn = "my-app";
        new InstallerPreferencesService(fqpn).save("latest", false, "");

        Properties props = read(prefsFileFor(fqpn));
        assertNull(props.getProperty("app-update-mode"));
    }

    @Test
    public void testPreservesExistingModeWhenNullOnReinstall() throws Exception {
        String fqpn = "my-app";
        InstallerPreferencesService service = new InstallerPreferencesService(fqpn);

        // First install sets prompt.
        service.save("latest", false, "prompt");
        // A subsequent save where the published metadata carries no mode (null) must
        // not clobber the existing user preference.
        service.save("^1", true, null);

        Properties props = read(prefsFileFor(fqpn));
        assertEquals("prompt", props.getProperty("app-update-mode"));
        assertEquals("^1", props.getProperty("version"));
        assertEquals("true", props.getProperty("prerelease"));
    }

    @Test
    public void testTwoArgSaveStillWorks() throws Exception {
        String fqpn = "my-app";
        new InstallerPreferencesService(fqpn).save("latest", false);

        Properties props = read(prefsFileFor(fqpn));
        assertEquals("latest", props.getProperty("version"));
        assertNull(props.getProperty("app-update-mode"));
    }
}

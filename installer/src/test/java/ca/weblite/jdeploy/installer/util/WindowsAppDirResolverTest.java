package ca.weblite.jdeploy.installer.util;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class WindowsAppDirResolverTest {

    private static final String USER_HOME = System.getProperty("user.home");
    private static final String FQPN = "com.example.myapp";

    @Test
    public void resolveAppDir_withNullWinAppDir_returnsLegacyPath() {
        File result = WindowsAppDirResolver.resolveAppDir(null, FQPN);
        File expected = new File(USER_HOME, ".jdeploy" + File.separator + "apps" + File.separator + FQPN);
        assertEquals(expected, result);
    }

    @Test
    public void resolveAppDir_withEmptyWinAppDir_returnsLegacyPath() {
        File result = WindowsAppDirResolver.resolveAppDir("", FQPN);
        File expected = new File(USER_HOME, ".jdeploy" + File.separator + "apps" + File.separator + FQPN);
        assertEquals(expected, result);
    }

    @Test
    public void resolveAppDir_withCustomWinAppDir_returnsCustomPath() {
        String customDir = "AppData" + File.separator + "Local" + File.separator + "Programs";
        File result = WindowsAppDirResolver.resolveAppDir(customDir, FQPN);
        File expected = new File(USER_HOME, customDir + File.separator + FQPN);
        assertEquals(expected, result);
    }

    @Test
    public void resolveAppsBaseDir_withNull_returnsLegacyBase() {
        File result = WindowsAppDirResolver.resolveAppsBaseDir(null);
        File expected = new File(USER_HOME, ".jdeploy" + File.separator + "apps");
        assertEquals(expected, result);
    }

    @Test
    public void resolveAppsBaseDir_withCustomDir_returnsCustomBase() {
        String customDir = "AppData" + File.separator + "Local" + File.separator + "Programs";
        File result = WindowsAppDirResolver.resolveAppsBaseDir(customDir);
        File expected = new File(USER_HOME, customDir);
        assertEquals(expected, result);
    }

    @Test
    public void getLegacyAppDir_alwaysReturnsLegacyPath() {
        File result = WindowsAppDirResolver.getLegacyAppDir(FQPN);
        File expected = new File(USER_HOME,
                ".jdeploy" + File.separator + "apps" + File.separator + FQPN);
        assertEquals(expected, result);
    }

    @Test
    public void resolveAppDir_withDefaultConstant_matchesLegacy() {
        File resolved = WindowsAppDirResolver.resolveAppDir(WindowsAppDirResolver.DEFAULT_WIN_APP_DIR, FQPN);
        File legacy = WindowsAppDirResolver.getLegacyAppDir(FQPN);
        assertEquals(legacy, resolved);
    }
}

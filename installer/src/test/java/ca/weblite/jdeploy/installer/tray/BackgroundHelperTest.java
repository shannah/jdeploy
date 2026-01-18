package ca.weblite.jdeploy.installer.tray;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BackgroundHelper context resolution.
 *
 * These tests verify that BackgroundHelper can correctly resolve:
 * - appName from AppInfo.getTitle() or package name fallback
 * - packageName from InstallationSettings or AppInfo fallback
 * - source from InstallationSettings or AppInfo fallback
 */
public class BackgroundHelperTest {

    private InstallationSettings settings;
    private AppInfo appInfo;

    @BeforeEach
    public void setUp() {
        settings = new InstallationSettings();
        appInfo = new AppInfo();
    }

    // ========== getAppName tests ==========

    @Test
    public void testGetAppName_FromAppInfoTitle() throws Exception {
        appInfo.setTitle("My Application");
        appInfo.setNpmPackage("my-package");
        settings.setAppInfo(appInfo);

        BackgroundHelper helper = new BackgroundHelper(settings);
        String appName = invokeGetAppName(helper);

        assertEquals("My Application", appName);
    }

    @Test
    public void testGetAppName_FallsBackToPackageName_WhenTitleIsNull() throws Exception {
        appInfo.setTitle(null);
        appInfo.setNpmPackage("my-package");
        settings.setAppInfo(appInfo);

        BackgroundHelper helper = new BackgroundHelper(settings);
        String appName = invokeGetAppName(helper);

        assertEquals("my-package", appName);
    }

    @Test
    public void testGetAppName_FallsBackToPackageName_WhenTitleIsEmpty() throws Exception {
        appInfo.setTitle("");
        appInfo.setNpmPackage("my-package");
        settings.setAppInfo(appInfo);

        BackgroundHelper helper = new BackgroundHelper(settings);
        String appName = invokeGetAppName(helper);

        assertEquals("my-package", appName);
    }

    @Test
    public void testGetAppName_FallsBackToApplication_WhenNoInfoAvailable() throws Exception {
        settings.setAppInfo(null);

        BackgroundHelper helper = new BackgroundHelper(settings);
        String appName = invokeGetAppName(helper);

        assertEquals("Application", appName);
    }

    // ========== getPackageName tests ==========

    @Test
    public void testGetPackageName_FromSettings() throws Exception {
        settings.setPackageName("settings-package");
        appInfo.setNpmPackage("appinfo-package");
        settings.setAppInfo(appInfo);

        BackgroundHelper helper = new BackgroundHelper(settings);
        String packageName = invokeGetPackageName(helper);

        assertEquals("settings-package", packageName);
    }

    @Test
    public void testGetPackageName_FallsBackToAppInfo_WhenSettingsNull() throws Exception {
        settings.setPackageName(null);
        appInfo.setNpmPackage("appinfo-package");
        settings.setAppInfo(appInfo);

        BackgroundHelper helper = new BackgroundHelper(settings);
        String packageName = invokeGetPackageName(helper);

        assertEquals("appinfo-package", packageName);
    }

    @Test
    public void testGetPackageName_FallsBackToAppInfo_WhenSettingsEmpty() throws Exception {
        settings.setPackageName("");
        appInfo.setNpmPackage("appinfo-package");
        settings.setAppInfo(appInfo);

        BackgroundHelper helper = new BackgroundHelper(settings);
        String packageName = invokeGetPackageName(helper);

        assertEquals("appinfo-package", packageName);
    }

    @Test
    public void testGetPackageName_ReturnsNull_WhenNoInfoAvailable() throws Exception {
        settings.setPackageName(null);
        settings.setAppInfo(null);

        BackgroundHelper helper = new BackgroundHelper(settings);
        String packageName = invokeGetPackageName(helper);

        assertNull(packageName);
    }

    // ========== getSource tests ==========

    @Test
    public void testGetSource_FromSettings() throws Exception {
        settings.setSource("https://github.com/user/repo");
        appInfo.setNpmSource("https://github.com/other/repo");
        settings.setAppInfo(appInfo);

        BackgroundHelper helper = new BackgroundHelper(settings);
        String source = invokeGetSource(helper);

        assertEquals("https://github.com/user/repo", source);
    }

    @Test
    public void testGetSource_FallsBackToAppInfo_WhenSettingsNull() throws Exception {
        settings.setSource(null);
        appInfo.setNpmSource("https://github.com/user/repo");
        settings.setAppInfo(appInfo);

        BackgroundHelper helper = new BackgroundHelper(settings);
        String source = invokeGetSource(helper);

        assertEquals("https://github.com/user/repo", source);
    }

    @Test
    public void testGetSource_ReturnsNull_WhenAppInfoSourceIsEmpty() throws Exception {
        settings.setSource(null);
        appInfo.setNpmSource("");
        settings.setAppInfo(appInfo);

        BackgroundHelper helper = new BackgroundHelper(settings);
        String source = invokeGetSource(helper);

        assertNull(source, "Empty source should be treated as null");
    }

    @Test
    public void testGetSource_ReturnsNull_WhenNoInfoAvailable() throws Exception {
        settings.setSource(null);
        settings.setAppInfo(null);

        BackgroundHelper helper = new BackgroundHelper(settings);
        String source = invokeGetSource(helper);

        assertNull(source);
    }

    // ========== Combined context tests ==========

    @Test
    public void testContextResolution_TypicalHelperScenario() throws Exception {
        // Simulate typical Helper scenario where:
        // - AppInfo is populated from app.xml (via loadAppInfo())
        // - settings.packageName and settings.source are NOT set (only set in install())
        appInfo.setTitle("My App");
        appInfo.setNpmPackage("@myorg/my-app");
        appInfo.setNpmSource("https://github.com/myorg/my-app");
        settings.setAppInfo(appInfo);
        // settings.packageName and source are intentionally NOT set

        BackgroundHelper helper = new BackgroundHelper(settings);

        assertEquals("My App", invokeGetAppName(helper));
        assertEquals("@myorg/my-app", invokeGetPackageName(helper));
        assertEquals("https://github.com/myorg/my-app", invokeGetSource(helper));
    }

    @Test
    public void testContextResolution_NpmPackageWithoutSource() throws Exception {
        // NPM package without GitHub source
        appInfo.setTitle("NPM App");
        appInfo.setNpmPackage("npm-package");
        appInfo.setNpmSource(null);
        settings.setAppInfo(appInfo);

        BackgroundHelper helper = new BackgroundHelper(settings);

        assertEquals("NPM App", invokeGetAppName(helper));
        assertEquals("npm-package", invokeGetPackageName(helper));
        assertNull(invokeGetSource(helper));
    }

    // ========== Constructor validation tests ==========

    @Test
    public void testConstructor_NullSettings_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BackgroundHelper(null);
        });
    }

    // ========== Helper methods for reflection-based testing ==========

    private String invokeGetAppName(BackgroundHelper helper) throws Exception {
        Method method = BackgroundHelper.class.getDeclaredMethod("getAppName");
        method.setAccessible(true);
        return (String) method.invoke(helper);
    }

    private String invokeGetPackageName(BackgroundHelper helper) throws Exception {
        Method method = BackgroundHelper.class.getDeclaredMethod("getPackageName");
        method.setAccessible(true);
        return (String) method.invoke(helper);
    }

    private String invokeGetSource(BackgroundHelper helper) throws Exception {
        Method method = BackgroundHelper.class.getDeclaredMethod("getSource");
        method.setAccessible(true);
        return (String) method.invoke(helper);
    }
}

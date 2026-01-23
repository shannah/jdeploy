package ca.weblite.jdeploy.installer.tray;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.tools.io.MD5;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
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

    // ========== Shutdown Signal Monitoring tests ==========

    @Test
    public void testGetShutdownSignalFile_WithPackageNameOnly() throws Exception {
        appInfo.setNpmPackage("my-package");
        appInfo.setNpmSource(null);
        settings.setAppInfo(appInfo);

        BackgroundHelper helper = new BackgroundHelper(settings);
        File signalFile = helper.getShutdownSignalFile();

        assertNotNull(signalFile);
        assertEquals("my-package.shutdown", signalFile.getName());
        assertTrue(signalFile.getAbsolutePath().contains(".jdeploy" + File.separator + "locks"));
    }

    @Test
    public void testGetShutdownSignalFile_WithPackageNameAndSource() throws Exception {
        appInfo.setNpmPackage("my-package");
        appInfo.setNpmSource("https://github.com/user/repo");
        settings.setAppInfo(appInfo);

        BackgroundHelper helper = new BackgroundHelper(settings);
        File signalFile = helper.getShutdownSignalFile();

        assertNotNull(signalFile);
        // Should have format: {sourceHash}.{sanitizedPackageName}.shutdown
        String expectedHash = MD5.getMd5("https://github.com/user/repo");
        assertEquals(expectedHash + ".my-package.shutdown", signalFile.getName());
    }

    @Test
    public void testGetShutdownSignalFile_SanitizesPackageName() throws Exception {
        appInfo.setNpmPackage("@myorg/my-package");
        appInfo.setNpmSource(null);
        settings.setAppInfo(appInfo);

        BackgroundHelper helper = new BackgroundHelper(settings);
        File signalFile = helper.getShutdownSignalFile();

        assertNotNull(signalFile);
        // @ removed, / replaced with -
        assertEquals("myorg-my-package.shutdown", signalFile.getName());
    }

    @Test
    public void testGetShutdownSignalFile_ReturnsNull_WhenNoPackageName() throws Exception {
        settings.setPackageName(null);
        settings.setAppInfo(null);

        BackgroundHelper helper = new BackgroundHelper(settings);
        File signalFile = helper.getShutdownSignalFile();

        assertNull(signalFile);
    }

    @Test
    public void testShutdownSignalFile_MatchesHelperProcessManagerFormat() throws Exception {
        // This test verifies that the shutdown signal file path created by BackgroundHelper
        // matches what HelperProcessManager.getShutdownSignalFile() would create
        String packageName = "@test/my-app";
        String source = "https://github.com/test/my-app";

        appInfo.setNpmPackage(packageName);
        appInfo.setNpmSource(source);
        settings.setAppInfo(appInfo);

        BackgroundHelper helper = new BackgroundHelper(settings);
        File signalFile = helper.getShutdownSignalFile();

        // Verify the expected path components
        assertNotNull(signalFile);
        assertTrue(signalFile.getAbsolutePath().endsWith(".shutdown"));
        assertTrue(signalFile.getParentFile().getAbsolutePath().endsWith("locks"));

        // The file name should be: {md5(source)}.test-my-app.shutdown
        String expectedSourceHash = MD5.getMd5(source);
        String expectedFileName = expectedSourceHash + ".test-my-app.shutdown";
        assertEquals(expectedFileName, signalFile.getName());
    }

    @Test
    public void testIsShutdownMonitorRunning_InitiallyFalse() throws Exception {
        BackgroundHelper helper = new BackgroundHelper(settings);

        assertFalse(helper.isShutdownMonitorRunning());
    }

    @Test
    public void testSanitizeName_MatchesHelperProcessManager() throws Exception {
        BackgroundHelper helper = new BackgroundHelper(settings);
        Method sanitizeMethod = BackgroundHelper.class.getDeclaredMethod("sanitizeName", String.class);
        sanitizeMethod.setAccessible(true);

        // Test various package name formats
        assertEquals("my-package", sanitizeMethod.invoke(helper, "My-Package"));
        assertEquals("myorg-my-package", sanitizeMethod.invoke(helper, "@myorg/my-package"));
        assertEquals("package-with-spaces", sanitizeMethod.invoke(helper, "Package With Spaces"));
        assertEquals("special--chars--test", sanitizeMethod.invoke(helper, "special:*chars?\"test"));
    }

    @Test
    public void testCreateFullyQualifiedName_WithoutSource() throws Exception {
        BackgroundHelper helper = new BackgroundHelper(settings);
        Method createFqnMethod = BackgroundHelper.class.getDeclaredMethod(
                "createFullyQualifiedName", String.class, String.class);
        createFqnMethod.setAccessible(true);

        String result = (String) createFqnMethod.invoke(helper, "my-package", null);
        assertEquals("my-package", result);

        result = (String) createFqnMethod.invoke(helper, "my-package", "");
        assertEquals("my-package", result);
    }

    @Test
    public void testCreateFullyQualifiedName_WithSource() throws Exception {
        BackgroundHelper helper = new BackgroundHelper(settings);
        Method createFqnMethod = BackgroundHelper.class.getDeclaredMethod(
                "createFullyQualifiedName", String.class, String.class);
        createFqnMethod.setAccessible(true);

        String source = "https://github.com/user/repo";
        String result = (String) createFqnMethod.invoke(helper, "my-package", source);

        String expectedHash = MD5.getMd5(source);
        assertEquals(expectedHash + ".my-package", result);
    }
}

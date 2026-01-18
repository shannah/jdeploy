package ca.weblite.jdeploy.installer.helpers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InstallerBundleLocator utility class.
 *
 * These tests verify the path resolution logic for finding installer bundles
 * and executables across different platforms.
 */
public class InstallerBundleLocatorTest {

    @TempDir
    File tempDir;

    private String originalLauncherPath;

    @BeforeEach
    public void setUp() {
        // Save the original property value
        originalLauncherPath = System.getProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY);
    }

    @AfterEach
    public void tearDown() {
        // Restore the original property value
        if (originalLauncherPath != null) {
            System.setProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY, originalLauncherPath);
        } else {
            System.clearProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY);
        }
    }

    // ========== resolveAppBundle tests ==========

    @Test
    public void testResolveAppBundle_NullPath() {
        File result = InstallerBundleLocator.resolveAppBundle(null);
        assertNull(result, "Should return null for null input");
    }

    @Test
    public void testResolveAppBundle_DirectAppBundle() throws IOException {
        // Create a .app directory
        File appBundle = new File(tempDir, "MyApp.app");
        assertTrue(appBundle.mkdir(), "Failed to create test .app directory");

        File result = InstallerBundleLocator.resolveAppBundle(appBundle);

        assertNotNull(result, "Should find .app bundle");
        assertEquals(appBundle.getAbsolutePath(), result.getAbsolutePath(),
            "Should return the .app directory itself");
    }

    @Test
    public void testResolveAppBundle_LauncherInsideBundle() throws IOException {
        // Create typical macOS bundle structure: MyApp.app/Contents/MacOS/launcher
        File appBundle = new File(tempDir, "MyApp.app");
        File contents = new File(appBundle, "Contents");
        File macOS = new File(contents, "MacOS");
        assertTrue(macOS.mkdirs(), "Failed to create test bundle structure");

        File launcher = new File(macOS, "launcher");
        assertTrue(launcher.createNewFile(), "Failed to create test launcher file");

        File result = InstallerBundleLocator.resolveAppBundle(launcher);

        assertNotNull(result, "Should find .app bundle");
        assertEquals(appBundle.getAbsolutePath(), result.getAbsolutePath(),
            "Should resolve to the .app bundle directory");
    }

    @Test
    public void testResolveAppBundle_DeepNestedPath() throws IOException {
        // Create deeply nested structure: MyApp.app/Contents/Resources/extra/deep/file
        File appBundle = new File(tempDir, "MyApp.app");
        File deep = new File(appBundle, "Contents/Resources/extra/deep");
        assertTrue(deep.mkdirs(), "Failed to create test directory structure");

        File file = new File(deep, "file.txt");
        assertTrue(file.createNewFile(), "Failed to create test file");

        File result = InstallerBundleLocator.resolveAppBundle(file);

        assertNotNull(result, "Should find .app bundle");
        assertEquals(appBundle.getAbsolutePath(), result.getAbsolutePath(),
            "Should resolve to the .app bundle even from deeply nested paths");
    }

    @Test
    public void testResolveAppBundle_NoAppBundle() throws IOException {
        // Create a regular directory structure without .app
        File regularDir = new File(tempDir, "regular/path/to");
        assertTrue(regularDir.mkdirs(), "Failed to create test directory");

        File file = new File(regularDir, "file.txt");
        assertTrue(file.createNewFile(), "Failed to create test file");

        File result = InstallerBundleLocator.resolveAppBundle(file);

        assertNull(result, "Should return null when no .app bundle exists in path");
    }

    @Test
    public void testResolveAppBundle_AppNameContainsAppButNotSuffix() throws IOException {
        // Create a directory that contains "app" but doesn't end with ".app"
        File myAppDir = new File(tempDir, "MyApplication/Contents/MacOS");
        assertTrue(myAppDir.mkdirs(), "Failed to create test directory");

        File launcher = new File(myAppDir, "launcher");
        assertTrue(launcher.createNewFile(), "Failed to create test launcher");

        File result = InstallerBundleLocator.resolveAppBundle(launcher);

        assertNull(result, "Should not match directories that don't end with .app");
    }

    @Test
    public void testResolveAppBundle_MultipleAppBundles() throws IOException {
        // Create nested .app bundles (should find the innermost one first when walking up)
        // Structure: Outer.app/Contents/Inner.app/Contents/MacOS/launcher
        File outerApp = new File(tempDir, "Outer.app");
        File innerApp = new File(outerApp, "Contents/Inner.app");
        File macOS = new File(innerApp, "Contents/MacOS");
        assertTrue(macOS.mkdirs(), "Failed to create test directory structure");

        File launcher = new File(macOS, "launcher");
        assertTrue(launcher.createNewFile(), "Failed to create test launcher");

        File result = InstallerBundleLocator.resolveAppBundle(launcher);

        assertNotNull(result, "Should find an .app bundle");
        assertEquals(innerApp.getAbsolutePath(), result.getAbsolutePath(),
            "Should find the innermost .app bundle (closest ancestor)");
    }

    @Test
    public void testResolveAppBundle_FileAtRoot() {
        // Test with a file at filesystem root (edge case)
        File rootFile = new File("/nonexistent.txt");
        File result = InstallerBundleLocator.resolveAppBundle(rootFile);
        assertNull(result, "Should return null for files not in any .app bundle");
    }

    @Test
    public void testResolveAppBundle_AppBundleIsFile() throws IOException {
        // Create a file named with .app extension (not a directory)
        File appFile = new File(tempDir, "NotABundle.app");
        assertTrue(appFile.createNewFile(), "Failed to create test file");

        // The file itself should not match since it's not a directory
        // But we need to check from inside - create a parent scenario
        File result = InstallerBundleLocator.resolveAppBundle(appFile);

        // Since it's a file not a directory, it won't match isDirectory() check
        // and we'll walk up to tempDir which has no .app
        assertNull(result, "Should not match .app files, only directories");
    }

    // ========== isLauncherPathSet tests ==========

    @Test
    public void testIsLauncherPathSet_WhenSet() {
        System.setProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY, "/some/path");
        assertTrue(InstallerBundleLocator.isLauncherPathSet(),
            "Should return true when property is set");
    }

    @Test
    public void testIsLauncherPathSet_WhenNotSet() {
        System.clearProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY);
        assertFalse(InstallerBundleLocator.isLauncherPathSet(),
            "Should return false when property is not set");
    }

    @Test
    public void testIsLauncherPathSet_WhenEmpty() {
        System.setProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY, "");
        assertFalse(InstallerBundleLocator.isLauncherPathSet(),
            "Should return false when property is empty");
    }

    @Test
    public void testIsLauncherPathSet_WhenWhitespaceOnly() {
        System.setProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY, "   ");
        assertFalse(InstallerBundleLocator.isLauncherPathSet(),
            "Should return false when property is whitespace only");
    }

    // ========== getInstallerPath error condition tests ==========

    @Test
    public void testGetInstallerPath_PropertyNotSet() {
        System.clearProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            InstallerBundleLocator.getInstallerPath();
        });

        assertTrue(exception.getMessage().contains(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY),
            "Exception message should mention the property name");
    }

    @Test
    public void testGetInstallerPath_PropertyEmpty() {
        System.setProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY, "");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            InstallerBundleLocator.getInstallerPath();
        });

        assertTrue(exception.getMessage().contains(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY),
            "Exception message should mention the property name");
    }

    @Test
    public void testGetInstallerPath_PathDoesNotExist() {
        System.setProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY,
            "/nonexistent/path/to/launcher");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            InstallerBundleLocator.getInstallerPath();
        });

        assertTrue(exception.getMessage().contains("does not exist"),
            "Exception message should indicate path doesn't exist");
    }

    // ========== getInstallerPath platform-specific tests ==========

    @Test
    public void testGetInstallerPath_WindowsLinuxDirectPath() throws IOException {
        // This test only runs on Windows/Linux
        if (ca.weblite.tools.platform.Platform.getSystemPlatform().isMac()) {
            return; // Skip on macOS
        }

        // Create a test executable
        File executable = new File(tempDir, "installer.exe");
        assertTrue(executable.createNewFile(), "Failed to create test executable");

        System.setProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY,
            executable.getAbsolutePath());

        File result = InstallerBundleLocator.getInstallerPath();

        assertEquals(executable.getAbsolutePath(), result.getAbsolutePath(),
            "On Windows/Linux, should return the executable path directly");
    }

    @Test
    public void testGetInstallerPath_MacOSResolvesAppBundle() throws IOException {
        // This test only runs on macOS
        if (!ca.weblite.tools.platform.Platform.getSystemPlatform().isMac()) {
            return; // Skip on Windows/Linux
        }

        // Create a macOS bundle structure
        File appBundle = new File(tempDir, "TestInstaller.app");
        File macOS = new File(appBundle, "Contents/MacOS");
        assertTrue(macOS.mkdirs(), "Failed to create test bundle structure");

        File launcher = new File(macOS, "launcher");
        assertTrue(launcher.createNewFile(), "Failed to create test launcher");

        System.setProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY,
            launcher.getAbsolutePath());

        File result = InstallerBundleLocator.getInstallerPath();

        assertEquals(appBundle.getAbsolutePath(), result.getAbsolutePath(),
            "On macOS, should resolve to the .app bundle");
    }

    @Test
    public void testGetInstallerPath_MacOSNoAppBundle() throws IOException {
        // This test only runs on macOS
        if (!ca.weblite.tools.platform.Platform.getSystemPlatform().isMac()) {
            return; // Skip on Windows/Linux
        }

        // Create a file not inside any .app bundle
        File launcher = new File(tempDir, "launcher");
        assertTrue(launcher.createNewFile(), "Failed to create test launcher");

        System.setProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY,
            launcher.getAbsolutePath());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            InstallerBundleLocator.getInstallerPath();
        });

        assertTrue(exception.getMessage().contains(".app bundle"),
            "Exception message should mention .app bundle");
    }
}

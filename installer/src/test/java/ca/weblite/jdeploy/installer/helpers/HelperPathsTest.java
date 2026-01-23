package ca.weblite.jdeploy.installer.helpers;

import ca.weblite.tools.platform.Platform;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HelperPaths utility class.
 *
 * These tests verify platform-specific path resolution for the Helper application.
 * Some assertions are conditional based on the current platform since Platform.getSystemPlatform()
 * is a static method that returns the actual system platform.
 */
public class HelperPathsTest {

    private static final String TEST_APP_NAME = "My Test App";
    private static final String TEST_APP_SIMPLE = "TestApp";
    private static final File TEST_APP_DIR = new File("/test/apps/myapp");

    @Test
    public void testGetHelperDirectory_Mac() {
        // This test only runs on macOS since null appDirectory throws on other platforms
        if (!Platform.getSystemPlatform().isMac()) {
            return; // Skip on non-Mac platforms
        }

        File helperDir = HelperPaths.getHelperDirectory(TEST_APP_NAME, null);

        assertNotNull(helperDir, "Helper directory should not be null");

        // On macOS, should be ~/Applications/{AppName} Helper/
        String expectedPath = System.getProperty("user.home") + File.separator +
            "Applications" + File.separator + TEST_APP_NAME + " Helper";
        assertEquals(expectedPath, helperDir.getAbsolutePath(), "macOS helper directory path incorrect");
        assertTrue(helperDir.getName().endsWith(" Helper"),
            "macOS helper directory should end with ' Helper'");
    }

    @Test
    public void testGetHelperDirectory_WindowsLinux() {
        File helperDir = HelperPaths.getHelperDirectory(TEST_APP_NAME, TEST_APP_DIR);

        assertNotNull(helperDir, "Helper directory should not be null");

        if (Platform.getSystemPlatform().isWindows() || Platform.getSystemPlatform().isLinux()) {
            // On Windows/Linux, should be {appDirectory}/helpers/
            File expectedDir = new File(TEST_APP_DIR, "helpers");
            assertEquals(expectedDir.getAbsolutePath(), helperDir.getAbsolutePath(),
                "Windows/Linux helper directory path incorrect");
            assertEquals("helpers", helperDir.getName(),
                "Windows/Linux helper directory should be 'helpers'");
        }
    }

    @Test
    public void testGetHelperDirectory_NullAppName() {
        assertThrows(IllegalArgumentException.class, () -> {
            HelperPaths.getHelperDirectory(null, TEST_APP_DIR);
        });
    }

    @Test
    public void testGetHelperDirectory_EmptyAppName() {
        assertThrows(IllegalArgumentException.class, () -> {
            HelperPaths.getHelperDirectory("", TEST_APP_DIR);
        });
    }

    @Test
    public void testGetHelperDirectory_NullAppDirectory_WindowsLinux() {
        if (Platform.getSystemPlatform().isWindows() || Platform.getSystemPlatform().isLinux()) {
            assertThrows(IllegalArgumentException.class, () -> {
                HelperPaths.getHelperDirectory(TEST_APP_NAME, null);
            });
        }
        // On macOS, this should succeed since appDirectory is ignored - tested in testGetHelperDirectory_Mac
    }

    @Test
    public void testGetHelperExecutablePath_Mac() {
        // This test only runs on macOS since null appDirectory throws on other platforms
        if (!Platform.getSystemPlatform().isMac()) {
            return; // Skip on non-Mac platforms
        }

        File helperExe = HelperPaths.getHelperExecutablePath(TEST_APP_NAME, null);

        assertNotNull(helperExe, "Helper executable path should not be null");

        // On macOS, should be ~/Applications/{AppName} Helper/{AppName} Helper.app
        String userHome = System.getProperty("user.home");
        String expectedPath = userHome + File.separator + "Applications" + File.separator +
            TEST_APP_NAME + " Helper" + File.separator + TEST_APP_NAME + " Helper.app";
        assertEquals(expectedPath, helperExe.getAbsolutePath(), "macOS helper executable path incorrect");
        assertTrue(helperExe.getName().endsWith(".app"), "macOS helper should end with .app");
        assertEquals(TEST_APP_NAME + " Helper.app", helperExe.getName(), "macOS helper app name incorrect");
    }

    @Test
    public void testGetHelperExecutablePath_Windows() {
        File helperExe = HelperPaths.getHelperExecutablePath(TEST_APP_NAME, TEST_APP_DIR);

        assertNotNull(helperExe, "Helper executable path should not be null");

        if (Platform.getSystemPlatform().isWindows()) {
            // On Windows, should be {appDirectory}/helpers/my-test-app-helper.exe
            String expectedPath = TEST_APP_DIR.getAbsolutePath() + File.separator +
                "helpers" + File.separator + "my-test-app-helper.exe";
            assertEquals(expectedPath, helperExe.getAbsolutePath(), "Windows helper executable path incorrect");
            assertTrue(helperExe.getName().endsWith(".exe"), "Windows helper should end with .exe");
            assertEquals("my-test-app-helper.exe", helperExe.getName(), "Windows helper name incorrect");
        }
    }

    @Test
    public void testGetHelperExecutablePath_Linux() {
        File helperExe = HelperPaths.getHelperExecutablePath(TEST_APP_NAME, TEST_APP_DIR);

        assertNotNull(helperExe, "Helper executable path should not be null");

        if (Platform.getSystemPlatform().isLinux()) {
            // On Linux, should be {appDirectory}/helpers/my-test-app-helper
            String expectedPath = TEST_APP_DIR.getAbsolutePath() + File.separator +
                "helpers" + File.separator + "my-test-app-helper";
            assertEquals(expectedPath, helperExe.getAbsolutePath(), "Linux helper executable path incorrect");
            assertFalse(helperExe.getName().contains("."), "Linux helper should not have extension");
            assertEquals("my-test-app-helper", helperExe.getName(), "Linux helper name incorrect");
        }
    }

    @Test
    public void testGetHelperContextDirectory_Mac() {
        // This test only runs on macOS since null appDirectory throws on other platforms
        if (!Platform.getSystemPlatform().isMac()) {
            return; // Skip on non-Mac platforms
        }

        File contextDir = HelperPaths.getHelperContextDirectory(TEST_APP_NAME, null);

        assertNotNull(contextDir, "Context directory should not be null");
        assertEquals(".jdeploy-files", contextDir.getName(), "Context directory should be .jdeploy-files");

        // On macOS, should be ~/Applications/{AppName} Helper/.jdeploy-files
        String userHome = System.getProperty("user.home");
        String expectedPath = userHome + File.separator + "Applications" + File.separator +
            TEST_APP_NAME + " Helper" + File.separator + ".jdeploy-files";
        assertEquals(expectedPath, contextDir.getAbsolutePath(), "macOS context directory path incorrect");
    }

    @Test
    public void testGetHelperContextDirectory_WindowsLinux() {
        File contextDir = HelperPaths.getHelperContextDirectory(TEST_APP_NAME, TEST_APP_DIR);

        assertNotNull(contextDir, "Context directory should not be null");
        assertEquals(".jdeploy-files", contextDir.getName(), "Context directory should be .jdeploy-files");

        if (Platform.getSystemPlatform().isWindows() || Platform.getSystemPlatform().isLinux()) {
            // On Windows/Linux, should be {appDirectory}/helpers/.jdeploy-files
            String expectedPath = TEST_APP_DIR.getAbsolutePath() + File.separator +
                "helpers" + File.separator + ".jdeploy-files";
            assertEquals(expectedPath, contextDir.getAbsolutePath(), "Windows/Linux context directory path incorrect");
        }
    }

    @Test
    public void testDeriveHelperName_Mac() {
        String helperName = HelperPaths.deriveHelperName(TEST_APP_NAME);

        assertNotNull(helperName, "Helper name should not be null");

        if (Platform.getSystemPlatform().isMac()) {
            // On macOS, should preserve case and add " Helper"
            assertEquals(TEST_APP_NAME + " Helper", helperName, "macOS helper name incorrect");
        }
    }

    @Test
    public void testDeriveHelperName_WindowsLinux() {
        String helperName = HelperPaths.deriveHelperName(TEST_APP_NAME);

        assertNotNull(helperName, "Helper name should not be null");

        if (Platform.getSystemPlatform().isWindows() || Platform.getSystemPlatform().isLinux()) {
            // On Windows/Linux, should be lowercase with hyphens and "-helper"
            assertEquals("my-test-app-helper", helperName, "Windows/Linux helper name incorrect");
        }
    }

    @Test
    public void testDeriveHelperName_SpecialCharacters() {
        // Test with special characters that should be removed on Windows/Linux
        String appNameWithSpecialChars = "My App! (Beta) v2.0";
        String helperName = HelperPaths.deriveHelperName(appNameWithSpecialChars);

        assertNotNull(helperName, "Helper name should not be null");

        if (Platform.getSystemPlatform().isMac()) {
            // macOS preserves everything and adds " Helper"
            assertEquals(appNameWithSpecialChars + " Helper", helperName,
                "macOS helper name should preserve special chars");
        } else {
            // Windows/Linux should remove special chars, keep only alphanumeric and hyphens
            assertEquals("my-app-beta-v20-helper", helperName,
                "Windows/Linux helper name should remove special chars");
        }
    }

    @Test
    public void testDeriveHelperName_SimpleAppName() {
        String helperName = HelperPaths.deriveHelperName(TEST_APP_SIMPLE);

        assertNotNull(helperName, "Helper name should not be null");

        if (Platform.getSystemPlatform().isMac()) {
            assertEquals("TestApp Helper", helperName, "macOS simple app helper name incorrect");
        } else {
            assertEquals("testapp-helper", helperName, "Windows/Linux simple app helper name incorrect");
        }
    }

    @Test
    public void testDeriveHelperName_MultipleSpaces() {
        String appNameWithSpaces = "My   App   Name";
        String helperName = HelperPaths.deriveHelperName(appNameWithSpaces);

        assertNotNull(helperName, "Helper name should not be null");

        if (Platform.getSystemPlatform().isMac()) {
            assertEquals(appNameWithSpaces + " Helper", helperName,
                "macOS helper name should preserve spaces");
        } else {
            // Windows/Linux: spaces become hyphens
            assertEquals("my---app---name-helper", helperName,
                "Windows/Linux helper name should convert spaces to hyphens");
        }
    }

    @Test
    public void testDeriveHelperName_NullAppTitle() {
        assertThrows(IllegalArgumentException.class, () -> {
            HelperPaths.deriveHelperName(null);
        });
    }

    @Test
    public void testDeriveHelperName_EmptyAppTitle() {
        assertThrows(IllegalArgumentException.class, () -> {
            HelperPaths.deriveHelperName("");
        });
    }

    @Test
    public void testDeriveHelperName_WhitespaceOnlyAppTitle() {
        assertThrows(IllegalArgumentException.class, () -> {
            HelperPaths.deriveHelperName("   ");
        });
    }

    @Test
    public void testPathConsistency() {
        // Verify that paths returned by different methods are consistent
        File helperDir = HelperPaths.getHelperDirectory(TEST_APP_NAME, TEST_APP_DIR);
        File helperExe = HelperPaths.getHelperExecutablePath(TEST_APP_NAME, TEST_APP_DIR);
        File contextDir = HelperPaths.getHelperContextDirectory(TEST_APP_NAME, TEST_APP_DIR);

        // Helper executable should be in helper directory
        assertEquals(helperDir.getAbsolutePath(), helperExe.getParentFile().getAbsolutePath(),
            "Helper executable parent should be helper directory");

        // Context directory should be in helper directory
        assertEquals(helperDir.getAbsolutePath(), contextDir.getParentFile().getAbsolutePath(),
            "Context directory parent should be helper directory");
    }

    @Test
    public void testDeriveHelperName_NumbersAndHyphens() {
        // Test that numbers and existing hyphens are preserved
        String appName = "App-123";
        String helperName = HelperPaths.deriveHelperName(appName);

        assertNotNull(helperName, "Helper name should not be null");

        if (Platform.getSystemPlatform().isMac()) {
            assertEquals("App-123 Helper", helperName, "macOS should preserve hyphens and numbers");
        } else {
            assertEquals("app-123-helper", helperName, "Windows/Linux should preserve hyphens and numbers");
        }
    }

    @Test
    public void testDeriveHelperName_LeadingTrailingSpaces() {
        // Test that leading/trailing spaces don't cause issues
        String appName = "  MyApp  ";
        String helperName = HelperPaths.deriveHelperName(appName.trim());

        assertNotNull(helperName, "Helper name should not be null");

        if (Platform.getSystemPlatform().isMac()) {
            assertEquals("MyApp Helper", helperName, "macOS helper name with trimmed input");
        } else {
            assertEquals("myapp-helper", helperName, "Windows/Linux helper name with trimmed input");
        }
    }
}

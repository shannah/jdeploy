package ca.weblite.jdeploy.installer.helpers;

import ca.weblite.tools.platform.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HelperSelfDeleteService.
 */
public class HelperSelfDeleteServiceTest {

    @TempDir
    File tempDir;

    private HelperCleanupScriptGenerator mockScriptGenerator;
    private Logger mockLogger;
    private HelperSelfDeleteService service;

    private String testAppName;
    private File appDirectory;
    private String originalLauncherPath;

    @BeforeEach
    public void setUp() {
        // Save and clear jdeploy.launcher.path to ensure clean test state
        originalLauncherPath = System.getProperty("jdeploy.launcher.path");
        System.clearProperty("jdeploy.launcher.path");

        mockScriptGenerator = mock(HelperCleanupScriptGenerator.class);
        mockLogger = mock(Logger.class);

        service = new HelperSelfDeleteService(mockScriptGenerator, mockLogger);

        // Use a unique app name to avoid conflicts with existing directories on the test machine
        testAppName = "JDeployTest_" + UUID.randomUUID().toString().substring(0, 8);
        appDirectory = tempDir;
    }

    @AfterEach
    public void tearDown() {
        // Restore original jdeploy.launcher.path
        if (originalLauncherPath != null) {
            System.setProperty("jdeploy.launcher.path", originalLauncherPath);
        } else {
            System.clearProperty("jdeploy.launcher.path");
        }
    }

    // ========== Constructor validation tests ==========

    @Test
    public void testConstructor_NullScriptGenerator_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new HelperSelfDeleteService(null, mockLogger);
        });
    }

    @Test
    public void testConstructor_NullLogger_DoesNotThrow() {
        assertDoesNotThrow(() -> {
            new HelperSelfDeleteService(mockScriptGenerator, null);
        });
    }

    @Test
    public void testConstructor_SingleArg_DoesNotThrow() {
        assertDoesNotThrow(() -> {
            new HelperSelfDeleteService(mockScriptGenerator);
        });
    }

    // ========== scheduleHelperCleanup validation tests ==========

    @Test
    public void testScheduleHelperCleanup_NullAppName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.scheduleHelperCleanup(null, appDirectory);
        });
    }

    @Test
    public void testScheduleHelperCleanup_EmptyAppName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.scheduleHelperCleanup("", appDirectory);
        });
    }

    @Test
    public void testScheduleHelperCleanup_BlankAppName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.scheduleHelperCleanup("   ", appDirectory);
        });
    }

    // ========== scheduleHelperCleanup behavior tests ==========

    @Test
    public void testScheduleHelperCleanup_HelperDoesNotExist_ReturnsTrue() {
        // Helper doesn't exist
        File helperPath = HelperPaths.getHelperExecutablePath(testAppName, appDirectory);
        assertFalse(helperPath.exists());

        // Execute
        boolean result = service.scheduleHelperCleanup(testAppName, appDirectory);

        // Verify - should return true (not an error, nothing to clean up)
        assertTrue(result, "Should return true when Helper doesn't exist");

        // Verify script generator was NOT called
        verifyNoInteractions(mockScriptGenerator);
    }

    @Test
    public void testScheduleHelperCleanup_HelperExists_GeneratesAndExecutesScript() throws IOException {
        // Create existing Helper
        File helperPath = HelperPaths.getHelperExecutablePath(testAppName, appDirectory);
        helperPath.getParentFile().mkdirs();
        helperPath.createNewFile();

        File helperContextDir = HelperPaths.getHelperContextDirectory(testAppName, appDirectory);
        File helperDir = HelperPaths.getHelperDirectory(testAppName, appDirectory);

        // Mock script generation
        File mockScript = new File(tempDir, "cleanup.sh");
        mockScript.createNewFile();
        when(mockScriptGenerator.generateCleanupScript(any(), any(), any())).thenReturn(mockScript);

        // Execute
        boolean result = service.scheduleHelperCleanup(testAppName, appDirectory);

        // Verify
        assertTrue(result, "Should return true on success");
        verify(mockScriptGenerator).generateCleanupScript(eq(helperPath), eq(helperContextDir), eq(helperDir));
        verify(mockScriptGenerator).executeCleanupScript(mockScript);
    }

    @Test
    public void testScheduleHelperCleanup_ScriptGenerationFails_ReturnsFalse() throws IOException {
        // Create existing Helper
        File helperPath = HelperPaths.getHelperExecutablePath(testAppName, appDirectory);
        helperPath.getParentFile().mkdirs();
        helperPath.createNewFile();

        // Mock script generation failure
        when(mockScriptGenerator.generateCleanupScript(any(), any(), any()))
                .thenThrow(new IOException("Script generation failed"));

        // Execute
        boolean result = service.scheduleHelperCleanup(testAppName, appDirectory);

        // Verify
        assertFalse(result, "Should return false on script generation failure");
        verify(mockScriptGenerator, never()).executeCleanupScript(any());
    }

    @Test
    public void testScheduleHelperCleanup_ScriptExecutionFails_ReturnsFalse() throws IOException {
        // Create existing Helper
        File helperPath = HelperPaths.getHelperExecutablePath(testAppName, appDirectory);
        helperPath.getParentFile().mkdirs();
        helperPath.createNewFile();

        // Mock script generation success but execution failure
        File mockScript = new File(tempDir, "cleanup.sh");
        mockScript.createNewFile();
        when(mockScriptGenerator.generateCleanupScript(any(), any(), any())).thenReturn(mockScript);
        doThrow(new IOException("Execution failed")).when(mockScriptGenerator).executeCleanupScript(any());

        // Execute
        boolean result = service.scheduleHelperCleanup(testAppName, appDirectory);

        // Verify
        assertFalse(result, "Should return false on script execution failure");
    }

    @Test
    public void testScheduleHelperCleanup_NullAppDirectory_UsesHelperPaths() throws IOException {
        // This test only runs on macOS since null appDirectory throws on other platforms
        if (!Platform.getSystemPlatform().isMac()) {
            return; // Skip on non-Mac platforms
        }

        // Create existing Helper at macOS location (null appDirectory uses ~/Applications)
        // For this test, we'll create at the expected location
        File helperPath = HelperPaths.getHelperExecutablePath(testAppName, null);

        // Only run this test if we can create the helper in the expected location
        if (helperPath.getParentFile().mkdirs() || helperPath.getParentFile().exists()) {
            try {
                helperPath.createNewFile();

                File mockScript = new File(tempDir, "cleanup.sh");
                mockScript.createNewFile();
                when(mockScriptGenerator.generateCleanupScript(any(), any(), any())).thenReturn(mockScript);

                // Execute
                boolean result = service.scheduleHelperCleanup(testAppName, null);

                // Verify script was generated (path resolution worked)
                verify(mockScriptGenerator).generateCleanupScript(any(), any(), any());
            } finally {
                // Cleanup
                helperPath.delete();
                helperPath.getParentFile().delete();
                if (helperPath.getParentFile().getParentFile() != null) {
                    helperPath.getParentFile().getParentFile().delete();
                }
            }
        }
    }

    // ========== getHelperPath tests ==========

    @Test
    public void testGetHelperPath_NoSystemProperty_UsesHelperPaths() {
        // System property already cleared in setUp

        File result = service.getHelperPath(testAppName, appDirectory);
        File expected = HelperPaths.getHelperExecutablePath(testAppName, appDirectory);

        assertEquals(expected.getAbsolutePath(), result.getAbsolutePath(),
                "Should use HelperPaths when system property is not set");
    }

    @Test
    public void testGetHelperPath_WithSystemProperty_UsesPropertyPath() throws IOException {
        // Create a test launcher file
        File testLauncher = new File(tempDir, "test-helper");
        testLauncher.createNewFile();

        System.setProperty("jdeploy.launcher.path", testLauncher.getAbsolutePath());

        File result = service.getHelperPath(testAppName, appDirectory);

        assertEquals(testLauncher.getAbsolutePath(), result.getAbsolutePath(),
                "Should use path from system property when set");
    }

    @Test
    public void testGetHelperPath_MacOSAppBundle_ResolvesToBundleRoot() throws IOException {
        // This test only runs on macOS since .app bundle resolution uses forward slashes
        if (!Platform.getSystemPlatform().isMac()) {
            return; // Skip on non-Mac platforms
        }

        // Create a mock macOS app bundle structure
        File appBundle = new File(tempDir, "Test Helper.app");
        File contents = new File(appBundle, "Contents");
        File macOS = new File(contents, "MacOS");
        macOS.mkdirs();
        File launcher = new File(macOS, "Test Helper");
        launcher.createNewFile();

        System.setProperty("jdeploy.launcher.path", launcher.getAbsolutePath());

        File result = service.getHelperPath(testAppName, appDirectory);

        assertEquals(appBundle.getAbsolutePath(), result.getAbsolutePath(),
                "Should resolve to .app bundle root on macOS");
    }

    @Test
    public void testGetHelperPath_SystemPropertyFileDoesNotExist_FallsBackToHelperPaths() {
        System.setProperty("jdeploy.launcher.path", "/nonexistent/path/to/helper");

        File result = service.getHelperPath(testAppName, appDirectory);
        File expected = HelperPaths.getHelperExecutablePath(testAppName, appDirectory);

        assertEquals(expected.getAbsolutePath(), result.getAbsolutePath(),
                "Should fall back to HelperPaths when system property path doesn't exist");
    }

    // ========== helperExists tests ==========

    @Test
    public void testHelperExists_HelperDoesNotExist_ReturnsFalse() {
        assertFalse(service.helperExists(testAppName, appDirectory),
                "Should return false when Helper doesn't exist");
    }

    @Test
    public void testHelperExists_HelperExists_ReturnsTrue() throws IOException {
        // Create Helper
        File helperPath = HelperPaths.getHelperExecutablePath(testAppName, appDirectory);
        helperPath.getParentFile().mkdirs();
        helperPath.createNewFile();

        assertTrue(service.helperExists(testAppName, appDirectory),
                "Should return true when Helper exists");
    }

    @Test
    public void testHelperExists_NullAppName_ReturnsFalse() {
        assertFalse(service.helperExists(null, appDirectory),
                "Should return false for null appName");
    }

    @Test
    public void testHelperExists_EmptyAppName_ReturnsFalse() {
        assertFalse(service.helperExists("", appDirectory),
                "Should return false for empty appName");
    }

    // ========== scheduleCurrentHelperCleanup tests ==========

    @Test
    public void testScheduleCurrentHelperCleanup_NoSystemProperty_ReturnsFalse() {
        // System property already cleared in setUp

        boolean result = service.scheduleCurrentHelperCleanup();

        assertFalse(result, "Should return false when jdeploy.launcher.path is not set");
        verifyNoInteractions(mockScriptGenerator);
    }

    @Test
    public void testScheduleCurrentHelperCleanup_LauncherDoesNotExist_ReturnsFalse() {
        System.setProperty("jdeploy.launcher.path", "/nonexistent/path");

        boolean result = service.scheduleCurrentHelperCleanup();

        assertFalse(result, "Should return false when launcher file doesn't exist");
        verifyNoInteractions(mockScriptGenerator);
    }

    @Test
    public void testScheduleCurrentHelperCleanup_LauncherExists_GeneratesScript() throws IOException {
        // Create a test launcher
        File testLauncher = new File(tempDir, "test-helper");
        testLauncher.createNewFile();

        File mockScript = new File(tempDir, "cleanup.sh");
        mockScript.createNewFile();
        when(mockScriptGenerator.generateCleanupScript(any(), any(), any())).thenReturn(mockScript);

        System.setProperty("jdeploy.launcher.path", testLauncher.getAbsolutePath());

        boolean result = service.scheduleCurrentHelperCleanup();

        assertTrue(result, "Should return true on success");
        verify(mockScriptGenerator).generateCleanupScript(any(), any(), any());
        verify(mockScriptGenerator).executeCleanupScript(mockScript);
    }

    @Test
    public void testScheduleCurrentHelperCleanup_ScriptGenerationFails_ReturnsFalse() throws IOException {
        // Create a test launcher
        File testLauncher = new File(tempDir, "test-helper");
        testLauncher.createNewFile();

        when(mockScriptGenerator.generateCleanupScript(any(), any(), any()))
                .thenThrow(new IOException("Script generation failed"));

        System.setProperty("jdeploy.launcher.path", testLauncher.getAbsolutePath());

        boolean result = service.scheduleCurrentHelperCleanup();

        assertFalse(result, "Should return false on failure");
    }

    // ========== Error handling tests ==========

    @Test
    public void testScheduleHelperCleanup_UnexpectedException_ReturnsFalse() throws IOException {
        // Create existing Helper
        File helperPath = HelperPaths.getHelperExecutablePath(testAppName, appDirectory);
        helperPath.getParentFile().mkdirs();
        helperPath.createNewFile();

        // Mock unexpected exception
        when(mockScriptGenerator.generateCleanupScript(any(), any(), any()))
                .thenThrow(new RuntimeException("Unexpected error"));

        // Execute
        boolean result = service.scheduleHelperCleanup(testAppName, appDirectory);

        // Verify - should handle gracefully
        assertFalse(result, "Should return false on unexpected exception");
    }

    @Test
    public void testScheduleHelperCleanup_LogsInfoMessages() throws IOException {
        // Create existing Helper
        File helperPath = HelperPaths.getHelperExecutablePath(testAppName, appDirectory);
        helperPath.getParentFile().mkdirs();
        helperPath.createNewFile();

        File mockScript = new File(tempDir, "cleanup.sh");
        mockScript.createNewFile();
        when(mockScriptGenerator.generateCleanupScript(any(), any(), any())).thenReturn(mockScript);

        // Execute
        service.scheduleHelperCleanup(testAppName, appDirectory);

        // Verify logging
        verify(mockLogger, atLeastOnce()).info(anyString());
    }

    // ========== Additional error context tests ==========

    @Test
    public void testScheduleHelperCleanup_ScriptGenerationFails_LogsContextualError() throws IOException {
        // Create existing Helper
        File helperPath = HelperPaths.getHelperExecutablePath(testAppName, appDirectory);
        helperPath.getParentFile().mkdirs();
        helperPath.createNewFile();

        // Mock script generation failure
        when(mockScriptGenerator.generateCleanupScript(any(), any(), any()))
                .thenThrow(new IOException("Disk full"));

        // Execute
        boolean result = service.scheduleHelperCleanup(testAppName, appDirectory);

        // Verify
        assertFalse(result, "Should return false on script generation failure");

        // Verify error message includes context (app name and path)
        verify(mockLogger).log(
                eq(Level.WARNING),
                contains(testAppName),
                any(IOException.class));
    }

    @Test
    public void testScheduleCurrentHelperCleanup_ScriptExecutionFails_LogsContextualError() throws IOException {
        // Create a test launcher
        File testLauncher = new File(tempDir, "test-helper");
        testLauncher.createNewFile();

        File mockScript = new File(tempDir, "cleanup.sh");
        mockScript.createNewFile();
        when(mockScriptGenerator.generateCleanupScript(any(), any(), any())).thenReturn(mockScript);
        doThrow(new IOException("Permission denied")).when(mockScriptGenerator).executeCleanupScript(any());

        System.setProperty("jdeploy.launcher.path", testLauncher.getAbsolutePath());

        boolean result = service.scheduleCurrentHelperCleanup();

        assertFalse(result, "Should return false on script execution failure");

        // Verify error message includes path context
        verify(mockLogger).log(
                eq(Level.WARNING),
                contains(testLauncher.getAbsolutePath()),
                any(IOException.class));
    }

    @Test
    public void testScheduleCurrentHelperCleanup_UnexpectedException_LogsSevereError() throws IOException {
        // Create a test launcher
        File testLauncher = new File(tempDir, "test-helper");
        testLauncher.createNewFile();

        // Mock unexpected exception (not IOException)
        when(mockScriptGenerator.generateCleanupScript(any(), any(), any()))
                .thenThrow(new RuntimeException("Unexpected null pointer"));

        System.setProperty("jdeploy.launcher.path", testLauncher.getAbsolutePath());

        boolean result = service.scheduleCurrentHelperCleanup();

        assertFalse(result, "Should return false on unexpected exception");

        // Verify severe error was logged with path context
        verify(mockLogger).log(
                eq(Level.SEVERE),
                contains(testLauncher.getAbsolutePath()),
                any(Exception.class));
    }

    @Test
    public void testScheduleHelperCleanup_IncludesPathInWarningWhenKnown() throws IOException {
        // Create existing Helper
        File helperPath = HelperPaths.getHelperExecutablePath(testAppName, appDirectory);
        helperPath.getParentFile().mkdirs();
        helperPath.createNewFile();

        // Mock script generation failure
        when(mockScriptGenerator.generateCleanupScript(any(), any(), any()))
                .thenThrow(new IOException("Cannot write script"));

        // Execute
        service.scheduleHelperCleanup(testAppName, appDirectory);

        // Verify the warning includes the helper path
        verify(mockLogger).log(
                eq(Level.WARNING),
                argThat((String msg) -> msg.contains(helperPath.getAbsolutePath())),
                any(IOException.class));
    }
}

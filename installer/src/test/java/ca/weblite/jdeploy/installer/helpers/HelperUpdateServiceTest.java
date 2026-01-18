package ca.weblite.jdeploy.installer.helpers;

import ca.weblite.jdeploy.installer.logging.InstallationLogger;
import ca.weblite.tools.platform.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HelperUpdateService.
 */
public class HelperUpdateServiceTest {

    @TempDir
    File tempDir;

    private HelperInstallationService mockInstallationService;
    private HelperProcessManager mockProcessManager;
    private InstallationLogger mockLogger;
    private HelperUpdateService service;

    private String testPackageName;
    private String testSource;
    private String testAppName;
    private File jdeployFilesDir;

    @BeforeEach
    public void setUp() throws IOException {
        mockInstallationService = mock(HelperInstallationService.class);
        mockProcessManager = mock(HelperProcessManager.class);
        mockLogger = mock(InstallationLogger.class);

        service = new HelperUpdateService(mockInstallationService, mockProcessManager, mockLogger);

        testPackageName = "@test/app";
        testSource = null; // npm package (no source)
        testAppName = "TestApp";
        jdeployFilesDir = new File(tempDir, ".jdeploy-files");
        jdeployFilesDir.mkdir();
    }

    // ========== Constructor validation tests ==========

    @Test
    public void testConstructor_NullInstallationService_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new HelperUpdateService(null, mockProcessManager, mockLogger);
        });
    }

    @Test
    public void testConstructor_NullProcessManager_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new HelperUpdateService(mockInstallationService, null, mockLogger);
        });
    }

    @Test
    public void testConstructor_NullLogger_DoesNotThrow() {
        assertDoesNotThrow(() -> {
            new HelperUpdateService(mockInstallationService, mockProcessManager, null);
        });
    }

    // ========== updateHelper validation tests ==========

    @Test
    public void testUpdateHelper_NullPackageName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.updateHelper(null, testSource, testAppName, tempDir, jdeployFilesDir, true);
        });
    }

    @Test
    public void testUpdateHelper_EmptyPackageName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.updateHelper("", testSource, testAppName, tempDir, jdeployFilesDir, true);
        });
    }

    @Test
    public void testUpdateHelper_BlankPackageName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.updateHelper("   ", testSource, testAppName, tempDir, jdeployFilesDir, true);
        });
    }

    @Test
    public void testUpdateHelper_NullAppName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.updateHelper(testPackageName, testSource, null, tempDir, jdeployFilesDir, true);
        });
    }

    @Test
    public void testUpdateHelper_EmptyAppName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.updateHelper(testPackageName, testSource, "", tempDir, jdeployFilesDir, true);
        });
    }

    // ========== updateHelper with servicesExist=true, no existing Helper ==========

    @Test
    public void testUpdateHelper_ServicesExist_NoExistingHelper_InstallsHelper() {
        // Helper doesn't exist
        File helperExecutable = HelperPaths.getHelperExecutablePath(testAppName, tempDir);
        assertFalse(helperExecutable.exists());

        // Mock successful installation
        HelperInstallationResult successResult = HelperInstallationResult.success(
                helperExecutable, new File(tempDir, "helpers/.jdeploy-files"));
        when(mockInstallationService.installHelper(testAppName, tempDir, jdeployFilesDir))
                .thenReturn(successResult);

        // Execute
        HelperUpdateService.HelperUpdateResult result = service.updateHelper(
                testPackageName, testSource, testAppName, tempDir, jdeployFilesDir, true);

        // Verify
        assertTrue(result.isSuccess(), "Update should succeed");
        assertEquals(HelperUpdateService.HelperUpdateResult.UpdateType.INSTALLED, result.getType(),
                "Should report as INSTALLED (new Helper)");
        assertNotNull(result.getInstallationResult(), "Should have installation result");

        verify(mockInstallationService).installHelper(testAppName, tempDir, jdeployFilesDir);
        verify(mockProcessManager, never()).terminateHelper(anyString(), any(), anyString(), anyLong());
    }

    @Test
    public void testUpdateHelper_ServicesExist_NoExistingHelper_InstallFails() {
        // Helper doesn't exist
        File helperExecutable = HelperPaths.getHelperExecutablePath(testAppName, tempDir);
        assertFalse(helperExecutable.exists());

        // Mock failed installation
        HelperInstallationResult failResult = HelperInstallationResult.failure("Installation failed");
        when(mockInstallationService.installHelper(testAppName, tempDir, jdeployFilesDir))
                .thenReturn(failResult);

        // Execute
        HelperUpdateService.HelperUpdateResult result = service.updateHelper(
                testPackageName, testSource, testAppName, tempDir, jdeployFilesDir, true);

        // Verify
        assertFalse(result.isSuccess(), "Update should fail");
        assertEquals(HelperUpdateService.HelperUpdateResult.UpdateType.FAILED, result.getType());
        assertNotNull(result.getErrorMessage(), "Should have error message");
    }

    // ========== updateHelper with servicesExist=true, existing Helper ==========

    @Test
    public void testUpdateHelper_ServicesExist_ExistingHelper_UpdatesHelper() throws IOException {
        // Create existing Helper at the correct location
        File helperExecutable = HelperPaths.getHelperExecutablePath(testAppName, tempDir);
        helperExecutable.getParentFile().mkdirs();
        helperExecutable.createNewFile();

        File helperContextDir = HelperPaths.getHelperContextDirectory(testAppName, tempDir);

        // Helper is not running
        when(mockProcessManager.isHelperRunning(testPackageName, testSource)).thenReturn(false);

        // Mock successful installation
        HelperInstallationResult successResult = HelperInstallationResult.success(
                helperExecutable, helperContextDir);
        when(mockInstallationService.installHelper(testAppName, tempDir, jdeployFilesDir))
                .thenReturn(successResult);

        // Execute
        HelperUpdateService.HelperUpdateResult result = service.updateHelper(
                testPackageName, testSource, testAppName, tempDir, jdeployFilesDir, true);

        // Verify
        assertTrue(result.isSuccess(), "Update should succeed");
        assertEquals(HelperUpdateService.HelperUpdateResult.UpdateType.UPDATED, result.getType(),
                "Should report as UPDATED (existing Helper replaced)");

        verify(mockInstallationService).installHelper(testAppName, tempDir, jdeployFilesDir);
    }

    @Test
    public void testUpdateHelper_ServicesExist_ExistingRunningHelper_TerminatesFirst() throws IOException {
        // Create existing Helper at the correct location
        File helperExecutable = HelperPaths.getHelperExecutablePath(testAppName, tempDir);
        helperExecutable.getParentFile().mkdirs();
        helperExecutable.createNewFile();

        File helperContextDir = HelperPaths.getHelperContextDirectory(testAppName, tempDir);

        // Helper is running
        when(mockProcessManager.isHelperRunning(testPackageName, testSource)).thenReturn(true);
        when(mockProcessManager.terminateHelper(eq(testPackageName), eq(testSource), eq(testAppName), anyLong())).thenReturn(true);

        // Mock successful installation
        HelperInstallationResult successResult = HelperInstallationResult.success(
                helperExecutable, helperContextDir);
        when(mockInstallationService.installHelper(testAppName, tempDir, jdeployFilesDir))
                .thenReturn(successResult);

        // Execute
        HelperUpdateService.HelperUpdateResult result = service.updateHelper(
                testPackageName, testSource, testAppName, tempDir, jdeployFilesDir, true);

        // Verify termination was called
        verify(mockProcessManager).terminateHelper(eq(testPackageName), eq(testSource), eq(testAppName), anyLong());
        assertTrue(result.isSuccess(), "Update should succeed");
    }

    @Test
    public void testUpdateHelper_ServicesExist_TerminationFails_ContinuesAnyway() throws IOException {
        // Create existing Helper at the correct location
        File helperExecutable = HelperPaths.getHelperExecutablePath(testAppName, tempDir);
        helperExecutable.getParentFile().mkdirs();
        helperExecutable.createNewFile();

        File helperContextDir = HelperPaths.getHelperContextDirectory(testAppName, tempDir);

        // Helper is running but termination fails
        when(mockProcessManager.isHelperRunning(testPackageName, testSource)).thenReturn(true);
        when(mockProcessManager.terminateHelper(eq(testPackageName), eq(testSource), eq(testAppName), anyLong())).thenReturn(false);

        // Mock successful installation (file deleted manually for test)
        // The deleteHelper will run and delete the file
        HelperInstallationResult successResult = HelperInstallationResult.success(
                helperExecutable, helperContextDir);
        when(mockInstallationService.installHelper(testAppName, tempDir, jdeployFilesDir))
                .thenReturn(successResult);

        // Execute - should continue despite termination failure
        HelperUpdateService.HelperUpdateResult result = service.updateHelper(
                testPackageName, testSource, testAppName, tempDir, jdeployFilesDir, true);

        // Verify termination was attempted
        verify(mockProcessManager).terminateHelper(eq(testPackageName), eq(testSource), eq(testAppName), anyLong());
        // Update may still succeed or fail depending on delete
    }

    // ========== updateHelper with servicesExist=false ==========

    @Test
    public void testUpdateHelper_ServicesRemoved_NoExistingHelper_NoActionNeeded() {
        // Helper doesn't exist
        File helperExecutable = HelperPaths.getHelperExecutablePath(testAppName, tempDir);
        assertFalse(helperExecutable.exists());

        // Execute
        HelperUpdateService.HelperUpdateResult result = service.updateHelper(
                testPackageName, testSource, testAppName, tempDir, jdeployFilesDir, false);

        // Verify
        assertTrue(result.isSuccess(), "Should succeed (nothing to do)");
        assertEquals(HelperUpdateService.HelperUpdateResult.UpdateType.NO_ACTION, result.getType(),
                "Should report NO_ACTION");

        verify(mockInstallationService, never()).installHelper(anyString(), any(), any());
        verify(mockProcessManager, never()).terminateHelper(anyString(), any(), anyString(), anyLong());
    }

    @Test
    public void testUpdateHelper_ServicesRemoved_ExistingHelper_RemovesHelper() throws IOException {
        // Create existing Helper at the correct location
        File helperExecutable = HelperPaths.getHelperExecutablePath(testAppName, tempDir);
        helperExecutable.getParentFile().mkdirs();
        helperExecutable.createNewFile();

        File helperContextDir = HelperPaths.getHelperContextDirectory(testAppName, tempDir);
        helperContextDir.mkdirs();
        new File(helperContextDir, "app.xml").createNewFile();

        // Helper is not running
        when(mockProcessManager.isHelperRunning(testPackageName, testSource)).thenReturn(false);

        // Execute
        HelperUpdateService.HelperUpdateResult result = service.updateHelper(
                testPackageName, testSource, testAppName, tempDir, jdeployFilesDir, false);

        // Verify
        assertTrue(result.isSuccess(), "Removal should succeed");
        assertEquals(HelperUpdateService.HelperUpdateResult.UpdateType.REMOVED, result.getType(),
                "Should report REMOVED");

        // Verify files were deleted
        assertFalse(helperExecutable.exists(), "Helper executable should be deleted");
        assertFalse(helperContextDir.exists(), "Context directory should be deleted");

        verify(mockInstallationService, never()).installHelper(anyString(), any(), any());
    }

    @Test
    public void testUpdateHelper_ServicesRemoved_RunningHelper_TerminatesFirst() throws IOException {
        // Create existing Helper at the correct location
        File helperExecutable = HelperPaths.getHelperExecutablePath(testAppName, tempDir);
        helperExecutable.getParentFile().mkdirs();
        helperExecutable.createNewFile();

        // Helper is running
        when(mockProcessManager.isHelperRunning(testPackageName, testSource)).thenReturn(true);
        when(mockProcessManager.terminateHelper(eq(testPackageName), eq(testSource), eq(testAppName), anyLong())).thenReturn(true);

        // Execute
        HelperUpdateService.HelperUpdateResult result = service.updateHelper(
                testPackageName, testSource, testAppName, tempDir, jdeployFilesDir, false);

        // Verify termination was called
        verify(mockProcessManager).terminateHelper(eq(testPackageName), eq(testSource), eq(testAppName), anyLong());
        assertTrue(result.isSuccess(), "Removal should succeed");
        assertEquals(HelperUpdateService.HelperUpdateResult.UpdateType.REMOVED, result.getType());
    }

    // ========== deleteHelper tests ==========

    @Test
    public void testDeleteHelper_NullAppName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.deleteHelper(null, tempDir);
        });
    }

    @Test
    public void testDeleteHelper_EmptyAppName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.deleteHelper("", tempDir);
        });
    }

    @Test
    public void testDeleteHelper_NoExistingHelper_ReturnsTrue() {
        // Helper doesn't exist
        File helperExecutable = HelperPaths.getHelperExecutablePath(testAppName, tempDir);
        assertFalse(helperExecutable.exists());

        // Execute
        boolean result = service.deleteHelper(testAppName, tempDir);

        // Verify
        assertTrue(result, "Should return true when nothing to delete");
    }

    @Test
    public void testDeleteHelper_ExistingExecutable_DeletesFile() throws IOException {
        // Create Helper executable at the correct location
        File helperExecutable = HelperPaths.getHelperExecutablePath(testAppName, tempDir);
        helperExecutable.getParentFile().mkdirs();
        helperExecutable.createNewFile();

        assertTrue(helperExecutable.exists(), "Helper should exist before delete");

        // Execute
        boolean result = service.deleteHelper(testAppName, tempDir);

        // Verify
        assertTrue(result, "Delete should succeed");
        assertFalse(helperExecutable.exists(), "Helper executable should be deleted");
    }

    @Test
    public void testDeleteHelper_ExistingContextDir_DeletesDirectory() throws IOException {
        // Create Helper context directory with files at the correct location
        File helperContextDir = HelperPaths.getHelperContextDirectory(testAppName, tempDir);
        helperContextDir.mkdirs();
        new File(helperContextDir, "app.xml").createNewFile();

        assertTrue(helperContextDir.exists(), "Context dir should exist before delete");

        // Execute
        boolean result = service.deleteHelper(testAppName, tempDir);

        // Verify
        assertTrue(result, "Delete should succeed");
        assertFalse(helperContextDir.exists(), "Context directory should be deleted");
    }

    @Test
    public void testDeleteHelper_EmptyHelpersDir_DeletesDirectory() throws IOException {
        if (Platform.getSystemPlatform().isMac()) {
            return; // macOS has different directory structure
        }

        // Create empty helpers directory using HelperPaths
        File helpersDir = HelperPaths.getHelperDirectory(testAppName, tempDir);
        helpersDir.mkdirs();

        assertTrue(helpersDir.exists(), "Helpers dir should exist before delete");

        // Execute
        boolean result = service.deleteHelper(testAppName, tempDir);

        // Verify
        assertTrue(result, "Delete should succeed");
        assertFalse(helpersDir.exists(), "Empty helpers directory should be deleted");
    }

    @Test
    public void testDeleteHelper_NonEmptyHelpersDir_PreservesDirectory() throws IOException {
        if (Platform.getSystemPlatform().isMac()) {
            return; // macOS has different directory structure
        }

        // Create helpers directory with other files
        File helpersDir = HelperPaths.getHelperDirectory(testAppName, tempDir);
        helpersDir.mkdirs();
        new File(helpersDir, "other-helper.exe").createNewFile();

        // Execute
        boolean result = service.deleteHelper(testAppName, tempDir);

        // Verify
        assertTrue(result, "Delete should succeed");
        assertTrue(helpersDir.exists(), "Non-empty helpers directory should be preserved");
    }

    // ========== HelperUpdateResult tests ==========

    @Test
    public void testHelperUpdateResult_Installed() {
        File helperExe = new File("/path/to/helper");
        File contextDir = new File("/path/to/.jdeploy-files");
        HelperInstallationResult installResult = HelperInstallationResult.success(helperExe, contextDir);

        HelperUpdateService.HelperUpdateResult result = HelperUpdateService.HelperUpdateResult.installed(installResult);

        assertTrue(result.isSuccess());
        assertEquals(HelperUpdateService.HelperUpdateResult.UpdateType.INSTALLED, result.getType());
        assertNull(result.getErrorMessage());
        assertNotNull(result.getInstallationResult());
    }

    @Test
    public void testHelperUpdateResult_Updated() {
        File helperExe = new File("/path/to/helper");
        File contextDir = new File("/path/to/.jdeploy-files");
        HelperInstallationResult installResult = HelperInstallationResult.success(helperExe, contextDir);

        HelperUpdateService.HelperUpdateResult result = HelperUpdateService.HelperUpdateResult.updated(installResult);

        assertTrue(result.isSuccess());
        assertEquals(HelperUpdateService.HelperUpdateResult.UpdateType.UPDATED, result.getType());
        assertNull(result.getErrorMessage());
        assertNotNull(result.getInstallationResult());
    }

    @Test
    public void testHelperUpdateResult_Removed() {
        HelperUpdateService.HelperUpdateResult result = HelperUpdateService.HelperUpdateResult.removed();

        assertTrue(result.isSuccess());
        assertEquals(HelperUpdateService.HelperUpdateResult.UpdateType.REMOVED, result.getType());
        assertNull(result.getErrorMessage());
        assertNull(result.getInstallationResult());
    }

    @Test
    public void testHelperUpdateResult_NoActionNeeded() {
        HelperUpdateService.HelperUpdateResult result = HelperUpdateService.HelperUpdateResult.noActionNeeded();

        assertTrue(result.isSuccess());
        assertEquals(HelperUpdateService.HelperUpdateResult.UpdateType.NO_ACTION, result.getType());
        assertNull(result.getErrorMessage());
        assertNull(result.getInstallationResult());
    }

    @Test
    public void testHelperUpdateResult_Failure() {
        String errorMessage = "Something went wrong";
        HelperUpdateService.HelperUpdateResult result = HelperUpdateService.HelperUpdateResult.failure(errorMessage);

        assertFalse(result.isSuccess());
        assertEquals(HelperUpdateService.HelperUpdateResult.UpdateType.FAILED, result.getType());
        assertEquals(errorMessage, result.getErrorMessage());
        assertNull(result.getInstallationResult());
    }

    @Test
    public void testHelperUpdateResult_ToString() {
        HelperUpdateService.HelperUpdateResult result = HelperUpdateService.HelperUpdateResult.failure("Test error");

        String toString = result.toString();

        assertTrue(toString.contains("FAILED"));
        assertTrue(toString.contains("false"));
        assertTrue(toString.contains("Test error"));
    }
}

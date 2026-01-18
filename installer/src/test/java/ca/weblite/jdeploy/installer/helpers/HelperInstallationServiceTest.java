package ca.weblite.jdeploy.installer.helpers;

import ca.weblite.jdeploy.installer.logging.InstallationLogger;
import ca.weblite.tools.platform.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HelperInstallationService.
 */
public class HelperInstallationServiceTest {

    @TempDir
    File tempDir;

    private InstallationLogger mockLogger;
    private HelperCopyService mockCopyService;
    private HelperInstallationService service;

    private String originalLauncherPath;

    @BeforeEach
    public void setUp() {
        mockLogger = mock(InstallationLogger.class);
        mockCopyService = mock(HelperCopyService.class);
        service = new HelperInstallationService(mockLogger, mockCopyService);

        // Save original launcher path
        originalLauncherPath = System.getProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY);
    }

    @AfterEach
    public void tearDown() {
        // Restore original launcher path
        if (originalLauncherPath != null) {
            System.setProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY, originalLauncherPath);
        } else {
            System.clearProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY);
        }
    }

    // ========== installHelper validation tests ==========

    @Test
    public void testInstallHelper_NullAppName_ReturnsFailure() {
        File jdeployFilesDir = new File(tempDir, ".jdeploy-files");
        jdeployFilesDir.mkdir();

        HelperInstallationResult result = service.installHelper(null, tempDir, jdeployFilesDir);

        assertFalse(result.isSuccess(), "Should fail with null app name");
        assertTrue(result.getErrorMessage().contains("null or empty"),
            "Error message should mention null or empty");
    }

    @Test
    public void testInstallHelper_EmptyAppName_ReturnsFailure() {
        File jdeployFilesDir = new File(tempDir, ".jdeploy-files");
        jdeployFilesDir.mkdir();

        HelperInstallationResult result = service.installHelper("", tempDir, jdeployFilesDir);

        assertFalse(result.isSuccess(), "Should fail with empty app name");
        assertTrue(result.getErrorMessage().contains("null or empty"),
            "Error message should mention null or empty");
    }

    @Test
    public void testInstallHelper_NullJdeployFilesDir_ReturnsFailure() {
        HelperInstallationResult result = service.installHelper("MyApp", tempDir, null);

        assertFalse(result.isSuccess(), "Should fail with null jdeploy-files dir");
        assertTrue(result.getErrorMessage().contains("does not exist"),
            "Error message should mention directory doesn't exist");
    }

    @Test
    public void testInstallHelper_NonExistentJdeployFilesDir_ReturnsFailure() {
        File jdeployFilesDir = new File(tempDir, "nonexistent");

        HelperInstallationResult result = service.installHelper("MyApp", tempDir, jdeployFilesDir);

        assertFalse(result.isSuccess(), "Should fail with non-existent jdeploy-files dir");
        assertTrue(result.getErrorMessage().contains("does not exist"),
            "Error message should mention directory doesn't exist");
    }

    @Test
    public void testInstallHelper_JdeployFilesDirIsFile_ReturnsFailure() throws IOException {
        File jdeployFilesDir = new File(tempDir, ".jdeploy-files");
        assertTrue(jdeployFilesDir.createNewFile()); // Create as file, not directory

        HelperInstallationResult result = service.installHelper("MyApp", tempDir, jdeployFilesDir);

        assertFalse(result.isSuccess(), "Should fail when jdeploy-files is a file");
        assertTrue(result.getErrorMessage().contains("not a directory"),
            "Error message should mention it's not a directory");
    }

    @Test
    public void testInstallHelper_LauncherPathNotSet_ReturnsFailure() throws IOException {
        File jdeployFilesDir = new File(tempDir, ".jdeploy-files");
        assertTrue(jdeployFilesDir.mkdir());

        // Ensure launcher path is not set
        System.clearProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY);

        HelperInstallationResult result = service.installHelper("MyApp", tempDir, jdeployFilesDir);

        assertFalse(result.isSuccess(), "Should fail when launcher path not set");
        assertTrue(result.getErrorMessage().contains("not set") ||
                   result.getErrorMessage().contains("not available"),
            "Error message should mention launcher path not available");
    }

    // ========== installHelper success tests ==========

    @Test
    public void testInstallHelper_Success_OnMacOS() throws IOException {
        if (!Platform.getSystemPlatform().isMac()) {
            return; // Skip on non-Mac
        }

        // Create source jdeploy-files directory
        File jdeployFilesDir = new File(tempDir, ".jdeploy-files");
        assertTrue(jdeployFilesDir.mkdir());
        File appXml = new File(jdeployFilesDir, "app.xml");
        try (FileWriter w = new FileWriter(appXml)) {
            w.write("<app/>");
        }

        // Create a mock installer bundle
        File installerBundle = new File(tempDir, "Installer.app");
        File macOS = new File(installerBundle, "Contents/MacOS");
        assertTrue(macOS.mkdirs());
        File launcher = new File(macOS, "launcher");
        assertTrue(launcher.createNewFile());

        // Set launcher path
        System.setProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY, launcher.getAbsolutePath());

        // Set up mock to simulate successful copy
        doAnswer(invocation -> {
            File dest = invocation.getArgument(1);
            dest.getParentFile().mkdirs();
            if (dest.getName().endsWith(".app")) {
                dest.mkdirs(); // Create as directory for .app
            } else {
                dest.createNewFile();
            }
            return null;
        }).when(mockCopyService).copyInstaller(any(File.class), any(File.class));

        doAnswer(invocation -> {
            File dest = invocation.getArgument(1);
            dest.mkdirs();
            return null;
        }).when(mockCopyService).copyContextDirectory(any(File.class), any(File.class));

        HelperInstallationResult result = service.installHelper("MyApp", null, jdeployFilesDir);

        assertTrue(result.isSuccess(), "Should succeed on macOS: " + result.getErrorMessage());
        assertNotNull(result.getHelperExecutable(), "Helper executable should be set");
        assertNotNull(result.getHelperContextDirectory(), "Context directory should be set");

        // Verify copy methods were called
        verify(mockCopyService).copyInstaller(eq(installerBundle), any(File.class));
        verify(mockCopyService).copyContextDirectory(eq(jdeployFilesDir), any(File.class));
    }

    @Test
    public void testInstallHelper_Success_OnWindowsLinux() throws IOException {
        if (Platform.getSystemPlatform().isMac()) {
            return; // Skip on Mac
        }

        // Create source jdeploy-files directory
        File jdeployFilesDir = new File(tempDir, ".jdeploy-files");
        assertTrue(jdeployFilesDir.mkdir());

        // Create a mock installer executable
        File installerExe = new File(tempDir, "installer.exe");
        assertTrue(installerExe.createNewFile());

        // Set launcher path
        System.setProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY, installerExe.getAbsolutePath());

        // Set up mock to simulate successful copy
        doAnswer(invocation -> {
            File dest = invocation.getArgument(1);
            dest.getParentFile().mkdirs();
            dest.createNewFile();
            return null;
        }).when(mockCopyService).copyInstaller(any(File.class), any(File.class));

        doAnswer(invocation -> {
            File dest = invocation.getArgument(1);
            dest.mkdirs();
            return null;
        }).when(mockCopyService).copyContextDirectory(any(File.class), any(File.class));

        // Use tempDir as app directory for Windows/Linux
        HelperInstallationResult result = service.installHelper("MyApp", tempDir, jdeployFilesDir);

        assertTrue(result.isSuccess(), "Should succeed: " + result.getErrorMessage());
        assertNotNull(result.getHelperExecutable(), "Helper executable should be set");
        assertNotNull(result.getHelperContextDirectory(), "Context directory should be set");
    }

    // ========== installHelper copy failure tests ==========

    @Test
    public void testInstallHelper_CopyInstallerFails_ReturnsFailure() throws IOException {
        // Create source jdeploy-files directory
        File jdeployFilesDir = new File(tempDir, ".jdeploy-files");
        assertTrue(jdeployFilesDir.mkdir());

        // Create a mock installer
        File installerPath;
        if (Platform.getSystemPlatform().isMac()) {
            File installerBundle = new File(tempDir, "Installer.app");
            File macOS = new File(installerBundle, "Contents/MacOS");
            assertTrue(macOS.mkdirs());
            File launcher = new File(macOS, "launcher");
            assertTrue(launcher.createNewFile());
            installerPath = launcher;
        } else {
            installerPath = new File(tempDir, "installer.exe");
            assertTrue(installerPath.createNewFile());
        }

        System.setProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY, installerPath.getAbsolutePath());

        // Set up mock to throw exception on copy
        doThrow(new IOException("Copy failed")).when(mockCopyService)
            .copyInstaller(any(File.class), any(File.class));

        HelperInstallationResult result = service.installHelper("MyApp", tempDir, jdeployFilesDir);

        assertFalse(result.isSuccess(), "Should fail when copy throws exception");
        assertTrue(result.getErrorMessage().contains("Copy failed") ||
                   result.getErrorMessage().contains("failed"),
            "Error message should mention failure");
    }

    @Test
    public void testInstallHelper_CopyContextFails_ReturnsFailure() throws IOException {
        // Create source jdeploy-files directory
        File jdeployFilesDir = new File(tempDir, ".jdeploy-files");
        assertTrue(jdeployFilesDir.mkdir());

        // Create a mock installer
        File installerPath;
        if (Platform.getSystemPlatform().isMac()) {
            File installerBundle = new File(tempDir, "Installer.app");
            File macOS = new File(installerBundle, "Contents/MacOS");
            assertTrue(macOS.mkdirs());
            File launcher = new File(macOS, "launcher");
            assertTrue(launcher.createNewFile());
            installerPath = launcher;
        } else {
            installerPath = new File(tempDir, "installer.exe");
            assertTrue(installerPath.createNewFile());
        }

        System.setProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY, installerPath.getAbsolutePath());

        // Set up mock - copyInstaller succeeds but creates the file
        doAnswer(invocation -> {
            File dest = invocation.getArgument(1);
            dest.getParentFile().mkdirs();
            if (dest.getName().endsWith(".app")) {
                dest.mkdirs();
            } else {
                dest.createNewFile();
            }
            return null;
        }).when(mockCopyService).copyInstaller(any(File.class), any(File.class));

        // copyContextDirectory fails
        doThrow(new IOException("Context copy failed")).when(mockCopyService)
            .copyContextDirectory(any(File.class), any(File.class));

        HelperInstallationResult result = service.installHelper("MyApp", tempDir, jdeployFilesDir);

        assertFalse(result.isSuccess(), "Should fail when context copy throws exception");
        assertTrue(result.getErrorMessage().contains("Context copy failed") ||
                   result.getErrorMessage().contains("failed"),
            "Error message should mention failure");
    }

    // ========== isHelperInstalled tests ==========

    @Test
    public void testIsHelperInstalled_NullAppName_ReturnsFalse() {
        assertFalse(service.isHelperInstalled(null, tempDir),
            "Should return false for null app name");
    }

    @Test
    public void testIsHelperInstalled_EmptyAppName_ReturnsFalse() {
        assertFalse(service.isHelperInstalled("", tempDir),
            "Should return false for empty app name");
    }

    @Test
    public void testIsHelperInstalled_HelperNotPresent_ReturnsFalse() {
        // Use a unique app name to avoid conflicts with existing installations
        String uniqueAppName = "TestApp_" + System.currentTimeMillis() + "_NotInstalled";
        assertFalse(service.isHelperInstalled(uniqueAppName, tempDir),
            "Should return false when helper doesn't exist");
    }

    @Test
    public void testIsHelperInstalled_HelperPresent_ReturnsTrue() throws IOException {
        // Skip this test on macOS to avoid creating files in ~/Applications
        // The logic is tested on Windows/Linux where we have full control over paths
        if (Platform.getSystemPlatform().isMac()) {
            // On macOS, we can't easily test this without polluting ~/Applications
            // The logic is the same - just checks if file exists
            return;
        }

        String appName = "TestApp";

        // Create the helper at the expected location (Windows/Linux only)
        File helperExecutable = HelperPaths.getHelperExecutablePath(appName, tempDir);
        helperExecutable.getParentFile().mkdirs();
        helperExecutable.createNewFile();

        assertTrue(service.isHelperInstalled(appName, tempDir),
            "Should return true when helper exists");

        // Cleanup
        helperExecutable.delete();
        helperExecutable.getParentFile().delete();
    }

    // ========== Logging tests ==========

    @Test
    public void testInstallHelper_LogsSection() throws IOException {
        File jdeployFilesDir = new File(tempDir, ".jdeploy-files");
        assertTrue(jdeployFilesDir.mkdir());

        // Will fail due to no launcher path, but should still log
        service.installHelper("MyApp", tempDir, jdeployFilesDir);

        verify(mockLogger).logSection(contains("Helper"));
    }

    @Test
    public void testInstallHelper_LogsAppInfo() throws IOException {
        File jdeployFilesDir = new File(tempDir, ".jdeploy-files");
        assertTrue(jdeployFilesDir.mkdir());

        service.installHelper("MyApp", tempDir, jdeployFilesDir);

        verify(mockLogger, atLeastOnce()).logInfo(contains("MyApp"));
    }

    // ========== Null logger tests ==========

    @Test
    public void testInstallHelper_NullLoggerDoesNotThrow() throws IOException {
        HelperInstallationService serviceWithNullLogger =
            new HelperInstallationService(null, mockCopyService);

        File jdeployFilesDir = new File(tempDir, ".jdeploy-files");
        assertTrue(jdeployFilesDir.mkdir());

        // Should not throw NPE
        assertDoesNotThrow(() -> {
            serviceWithNullLogger.installHelper("MyApp", tempDir, jdeployFilesDir);
        });
    }

    @Test
    public void testIsHelperInstalled_NullLoggerDoesNotThrow() {
        HelperInstallationService serviceWithNullLogger =
            new HelperInstallationService(null, mockCopyService);

        // Should not throw NPE
        assertDoesNotThrow(() -> {
            serviceWithNullLogger.isHelperInstalled("MyApp", tempDir);
        });
    }
}

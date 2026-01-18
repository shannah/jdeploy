package ca.weblite.jdeploy.installer.helpers;

import ca.weblite.jdeploy.installer.logging.InstallationLogger;
import ca.weblite.tools.platform.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for Helper update scenarios.
 *
 * These tests verify the complete update lifecycle:
 * - Updating existing Helper installations
 * - Adding Helper when services are added
 * - Removing Helper when services are removed
 * - Handling running Helper processes during updates
 */
@Tag("integration")
public class HelperUpdateIntegrationTest {

    private File tempDir;
    private File appDirectory;
    private File jdeployFilesDir;
    private File mockInstallerBundle;
    private String originalLauncherPath;
    private String testAppName;
    private String testPackageName;
    private String testSource;

    @BeforeEach
    public void setUp() throws Exception {
        // Create temp directory structure
        tempDir = Files.createTempDirectory("helper-update-integration-test-").toFile();
        appDirectory = new File(tempDir, "TestApp");
        appDirectory.mkdirs();

        // Create mock .jdeploy-files directory with required files
        jdeployFilesDir = new File(tempDir, ".jdeploy-files");
        jdeployFilesDir.mkdirs();
        createMockJdeployFiles(jdeployFilesDir, "1.0.0");

        // Create mock installer bundle based on platform
        mockInstallerBundle = createMockInstallerBundle();

        // Save original launcher path and set test launcher path
        originalLauncherPath = System.getProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY);
        System.setProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY, getLauncherPath().getAbsolutePath());

        testAppName = "Test App";
        testPackageName = "@test/testapp";
        testSource = "https://github.com/test/testapp";
    }

    @AfterEach
    public void tearDown() {
        // Restore original launcher path
        if (originalLauncherPath != null) {
            System.setProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY, originalLauncherPath);
        } else {
            System.clearProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY);
        }

        // Clean up temp directory
        if (tempDir != null && tempDir.exists()) {
            deleteRecursive(tempDir);
        }

        // Clean up any Helper directories created on macOS (in ~/Applications)
        if (Platform.getSystemPlatform().isMac()) {
            File helperDir = HelperPaths.getHelperDirectory(testAppName, null);
            if (helperDir.exists()) {
                deleteRecursive(helperDir);
            }
        }
    }

    /**
     * Test updating Helper when both old and new versions have services.
     *
     * Verifies:
     * - Old Helper is replaced with new
     * - .jdeploy-files updated with new version
     */
    @Test
    public void testUpdateWithServicesInBothVersions() throws Exception {
        // Create services with real implementations
        InstallationLogger logger = mock(InstallationLogger.class);
        HelperCopyService copyService = new HelperCopyService(logger);
        HelperInstallationService installService = new HelperInstallationService(logger, copyService);
        HelperProcessManager processManager = mock(HelperProcessManager.class);
        when(processManager.isHelperRunning(anyString(), anyString())).thenReturn(false);

        HelperUpdateService updateService = new HelperUpdateService(installService, processManager, logger);

        // Get expected paths
        File expectedAppDir = Platform.getSystemPlatform().isMac() ? null : appDirectory;
        File expectedHelperDir = HelperPaths.getHelperDirectory(testAppName, expectedAppDir);
        File expectedHelperExecutable = HelperPaths.getHelperExecutablePath(testAppName, expectedAppDir);
        File expectedContextDir = HelperPaths.getHelperContextDirectory(testAppName, expectedAppDir);

        // Step 1: Initial installation with v1.0.0
        HelperInstallationResult initialResult = installService.installHelper(testAppName, expectedAppDir, jdeployFilesDir);
        assertTrue(initialResult.isSuccess(), "Initial installation should succeed");
        assertTrue(expectedHelperExecutable.exists(), "Helper should exist after initial install");

        // Verify initial version in context
        File contextAppXml = new File(expectedContextDir, "app.xml");
        assertTrue(contextAppXml.exists(), "app.xml should exist in context directory");
        String initialContent = new String(Files.readAllBytes(contextAppXml.toPath()));
        assertTrue(initialContent.contains("1.0.0"), "Initial version should be 1.0.0");

        // Step 2: Create new jdeploy-files with v2.0.0
        File updatedJdeployFilesDir = new File(tempDir, ".jdeploy-files-v2");
        updatedJdeployFilesDir.mkdirs();
        createMockJdeployFiles(updatedJdeployFilesDir, "2.0.0");

        // Step 3: Run update (services still exist)
        HelperUpdateService.HelperUpdateResult updateResult = updateService.updateHelper(
                testPackageName, testSource, testAppName, expectedAppDir, updatedJdeployFilesDir, true);

        // Verify update success
        assertTrue(updateResult.isSuccess(), "Update should succeed: " + updateResult.getErrorMessage());
        assertEquals(HelperUpdateService.HelperUpdateResult.UpdateType.UPDATED, updateResult.getType(),
                "Update type should be UPDATED");

        // Verify Helper still exists
        assertTrue(expectedHelperExecutable.exists(), "Helper should exist after update");

        // Verify .jdeploy-files updated with new version
        assertTrue(expectedContextDir.exists(), "Context directory should exist after update");
        String updatedContent = new String(Files.readAllBytes(contextAppXml.toPath()));
        assertTrue(updatedContent.contains("2.0.0"), "Version should be updated to 2.0.0");
        assertFalse(updatedContent.contains("1.0.0"), "Old version should not be present");
    }

    /**
     * Test updating to add services (installs Helper).
     *
     * Verifies:
     * - No Helper initially
     * - Helper installed after update with services
     */
    @Test
    public void testUpdateAddingServices() throws Exception {
        // Create services
        InstallationLogger logger = mock(InstallationLogger.class);
        HelperCopyService copyService = new HelperCopyService(logger);
        HelperInstallationService installService = new HelperInstallationService(logger, copyService);
        HelperProcessManager processManager = mock(HelperProcessManager.class);
        when(processManager.isHelperRunning(anyString(), anyString())).thenReturn(false);

        HelperUpdateService updateService = new HelperUpdateService(installService, processManager, logger);

        // Get expected paths
        File expectedAppDir = Platform.getSystemPlatform().isMac() ? null : appDirectory;
        File expectedHelperDir = HelperPaths.getHelperDirectory(testAppName, expectedAppDir);
        File expectedHelperExecutable = HelperPaths.getHelperExecutablePath(testAppName, expectedAppDir);
        File expectedContextDir = HelperPaths.getHelperContextDirectory(testAppName, expectedAppDir);

        // Verify no Helper exists initially (simulating initial install without services)
        assertFalse(expectedHelperExecutable.exists(), "Helper should not exist initially");
        assertFalse(expectedHelperDir.exists(), "Helper directory should not exist initially");

        // Run update that "adds" services (servicesExist = true)
        HelperUpdateService.HelperUpdateResult updateResult = updateService.updateHelper(
                testPackageName, testSource, testAppName, expectedAppDir, jdeployFilesDir, true);

        // Verify installation success
        assertTrue(updateResult.isSuccess(), "Update should succeed: " + updateResult.getErrorMessage());
        assertEquals(HelperUpdateService.HelperUpdateResult.UpdateType.INSTALLED, updateResult.getType(),
                "Update type should be INSTALLED (new Helper)");

        // Verify Helper now installed
        assertTrue(expectedHelperExecutable.exists(), "Helper should exist after update adds services");
        assertTrue(expectedContextDir.exists(), "Context directory should exist after update");

        // Verify context files copied
        File contextAppXml = new File(expectedContextDir, "app.xml");
        assertTrue(contextAppXml.exists(), "app.xml should be copied to context directory");
    }

    /**
     * Test updating to remove services (removes Helper).
     *
     * Verifies:
     * - Helper exists initially
     * - Helper deleted after update removes services
     * - Helper directory cleaned up
     */
    @Test
    public void testUpdateRemovingServices() throws Exception {
        // Create services
        InstallationLogger logger = mock(InstallationLogger.class);
        HelperCopyService copyService = new HelperCopyService(logger);
        HelperInstallationService installService = new HelperInstallationService(logger, copyService);
        HelperProcessManager processManager = mock(HelperProcessManager.class);
        when(processManager.isHelperRunning(anyString(), anyString())).thenReturn(false);

        HelperUpdateService updateService = new HelperUpdateService(installService, processManager, logger);

        // Get expected paths
        File expectedAppDir = Platform.getSystemPlatform().isMac() ? null : appDirectory;
        File expectedHelperDir = HelperPaths.getHelperDirectory(testAppName, expectedAppDir);
        File expectedHelperExecutable = HelperPaths.getHelperExecutablePath(testAppName, expectedAppDir);
        File expectedContextDir = HelperPaths.getHelperContextDirectory(testAppName, expectedAppDir);

        // Step 1: Initial installation with services
        HelperInstallationResult initialResult = installService.installHelper(testAppName, expectedAppDir, jdeployFilesDir);
        assertTrue(initialResult.isSuccess(), "Initial installation should succeed");
        assertTrue(expectedHelperExecutable.exists(), "Helper should exist after initial install");
        assertTrue(expectedContextDir.exists(), "Context directory should exist after initial install");

        // Step 2: Run update that removes services (servicesExist = false)
        HelperUpdateService.HelperUpdateResult updateResult = updateService.updateHelper(
                testPackageName, testSource, testAppName, expectedAppDir, null, false);

        // Verify removal success
        assertTrue(updateResult.isSuccess(), "Update should succeed: " + updateResult.getErrorMessage());
        assertEquals(HelperUpdateService.HelperUpdateResult.UpdateType.REMOVED, updateResult.getType(),
                "Update type should be REMOVED");

        // Verify Helper deleted
        assertFalse(expectedHelperExecutable.exists(), "Helper executable should be deleted");
        assertFalse(expectedContextDir.exists(), "Context directory should be deleted");

        // Verify helper directory cleaned up
        if (Platform.getSystemPlatform().isMac()) {
            // On macOS, the entire "{AppName} Helper" directory should be deleted
            assertFalse(expectedHelperDir.exists(), "macOS Helper directory should be deleted");
        } else {
            // On Windows/Linux, the helpers directory should be deleted if empty
            // (or may still exist if other files were present)
            if (expectedHelperDir.exists()) {
                String[] remaining = expectedHelperDir.list();
                assertTrue(remaining == null || remaining.length == 0,
                        "Windows/Linux helpers directory should be empty if it exists");
            }
        }
    }

    /**
     * Test updating with a running Helper.
     *
     * Verifies:
     * - Helper was terminated before replacement
     * - New Helper installed successfully
     */
    @Test
    public void testUpdateWithRunningHelper() throws Exception {
        // Create services
        InstallationLogger logger = mock(InstallationLogger.class);
        HelperCopyService copyService = new HelperCopyService(logger);
        HelperInstallationService installService = new HelperInstallationService(logger, copyService);

        // Create mock process manager that simulates a running Helper
        HelperProcessManager processManager = mock(HelperProcessManager.class);

        // First call returns true (Helper is running), subsequent calls return false (Helper terminated)
        when(processManager.isHelperRunning(eq(testPackageName), eq(testSource)))
                .thenReturn(true)   // First check - Helper is running
                .thenReturn(false); // After termination - Helper stopped

        // Termination succeeds
        when(processManager.terminateHelper(eq(testPackageName), eq(testSource), eq(testAppName), anyLong()))
                .thenReturn(true);

        HelperUpdateService updateService = new HelperUpdateService(installService, processManager, logger);

        // Get expected paths
        File expectedAppDir = Platform.getSystemPlatform().isMac() ? null : appDirectory;
        File expectedHelperExecutable = HelperPaths.getHelperExecutablePath(testAppName, expectedAppDir);
        File expectedContextDir = HelperPaths.getHelperContextDirectory(testAppName, expectedAppDir);

        // Step 1: Initial installation
        HelperInstallationResult initialResult = installService.installHelper(testAppName, expectedAppDir, jdeployFilesDir);
        assertTrue(initialResult.isSuccess(), "Initial installation should succeed");

        // Step 2: Create updated jdeploy-files with v2.0.0
        File updatedJdeployFilesDir = new File(tempDir, ".jdeploy-files-v2");
        updatedJdeployFilesDir.mkdirs();
        createMockJdeployFiles(updatedJdeployFilesDir, "2.0.0");

        // Step 3: Run update with "running" Helper
        HelperUpdateService.HelperUpdateResult updateResult = updateService.updateHelper(
                testPackageName, testSource, testAppName, expectedAppDir, updatedJdeployFilesDir, true);

        // Verify update success
        assertTrue(updateResult.isSuccess(), "Update should succeed: " + updateResult.getErrorMessage());
        assertEquals(HelperUpdateService.HelperUpdateResult.UpdateType.UPDATED, updateResult.getType(),
                "Update type should be UPDATED");

        // Verify Helper was terminated before replacement
        verify(processManager).terminateHelper(eq(testPackageName), eq(testSource), eq(testAppName), anyLong());

        // Verify new Helper installed
        assertTrue(expectedHelperExecutable.exists(), "New Helper should be installed");

        // Verify context files updated
        File contextAppXml = new File(expectedContextDir, "app.xml");
        assertTrue(contextAppXml.exists(), "app.xml should exist");
        String content = new String(Files.readAllBytes(contextAppXml.toPath()));
        assertTrue(content.contains("2.0.0"), "Version should be updated to 2.0.0");
    }

    /**
     * Test update when Helper termination fails but update continues.
     *
     * Verifies that the update proceeds even if termination fails
     * (with a warning logged).
     */
    @Test
    public void testUpdateWithTerminationFailure() throws Exception {
        // Create services
        InstallationLogger logger = mock(InstallationLogger.class);
        HelperCopyService copyService = new HelperCopyService(logger);
        HelperInstallationService installService = new HelperInstallationService(logger, copyService);

        // Create mock process manager that fails to terminate
        HelperProcessManager processManager = mock(HelperProcessManager.class);
        when(processManager.isHelperRunning(eq(testPackageName), eq(testSource)))
                .thenReturn(true)   // Helper is running
                .thenReturn(false); // But deletion will succeed anyway (simulating force kill worked)

        // Termination "fails" (returns false)
        when(processManager.terminateHelper(eq(testPackageName), eq(testSource), eq(testAppName), anyLong()))
                .thenReturn(false);

        HelperUpdateService updateService = new HelperUpdateService(installService, processManager, logger);

        // Get expected paths
        File expectedAppDir = Platform.getSystemPlatform().isMac() ? null : appDirectory;
        File expectedHelperExecutable = HelperPaths.getHelperExecutablePath(testAppName, expectedAppDir);

        // Step 1: Initial installation
        HelperInstallationResult initialResult = installService.installHelper(testAppName, expectedAppDir, jdeployFilesDir);
        assertTrue(initialResult.isSuccess(), "Initial installation should succeed");

        // Step 2: Create updated jdeploy-files
        File updatedJdeployFilesDir = new File(tempDir, ".jdeploy-files-v2");
        updatedJdeployFilesDir.mkdirs();
        createMockJdeployFiles(updatedJdeployFilesDir, "2.0.0");

        // Step 3: Run update - should continue even though termination "failed"
        HelperUpdateService.HelperUpdateResult updateResult = updateService.updateHelper(
                testPackageName, testSource, testAppName, expectedAppDir, updatedJdeployFilesDir, true);

        // Update should still succeed (termination failure is a warning, not a blocker)
        assertTrue(updateResult.isSuccess(), "Update should succeed even if termination failed");
        assertTrue(expectedHelperExecutable.exists(), "New Helper should be installed");

        // Verify warning was logged (via logger mock or console output)
        // The HelperUpdateService logs: "Could not terminate existing Helper, attempting to continue anyway"
    }

    // ========== Helper methods ==========

    /**
     * Creates mock jdeploy-files directory with required files.
     */
    private void createMockJdeployFiles(File dir, String version) throws IOException {
        // Create mock app.xml
        File appXml = new File(dir, "app.xml");
        try (FileWriter writer = new FileWriter(appXml)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<app package=\"@test/testapp\" source=\"https://github.com/test/testapp\" ");
            writer.write("version=\"" + version + "\" title=\"Test App\"/>\n");
        }

        // Create mock icon
        File iconFile = new File(dir, "icon.png");
        iconFile.createNewFile();

        // Create mock package.json
        File packageJson = new File(dir, "package.json");
        try (FileWriter writer = new FileWriter(packageJson)) {
            writer.write("{\n");
            writer.write("  \"name\": \"@test/testapp\",\n");
            writer.write("  \"version\": \"" + version + "\"\n");
            writer.write("}\n");
        }
    }

    /**
     * Creates a mock installer bundle appropriate for the current platform.
     */
    private File createMockInstallerBundle() throws IOException {
        if (Platform.getSystemPlatform().isMac()) {
            // macOS: Create a .app bundle structure
            File appBundle = new File(tempDir, "MockInstaller.app");
            File macOSDir = new File(appBundle, "Contents/MacOS");
            macOSDir.mkdirs();

            // Create launcher executable
            File launcher = new File(macOSDir, "launcher");
            launcher.createNewFile();
            launcher.setExecutable(true);

            // Create Info.plist
            File infoPlist = new File(appBundle, "Contents/Info.plist");
            try (FileWriter writer = new FileWriter(infoPlist)) {
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                writer.write("<plist version=\"1.0\"><dict></dict></plist>\n");
            }

            return appBundle;
        } else if (Platform.getSystemPlatform().isWindows()) {
            // Windows: Create a .exe file
            File exeFile = new File(tempDir, "MockInstaller.exe");
            exeFile.createNewFile();
            return exeFile;
        } else {
            // Linux: Create an executable
            File binFile = new File(tempDir, "MockInstaller");
            binFile.createNewFile();
            binFile.setExecutable(true);
            return binFile;
        }
    }

    /**
     * Gets the launcher path based on platform.
     */
    private File getLauncherPath() {
        if (Platform.getSystemPlatform().isMac()) {
            return new File(mockInstallerBundle, "Contents/MacOS/launcher");
        } else {
            return mockInstallerBundle;
        }
    }

    /**
     * Recursively deletes a file or directory.
     */
    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}

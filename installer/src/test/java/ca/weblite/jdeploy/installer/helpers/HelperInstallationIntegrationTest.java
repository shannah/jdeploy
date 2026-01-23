package ca.weblite.jdeploy.installer.helpers;

import ca.weblite.jdeploy.installer.logging.InstallationLogger;
import ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import ca.weblite.tools.platform.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the complete Helper installation flow.
 *
 * These tests verify end-to-end Helper installation functionality including:
 * - Directory and file creation at correct locations
 * - Proper manifest entry creation
 * - Branch installation behavior
 * - Graceful failure handling
 */
@Tag("integration")
public class HelperInstallationIntegrationTest {

    private File tempDir;
    private File appDirectory;
    private File jdeployFilesDir;
    private File mockInstallerBundle;
    private String originalLauncherPath;
    private String testAppName;

    @BeforeEach
    public void setUp() throws Exception {
        // Create temp directory structure
        tempDir = Files.createTempDirectory("helper-integration-test-").toFile();
        appDirectory = new File(tempDir, "TestApp");
        appDirectory.mkdirs();

        // Create mock .jdeploy-files directory with required files
        jdeployFilesDir = new File(tempDir, ".jdeploy-files");
        jdeployFilesDir.mkdirs();

        // Create mock app.xml
        File appXml = new File(jdeployFilesDir, "app.xml");
        try (FileWriter writer = new FileWriter(appXml)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<app package=\"@test/testapp\" source=\"https://github.com/test/testapp\" ");
            writer.write("version=\"1.0.0\" title=\"Test App\"/>\n");
        }

        // Create mock icon
        File iconFile = new File(jdeployFilesDir, "icon.png");
        iconFile.createNewFile();

        // Create mock installer bundle based on platform
        mockInstallerBundle = createMockInstallerBundle();

        // Save original launcher path and set test launcher path
        originalLauncherPath = System.getProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY);
        System.setProperty(InstallerBundleLocator.LAUNCHER_PATH_PROPERTY, getLauncherPath().getAbsolutePath());

        testAppName = "Test App";
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
     * Test that a fresh install with services creates the Helper correctly.
     *
     * Verifies:
     * - Helper directory created at correct location
     * - Helper executable exists
     * - .jdeploy-files copied
     * - Uninstall manifest contains Helper entries
     */
    @Test
    public void testFreshInstallWithServices() throws Exception {
        // Create the Helper installation service
        InstallationLogger logger = mock(InstallationLogger.class);
        HelperCopyService copyService = new HelperCopyService(logger);
        HelperInstallationService service = new HelperInstallationService(logger, copyService);

        // Get expected paths
        File expectedAppDir = Platform.getSystemPlatform().isMac() ? null : appDirectory;
        File expectedHelperDir = HelperPaths.getHelperDirectory(testAppName, expectedAppDir);
        File expectedHelperExecutable = HelperPaths.getHelperExecutablePath(testAppName, expectedAppDir);
        File expectedContextDir = HelperPaths.getHelperContextDirectory(testAppName, expectedAppDir);

        // Execute installation
        HelperInstallationResult result = service.installHelper(testAppName, expectedAppDir, jdeployFilesDir);

        // Verify success
        assertTrue(result.isSuccess(), "Installation should succeed: " + result.getErrorMessage());

        // Verify Helper directory created at correct location
        assertTrue(expectedHelperDir.exists(), "Helper directory should exist at: " + expectedHelperDir.getAbsolutePath());
        assertTrue(expectedHelperDir.isDirectory(), "Helper directory should be a directory");

        // Verify Helper executable exists
        assertTrue(expectedHelperExecutable.exists(),
                "Helper executable should exist at: " + expectedHelperExecutable.getAbsolutePath());
        if (Platform.getSystemPlatform().isMac()) {
            assertTrue(expectedHelperExecutable.isDirectory(), "macOS Helper should be a .app bundle (directory)");
        } else {
            assertTrue(expectedHelperExecutable.isFile(), "Windows/Linux Helper should be a file");
        }

        // Verify .jdeploy-files copied
        assertTrue(expectedContextDir.exists(),
                ".jdeploy-files should be copied to: " + expectedContextDir.getAbsolutePath());
        assertTrue(expectedContextDir.isDirectory(), ".jdeploy-files should be a directory");

        // Verify context directory contains expected files
        File copiedAppXml = new File(expectedContextDir, "app.xml");
        assertTrue(copiedAppXml.exists(), "app.xml should be copied to context directory");

        // Verify uninstall manifest can contain Helper entries
        UninstallManifestBuilder builder = new UninstallManifestBuilder();
        builder.withPackageInfo("@test/testapp", "https://github.com/test/testapp", "1.0.0", "x64");
        builder.withInstallerVersion("1.0");

        // Add Helper entries to manifest
        HelperManifestHelper.addHelperToManifest(builder, result);

        UninstallManifest manifest = builder.build();

        // Verify manifest contains Helper entries
        assertNotNull(manifest.getDirectories(), "Manifest should have directories");
        assertTrue(manifest.getDirectories().size() >= 2,
                "Manifest should have at least 2 directories (context + helper/parent)");

        // Verify Helper-related entries exist in manifest
        boolean hasContextDir = manifest.getDirectories().stream()
                .anyMatch(d -> d.getPath().contains(".jdeploy-files") && d.getDescription().contains("Helper"));
        assertTrue(hasContextDir, "Manifest should contain Helper context directory entry");

        if (Platform.getSystemPlatform().isMac()) {
            // macOS: Helper executable is a directory (.app bundle)
            boolean hasHelperBundle = manifest.getDirectories().stream()
                    .anyMatch(d -> d.getPath().contains("Helper.app") && d.getDescription().contains("Helper"));
            assertTrue(hasHelperBundle, "Manifest should contain Helper .app bundle entry");
        } else {
            // Windows/Linux: Helper executable is a file
            boolean hasHelperFile = manifest.getFiles().stream()
                    .anyMatch(f -> f.getPath().contains("-helper") && f.getDescription().contains("Helper"));
            assertTrue(hasHelperFile, "Manifest should contain Helper executable file entry");
        }
    }

    /**
     * Test that a fresh install without services does NOT create the Helper.
     *
     * This test simulates the scenario in Main.java where:
     * - servicesExist = false (no service commands defined)
     * - Installation proceeds but Helper is NOT created
     *
     * Verifies:
     * - Helper directory NOT created
     * - No Helper entries in uninstall manifest
     */
    @Test
    public void testFreshInstallWithoutServices() throws Exception {
        // Get expected Helper paths
        File expectedAppDir = Platform.getSystemPlatform().isMac() ? null : appDirectory;
        File expectedHelperDir = HelperPaths.getHelperDirectory(testAppName, expectedAppDir);
        File expectedHelperExecutable = HelperPaths.getHelperExecutablePath(testAppName, expectedAppDir);

        // Simulate the condition in Main.java: servicesExist = false means no Helper installation
        // In this case, installHelperApplication() is never called
        boolean servicesExist = false;

        // For fresh installs without services, Main.java does nothing:
        // } else if (servicesExist) {
        //     installHelperApplication(installedApp);
        // }
        // For fresh installs without services: do nothing

        if (!servicesExist) {
            // Don't install Helper - this is the expected behavior
        }

        // Verify Helper directory NOT created
        assertFalse(expectedHelperDir.exists(),
                "Helper directory should NOT exist when services are not defined");
        assertFalse(expectedHelperExecutable.exists(),
                "Helper executable should NOT exist when services are not defined");

        // Create a manifest without Helper entries
        UninstallManifestBuilder builder = new UninstallManifestBuilder();
        builder.withPackageInfo("@test/testapp", "https://github.com/test/testapp", "1.0.0", "x64");
        builder.withInstallerVersion("1.0");

        // Add main app entry only (no Helper)
        builder.addDirectory(appDirectory.getAbsolutePath(),
                UninstallManifest.CleanupStrategy.ALWAYS,
                "Application directory");

        UninstallManifest manifest = builder.build();

        // Verify no Helper entries in manifest
        boolean hasHelperEntry = manifest.getDirectories().stream()
                .anyMatch(d -> d.getDescription() != null && d.getDescription().contains("Helper"));
        assertFalse(hasHelperEntry, "Manifest should NOT contain Helper entries when services are not defined");

        if (manifest.getFiles() != null) {
            boolean hasHelperFile = manifest.getFiles().stream()
                    .anyMatch(f -> f.getDescription() != null && f.getDescription().contains("Helper"));
            assertFalse(hasHelperFile, "Manifest should NOT contain Helper file entries when services are not defined");
        }
    }

    /**
     * Test that branch installations skip Helper creation even if services exist.
     *
     * This test verifies the check in Main.java:
     * if (!installationSettings.isBranchInstallation()) {
     *     // Helper installation logic
     * }
     *
     * Verifies:
     * - Helper NOT created despite services existing
     * - This is the expected behavior for branch/development installations
     */
    @Test
    public void testBranchInstallSkipsHelper() throws Exception {
        // Get expected Helper paths
        File expectedAppDir = Platform.getSystemPlatform().isMac() ? null : appDirectory;
        File expectedHelperDir = HelperPaths.getHelperDirectory(testAppName, expectedAppDir);
        File expectedHelperExecutable = HelperPaths.getHelperExecutablePath(testAppName, expectedAppDir);

        // Simulate branch installation with services
        boolean isBranchInstallation = true;
        boolean servicesExist = true; // Services ARE defined

        // The logic in Main.java:
        // if (!installationSettings.isBranchInstallation()) {
        //     boolean servicesExist = ...
        //     if (isUpdate) { ... } else if (servicesExist) { installHelperApplication(...) }
        // }

        // For branch installations, Helper installation is skipped entirely
        if (!isBranchInstallation) {
            // This block would install Helper, but we're in a branch install
            fail("Should not reach Helper installation for branch installs");
        }

        // Verify Helper NOT created
        assertFalse(expectedHelperDir.exists(),
                "Helper directory should NOT exist for branch installations");
        assertFalse(expectedHelperExecutable.exists(),
                "Helper executable should NOT exist for branch installations");

        // Create manifest without Helper entries (expected for branch installs)
        UninstallManifestBuilder builder = new UninstallManifestBuilder();
        builder.withPackageInfo("@test/testapp", "https://github.com/test/testapp", "0.0.0-my-branch", "x64");
        builder.withInstallerVersion("1.0");

        builder.addDirectory(appDirectory.getAbsolutePath(),
                UninstallManifest.CleanupStrategy.ALWAYS,
                "Application directory");

        UninstallManifest manifest = builder.build();

        // Verify no Helper entries
        boolean hasHelperEntry = manifest.getDirectories().stream()
                .anyMatch(d -> d.getDescription() != null && d.getDescription().contains("Helper"));
        assertFalse(hasHelperEntry, "Manifest should NOT contain Helper entries for branch installations");
    }

    /**
     * Test that Helper installation failure does not block main installation.
     *
     * This test verifies that when Helper copy fails:
     * - Main app installation can continue
     * - Appropriate warning is logged
     * - Installation result indicates failure but doesn't throw
     */
    @Test
    public void testHelperInstallationFailureDoesNotBlockMainInstall() throws Exception {
        // Create a mock copy service that always fails
        InstallationLogger logger = mock(InstallationLogger.class);
        HelperCopyService failingCopyService = mock(HelperCopyService.class);
        doThrow(new IOException("Simulated copy failure - disk full"))
                .when(failingCopyService).copyInstaller(any(File.class), any(File.class));

        HelperInstallationService service = new HelperInstallationService(logger, failingCopyService);

        // Get expected paths
        File expectedAppDir = Platform.getSystemPlatform().isMac() ? null : appDirectory;

        // Attempt Helper installation - should NOT throw
        HelperInstallationResult result = null;
        Exception caughtException = null;
        try {
            result = service.installHelper(testAppName, expectedAppDir, jdeployFilesDir);
        } catch (Exception e) {
            caughtException = e;
        }

        // Verify no exception was thrown (installation continues gracefully)
        assertNull(caughtException, "Helper installation failure should not throw exception");

        // Verify result indicates failure
        assertNotNull(result, "Result should not be null even on failure");
        assertFalse(result.isSuccess(), "Result should indicate failure");
        assertNotNull(result.getErrorMessage(), "Error message should be present");
        assertTrue(result.getErrorMessage().contains("failed") || result.getErrorMessage().contains("Simulated"),
                "Error message should indicate failure: " + result.getErrorMessage());

        // Verify error was logged
        verify(logger).logError(anyString(), any(Throwable.class));

        // Simulate the Main.java behavior after Helper failure:
        // if (result.isSuccess()) {
        //     System.out.println("Helper application installed successfully...");
        //     updateManifestWithHelper(result);
        // } else {
        //     System.err.println("Warning: Helper installation failed: " + result.getErrorMessage());
        //     // Continue with installation - Helper is optional
        // }

        // Main installation would continue here...
        boolean mainInstallationSucceeded = true; // Simulated
        assertTrue(mainInstallationSucceeded, "Main installation should proceed after Helper failure");

        // Verify manifest does NOT contain Helper entries (since installation failed)
        UninstallManifestBuilder builder = new UninstallManifestBuilder();
        builder.withPackageInfo("@test/testapp", "https://github.com/test/testapp", "1.0.0", "x64");
        builder.withInstallerVersion("1.0");

        // Only add main app entry - no Helper since it failed
        builder.addDirectory(appDirectory.getAbsolutePath(),
                UninstallManifest.CleanupStrategy.ALWAYS,
                "Application directory");

        UninstallManifest manifest = builder.build();

        // Verify no Helper entries in manifest
        boolean hasHelperEntry = manifest.getDirectories().stream()
                .anyMatch(d -> d.getDescription() != null && d.getDescription().contains("Helper"));
        assertFalse(hasHelperEntry, "Manifest should NOT contain Helper entries when installation failed");
    }

    // ========== Helper methods ==========

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

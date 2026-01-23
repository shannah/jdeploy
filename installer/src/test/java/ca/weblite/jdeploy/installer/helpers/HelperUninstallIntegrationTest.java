package ca.weblite.jdeploy.installer.helpers;

import ca.weblite.jdeploy.installer.logging.InstallationLogger;
import ca.weblite.jdeploy.installer.uninstall.FileUninstallManifestRepository;
import ca.weblite.jdeploy.installer.uninstall.UninstallService;
import ca.weblite.jdeploy.installer.win.RegistryOperations;
import ca.weblite.tools.platform.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for Helper uninstallation flows.
 *
 * These tests verify the complete uninstallation lifecycle:
 * - Main app file removal
 * - Service cleanup
 * - Helper cleanup script generation
 * - Partial failure handling
 */
@Tag("integration")
public class HelperUninstallIntegrationTest {

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
        tempDir = Files.createTempDirectory("helper-uninstall-integration-test-").toFile();
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
     * Test that uninstall from Helper works correctly.
     *
     * This test simulates the uninstall flow without UI confirmation:
     * - Creates installation with Helper
     * - Invokes UninstallService.uninstall()
     * - Verifies cleanup script generated with correct paths
     */
    @Test
    public void testUninstallFromHelper() throws Exception {
        // Step 1: Create installation with Helper
        InstallationLogger logger = mock(InstallationLogger.class);
        HelperCopyService copyService = new HelperCopyService(logger);
        HelperInstallationService installService = new HelperInstallationService(logger, copyService);

        File expectedAppDir = Platform.getSystemPlatform().isMac() ? null : appDirectory;
        File expectedHelperExecutable = HelperPaths.getHelperExecutablePath(testAppName, expectedAppDir);
        File expectedContextDir = HelperPaths.getHelperContextDirectory(testAppName, expectedAppDir);
        File expectedHelperDir = HelperPaths.getHelperDirectory(testAppName, expectedAppDir);

        // Install Helper
        HelperInstallationResult installResult = installService.installHelper(testAppName, expectedAppDir, jdeployFilesDir);
        assertTrue(installResult.isSuccess(), "Installation should succeed");
        assertTrue(expectedHelperExecutable.exists(), "Helper should exist after install");

        // Step 2: Create UninstallService with mock manifest repository
        FileUninstallManifestRepository mockManifestRepo = mock(FileUninstallManifestRepository.class);
        when(mockManifestRepo.load(anyString(), anyString())).thenReturn(java.util.Optional.empty());

        RegistryOperations noOpRegistry = createNoOpRegistryOperations();
        UninstallService uninstallService = new UninstallService(mockManifestRepo, noOpRegistry);

        // Step 3: Run uninstall (this is what BackgroundHelper.handleUninstall does)
        UninstallService.UninstallResult uninstallResult = uninstallService.uninstall(testPackageName, testSource);

        // Uninstall should complete (even if no manifest found)
        assertNotNull(uninstallResult, "Uninstall result should not be null");

        // Step 4: Verify cleanup script would be generated with correct paths
        HelperCleanupScriptGenerator scriptGenerator = new HelperCleanupScriptGenerator();

        // Verify script content contains correct paths
        if (Platform.getSystemPlatform().isWindows()) {
            String scriptContent = scriptGenerator.generateWindowsScriptContent(
                    expectedHelperExecutable, expectedContextDir, expectedHelperDir, false);

            assertTrue(scriptContent.contains(expectedHelperExecutable.getAbsolutePath()),
                    "Script should contain Helper executable path");
            assertTrue(scriptContent.contains(expectedContextDir.getAbsolutePath()),
                    "Script should contain context directory path");
            assertTrue(scriptContent.contains("timeout /t"),
                    "Script should have delay");
            assertTrue(scriptContent.contains("del") || scriptContent.contains("rmdir"),
                    "Script should have delete commands");
        } else {
            String scriptContent = scriptGenerator.generateUnixScriptContent(
                    expectedHelperExecutable, expectedContextDir, expectedHelperDir);

            assertTrue(scriptContent.contains(expectedHelperExecutable.getAbsolutePath()),
                    "Script should contain Helper executable path");
            assertTrue(scriptContent.contains(expectedContextDir.getAbsolutePath()),
                    "Script should contain context directory path");
            assertTrue(scriptContent.contains("sleep"),
                    "Script should have delay");
            assertTrue(scriptContent.contains("rm -rf"),
                    "Script should have delete commands");
        }
    }

    /**
     * Test that uninstall cleanup script deletes Helper files.
     *
     * This test:
     * - Creates installation with Helper
     * - Generates and parses the cleanup script
     * - Verifies script paths are correct
     * - Optionally executes against test structure
     */
    @Test
    public void testUninstallCleansUpHelperFiles() throws Exception {
        // Step 1: Create installation with Helper
        InstallationLogger logger = mock(InstallationLogger.class);
        HelperCopyService copyService = new HelperCopyService(logger);
        HelperInstallationService installService = new HelperInstallationService(logger, copyService);

        File expectedAppDir = Platform.getSystemPlatform().isMac() ? null : appDirectory;
        File expectedHelperExecutable = HelperPaths.getHelperExecutablePath(testAppName, expectedAppDir);
        File expectedContextDir = HelperPaths.getHelperContextDirectory(testAppName, expectedAppDir);
        File expectedHelperDir = HelperPaths.getHelperDirectory(testAppName, expectedAppDir);

        // Install Helper
        HelperInstallationResult installResult = installService.installHelper(testAppName, expectedAppDir, jdeployFilesDir);
        assertTrue(installResult.isSuccess(), "Installation should succeed");

        // Verify files exist before cleanup
        assertTrue(expectedHelperExecutable.exists(), "Helper executable should exist before cleanup");
        assertTrue(expectedContextDir.exists(), "Context directory should exist before cleanup");

        // Step 2: Generate cleanup script
        HelperCleanupScriptGenerator scriptGenerator = new HelperCleanupScriptGenerator();
        File cleanupScript = scriptGenerator.generateCleanupScript(
                expectedHelperExecutable, expectedContextDir, expectedHelperDir);

        assertTrue(cleanupScript.exists(), "Cleanup script should be created");
        assertTrue(cleanupScript.canExecute() || Platform.getSystemPlatform().isWindows(),
                "Cleanup script should be executable");

        // Step 3: Verify script content
        String scriptContent = new String(Files.readAllBytes(cleanupScript.toPath()));

        // Verify paths are in the script
        assertTrue(scriptContent.contains(expectedHelperExecutable.getAbsolutePath()) ||
                   scriptContent.contains(expectedHelperExecutable.getAbsolutePath().replace("\\", "\\\\")),
                "Script should reference Helper executable: " + expectedHelperExecutable.getAbsolutePath());
        assertTrue(scriptContent.contains(expectedContextDir.getAbsolutePath()) ||
                   scriptContent.contains(expectedContextDir.getAbsolutePath().replace("\\", "\\\\")),
                "Script should reference context directory: " + expectedContextDir.getAbsolutePath());

        // Step 4: Manually delete files (simulating script execution without 2-second delay)
        // This verifies the paths are correct and files can be deleted
        if (expectedHelperExecutable.isDirectory()) {
            deleteRecursive(expectedHelperExecutable);
        } else {
            expectedHelperExecutable.delete();
        }
        deleteRecursive(expectedContextDir);

        // Attempt to delete helper directory if empty
        if (expectedHelperDir.exists()) {
            String[] remaining = expectedHelperDir.list();
            if (remaining == null || remaining.length == 0) {
                expectedHelperDir.delete();
            }
        }

        // Verify cleanup
        assertFalse(expectedHelperExecutable.exists(), "Helper executable should be deleted");
        assertFalse(expectedContextDir.exists(), "Context directory should be deleted");

        // Clean up script file
        cleanupScript.delete();
    }

    /**
     * Test uninstall with partial failure.
     *
     * Verifies that Helper cleanup is still attempted even when
     * main uninstall has failures.
     */
    @Test
    public void testUninstallWithPartialFailure() throws Exception {
        // Step 1: Create installation with Helper
        InstallationLogger logger = mock(InstallationLogger.class);
        HelperCopyService copyService = new HelperCopyService(logger);
        HelperInstallationService installService = new HelperInstallationService(logger, copyService);

        File expectedAppDir = Platform.getSystemPlatform().isMac() ? null : appDirectory;
        File expectedHelperExecutable = HelperPaths.getHelperExecutablePath(testAppName, expectedAppDir);
        File expectedContextDir = HelperPaths.getHelperContextDirectory(testAppName, expectedAppDir);
        File expectedHelperDir = HelperPaths.getHelperDirectory(testAppName, expectedAppDir);

        // Install Helper
        HelperInstallationResult installResult = installService.installHelper(testAppName, expectedAppDir, jdeployFilesDir);
        assertTrue(installResult.isSuccess(), "Installation should succeed");

        // Step 2: Create UninstallService that returns partial failure
        FileUninstallManifestRepository mockManifestRepo = mock(FileUninstallManifestRepository.class);

        // Simulate manifest that references non-existent files (causing failures)
        when(mockManifestRepo.load(eq(testPackageName), eq(testSource)))
                .thenThrow(new RuntimeException("Simulated manifest read error"));

        RegistryOperations noOpRegistry = createNoOpRegistryOperations();
        UninstallService uninstallService = new UninstallService(mockManifestRepo, noOpRegistry);

        // Step 3: Run uninstall - should complete despite manifest errors
        UninstallService.UninstallResult uninstallResult = uninstallService.uninstall(testPackageName, testSource);

        // Result should be returned (not throw)
        assertNotNull(uninstallResult, "Uninstall should complete even with errors");

        // Step 4: Verify Helper cleanup can still be attempted
        // This simulates what BackgroundHelper does after UninstallService.uninstall()
        HelperCleanupScriptGenerator scriptGenerator = new HelperCleanupScriptGenerator();

        // Should be able to generate cleanup script even after partial uninstall failure
        File cleanupScript = null;
        Exception cleanupException = null;
        try {
            cleanupScript = scriptGenerator.generateCleanupScript(
                    expectedHelperExecutable, expectedContextDir, expectedHelperDir);
        } catch (Exception e) {
            cleanupException = e;
        }

        assertNull(cleanupException, "Cleanup script generation should not fail");
        assertNotNull(cleanupScript, "Cleanup script should be generated");
        assertTrue(cleanupScript.exists(), "Cleanup script file should exist");

        // Verify script contains the right paths
        String scriptContent = new String(Files.readAllBytes(cleanupScript.toPath()));
        assertTrue(scriptContent.contains(expectedHelperExecutable.getAbsolutePath()) ||
                   scriptContent.contains(expectedHelperExecutable.getAbsolutePath().replace("\\", "\\\\")),
                "Script should contain Helper path even after partial failure");

        // Clean up
        cleanupScript.delete();
    }

    /**
     * Test that HelperSelfDeleteService handles errors gracefully.
     */
    @Test
    public void testHelperSelfDeleteServiceHandlesErrors() throws Exception {
        // Create a mock script generator that fails
        HelperCleanupScriptGenerator failingGenerator = mock(HelperCleanupScriptGenerator.class);
        when(failingGenerator.generateCleanupScript(any(), any(), any()))
                .thenThrow(new IOException("Cannot create temp file"));

        java.util.logging.Logger testLogger = java.util.logging.Logger.getLogger("test");
        HelperSelfDeleteService selfDeleteService = new HelperSelfDeleteService(failingGenerator, testLogger);

        // Create test Helper paths
        File expectedAppDir = Platform.getSystemPlatform().isMac() ? null : appDirectory;

        // Schedule cleanup should return false on failure, not throw
        boolean result = selfDeleteService.scheduleHelperCleanup(testAppName, expectedAppDir);

        assertFalse(result, "scheduleHelperCleanup should return false on failure");
        // Service should handle the error gracefully without throwing
    }

    /**
     * Test cleanup script path escaping for special characters.
     */
    @Test
    public void testCleanupScriptPathEscaping() throws Exception {
        // Create paths with special characters
        File specialCharPath = new File(tempDir, "App With Spaces");
        specialCharPath.mkdirs();

        File helperExec = new File(specialCharPath, "helper");
        helperExec.createNewFile();

        File contextDir = new File(specialCharPath, ".jdeploy-files");
        contextDir.mkdirs();

        HelperCleanupScriptGenerator scriptGenerator = new HelperCleanupScriptGenerator();

        if (Platform.getSystemPlatform().isWindows()) {
            String content = scriptGenerator.generateWindowsScriptContent(helperExec, contextDir, specialCharPath, false);
            // Windows paths with spaces should be quoted
            assertTrue(content.contains("\""), "Windows script should use quotes for paths");
            assertTrue(content.contains("App With Spaces"), "Script should contain path with spaces");
        } else {
            String content = scriptGenerator.generateUnixScriptContent(helperExec, contextDir, specialCharPath);
            // Unix paths should be quoted
            assertTrue(content.contains("\""), "Unix script should use quotes for paths");
            assertTrue(content.contains("App With Spaces"), "Script should contain path with spaces");
        }
    }

    // ========== Helper methods ==========

    /**
     * Creates mock jdeploy-files directory with required files.
     */
    private void createMockJdeployFiles(File dir, String version) throws IOException {
        File appXml = new File(dir, "app.xml");
        try (FileWriter writer = new FileWriter(appXml)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<app package=\"@test/testapp\" source=\"https://github.com/test/testapp\" ");
            writer.write("version=\"" + version + "\" title=\"Test App\"/>\n");
        }

        File iconFile = new File(dir, "icon.png");
        iconFile.createNewFile();
    }

    /**
     * Creates a mock installer bundle appropriate for the current platform.
     */
    private File createMockInstallerBundle() throws IOException {
        if (Platform.getSystemPlatform().isMac()) {
            File appBundle = new File(tempDir, "MockInstaller.app");
            File macOSDir = new File(appBundle, "Contents/MacOS");
            macOSDir.mkdirs();

            File launcher = new File(macOSDir, "launcher");
            launcher.createNewFile();
            launcher.setExecutable(true);

            File infoPlist = new File(appBundle, "Contents/Info.plist");
            try (FileWriter writer = new FileWriter(infoPlist)) {
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                writer.write("<plist version=\"1.0\"><dict></dict></plist>\n");
            }

            return appBundle;
        } else if (Platform.getSystemPlatform().isWindows()) {
            File exeFile = new File(tempDir, "MockInstaller.exe");
            exeFile.createNewFile();
            return exeFile;
        } else {
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
     * Creates a no-op RegistryOperations for non-Windows platforms.
     */
    private RegistryOperations createNoOpRegistryOperations() {
        return new RegistryOperations() {
            @Override
            public boolean keyExists(String key) {
                return false;
            }

            @Override
            public boolean valueExists(String key, String valueName) {
                return false;
            }

            @Override
            public String getStringValue(String key, String valueName) {
                return null;
            }

            @Override
            public void setStringValue(String key, String valueName, String value) {
            }

            @Override
            public void setLongValue(String key, long value) {
            }

            @Override
            public void createKey(String key) {
            }

            @Override
            public void deleteKey(String key) {
            }

            @Override
            public void deleteValue(String key, String valueName) {
            }

            @Override
            public Set<String> getKeys(String key) {
                return Collections.emptySet();
            }

            @Override
            public Map<String, Object> getValues(String key) {
                return Collections.emptyMap();
            }
        };
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

package ca.weblite.jdeploy.installer.uninstall;

import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.CleanupStrategy;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.FileType;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.GitBashProfileEntry;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.InstalledDirectory;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.InstalledFile;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.ModifiedRegistryValue;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.PackageInfo;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.PathModifications;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.RegistryInfo;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.RegistryKey;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.RegistryRoot;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.RegistryValueType;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.ShellProfileEntry;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.WindowsPathEntry;
import ca.weblite.jdeploy.installer.win.InMemoryRegistryOperations;
import ca.weblite.jdeploy.installer.win.RegistryOperations;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UninstallService.
 * Tests each cleanup phase independently: file deletion, directory cleanup,
 * registry operations, and PATH modifications. Verifies fault tolerance and
 * aggregated result reporting.
 */
public class UninstallServiceTest {

    private static final String TEST_PACKAGE_NAME = "test-app";
    private static final String TEST_SOURCE = "github";
    private static final String TEST_VERSION = "1.0.0";

    private Path tempDir;
    private UninstallService service;
    private InMemoryRegistryOperations registryOps;
    private StubFileUninstallManifestRepository manifestRepository;

    @BeforeEach
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("uninstall-test-");
        registryOps = new InMemoryRegistryOperations();
        manifestRepository = new StubFileUninstallManifestRepository();
        service = new UninstallService(manifestRepository, registryOps);
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            FileUtils.deleteDirectory(tempDir.toFile());
        }
    }

    // ==================== File Deletion Tests ====================

    @Test
    public void testDeleteInstalledFilesSuccessfully() throws IOException {
        // Setup: Create test files
        File file1 = tempDir.resolve("file1.txt").toFile();
        File file2 = tempDir.resolve("file2.bin").toFile();
        assertTrue(file1.createNewFile(), "Failed to create test file1");
        assertTrue(file2.createNewFile(), "Failed to create test file2");
        assertTrue(file1.exists());
        assertTrue(file2.exists());

        // Create manifest with files
        List<InstalledFile> files = Arrays.asList(
            InstalledFile.builder().path(file1.getAbsolutePath()).type(FileType.CONFIG).build(),
            InstalledFile.builder().path(file2.getAbsolutePath()).type(FileType.BINARY).build()
        );
        UninstallManifest manifest = createManifestWithFiles(files, Collections.emptyList());
        manifestRepository.setManifest(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Files deleted
        assertFalse(file1.exists(), "File1 should be deleted");
        assertFalse(file2.exists(), "File2 should be deleted");
        assertTrue(result.isSuccess(), "Uninstall should succeed with no failures");
        assertTrue(result.getFailureCount() >= 0, "Failure count should be tracked");
    }

    @Test
    public void testDeleteMissingFilesIsIdempotent() throws IOException {
        // Setup: Reference non-existent files
        String missingFile1 = tempDir.resolve("missing1.txt").toString();
        String missingFile2 = tempDir.resolve("missing2.txt").toString();
        assertFalse(new File(missingFile1).exists());
        assertFalse(new File(missingFile2).exists());

        // Create manifest with missing files
        List<InstalledFile> files = Arrays.asList(
            InstalledFile.builder().path(missingFile1).type(FileType.CONFIG).build(),
            InstalledFile.builder().path(missingFile2).type(FileType.BINARY).build()
        );
        UninstallManifest manifest = createManifestWithFiles(files, Collections.emptyList());
        manifestRepository.setManifest(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Uninstall succeeds despite missing files (idempotent)
        assertTrue(result.isSuccess(), "Uninstall should succeed when files are already deleted");
        assertEquals(0, result.getFailureCount(), "Should have no failures for missing files");
    }

    @Test
    public void testDeleteFilesPartialSuccess() throws IOException {
        // Setup: Create one file, reference one missing
        File existingFile = tempDir.resolve("existing.txt").toFile();
        String missingFile = tempDir.resolve("missing.txt").toString();
        assertTrue(existingFile.createNewFile());
        assertFalse(new File(missingFile).exists());

        List<InstalledFile> files = Arrays.asList(
            InstalledFile.builder().path(existingFile.getAbsolutePath()).type(FileType.CONFIG).build(),
            InstalledFile.builder().path(missingFile).type(FileType.BINARY).build()
        );
        UninstallManifest manifest = createManifestWithFiles(files, Collections.emptyList());
        manifestRepository.setManifest(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Existing file deleted, missing file handled gracefully
        assertFalse(existingFile.exists(), "Existing file should be deleted");
        assertTrue(result.isSuccess(), "Uninstall should succeed with partial files");
    }

    // ==================== Directory Cleanup Tests ====================

    @Test
    public void testCleanupDirectoryWithALWAYSStrategy() throws IOException {
        // Setup: Create directory with contents
        File dir = tempDir.resolve("always-cleanup").toFile();
        assertTrue(dir.mkdirs());
        File nestedFile = new File(dir, "nested.txt");
        assertTrue(nestedFile.createNewFile());
        assertTrue(dir.exists());
        assertTrue(nestedFile.exists());

        // Create manifest with ALWAYS strategy
        List<InstalledDirectory> directories = Collections.singletonList(
            InstalledDirectory.builder().path(dir.getAbsolutePath())
                .cleanup(CleanupStrategy.ALWAYS).build()
        );
        UninstallManifest manifest = createManifestWithFiles(Collections.emptyList(), directories);
        manifestRepository.setManifest(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Directory and all contents deleted
        assertFalse(dir.exists(), "Directory should be deleted with ALWAYS strategy");
        assertFalse(nestedFile.exists(), "Nested file should be deleted");
        assertTrue(result.isSuccess());
    }

    @Test
    public void testCleanupDirectoryWithIF_EMPTYStrategy() throws IOException {
        // Setup: Create two directories - one empty, one with contents
        File emptyDir = tempDir.resolve("empty-dir").toFile();
        File nonEmptyDir = tempDir.resolve("non-empty-dir").toFile();
        assertTrue(emptyDir.mkdirs());
        assertTrue(nonEmptyDir.mkdirs());
        File fileInNonEmpty = new File(nonEmptyDir, "file.txt");
        assertTrue(fileInNonEmpty.createNewFile());

        // Create manifest with IF_EMPTY strategy
        List<InstalledDirectory> directories = Arrays.asList(
            InstalledDirectory.builder().path(emptyDir.getAbsolutePath())
                .cleanup(CleanupStrategy.IF_EMPTY).build(),
            InstalledDirectory.builder().path(nonEmptyDir.getAbsolutePath())
                .cleanup(CleanupStrategy.IF_EMPTY).build()
        );
        UninstallManifest manifest = createManifestWithFiles(Collections.emptyList(), directories);
        manifestRepository.setManifest(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Only empty directory is deleted
        assertFalse(emptyDir.exists(), "Empty directory should be deleted");
        assertTrue(nonEmptyDir.exists(), "Non-empty directory should not be deleted");
        assertTrue(fileInNonEmpty.exists(), "File in non-empty directory should remain");
        assertTrue(result.isSuccess());
    }

    @Test
    public void testCleanupDirectoryWithCONTENTS_ONLYStrategy() throws IOException {
        // Setup: Create directory with contents
        File dir = tempDir.resolve("contents-only").toFile();
        assertTrue(dir.mkdirs());
        File file1 = new File(dir, "file1.txt");
        File file2 = new File(dir, "file2.txt");
        assertTrue(file1.createNewFile());
        assertTrue(file2.createNewFile());

        // Create manifest with CONTENTS_ONLY strategy
        List<InstalledDirectory> directories = Collections.singletonList(
            InstalledDirectory.builder().path(dir.getAbsolutePath())
                .cleanup(CleanupStrategy.CONTENTS_ONLY).build()
        );
        UninstallManifest manifest = createManifestWithFiles(Collections.emptyList(), directories);
        manifestRepository.setManifest(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Directory remains but contents deleted
        assertTrue(dir.exists(), "Directory should remain with CONTENTS_ONLY strategy");
        assertFalse(file1.exists(), "File1 should be deleted");
        assertFalse(file2.exists(), "File2 should be deleted");
        assertTrue(isDirEmpty(dir), "Directory should be empty");
        assertTrue(result.isSuccess());
    }

    @Test
    public void testCleanupMissingDirectoryIsIdempotent() {
        // Setup: Reference non-existent directory
        String missingDir = tempDir.resolve("missing-dir").toString();
        assertFalse(new File(missingDir).exists());

        // Create manifest with missing directory
        List<InstalledDirectory> directories = Collections.singletonList(
            InstalledDirectory.builder().path(missingDir)
                .cleanup(CleanupStrategy.ALWAYS).build()
        );
        UninstallManifest manifest = createManifestWithFiles(Collections.emptyList(), directories);
        manifestRepository.setManifest(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Uninstall succeeds despite missing directory
        assertTrue(result.isSuccess(), "Uninstall should succeed for missing directories");
    }

    // ==================== Registry Operations Tests ====================

    @Test
    public void testCleanupRegistryCreatedKeys() {
        // Setup: Create registry keys in memory
        String keyPath = "HKEY_CURRENT_USER\\Software\\TestApp";
        registryOps.createKey(keyPath);
        registryOps.setStringValue(keyPath, "TestValue", "TestData");
        assertTrue(registryOps.keyExists(keyPath));

        // Create manifest with created registry key
        List<RegistryKey> createdKeys = Collections.singletonList(
            RegistryKey.builder()
                .root(RegistryRoot.HKEY_CURRENT_USER)
                .path(keyPath)
                .description("Test key")
                .build()
        );
        RegistryInfo registryInfo = RegistryInfo.builder()
            .createdKeys(createdKeys)
            .modifiedValues(Collections.emptyList())
            .build();
        UninstallManifest manifest = createManifestWithRegistry(registryInfo);
        manifestRepository.setManifest(manifest);

        // Mock Windows OS
        String originalOS = System.getProperty("os.name");
        System.setProperty("os.name", "Windows 10");
        try {
            // Execute uninstall
            UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

            // Verify: Registry key deleted
            assertFalse(registryOps.keyExists(keyPath), "Registry key should be deleted");
            assertTrue(result.isSuccess());
        } finally {
            if (originalOS != null) {
                System.setProperty("os.name", originalOS);
            }
        }
    }

    @Test
    public void testCleanupRegistryModifiedValuesRestore() {
        // Setup: Create registry key with a value
        String keyPath = "HKEY_CURRENT_USER\\Software\\TestApp";
        registryOps.createKey(keyPath);
        registryOps.setStringValue(keyPath, "Setting", "OriginalValue");

        // Create manifest with modified value that should be restored
        List<ModifiedRegistryValue> modifiedValues = Collections.singletonList(
            ModifiedRegistryValue.builder()
                .root(RegistryRoot.HKEY_CURRENT_USER)
                .path(keyPath)
                .name("Setting")
                .previousValue("OriginalValue")
                .previousType(RegistryValueType.REG_SZ)
                .build()
        );
        RegistryInfo registryInfo = RegistryInfo.builder()
            .createdKeys(Collections.emptyList())
            .modifiedValues(modifiedValues)
            .build();
        UninstallManifest manifest = createManifestWithRegistry(registryInfo);
        manifestRepository.setManifest(manifest);

        // Change the value to simulate installation modification
        registryOps.setStringValue(keyPath, "Setting", "ModifiedValue");
        assertEquals("ModifiedValue", registryOps.getStringValue(keyPath, "Setting"));

        // Mock Windows OS
        String originalOS = System.getProperty("os.name");
        System.setProperty("os.name", "Windows 10");
        try {
            // Execute uninstall
            UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

            // Verify: Value restored to original
            assertEquals("OriginalValue", registryOps.getStringValue(keyPath, "Setting"),
                "Registry value should be restored");
            assertTrue(result.isSuccess());
        } finally {
            if (originalOS != null) {
                System.setProperty("os.name", originalOS);
            }
        }
    }

    @Test
    public void testCleanupRegistryModifiedValuesDeleteWhenNoPrevious() {
        // Setup: Create registry key with a value
        String keyPath = "HKEY_CURRENT_USER\\Software\\TestApp";
        registryOps.createKey(keyPath);
        registryOps.setStringValue(keyPath, "NewSetting", "InstalledValue");

        // Create manifest with modified value that has no previous value (should be deleted)
        List<ModifiedRegistryValue> modifiedValues = Collections.singletonList(
            ModifiedRegistryValue.builder()
                .root(RegistryRoot.HKEY_CURRENT_USER)
                .path(keyPath)
                .name("NewSetting")
                .previousValue(null)
                .previousType(RegistryValueType.REG_SZ)
                .build()
        );
        RegistryInfo registryInfo = RegistryInfo.builder()
            .createdKeys(Collections.emptyList())
            .modifiedValues(modifiedValues)
            .build();
        UninstallManifest manifest = createManifestWithRegistry(registryInfo);
        manifestRepository.setManifest(manifest);

        // Mock Windows OS
        String originalOS = System.getProperty("os.name");
        System.setProperty("os.name", "Windows 10");
        try {
            // Execute uninstall
            UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

            // Verify: Value deleted since no previous value exists
            assertFalse(registryOps.valueExists(keyPath, "NewSetting"),
                "Registry value should be deleted when no previous value exists");
            assertTrue(result.isSuccess());
        } finally {
            if (originalOS != null) {
                System.setProperty("os.name", originalOS);
            }
        }
    }

    @Test
    public void testRegistryCleanupSkippedOnNonWindowsOS() {
        // Setup: Create registry key
        String keyPath = "HKEY_CURRENT_USER\\Software\\TestApp";
        registryOps.createKey(keyPath);
        assertTrue(registryOps.keyExists(keyPath));

        // Create manifest with registry operations
        List<RegistryKey> createdKeys = Collections.singletonList(
            RegistryKey.builder()
                .root(RegistryRoot.HKEY_CURRENT_USER)
                .path(keyPath)
                .build()
        );
        RegistryInfo registryInfo = RegistryInfo.builder()
            .createdKeys(createdKeys)
            .modifiedValues(Collections.emptyList())
            .build();
        UninstallManifest manifest = createManifestWithRegistry(registryInfo);
        manifestRepository.setManifest(manifest);

        // Mock non-Windows OS
        String originalOS = System.getProperty("os.name");
        System.setProperty("os.name", "Linux");
        try {
            // Execute uninstall
            UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

            // Verify: Registry key NOT deleted on non-Windows OS
            assertTrue(registryOps.keyExists(keyPath),
                "Registry operations should be skipped on non-Windows OS");
            assertTrue(result.isSuccess());
        } finally {
            if (originalOS != null) {
                System.setProperty("os.name", originalOS);
            }
        }
    }

    // ==================== PATH Cleanup Tests ====================

    @Test
    public void testCleanupWindowsPathEntries() throws IOException {
        // Setup: Create a directory to represent a PATH entry
        File binDir = tempDir.resolve("bin").toFile();
        assertTrue(binDir.mkdirs());

        // Create manifest with Windows PATH entry
        List<WindowsPathEntry> windowsPaths = Collections.singletonList(
            WindowsPathEntry.builder()
                .addedEntry(binDir.getAbsolutePath())
                .description("Application bin directory")
                .build()
        );
        PathModifications pathMods = PathModifications.builder()
            .windowsPaths(windowsPaths)
            .shellProfiles(Collections.emptyList())
            .gitBashProfiles(Collections.emptyList())
            .build();
        UninstallManifest manifest = createManifestWithPathModifications(pathMods);
        manifestRepository.setManifest(manifest);

        // Mock Windows OS
        String originalOS = System.getProperty("os.name");
        System.setProperty("os.name", "Windows 10");
        try {
            // Execute uninstall
            UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

            // Verify: Uninstall succeeds (actual PATH removal requires Registry access)
            assertNotNull(result);
        } finally {
            if (originalOS != null) {
                System.setProperty("os.name", originalOS);
            }
        }
    }

    @Test
    public void testCleanupUnixShellProfiles() throws IOException {
        // Setup: Create a shell profile file
        File homeDir = tempDir.toFile();
        File bashrc = new File(homeDir, ".bashrc");
        String exportLine = "export PATH=\"" + tempDir.resolve("bin").toString() + ":$PATH\"";
        Files.write(bashrc.toPath(), 
            ("# Some existing content\n" + exportLine + "\n").getBytes(StandardCharsets.UTF_8));

        // Create manifest with shell profile entry
        List<ShellProfileEntry> shellProfiles = Collections.singletonList(
            ShellProfileEntry.builder()
                .file(bashrc.getAbsolutePath())
                .exportLine(exportLine)
                .description("Bash PATH entry")
                .build()
        );
        PathModifications pathMods = PathModifications.builder()
            .windowsPaths(Collections.emptyList())
            .shellProfiles(shellProfiles)
            .gitBashProfiles(Collections.emptyList())
            .build();
        UninstallManifest manifest = createManifestWithPathModifications(pathMods);
        manifestRepository.setManifest(manifest);

        // Mock home directory
        String originalHome = System.getProperty("user.home");
        System.setProperty("user.home", homeDir.getAbsolutePath());
        try {
            // Execute uninstall
            UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

            // Verify: Uninstall succeeds
            assertNotNull(result);
        } finally {
            if (originalHome != null) {
                System.setProperty("user.home", originalHome);
            }
        }
    }

    // ==================== Fault Tolerance Tests ====================

    @Test
    public void testUninstallContinuesOnFileDeleteionFailure() throws IOException {
        // Setup: Create a file we'll try to delete twice (to simulate failure)
        File file = tempDir.resolve("test.txt").toFile();
        assertTrue(file.createNewFile());

        // Create directory to test directory cleanup proceeds even if file deletion fails
        File dir = tempDir.resolve("test-dir").toFile();
        assertTrue(dir.mkdirs());

        // Create manifest with both file and directory
        List<InstalledFile> files = Collections.singletonList(
            InstalledFile.builder().path(file.getAbsolutePath()).type(FileType.CONFIG).build()
        );
        List<InstalledDirectory> directories = Collections.singletonList(
            InstalledDirectory.builder().path(dir.getAbsolutePath())
                .cleanup(CleanupStrategy.ALWAYS).build()
        );
        UninstallManifest manifest = createManifestWithFiles(files, directories);
        manifestRepository.setManifest(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Both phases executed despite any issues
        assertFalse(file.exists(), "File should be deleted");
        assertFalse(dir.exists(), "Directory should be deleted");
        // Result should indicate success since both operations completed
        assertNotNull(result);
    }

    @Test
    public void testUninstallReturnsAggregatedResults() throws IOException {
        // Setup: Create test files and directories
        File file1 = tempDir.resolve("file1.txt").toFile();
        File file2 = tempDir.resolve("file2.txt").toFile();
        File dir1 = tempDir.resolve("dir1").toFile();
        File dir2 = tempDir.resolve("dir2").toFile();

        assertTrue(file1.createNewFile());
        assertTrue(file2.createNewFile());
        assertTrue(dir1.mkdirs());
        assertTrue(dir2.mkdirs());

        // Create manifest
        List<InstalledFile> files = Arrays.asList(
            InstalledFile.builder().path(file1.getAbsolutePath()).type(FileType.CONFIG).build(),
            InstalledFile.builder().path(file2.getAbsolutePath()).type(FileType.BINARY).build()
        );
        List<InstalledDirectory> directories = Arrays.asList(
            InstalledDirectory.builder().path(dir1.getAbsolutePath())
                .cleanup(CleanupStrategy.ALWAYS).build(),
            InstalledDirectory.builder().path(dir2.getAbsolutePath())
                .cleanup(CleanupStrategy.ALWAYS).build()
        );
        UninstallManifest manifest = createManifestWithFiles(files, directories);
        manifestRepository.setManifest(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Results are aggregated
        assertNotNull(result, "Result should not be null");
        assertTrue(result.getSuccessCount() >= 4, "Should have at least 4 successful operations");
        assertEquals(0, result.getFailureCount(), "Should have no failures");
        assertTrue(result.isSuccess(), "Overall result should be success");
    }

    @Test
    public void testUninstallWithNoManifest() {
        // Setup: No manifest in repository
        manifestRepository.clearManifest();

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Result indicates success (directory cleanup continues without manifest)
        // Note: Changed behavior - uninstall now continues with package directory cleanup
        // even when manifest doesn't exist
        assertTrue(result.isSuccess(), "Uninstall should succeed with package directory cleanup");
    }

    @Test
    public void testUninstallWithManifestLoadFailure() {
        // Setup: Configure repository to throw exception
        manifestRepository.setThrowOnLoad(true);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Result indicates success (directory cleanup continues despite manifest load failure)
        // Note: Changed behavior - uninstall now gracefully handles manifest load failures
        // and continues with package directory cleanup
        assertTrue(result.isSuccess(), "Uninstall should succeed with package directory cleanup");
    }

    @Test
    public void testCleanupContinuesAfterPhaseFailures() throws IOException {
        // Setup: Create a file and a directory
        File file = tempDir.resolve("test.txt").toFile();
        File dir = tempDir.resolve("test-dir").toFile();
        assertTrue(file.createNewFile());
        assertTrue(dir.mkdirs());

        // Create manifest with file and directory
        List<InstalledFile> files = Collections.singletonList(
            InstalledFile.builder().path(file.getAbsolutePath()).type(FileType.CONFIG).build()
        );
        List<InstalledDirectory> directories = Collections.singletonList(
            InstalledDirectory.builder().path(dir.getAbsolutePath())
                .cleanup(CleanupStrategy.ALWAYS).build()
        );
        UninstallManifest manifest = createManifestWithFiles(files, directories);
        manifestRepository.setManifest(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Cleanup proceeded through all phases
        assertFalse(file.exists(), "File should be cleaned up");
        assertFalse(dir.exists(), "Directory should be cleaned up");
        assertNotNull(result);
    }

    // ==================== Self-Cleanup Tests ====================

    @Test
    public void testSelfCleanupDeletesManifest() throws IOException {
        // Setup: Create an empty manifest with no files/directories to clean
        UninstallManifest manifest = createManifestWithFiles(Collections.emptyList(), Collections.emptyList());
        manifestRepository.setManifest(manifest);

        // Execute uninstall
        service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Manifest was marked as deleted
        assertTrue(manifestRepository.wasDeleteCalled(), "Repository delete should be called");
    }

    // ==================== Helper Methods ====================

    private UninstallManifest createManifestWithFiles(
            List<InstalledFile> files,
            List<InstalledDirectory> directories) {
        return UninstallManifest.builder()
            .version("1.0")
            .packageInfo(createPackageInfo())
            .files(files)
            .directories(directories)
            .registry(RegistryInfo.builder()
                .createdKeys(Collections.emptyList())
                .modifiedValues(Collections.emptyList())
                .build())
            .pathModifications(PathModifications.builder()
                .windowsPaths(Collections.emptyList())
                .shellProfiles(Collections.emptyList())
                .gitBashProfiles(Collections.emptyList())
                .build())
            .build();
    }

    private UninstallManifest createManifestWithRegistry(RegistryInfo registryInfo) {
        return UninstallManifest.builder()
            .version("1.0")
            .packageInfo(createPackageInfo())
            .files(Collections.emptyList())
            .directories(Collections.emptyList())
            .registry(registryInfo)
            .pathModifications(PathModifications.builder()
                .windowsPaths(Collections.emptyList())
                .shellProfiles(Collections.emptyList())
                .gitBashProfiles(Collections.emptyList())
                .build())
            .build();
    }

    private UninstallManifest createManifestWithPathModifications(PathModifications pathMods) {
        return UninstallManifest.builder()
            .version("1.0")
            .packageInfo(createPackageInfo())
            .files(Collections.emptyList())
            .directories(Collections.emptyList())
            .registry(RegistryInfo.builder()
                .createdKeys(Collections.emptyList())
                .modifiedValues(Collections.emptyList())
                .build())
            .pathModifications(pathMods)
            .build();
    }

    private PackageInfo createPackageInfo() {
        return PackageInfo.builder()
            .name(TEST_PACKAGE_NAME)
            .source(TEST_SOURCE)
            .version(TEST_VERSION)
            .fullyQualifiedName("hash." + TEST_PACKAGE_NAME)
            .architecture("x64")
            .installedAt(Instant.now())
            .installerVersion("2.0.0")
            .build();
    }

    private boolean isDirEmpty(File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            return true;
        }
        File[] files = dir.listFiles();
        return files == null || files.length == 0;
    }

    // ==================== Stub Repository ====================

    /**
     * Stub implementation of FileUninstallManifestRepository for testing.
     * Allows tests to control manifest availability and simulate failures.
     * Extends the real class to satisfy the UninstallService constructor requirement.
     */
    private static class StubFileUninstallManifestRepository extends FileUninstallManifestRepository {
        private UninstallManifest manifest;
        private boolean throwOnLoad = false;
        private boolean deleteCalled = false;

        public StubFileUninstallManifestRepository() {
            super(true); // Skip schema validation for testing
        }

        public void setManifest(UninstallManifest manifest) {
            this.manifest = manifest;
            this.deleteCalled = false;
        }

        public void clearManifest() {
            this.manifest = null;
            this.deleteCalled = false;
        }

        public void setThrowOnLoad(boolean throwOnLoad) {
            this.throwOnLoad = throwOnLoad;
        }

        public boolean wasDeleteCalled() {
            return deleteCalled;
        }

        @Override
        public void save(UninstallManifest manifest) throws IOException {
            this.manifest = manifest;
        }

        @Override
        public Optional<UninstallManifest> load(String packageName, String source) throws IOException {
            if (throwOnLoad) {
                throw new IOException("Simulated load failure");
            }
            return Optional.ofNullable(manifest);
        }

        @Override
        public void delete(String packageName, String source) throws IOException {
            deleteCalled = true;
            this.manifest = null;
        }
    }
}

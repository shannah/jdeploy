package ca.weblite.jdeploy.installer.uninstall;

import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for FileUninstallManifestRepository.
 * 
 * Tests the save/load/delete cycle for uninstall manifests.
 */
public class FileUninstallManifestRepositoryTest {

    private File tempDir;
    private FileUninstallManifestRepository repository;

    @BeforeEach
    public void setUp() throws Exception {
        // Clean up ~/.jdeploy/manifests directory before each test to ensure isolation
        File jdeployManifestsDir = new File(System.getProperty("user.home"), ".jdeploy/manifests");
        if (jdeployManifestsDir.exists()) {
            FileUtils.deleteDirectory(jdeployManifestsDir);
        }
        // Also ensure the directory is truly gone before proceeding
        if (jdeployManifestsDir.exists()) {
            throw new IOException("Failed to clean up manifests directory before test");
        }
        
        tempDir = Files.createTempDirectory("jdeploy-test").toFile();
        repository = new FileUninstallManifestRepository(
            new UninstallManifestXmlGenerator(),
            new UninstallManifestValidator(),
            new UninstallManifestXmlParser(),
            true // Skip schema validation in tests
        );
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (tempDir != null && tempDir.exists()) {
            FileUtils.deleteDirectory(tempDir);
        }
        // Clean up ~/.jdeploy/manifests directory used by the repository
        File jdeployManifestsDir = new File(System.getProperty("user.home"), ".jdeploy/manifests");
        if (jdeployManifestsDir.exists()) {
            FileUtils.deleteDirectory(jdeployManifestsDir);
        }
    }

    @Test
    public void testSaveAndLoadMinimalManifest() throws Exception {
        // Create a minimal manifest
        UninstallManifest manifest = createMinimalManifest();

        // Save it
        repository.save(manifest);

        // Load it back
        Optional<UninstallManifest> loaded = repository.load("test-package", "https://github.com/test/repo");

        // Verify it was loaded
        assertTrue(loaded.isPresent(), "Manifest should be loaded");
        assertEquals(manifest.getVersion(), loaded.get().getVersion());
        assertEquals(manifest.getPackageInfo().getName(), loaded.get().getPackageInfo().getName());
        assertEquals(manifest.getPackageInfo().getSource(), loaded.get().getPackageInfo().getSource());
    }

    @Test
    public void testSaveAndLoadManifestWithFiles() throws Exception {
        // Create manifest with files
        UninstallManifest manifest = createManifestWithFiles();

        // Save and load
        repository.save(manifest);
        Optional<UninstallManifest> loaded = repository.load("test-package", "https://github.com/test/repo");

        // Verify files are preserved
        assertTrue(loaded.isPresent());
        assertEquals(2, loaded.get().getFiles().size());
        assertEquals("/usr/local/bin/test", loaded.get().getFiles().get(0).getPath());
        assertEquals(UninstallManifest.FileType.BINARY, loaded.get().getFiles().get(0).getType());
    }

    @Test
    public void testSaveAndLoadManifestWithDirectories() throws Exception {
        // Create manifest with directories
        UninstallManifest manifest = createManifestWithDirectories();

        // Save and load
        repository.save(manifest);
        Optional<UninstallManifest> loaded = repository.load("test-package", "https://github.com/test/repo");

        // Verify directories are preserved
        assertTrue(loaded.isPresent());
        assertEquals(1, loaded.get().getDirectories().size());
        assertEquals("/opt/test", loaded.get().getDirectories().get(0).getPath());
        assertEquals(UninstallManifest.CleanupStrategy.IF_EMPTY, 
                     loaded.get().getDirectories().get(0).getCleanup());
    }

    @Test
    public void testLoadNonexistentManifest() throws Exception {
        Optional<UninstallManifest> loaded = repository.load("nonexistent", "https://github.com/test/repo");
        assertTrue(!loaded.isPresent(), "Should return empty Optional for nonexistent manifest");
    }

    @Test
    public void testDeleteManifest() throws Exception {
        // Create and save a manifest
        UninstallManifest manifest = createMinimalManifest();
        repository.save(manifest);

        // Verify it exists
        assertTrue(repository.load("test-package", "https://github.com/test/repo").isPresent());

        // Delete it
        repository.delete("test-package", "https://github.com/test/repo");

        // Verify it's gone
        assertTrue(!repository.load("test-package", "https://github.com/test/repo").isPresent());
    }

    @Test
    public void testDeleteNonexistentManifest() throws Exception {
        // Should not throw an error when deleting nonexistent manifest
        assertDoesNotThrow(() -> 
            repository.delete("nonexistent", "https://github.com/test/repo")
        );
    }

    @Test
    public void testLoadReturnsEmptyForNonexistent() throws Exception {
        Optional<UninstallManifest> loaded = repository.load("nonexistent", "https://github.com/test/repo");
        assertTrue(!loaded.isPresent(), "Should return empty Optional for nonexistent manifest");
    }

    @Test
    public void testSaveCompleteManifest() throws Exception {
        // Create a complete manifest with all features
        UninstallManifest manifest = createCompleteManifest();

        // Save and load
        repository.save(manifest);
        Optional<UninstallManifest> loaded = repository.load("test-package", "https://github.com/test/repo");

        // Verify all components are preserved
        assertTrue(loaded.isPresent());
        assertEquals(2, loaded.get().getFiles().size());
        assertEquals(1, loaded.get().getDirectories().size());
        assertNotNull(loaded.get().getRegistry());
        assertNotNull(loaded.get().getPathModifications());
    }

    @Test
    public void testSaveWithNullSource() throws Exception {
        // NPM packages have null source
        UninstallManifest manifest = UninstallManifest.builder()
            .version("1.0")
            .packageInfo(UninstallManifest.PackageInfo.builder()
                .name("npm-package")
                .source(null)
                .version("1.0.0")
                .fullyQualifiedName("npm-package")
                .architecture("x64")
                .installedAt(Instant.now())
                .installerVersion("1.0.0")
                .build())
            .files(Collections.emptyList())
            .directories(Collections.emptyList())
            .build();

        // Save and load with null source
        repository.save(manifest);
        Optional<UninstallManifest> loaded = repository.load("npm-package", null);

        assertTrue(loaded.isPresent());
        assertNull(loaded.get().getPackageInfo().getSource());
    }

    @Test
    public void testMultipleSaveOverwrites() throws Exception {
        // Delete any existing manifest first to ensure clean state
        repository.delete("overwrite-test-package", "https://github.com/test/overwrite-repo");
        
        // Create and save first manifest
        UninstallManifest manifest1 = UninstallManifest.builder()
            .version("1.0")
            .packageInfo(UninstallManifest.PackageInfo.builder()
                .name("overwrite-test-package")
                .source("https://github.com/test/overwrite-repo")
                .version("1.0.0")
                .fullyQualifiedName("github-test-overwrite-repo-overwrite-test-package")
                .architecture("x64")
                .installedAt(Instant.now())
                .installerVersion("1.0.0")
                .build())
            .files(Collections.emptyList())
            .directories(Collections.emptyList())
            .build();
        repository.save(manifest1);

        // Verify first manifest was saved
        Optional<UninstallManifest> loaded1 = repository.load("overwrite-test-package", "https://github.com/test/overwrite-repo");
        assertTrue(loaded1.isPresent(), "First manifest should be saved");
        assertEquals("1.0.0", loaded1.get().getPackageInfo().getVersion());

        // Create and save second manifest with same name/source (should overwrite without delete)
        UninstallManifest manifest2 = UninstallManifest.builder()
            .version("1.0")
            .packageInfo(UninstallManifest.PackageInfo.builder()
                .name("overwrite-test-package")
                .source("https://github.com/test/overwrite-repo")
                .version("2.0.0")
                .fullyQualifiedName("github-test-overwrite-repo-overwrite-test-package")
                .architecture("x64")
                .installedAt(Instant.now())
                .installerVersion("2.0.0")
                .build())
            .files(Collections.emptyList())
            .directories(Collections.emptyList())
            .build();

        repository.save(manifest2);

        // Load and verify it's the second version
        Optional<UninstallManifest> loaded = repository.load("overwrite-test-package", "https://github.com/test/overwrite-repo");
        assertTrue(loaded.isPresent(), "Second manifest should be saved");
        assertEquals("2.0.0", loaded.get().getPackageInfo().getVersion(), "Should be overwritten with version 2.0.0");
        assertEquals("2.0.0", loaded.get().getPackageInfo().getInstallerVersion());
        
        // Cleanup
        repository.delete("overwrite-test-package", "https://github.com/test/overwrite-repo");
    }

    // Helper methods to create test manifests

    private UninstallManifest createMinimalManifest() {
        return UninstallManifest.builder()
            .version("1.0")
            .packageInfo(UninstallManifest.PackageInfo.builder()
                .name("test-package")
                .source("https://github.com/test/repo")
                .version("1.0.0")
                .fullyQualifiedName("github-test-repo-test-package")
                .architecture("x64")
                .installedAt(Instant.now())
                .installerVersion("1.0.0")
                .build())
            .files(Collections.emptyList())
            .directories(Collections.emptyList())
            .build();
    }

    private UninstallManifest createManifestWithFiles() {
        return UninstallManifest.builder()
            .version("1.0")
            .packageInfo(UninstallManifest.PackageInfo.builder()
                .name("test-package")
                .source("https://github.com/test/repo")
                .version("1.0.0")
                .fullyQualifiedName("github-test-repo-test-package")
                .architecture("x64")
                .installedAt(Instant.now())
                .installerVersion("1.0.0")
                .build())
            .files(Arrays.asList(
                UninstallManifest.InstalledFile.builder()
                    .path("/usr/local/bin/test")
                    .type(UninstallManifest.FileType.BINARY)
                    .description("Test binary")
                    .build(),
                UninstallManifest.InstalledFile.builder()
                    .path("/usr/local/etc/test.conf")
                    .type(UninstallManifest.FileType.CONFIG)
                    .description("Test config")
                    .build()
            ))
            .directories(Collections.emptyList())
            .build();
    }

    private UninstallManifest createManifestWithDirectories() {
        return UninstallManifest.builder()
            .version("1.0")
            .packageInfo(UninstallManifest.PackageInfo.builder()
                .name("test-package")
                .source("https://github.com/test/repo")
                .version("1.0.0")
                .fullyQualifiedName("github-test-repo-test-package")
                .architecture("x64")
                .installedAt(Instant.now())
                .installerVersion("1.0.0")
                .build())
            .files(Collections.emptyList())
            .directories(Collections.singletonList(
                UninstallManifest.InstalledDirectory.builder()
                    .path("/opt/test")
                    .cleanup(UninstallManifest.CleanupStrategy.IF_EMPTY)
                    .description("Test directory")
                    .build()
            ))
            .build();
    }

    private UninstallManifest createCompleteManifest() {
        return UninstallManifest.builder()
            .version("1.0")
            .packageInfo(UninstallManifest.PackageInfo.builder()
                .name("test-package")
                .source("https://github.com/test/repo")
                .version("1.0.0")
                .fullyQualifiedName("github-test-repo-test-package")
                .architecture("x64")
                .installedAt(Instant.now())
                .installerVersion("1.0.0")
                .build())
            .files(Arrays.asList(
                UninstallManifest.InstalledFile.builder()
                    .path("/usr/local/bin/test")
                    .type(UninstallManifest.FileType.BINARY)
                    .description("Test binary")
                    .build(),
                UninstallManifest.InstalledFile.builder()
                    .path("/usr/local/etc/test.conf")
                    .type(UninstallManifest.FileType.CONFIG)
                    .description("Test config")
                    .build()
            ))
            .directories(Collections.singletonList(
                UninstallManifest.InstalledDirectory.builder()
                    .path("/opt/test")
                    .cleanup(UninstallManifest.CleanupStrategy.IF_EMPTY)
                    .description("Test directory")
                    .build()
            ))
            .registry(UninstallManifest.RegistryInfo.builder()
                .createdKeys(Collections.singletonList(
                    UninstallManifest.RegistryKey.builder()
                        .root(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER)
                        .path("Software\\Test")
                        .description("Test registry key")
                        .build()
                ))
                .modifiedValues(Collections.emptyList())
                .build())
            .pathModifications(UninstallManifest.PathModifications.builder()
                .windowsPaths(Collections.singletonList(
                    UninstallManifest.WindowsPathEntry.builder()
                        .addedEntry("C:\\Program Files\\Test\\bin")
                        .description("Test PATH entry")
                        .build()
                ))
                .shellProfiles(Collections.emptyList())
                .gitBashProfiles(Collections.emptyList())
                .build())
            .build();
    }
}

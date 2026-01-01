package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.util.ArchitectureUtil;
import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileCliCommandManifestRepository.
 */
public class FileCliCommandManifestRepositoryTest {

    private File tempDir;
    private FileCliCommandManifestRepository repository;
    private String originalUserHome;

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = new File(System.getProperty("java.io.tmpdir"), 
                          "jdeploy-cli-manifest-test-" + System.currentTimeMillis());
        tempDir.mkdirs();
        
        // Override user.home for testing
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.getAbsolutePath());
        
        repository = new FileCliCommandManifestRepository();
    }

    @AfterEach
    public void tearDown() throws Exception {
        // Restore original user.home
        System.setProperty("user.home", originalUserHome);
        
        // Clean up temp directory
        if (tempDir.exists()) {
            FileUtils.deleteDirectory(tempDir);
        }
    }

    @Test
    public void testSaveAndLoadNpmPackage() throws Exception {
        // Arrange
        String packageName = "test-app";
        File binDir = new File(tempDir, "bin");
        binDir.mkdirs();
        List<String> commandNames = Arrays.asList("test-cli", "test-admin");
        long timestamp = System.currentTimeMillis();
        
        CliCommandManifest manifest = new CliCommandManifest(
            packageName, 
            null,  // NPM package (no source)
            binDir, 
            commandNames, 
            true, 
            timestamp
        );
        
        // Act
        repository.save(manifest);
        Optional<CliCommandManifest> loaded = repository.load(packageName, null);
        
        // Assert
        assertTrue(loaded.isPresent());
        CliCommandManifest loadedManifest = loaded.get();
        assertEquals(packageName, loadedManifest.getPackageName());
        assertNull(loadedManifest.getSource());
        assertEquals(binDir.getAbsolutePath(), loadedManifest.getBinDir().getAbsolutePath());
        assertEquals(commandNames, loadedManifest.getCommandNames());
        assertTrue(loadedManifest.isPathUpdated());
        assertEquals(timestamp, loadedManifest.getTimestamp());
    }

    @Test
    public void testSaveAndLoadGitHubPackage() throws Exception {
        // Arrange
        String packageName = "test-app";
        String source = "https://github.com/test/repo";
        File binDir = new File(tempDir, "bin");
        binDir.mkdirs();
        List<String> commandNames = Arrays.asList("cmd1", "cmd2", "cmd3");
        long timestamp = System.currentTimeMillis();
        
        CliCommandManifest manifest = new CliCommandManifest(
            packageName, 
            source, 
            binDir, 
            commandNames, 
            false,  // PATH not updated
            timestamp
        );
        
        // Act
        repository.save(manifest);
        Optional<CliCommandManifest> loaded = repository.load(packageName, source);
        
        // Assert
        assertTrue(loaded.isPresent());
        CliCommandManifest loadedManifest = loaded.get();
        assertEquals(packageName, loadedManifest.getPackageName());
        assertEquals(source, loadedManifest.getSource());
        assertEquals(binDir.getAbsolutePath(), loadedManifest.getBinDir().getAbsolutePath());
        assertEquals(commandNames, loadedManifest.getCommandNames());
        assertFalse(loadedManifest.isPathUpdated());
        assertEquals(timestamp, loadedManifest.getTimestamp());
    }

    @Test
    public void testLoadNonExistentManifest() throws Exception {
        // Act
        Optional<CliCommandManifest> loaded = repository.load("nonexistent-app", null);
        
        // Assert
        assertFalse(loaded.isPresent());
    }

    @Test
    public void testDeleteManifest() throws Exception {
        // Arrange
        String packageName = "test-app";
        File binDir = new File(tempDir, "bin");
        binDir.mkdirs();
        List<String> commandNames = Arrays.asList("test-cli");
        long timestamp = System.currentTimeMillis();
        
        CliCommandManifest manifest = new CliCommandManifest(
            packageName, 
            null, 
            binDir, 
            commandNames, 
            true, 
            timestamp
        );
        
        // Save the manifest
        repository.save(manifest);
        assertTrue(repository.load(packageName, null).isPresent());
        
        // Act - Delete
        repository.delete(packageName, null);
        
        // Assert
        assertFalse(repository.load(packageName, null).isPresent());
    }

    @Test
    public void testDeleteNonExistentManifest() throws Exception {
        // Act & Assert - Should not throw
        assertDoesNotThrow(() -> repository.delete("nonexistent-app", null));
    }

    @Test
    public void testSaveMultipleManifestsForSamePackage() throws Exception {
        // Arrange
        String packageName = "test-app";
        File binDir = new File(tempDir, "bin");
        binDir.mkdirs();
        
        long timestamp1 = System.currentTimeMillis();
        CliCommandManifest manifest1 = new CliCommandManifest(
            packageName, null, binDir, 
            Arrays.asList("cmd1"), true, timestamp1
        );
        
        // Wait a bit to ensure different timestamp
        Thread.sleep(10);
        long timestamp2 = System.currentTimeMillis();
        CliCommandManifest manifest2 = new CliCommandManifest(
            packageName, null, binDir, 
            Arrays.asList("cmd1", "cmd2"), true, timestamp2
        );
        
        // Act
        repository.save(manifest1);
        repository.save(manifest2);
        Optional<CliCommandManifest> loaded = repository.load(packageName, null);
        
        // Assert - Should load the most recent one (manifest2)
        assertTrue(loaded.isPresent());
        assertEquals(2, loaded.get().getCommandNames().size());
        assertEquals(timestamp2, loaded.get().getTimestamp());
    }

    @Test
    public void testManifestDirectoryStructure() throws Exception {
        // Arrange
        String packageName = "test-app";
        File binDir = new File(tempDir, "bin");
        binDir.mkdirs();
        long timestamp = System.currentTimeMillis();
        
        CliCommandManifest manifest = new CliCommandManifest(
            packageName, null, binDir, 
            Arrays.asList("test-cli"), true, timestamp
        );
        
        // Act
        repository.save(manifest);
        
        // Assert - Check directory structure
        String arch = ArchitectureUtil.getArchitecture();
        String fqpn = CliCommandBinDirResolver.computeFullyQualifiedPackageName(packageName, null);
        File expectedDir = new File(tempDir, ".jdeploy/manifests/" + arch + "/" + fqpn);
        
        assertTrue(expectedDir.exists(), "Expected manifest directory to exist: " + expectedDir);
        
        File[] files = expectedDir.listFiles((dir, name) -> name.endsWith(".json"));
        assertNotNull(files);
        assertEquals(1, files.length);
        assertTrue(files[0].getName().contains(packageName));
        assertTrue(files[0].getName().contains(String.valueOf(timestamp)));
    }

    @Test
    public void testSaveWithEmptyCommandNamesList() throws Exception {
        // Arrange
        String packageName = "test-app";
        File binDir = new File(tempDir, "bin");
        binDir.mkdirs();
        long timestamp = System.currentTimeMillis();
        
        CliCommandManifest manifest = new CliCommandManifest(
            packageName, null, binDir, 
            Arrays.asList(), false, timestamp  // Empty list
        );
        
        // Act
        repository.save(manifest);
        Optional<CliCommandManifest> loaded = repository.load(packageName, null);
        
        // Assert
        assertTrue(loaded.isPresent());
        assertTrue(loaded.get().getCommandNames().isEmpty());
    }

    @Test
    public void testLoadCreatesParentDirectoriesIfMissing() throws Exception {
        // Arrange
        String packageName = "test-app";
        File binDir = new File(tempDir, "bin");
        binDir.mkdirs();
        long timestamp = System.currentTimeMillis();
        
        CliCommandManifest manifest = new CliCommandManifest(
            packageName, null, binDir, 
            Arrays.asList("cmd1"), true, timestamp
        );
        
        // Act
        repository.save(manifest);
        
        // Assert - Parent directories should be created automatically
        String arch = ArchitectureUtil.getArchitecture();
        String fqpn = CliCommandBinDirResolver.computeFullyQualifiedPackageName(packageName, null);
        File expectedDir = new File(tempDir, ".jdeploy/manifests/" + arch + "/" + fqpn);
        assertTrue(expectedDir.exists());
    }

    @Test
    public void testDifferentPackagesStoredSeparately() throws Exception {
        // Arrange
        File binDir = new File(tempDir, "bin");
        binDir.mkdirs();
        long timestamp = System.currentTimeMillis();
        
        CliCommandManifest manifest1 = new CliCommandManifest(
            "app1", null, binDir, Arrays.asList("cmd1"), true, timestamp
        );
        
        CliCommandManifest manifest2 = new CliCommandManifest(
            "app2", null, binDir, Arrays.asList("cmd2"), true, timestamp
        );
        
        // Act
        repository.save(manifest1);
        repository.save(manifest2);
        
        Optional<CliCommandManifest> loaded1 = repository.load("app1", null);
        Optional<CliCommandManifest> loaded2 = repository.load("app2", null);
        
        // Assert
        assertTrue(loaded1.isPresent());
        assertTrue(loaded2.isPresent());
        assertEquals("app1", loaded1.get().getPackageName());
        assertEquals("app2", loaded2.get().getPackageName());
    }

    @Test
    public void testDifferentSourcesStoredSeparately() throws Exception {
        // Arrange
        String packageName = "test-app";
        String source1 = "https://github.com/user1/repo";
        String source2 = "https://github.com/user2/repo";
        File binDir = new File(tempDir, "bin");
        binDir.mkdirs();
        long timestamp = System.currentTimeMillis();
        
        CliCommandManifest manifest1 = new CliCommandManifest(
            packageName, source1, binDir, Arrays.asList("cmd1"), true, timestamp
        );
        
        CliCommandManifest manifest2 = new CliCommandManifest(
            packageName, source2, binDir, Arrays.asList("cmd2"), true, timestamp
        );
        
        // Act
        repository.save(manifest1);
        repository.save(manifest2);
        
        Optional<CliCommandManifest> loaded1 = repository.load(packageName, source1);
        Optional<CliCommandManifest> loaded2 = repository.load(packageName, source2);
        
        // Assert
        assertTrue(loaded1.isPresent());
        assertTrue(loaded2.isPresent());
        assertEquals(source1, loaded1.get().getSource());
        assertEquals(source2, loaded2.get().getSource());
    }
}

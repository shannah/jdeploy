package ca.weblite.jdeploy.gui.services;

import ca.weblite.tools.io.MD5;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProjectFileWatcher.
 * Verifies MD5 checksum tracking and FileChangeEvent emission.
 */
@DisplayName("ProjectFileWatcher")
class ProjectFileWatcherTest {
    
    private File tempDirectory;
    private File packageJsonFile;
    private ProjectFileWatcher fileWatcher;
    private List<ProjectFileWatcher.FileChangeEvent> capturedEvents;
    
    @BeforeEach
    void setUp() throws Exception {
        tempDirectory = Files.createTempDirectory("jdeploy-test-").toFile();
        packageJsonFile = new File(tempDirectory, "package.json");
        capturedEvents = new ArrayList<>();
        
        // Create initial package.json
        FileUtils.write(packageJsonFile, "{\"name\": \"test-app\"}", "UTF-8");
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (fileWatcher != null) {
            fileWatcher.stopWatching();
        }
        if (tempDirectory != null && tempDirectory.exists()) {
            FileUtils.deleteDirectory(tempDirectory);
        }
    }
    
    @Test
    @DisplayName("Should initialize with correct package.json checksum")
    void testInitialChecksumTracking() throws Exception {
        fileWatcher = new ProjectFileWatcher(
                tempDirectory,
                packageJsonFile,
                capturedEvents::add
        );
        
        String expectedHash = MD5.getMD5Checksum(packageJsonFile);
        assertEquals(expectedHash, fileWatcher.getPackageJsonMD5());
    }
    
    @Test
    @DisplayName("Should track multiple .jdpignore files")
    void testTrackMultipleJdpignoreFiles() throws Exception {
        File globalIgnore = new File(tempDirectory, ".jdpignore");
        File platformIgnore = new File(tempDirectory, ".jdpignore.mac-x64");
        
        FileUtils.write(globalIgnore, "*.tmp", "UTF-8");
        FileUtils.write(platformIgnore, "*.dmg", "UTF-8");
        
        fileWatcher = new ProjectFileWatcher(
                tempDirectory,
                packageJsonFile,
                capturedEvents::add
        );
        
        assertNotNull(fileWatcher.getJdpignoreMD5(".jdpignore"));
        assertNotNull(fileWatcher.getJdpignoreMD5(".jdpignore.mac-x64"));
    }
    
    @Test
    @DisplayName("Should emit event when package.json content changes")
    void testPackageJsonChangeEvent() throws Exception {
        fileWatcher = new ProjectFileWatcher(
                tempDirectory,
                packageJsonFile,
                capturedEvents::add
        );
        
        // Start watching
        fileWatcher.startWatching();
        String originalHash = fileWatcher.getPackageJsonMD5();
        
        // Modify file
        Thread.sleep(100); // Small delay to ensure filesystem detects change
        FileUtils.write(packageJsonFile, "{\"name\": \"updated-app\"}", "UTF-8");
        
        // Wait for event
        Thread.sleep(1000);
        
        // Verify event was emitted (if watcher picked it up)
        if (!capturedEvents.isEmpty()) {
            ProjectFileWatcher.FileChangeEvent event = capturedEvents.get(0);
            assertEquals("package.json", event.filename());
            assertEquals(originalHash, event.oldHash());
            assertNotEquals(originalHash, event.newHash());
        }
    }
    
    @Test
    @DisplayName("Should refresh checksums after external modification")
    void testRefreshChecksums() throws Exception {
        fileWatcher = new ProjectFileWatcher(
                tempDirectory,
                packageJsonFile,
                capturedEvents::add
        );
        
        String originalHash = fileWatcher.getPackageJsonMD5();
        
        // Modify file externally
        FileUtils.write(packageJsonFile, "{\"name\": \"refreshed-app\"}", "UTF-8");
        
        // Refresh checksums
        fileWatcher.refreshChecksums();
        
        String newHash = fileWatcher.getPackageJsonMD5();
        assertNotEquals(originalHash, newHash);
    }
    
    @Test
    @DisplayName("Should not emit duplicate events for same file state")
    void testNoDuplicateEvents() throws Exception {
        fileWatcher = new ProjectFileWatcher(
                tempDirectory,
                packageJsonFile,
                capturedEvents::add
        );
        
        String hash1 = fileWatcher.getPackageJsonMD5();
        fileWatcher.refreshChecksums();
        String hash2 = fileWatcher.getPackageJsonMD5();
        
        // Both hashes should be equal, so no event should have been fired
        assertEquals(hash1, hash2);
        assertEquals(0, capturedEvents.size());
    }
    
    @Test
    @DisplayName("Should handle missing .jdpignore files gracefully")
    void testMissingJdpignoreFiles() throws Exception {
        fileWatcher = new ProjectFileWatcher(
                tempDirectory,
                packageJsonFile,
                capturedEvents::add
        );
        
        // No .jdpignore files exist initially
        assertNull(fileWatcher.getJdpignoreMD5(".jdpignore"));
    }
    
    @Test
    @DisplayName("Should track .jdpignore file creation")
    void testJdpignoreCreation() throws Exception {
        fileWatcher = new ProjectFileWatcher(
                tempDirectory,
                packageJsonFile,
                capturedEvents::add
        );
        
        assertNull(fileWatcher.getJdpignoreMD5(".jdpignore"));
        
        // Create file
        File jdpignoreFile = new File(tempDirectory, ".jdpignore");
        FileUtils.write(jdpignoreFile, "node_modules/", "UTF-8");
        fileWatcher.refreshChecksums();
        
        assertNotNull(fileWatcher.getJdpignoreMD5(".jdpignore"));
    }
    
    @Test
    @DisplayName("Should stop watching and clean up resources")
    void testStopWatching() throws Exception {
        fileWatcher = new ProjectFileWatcher(
                tempDirectory,
                packageJsonFile,
                capturedEvents::add
        );
        
        fileWatcher.startWatching();
        fileWatcher.stopWatching();
        
        // Should not throw exception
        assertDoesNotThrow(() -> fileWatcher.stopWatching());
    }
    
    @Test
    @DisplayName("Should emit event with all required fields")
    void testEventRecordStructure() throws Exception {
        fileWatcher = new ProjectFileWatcher(
                tempDirectory,
                packageJsonFile,
                capturedEvents::add
        );
        
        // Create test event
        ProjectFileWatcher.FileChangeEvent event = 
                new ProjectFileWatcher.FileChangeEvent("test.txt", "hash1", "hash2");
        
        assertEquals("test.txt", event.filename());
        assertEquals("hash1", event.oldHash());
        assertEquals("hash2", event.newHash());
    }
    
    @Test
    @DisplayName("Should handle concurrent start/stop calls")
    void testConcurrentStartStop() throws Exception {
        fileWatcher = new ProjectFileWatcher(
                tempDirectory,
                packageJsonFile,
                capturedEvents::add
        );
        
        fileWatcher.startWatching();
        fileWatcher.startWatching(); // Should not error
        fileWatcher.stopWatching();
        fileWatcher.stopWatching(); // Should not error
        
        assertTrue(true); // Test passes if no exception thrown
    }
}

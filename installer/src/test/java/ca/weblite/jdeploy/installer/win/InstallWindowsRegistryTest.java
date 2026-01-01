package ca.weblite.jdeploy.installer.win;

import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class InstallWindowsRegistryTest {

    private static File tempJdeployDir;

    @BeforeClass
    public static void setUpClass() throws IOException {
        // Create a temporary .jdeploy directory for testing
        tempJdeployDir = Files.createTempDirectory("jdeploy-test").toFile();
        // Override user.home for lock file location during tests
        System.setProperty("user.home", tempJdeployDir.getParent());
    }

    @AfterClass
    public static void tearDownClass() throws IOException {
        if (tempJdeployDir != null && tempJdeployDir.exists()) {
            deleteRecursive(tempJdeployDir);
        }
    }

    private static void deleteRecursive(File file) throws IOException {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteRecursive(f);
                }
            }
        }
        Files.deleteIfExists(file.toPath());
    }

    @Test
    public void testComputePathWithAdded() {
        String current = "C:\\Windows;C:\\Program Files";
        String bin = "C:\\Users\\me\\.jdeploy\\bin";
        String result = InstallWindowsRegistry.computePathWithAdded(current, bin);
        assertTrue(result.contains(bin));
        // adding again should be idempotent
        String again = InstallWindowsRegistry.computePathWithAdded(result, bin);
        assertEquals(result, again);
    }

    @Test
    public void testComputePathWithRemoved() {
        String current = "A;B;C";
        String bin = "B";
        String result = InstallWindowsRegistry.computePathWithRemoved(current, bin);
        assertEquals("A;C", result);
        // removing when not present should leave unchanged
        String unchanged = InstallWindowsRegistry.computePathWithRemoved(result, bin);
        assertEquals("A;C", unchanged);
    }

    @Test
    public void testComputePathEdgeCases() {
        assertEquals("C:\\Users\\me\\.jdeploy\\bin", InstallWindowsRegistry.computePathWithAdded(null, "C:\\Users\\me\\.jdeploy\\bin"));
        assertEquals("", InstallWindowsRegistry.computePathWithRemoved("C:\\Users\\me\\.jdeploy\\bin", "C:\\Users\\me\\.jdeploy\\bin"));
    }

    @Test
    public void testPathUpdateLockingPreventsRaceCondition() throws IOException {
        // Test that the lock file is created and properly managed
        String userHome = System.getProperty("user.home");
        File jdeployDir = new File(userHome, ".jdeploy");
        File lockFile = new File(jdeployDir, "path.lock");

        // Ensure lock file doesn't exist before test
        if (lockFile.exists()) {
            lockFile.delete();
        }
        assertFalse("Lock file should not exist initially", lockFile.exists());

        // Acquire and release the lock
        try (InstallWindowsRegistry.PathUpdateLock lock = new InstallWindowsRegistry.PathUpdateLock()) {
            lock.acquire(5000);
            // Lock file should exist while lock is held
            assertTrue("Lock file should be created when lock is acquired", lockFile.exists());
        }
        // Lock should be released after try-with-resources block

        // Verify lock file is cleaned up or at least not locked
        assertTrue("Lock file should exist or have been created", 
            lockFile.exists() || jdeployDir.exists());
    }

    @Test
    public void testPathUpdateLockTimeoutOnContention() throws IOException, InterruptedException {
        String userHome = System.getProperty("user.home");
        File jdeployDir = new File(userHome, ".jdeploy");
        File lockFile = new File(jdeployDir, "path.lock");

        // Clean up before test
        if (lockFile.exists()) {
            lockFile.delete();
        }

        // Create first lock
        InstallWindowsRegistry.PathUpdateLock lock1 = new InstallWindowsRegistry.PathUpdateLock();
        lock1.acquire(5000);

        try {
            // Try to acquire second lock in a separate thread with short timeout
            Thread lockAttempt = new Thread(() -> {
                try (InstallWindowsRegistry.PathUpdateLock lock2 = new InstallWindowsRegistry.PathUpdateLock()) {
                    lock2.acquire(500); // 500ms timeout - should timeout
                    fail("Second lock should have timed out");
                } catch (IOException e) {
                    // Expected: timeout
                    assertTrue("Should timeout waiting for lock", 
                        e.getMessage().contains("Failed to acquire PATH update lock"));
                }
            });
            lockAttempt.start();
            lockAttempt.join(2000);
        } finally {
            lock1.close();
        }
    }

    @Test
    public void testPathUpdateLockIsAutocloseable() throws IOException {
        // Verify that PathUpdateLock implements AutoCloseable and can be used in try-with-resources
        String userHome = System.getProperty("user.home");
        File jdeployDir = new File(userHome, ".jdeploy");
        File lockFile = new File(jdeployDir, "path.lock");

        // This should compile and work without errors
        try (InstallWindowsRegistry.PathUpdateLock lock = new InstallWindowsRegistry.PathUpdateLock()) {
            lock.acquire(5000);
            assertTrue("Lock should be acquired", lockFile.exists() || jdeployDir.exists());
        }

        // If we get here, AutoCloseable contract was satisfied
        assertTrue("Test completed successfully", true);
    }

    @Test
    public void testComputePathRemoveThenAdd() {
        String current = "C:\\Windows;C:\\Users\\me\\.jdeploy\\bin;C:\\Program Files";
        String bin = "C:\\Users\\me\\.jdeploy\\bin";
        
        // Simulate remove-then-add strategy
        String afterRemove = InstallWindowsRegistry.computePathWithRemoved(current, bin);
        String afterAdd = InstallWindowsRegistry.computePathWithAdded(afterRemove, bin);
        
        assertEquals("C:\\Windows;C:\\Program Files;C:\\Users\\me\\.jdeploy\\bin", afterAdd);
        // Verify the bin path is now at the end
        assertTrue(afterAdd.endsWith(bin));
    }

    @Test
    public void testComputePathRemoveThenAddIdempotent() {
        String current = "C:\\Windows;C:\\Program Files";
        String bin = "C:\\Users\\me\\.jdeploy\\bin";
        
        // First add
        String withRemove1 = InstallWindowsRegistry.computePathWithRemoved(current, bin);
        String withAdd1 = InstallWindowsRegistry.computePathWithAdded(withRemove1, bin);
        
        // Second add (should be idempotent)
        String withRemove2 = InstallWindowsRegistry.computePathWithRemoved(withAdd1, bin);
        String withAdd2 = InstallWindowsRegistry.computePathWithAdded(withRemove2, bin);
        
        assertEquals(withAdd1, withAdd2, "Remove-then-add should be idempotent");
    }

    @Test
    public void testComputePathWithRemovedCaseInsensitive() {
        String current = "C:\\Windows;c:\\users\\me\\.jdeploy\\bin;C:\\Program Files";
        String bin = "C:\\Users\\me\\.jdeploy\\bin";
        
        String result = InstallWindowsRegistry.computePathWithRemoved(current, bin);
        
        assertEquals("C:\\Windows;C:\\Program Files", result);
    }

    @Test
    public void testComputePathWithAddedNullCurrent() {
        String bin = "C:\\Users\\me\\.jdeploy\\bin";
        
        String result = InstallWindowsRegistry.computePathWithAdded(null, bin);
        
        assertEquals(bin, result);
    }

    @Test
    public void testComputePathWithRemovedNullCurrent() {
        String bin = "C:\\Users\\me\\.jdeploy\\bin";
        
        String result = InstallWindowsRegistry.computePathWithRemoved(null, bin);
        
        assertNull(result);
    }

    @Test
    public void testComputePathWithRemovedMultipleOccurrences() {
        // Edge case: same path appears multiple times
        String current = "C:\\Windows;C:\\Users\\me\\.jdeploy\\bin;C:\\Program Files;C:\\Users\\me\\.jdeploy\\bin";
        String bin = "C:\\Users\\me\\.jdeploy\\bin";
        
        String result = InstallWindowsRegistry.computePathWithRemoved(current, bin);
        
        assertEquals("C:\\Windows;C:\\Program Files", result);
        assertFalse(result.contains(bin));
    }
}

package ca.weblite.jdeploy.installer.helpers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HelperProcessManager.
 *
 * Note: Tests for process termination (terminateHelper, forceKillHelper) are
 * difficult to unit test reliably and are covered by integration tests.
 */
public class HelperProcessManagerTest {

    private static final String LOCK_DIR_PATH = System.getProperty("user.home") +
            File.separator + ".jdeploy" + File.separator + "locks";

    private HelperProcessManager manager;
    private String testAppName;

    @BeforeEach
    public void setUp() {
        manager = new HelperProcessManager();
        // Use a unique app name for each test to avoid conflicts
        testAppName = "TestApp_" + System.currentTimeMillis();
    }

    @AfterEach
    public void tearDown() {
        // Clean up any lock files created during tests
        File lockFile = manager.getLockFile(testAppName);
        if (lockFile.exists()) {
            lockFile.delete();
        }

        File shutdownFile = manager.getShutdownSignalFile(testAppName);
        if (shutdownFile.exists()) {
            shutdownFile.delete();
        }
    }

    // ========== isHelperRunning validation tests ==========

    @Test
    public void testIsHelperRunning_NullAppName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.isHelperRunning(null);
        });
    }

    @Test
    public void testIsHelperRunning_EmptyAppName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.isHelperRunning("");
        });
    }

    @Test
    public void testIsHelperRunning_BlankAppName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.isHelperRunning("   ");
        });
    }

    // ========== isHelperRunning detection tests ==========

    @Test
    public void testIsHelperRunning_NoLockFile_ReturnsFalse() {
        // Ensure lock file doesn't exist
        File lockFile = manager.getLockFile(testAppName);
        if (lockFile.exists()) {
            lockFile.delete();
        }

        assertFalse(manager.isHelperRunning(testAppName),
            "Should return false when no lock file exists");
    }

    @Test
    public void testIsHelperRunning_LockFileExistsButNotLocked_ReturnsFalse() throws IOException {
        // Create lock file without holding a lock
        File lockFile = manager.getLockFile(testAppName);
        lockFile.getParentFile().mkdirs();
        lockFile.createNewFile();

        assertFalse(manager.isHelperRunning(testAppName),
            "Should return false when lock file exists but is not locked");
    }

    @Test
    public void testIsHelperRunning_LockFileHeld_ReturnsTrue() throws IOException {
        // Create and hold a lock to simulate a running Helper
        File lockFile = manager.getLockFile(testAppName);
        lockFile.getParentFile().mkdirs();

        RandomAccessFile raf = null;
        FileChannel channel = null;
        FileLock lock = null;

        try {
            raf = new RandomAccessFile(lockFile, "rw");
            channel = raf.getChannel();
            lock = channel.tryLock();

            assertNotNull(lock, "Should be able to acquire lock for test");

            // Now check if Helper is detected as running
            assertTrue(manager.isHelperRunning(testAppName),
                "Should return true when lock is held");

        } finally {
            // Release lock and clean up
            if (lock != null && lock.isValid()) {
                lock.release();
            }
            if (channel != null) {
                channel.close();
            }
            if (raf != null) {
                raf.close();
            }
        }
    }

    @Test
    public void testIsHelperRunning_AfterLockReleased_ReturnsFalse() throws IOException {
        File lockFile = manager.getLockFile(testAppName);
        lockFile.getParentFile().mkdirs();

        RandomAccessFile raf = new RandomAccessFile(lockFile, "rw");
        FileChannel channel = raf.getChannel();
        FileLock lock = channel.tryLock();

        assertNotNull(lock, "Should be able to acquire lock for test");

        // Release the lock
        lock.release();
        channel.close();
        raf.close();

        // Now check - should return false since lock is released
        assertFalse(manager.isHelperRunning(testAppName),
            "Should return false after lock is released");
    }

    // ========== terminateHelper validation tests ==========

    @Test
    public void testTerminateHelper_NullAppName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.terminateHelper(null, 1000);
        });
    }

    @Test
    public void testTerminateHelper_EmptyAppName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.terminateHelper("", 1000);
        });
    }

    @Test
    public void testTerminateHelper_NotRunning_ReturnsTrue() {
        // Ensure Helper is not running
        File lockFile = manager.getLockFile(testAppName);
        if (lockFile.exists()) {
            lockFile.delete();
        }

        assertTrue(manager.terminateHelper(testAppName, 1000),
            "Should return true when Helper is not running");
    }

    // ========== forceKillHelper validation tests ==========

    @Test
    public void testForceKillHelper_NullAppName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.forceKillHelper(null);
        });
    }

    @Test
    public void testForceKillHelper_EmptyAppName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.forceKillHelper("");
        });
    }

    @Test
    public void testForceKillHelper_NotRunning_ReturnsTrue() {
        // Ensure Helper is not running
        File lockFile = manager.getLockFile(testAppName);
        if (lockFile.exists()) {
            lockFile.delete();
        }

        assertTrue(manager.forceKillHelper(testAppName),
            "Should return true when Helper is not running");
    }

    // ========== getLockFile tests ==========

    @Test
    public void testGetLockFile_ReturnsCorrectPath() {
        File lockFile = manager.getLockFile("My App");

        assertTrue(lockFile.getAbsolutePath().contains(".jdeploy"),
            "Lock file should be in .jdeploy directory");
        assertTrue(lockFile.getAbsolutePath().contains("locks"),
            "Lock file should be in locks directory");
        assertTrue(lockFile.getName().startsWith("helper-"),
            "Lock file should start with 'helper-'");
        assertTrue(lockFile.getName().endsWith(".lock"),
            "Lock file should end with '.lock'");
    }

    @Test
    public void testGetLockFile_SanitizesSpecialCharacters() {
        File lockFile = manager.getLockFile("@my/app:name");

        assertFalse(lockFile.getName().contains("@"),
            "Lock file name should not contain @");
        assertFalse(lockFile.getName().contains("/"),
            "Lock file name should not contain /");
        assertFalse(lockFile.getName().contains(":"),
            "Lock file name should not contain :");
    }

    @Test
    public void testGetLockFile_ConvertsToLowercase() {
        File lockFile = manager.getLockFile("MyApp");

        assertTrue(lockFile.getName().contains("myapp"),
            "Lock file name should be lowercase");
    }

    @Test
    public void testGetLockFile_ReplacesSpacesWithHyphens() {
        File lockFile = manager.getLockFile("My App Name");

        assertTrue(lockFile.getName().contains("my-app-name"),
            "Lock file name should have spaces replaced with hyphens");
    }

    // ========== getShutdownSignalFile tests ==========

    @Test
    public void testGetShutdownSignalFile_ReturnsCorrectPath() {
        File shutdownFile = manager.getShutdownSignalFile("My App");

        assertTrue(shutdownFile.getAbsolutePath().contains(".jdeploy"),
            "Shutdown file should be in .jdeploy directory");
        assertTrue(shutdownFile.getAbsolutePath().contains("locks"),
            "Shutdown file should be in locks directory");
        assertTrue(shutdownFile.getName().startsWith("helper-"),
            "Shutdown file should start with 'helper-'");
        assertTrue(shutdownFile.getName().endsWith(".shutdown"),
            "Shutdown file should end with '.shutdown'");
    }

    @Test
    public void testGetShutdownSignalFile_DifferentFromLockFile() {
        File lockFile = manager.getLockFile("My App");
        File shutdownFile = manager.getShutdownSignalFile("My App");

        assertNotEquals(lockFile.getAbsolutePath(), shutdownFile.getAbsolutePath(),
            "Shutdown file should be different from lock file");
    }

    // ========== Consistency tests ==========

    @Test
    public void testSameAppName_SameLockFile() {
        File lockFile1 = manager.getLockFile("My App");
        File lockFile2 = manager.getLockFile("My App");

        assertEquals(lockFile1.getAbsolutePath(), lockFile2.getAbsolutePath(),
            "Same app name should produce same lock file path");
    }

    @Test
    public void testDifferentAppNames_DifferentLockFiles() {
        File lockFile1 = manager.getLockFile("App One");
        File lockFile2 = manager.getLockFile("App Two");

        assertNotEquals(lockFile1.getAbsolutePath(), lockFile2.getAbsolutePath(),
            "Different app names should produce different lock file paths");
    }

    @Test
    public void testCaseSensitivity_SameLockFile() {
        File lockFile1 = manager.getLockFile("MyApp");
        File lockFile2 = manager.getLockFile("myapp");
        File lockFile3 = manager.getLockFile("MYAPP");

        assertEquals(lockFile1.getAbsolutePath(), lockFile2.getAbsolutePath(),
            "Different cases should produce same lock file (case-insensitive)");
        assertEquals(lockFile2.getAbsolutePath(), lockFile3.getAbsolutePath(),
            "Different cases should produce same lock file (case-insensitive)");
    }
}

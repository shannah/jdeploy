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
    private String testPackageName;
    private String testSource;
    private String testAppName;

    @BeforeEach
    public void setUp() {
        manager = new HelperProcessManager();
        // Use a unique package name for each test to avoid conflicts
        testPackageName = "@test/app_" + System.currentTimeMillis();
        testSource = null; // npm package (no source)
        testAppName = "TestApp_" + System.currentTimeMillis();
    }

    @AfterEach
    public void tearDown() {
        // Clean up any lock files created during tests
        File lockFile = manager.getLockFile(testPackageName, testSource);
        if (lockFile.exists()) {
            lockFile.delete();
        }

        File shutdownFile = manager.getShutdownSignalFile(testPackageName, testSource);
        if (shutdownFile.exists()) {
            shutdownFile.delete();
        }
    }

    // ========== isHelperRunning validation tests ==========

    @Test
    public void testIsHelperRunning_NullPackageName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.isHelperRunning(null, testSource);
        });
    }

    @Test
    public void testIsHelperRunning_EmptyPackageName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.isHelperRunning("", testSource);
        });
    }

    @Test
    public void testIsHelperRunning_BlankPackageName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.isHelperRunning("   ", testSource);
        });
    }

    // ========== isHelperRunning detection tests ==========

    @Test
    public void testIsHelperRunning_NoLockFile_ReturnsFalse() {
        // Ensure lock file doesn't exist
        File lockFile = manager.getLockFile(testPackageName, testSource);
        if (lockFile.exists()) {
            lockFile.delete();
        }

        assertFalse(manager.isHelperRunning(testPackageName, testSource),
            "Should return false when no lock file exists");
    }

    @Test
    public void testIsHelperRunning_LockFileExistsButNotLocked_ReturnsFalse() throws IOException {
        // Create lock file without holding a lock
        File lockFile = manager.getLockFile(testPackageName, testSource);
        lockFile.getParentFile().mkdirs();
        lockFile.createNewFile();

        assertFalse(manager.isHelperRunning(testPackageName, testSource),
            "Should return false when lock file exists but is not locked");
    }

    @Test
    public void testIsHelperRunning_LockFileHeld_ReturnsTrue() throws IOException {
        // Create and hold a lock to simulate a running Helper
        File lockFile = manager.getLockFile(testPackageName, testSource);
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
            assertTrue(manager.isHelperRunning(testPackageName, testSource),
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
        File lockFile = manager.getLockFile(testPackageName, testSource);
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
        assertFalse(manager.isHelperRunning(testPackageName, testSource),
            "Should return false after lock is released");
    }

    // ========== terminateHelper validation tests ==========

    @Test
    public void testTerminateHelper_NullPackageName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.terminateHelper(null, testSource, testAppName, 1000);
        });
    }

    @Test
    public void testTerminateHelper_EmptyPackageName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.terminateHelper("", testSource, testAppName, 1000);
        });
    }

    @Test
    public void testTerminateHelper_NullAppName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.terminateHelper(testPackageName, testSource, null, 1000);
        });
    }

    @Test
    public void testTerminateHelper_EmptyAppName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.terminateHelper(testPackageName, testSource, "", 1000);
        });
    }

    @Test
    public void testTerminateHelper_NotRunning_ReturnsTrue() {
        // Ensure Helper is not running
        File lockFile = manager.getLockFile(testPackageName, testSource);
        if (lockFile.exists()) {
            lockFile.delete();
        }

        assertTrue(manager.terminateHelper(testPackageName, testSource, testAppName, 1000),
            "Should return true when Helper is not running");
    }

    // ========== forceKillHelper validation tests ==========

    @Test
    public void testForceKillHelper_NullPackageName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.forceKillHelper(null, testSource, testAppName);
        });
    }

    @Test
    public void testForceKillHelper_EmptyPackageName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.forceKillHelper("", testSource, testAppName);
        });
    }

    @Test
    public void testForceKillHelper_NullAppName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.forceKillHelper(testPackageName, testSource, null);
        });
    }

    @Test
    public void testForceKillHelper_EmptyAppName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.forceKillHelper(testPackageName, testSource, "");
        });
    }

    @Test
    public void testForceKillHelper_NotRunning_ReturnsTrue() {
        // Ensure Helper is not running
        File lockFile = manager.getLockFile(testPackageName, testSource);
        if (lockFile.exists()) {
            lockFile.delete();
        }

        assertTrue(manager.forceKillHelper(testPackageName, testSource, testAppName),
            "Should return true when Helper is not running");
    }

    // ========== getLockFile tests ==========

    @Test
    public void testGetLockFile_ReturnsCorrectPath() {
        File lockFile = manager.getLockFile("@my/app", null);

        assertTrue(lockFile.getAbsolutePath().contains(".jdeploy"),
            "Lock file should be in .jdeploy directory");
        assertTrue(lockFile.getAbsolutePath().contains("locks"),
            "Lock file should be in locks directory");
        assertTrue(lockFile.getName().endsWith(".lock"),
            "Lock file should end with '.lock'");
    }

    @Test
    public void testGetLockFile_SanitizesSpecialCharacters() {
        File lockFile = manager.getLockFile("@my/app:name", null);

        assertFalse(lockFile.getName().contains("@"),
            "Lock file name should not contain @");
        assertFalse(lockFile.getName().contains("/"),
            "Lock file name should not contain /");
        assertFalse(lockFile.getName().contains(":"),
            "Lock file name should not contain :");
    }

    @Test
    public void testGetLockFile_ConvertsToLowercase() {
        File lockFile = manager.getLockFile("@My/App", null);

        assertTrue(lockFile.getName().toLowerCase().equals(lockFile.getName()),
            "Lock file name should be lowercase");
    }

    @Test
    public void testGetLockFile_WithSource_IncludesHash() {
        File lockFileWithSource = manager.getLockFile("@my/app", "https://github.com/user/repo");
        File lockFileNoSource = manager.getLockFile("@my/app", null);

        assertNotEquals(lockFileWithSource.getName(), lockFileNoSource.getName(),
            "Lock file with source should be different from lock file without source");
        assertTrue(lockFileWithSource.getName().contains("."),
            "Lock file with source should contain hash separator");
    }

    // ========== getShutdownSignalFile tests ==========

    @Test
    public void testGetShutdownSignalFile_ReturnsCorrectPath() {
        File shutdownFile = manager.getShutdownSignalFile("@my/app", null);

        assertTrue(shutdownFile.getAbsolutePath().contains(".jdeploy"),
            "Shutdown file should be in .jdeploy directory");
        assertTrue(shutdownFile.getAbsolutePath().contains("locks"),
            "Shutdown file should be in locks directory");
        assertTrue(shutdownFile.getName().endsWith(".shutdown"),
            "Shutdown file should end with '.shutdown'");
    }

    @Test
    public void testGetShutdownSignalFile_DifferentFromLockFile() {
        File lockFile = manager.getLockFile("@my/app", null);
        File shutdownFile = manager.getShutdownSignalFile("@my/app", null);

        assertNotEquals(lockFile.getAbsolutePath(), shutdownFile.getAbsolutePath(),
            "Shutdown file should be different from lock file");
    }

    // ========== Consistency tests ==========

    @Test
    public void testSamePackageName_SameLockFile() {
        File lockFile1 = manager.getLockFile("@my/app", null);
        File lockFile2 = manager.getLockFile("@my/app", null);

        assertEquals(lockFile1.getAbsolutePath(), lockFile2.getAbsolutePath(),
            "Same package name should produce same lock file path");
    }

    @Test
    public void testDifferentPackageNames_DifferentLockFiles() {
        File lockFile1 = manager.getLockFile("@my/app-one", null);
        File lockFile2 = manager.getLockFile("@my/app-two", null);

        assertNotEquals(lockFile1.getAbsolutePath(), lockFile2.getAbsolutePath(),
            "Different package names should produce different lock file paths");
    }

    @Test
    public void testCaseSensitivity_SameLockFile() {
        File lockFile1 = manager.getLockFile("@My/App", null);
        File lockFile2 = manager.getLockFile("@my/app", null);
        File lockFile3 = manager.getLockFile("@MY/APP", null);

        assertEquals(lockFile1.getAbsolutePath(), lockFile2.getAbsolutePath(),
            "Different cases should produce same lock file (case-insensitive)");
        assertEquals(lockFile2.getAbsolutePath(), lockFile3.getAbsolutePath(),
            "Different cases should produce same lock file (case-insensitive)");
    }

    @Test
    public void testSamePackageDifferentSource_DifferentLockFiles() {
        File lockFile1 = manager.getLockFile("@my/app", null);
        File lockFile2 = manager.getLockFile("@my/app", "https://github.com/user/repo");
        File lockFile3 = manager.getLockFile("@my/app", "https://github.com/other/repo");

        assertNotEquals(lockFile1.getAbsolutePath(), lockFile2.getAbsolutePath(),
            "Same package with different source should produce different lock files");
        assertNotEquals(lockFile2.getAbsolutePath(), lockFile3.getAbsolutePath(),
            "Same package with different source should produce different lock files");
    }
}

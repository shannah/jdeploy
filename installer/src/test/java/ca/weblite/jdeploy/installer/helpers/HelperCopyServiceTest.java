package ca.weblite.jdeploy.installer.helpers;

import ca.weblite.jdeploy.installer.logging.InstallationLogger;
import ca.weblite.tools.platform.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HelperCopyService.
 */
public class HelperCopyServiceTest {

    @TempDir
    File tempDir;

    private InstallationLogger mockLogger;
    private HelperCopyService service;

    @BeforeEach
    public void setUp() {
        mockLogger = mock(InstallationLogger.class);
        service = new HelperCopyService(mockLogger);
    }

    // ========== Validation tests ==========

    @Test
    public void testCopyInstaller_NullSource() {
        File destination = new File(tempDir, "dest");

        assertThrows(IllegalArgumentException.class, () -> {
            service.copyInstaller(null, destination);
        });
    }

    @Test
    public void testCopyInstaller_NonExistentSource() {
        File source = new File(tempDir, "nonexistent");
        File destination = new File(tempDir, "dest");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.copyInstaller(source, destination);
        });

        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    public void testCopyInstaller_NullDestination() throws IOException {
        File source = new File(tempDir, "source.txt");
        assertTrue(source.createNewFile());

        assertThrows(IllegalArgumentException.class, () -> {
            service.copyInstaller(source, null);
        });
    }

    // ========== Single file copy tests ==========

    @Test
    public void testCopyInstaller_SingleFile() throws IOException {
        // Create source file with content
        File source = new File(tempDir, "source.txt");
        String content = "Hello, World!";
        try (FileWriter writer = new FileWriter(source)) {
            writer.write(content);
        }

        File destination = new File(tempDir, "dest/copied.txt");

        service.copyInstaller(source, destination);

        assertTrue(destination.exists(), "Destination file should exist");
        assertEquals(content, new String(Files.readAllBytes(destination.toPath())),
            "File content should match");
    }

    @Test
    public void testCopyInstaller_CreatesParentDirectories() throws IOException {
        File source = new File(tempDir, "source.txt");
        assertTrue(source.createNewFile());

        // Destination in nested non-existent directory
        File destination = new File(tempDir, "a/b/c/d/dest.txt");

        service.copyInstaller(source, destination);

        assertTrue(destination.exists(), "Destination should exist");
        assertTrue(destination.getParentFile().exists(), "Parent directories should be created");
    }

    @Test
    public void testCopyInstaller_OverwritesExistingFile() throws IOException {
        // Create source with new content
        File source = new File(tempDir, "source.txt");
        String newContent = "New content";
        try (FileWriter writer = new FileWriter(source)) {
            writer.write(newContent);
        }

        // Create existing destination with old content
        File destination = new File(tempDir, "dest.txt");
        try (FileWriter writer = new FileWriter(destination)) {
            writer.write("Old content");
        }

        service.copyInstaller(source, destination);

        assertEquals(newContent, new String(Files.readAllBytes(destination.toPath())),
            "Destination should have new content");
    }

    // ========== Directory copy tests ==========

    @Test
    public void testCopyInstaller_Directory() throws IOException {
        // Create source directory with files
        File sourceDir = new File(tempDir, "sourceDir");
        assertTrue(sourceDir.mkdir());

        File file1 = new File(sourceDir, "file1.txt");
        File file2 = new File(sourceDir, "file2.txt");
        try (FileWriter w1 = new FileWriter(file1); FileWriter w2 = new FileWriter(file2)) {
            w1.write("Content 1");
            w2.write("Content 2");
        }

        File destDir = new File(tempDir, "destDir");

        service.copyInstaller(sourceDir, destDir);

        assertTrue(destDir.exists(), "Destination directory should exist");
        assertTrue(destDir.isDirectory(), "Destination should be a directory");
        assertTrue(new File(destDir, "file1.txt").exists(), "file1.txt should be copied");
        assertTrue(new File(destDir, "file2.txt").exists(), "file2.txt should be copied");
    }

    @Test
    public void testCopyInstaller_NestedDirectories() throws IOException {
        // Create nested directory structure
        File sourceDir = new File(tempDir, "source");
        File subDir = new File(sourceDir, "sub/nested");
        assertTrue(subDir.mkdirs());

        File rootFile = new File(sourceDir, "root.txt");
        File nestedFile = new File(subDir, "nested.txt");
        try (FileWriter w1 = new FileWriter(rootFile); FileWriter w2 = new FileWriter(nestedFile)) {
            w1.write("Root");
            w2.write("Nested");
        }

        File destDir = new File(tempDir, "dest");

        service.copyInstaller(sourceDir, destDir);

        assertTrue(new File(destDir, "root.txt").exists(), "Root file should be copied");
        assertTrue(new File(destDir, "sub/nested/nested.txt").exists(), "Nested file should be copied");
    }

    @Test
    public void testCopyInstaller_OverwritesExistingDirectory() throws IOException {
        // Create source directory
        File sourceDir = new File(tempDir, "source");
        assertTrue(sourceDir.mkdir());
        File sourceFile = new File(sourceDir, "new.txt");
        try (FileWriter w = new FileWriter(sourceFile)) {
            w.write("New");
        }

        // Create existing destination with different content
        File destDir = new File(tempDir, "dest");
        assertTrue(destDir.mkdir());
        File oldFile = new File(destDir, "old.txt");
        try (FileWriter w = new FileWriter(oldFile)) {
            w.write("Old");
        }

        service.copyInstaller(sourceDir, destDir);

        assertTrue(new File(destDir, "new.txt").exists(), "New file should exist");
        assertFalse(new File(destDir, "old.txt").exists(), "Old file should be removed");
    }

    // ========== Executable permission tests (Unix only) ==========

    @Test
    public void testCopyInstaller_PreservesExecutablePermission() throws IOException {
        // Skip on Windows
        if (Platform.getSystemPlatform().isWindows()) {
            return;
        }

        File source = new File(tempDir, "executable");
        assertTrue(source.createNewFile());
        assertTrue(source.setExecutable(true));

        File destination = new File(tempDir, "copied_executable");

        service.copyInstaller(source, destination);

        assertTrue(destination.canExecute(), "Executable permission should be preserved");
    }

    // ========== Null logger tests ==========

    @Test
    public void testCopyInstaller_NullLoggerDoesNotThrow() throws IOException {
        HelperCopyService serviceWithNullLogger = new HelperCopyService(null);

        File source = new File(tempDir, "source.txt");
        assertTrue(source.createNewFile());
        File destination = new File(tempDir, "dest.txt");

        // Should not throw NPE with null logger
        assertDoesNotThrow(() -> {
            serviceWithNullLogger.copyInstaller(source, destination);
        });

        assertTrue(destination.exists());
    }

    // ========== macOS ditto tests (only run on macOS) ==========

    @Test
    public void testCopyInstaller_MacOS_UsesDitto() throws IOException {
        // This test only verifies behavior on macOS
        if (!Platform.getSystemPlatform().isMac()) {
            return;
        }

        // Create a .app bundle structure
        File appBundle = new File(tempDir, "Test.app");
        File contents = new File(appBundle, "Contents");
        File macOS = new File(contents, "MacOS");
        assertTrue(macOS.mkdirs());

        File executable = new File(macOS, "Test");
        try (FileWriter w = new FileWriter(executable)) {
            w.write("#!/bin/bash\necho test");
        }
        assertTrue(executable.setExecutable(true));

        File infoPlist = new File(contents, "Info.plist");
        try (FileWriter w = new FileWriter(infoPlist)) {
            w.write("<?xml version=\"1.0\"?><plist></plist>");
        }

        File destBundle = new File(tempDir, "Copied.app");

        service.copyInstaller(appBundle, destBundle);

        assertTrue(destBundle.exists(), ".app bundle should be copied");
        assertTrue(new File(destBundle, "Contents/MacOS/Test").exists(), "Executable should exist");
        assertTrue(new File(destBundle, "Contents/Info.plist").exists(), "Info.plist should exist");
    }

    // ========== Logging verification tests ==========

    @Test
    public void testCopyInstaller_LogsSection() throws IOException {
        File source = new File(tempDir, "source.txt");
        assertTrue(source.createNewFile());
        File destination = new File(tempDir, "dest.txt");

        service.copyInstaller(source, destination);

        verify(mockLogger).logSection(contains("Copying"));
    }

    @Test
    public void testCopyInstaller_LogsSourceAndDestination() throws IOException {
        File source = new File(tempDir, "source.txt");
        assertTrue(source.createNewFile());
        File destination = new File(tempDir, "dest.txt");

        service.copyInstaller(source, destination);

        verify(mockLogger, atLeastOnce()).logInfo(contains("Source:"));
        verify(mockLogger, atLeastOnce()).logInfo(contains("Destination:"));
    }

    @Test
    public void testCopyInstaller_LogsCompletionMessage() throws IOException {
        File source = new File(tempDir, "source.txt");
        assertTrue(source.createNewFile());
        File destination = new File(tempDir, "dest.txt");

        service.copyInstaller(source, destination);

        verify(mockLogger).logInfo(contains("completed"));
    }

    // ========== Empty directory tests ==========

    @Test
    public void testCopyInstaller_EmptyDirectory() throws IOException {
        File sourceDir = new File(tempDir, "emptySource");
        assertTrue(sourceDir.mkdir());

        File destDir = new File(tempDir, "emptyDest");

        service.copyInstaller(sourceDir, destDir);

        assertTrue(destDir.exists(), "Empty directory should be copied");
        assertTrue(destDir.isDirectory(), "Should be a directory");
        String[] contents = destDir.list();
        assertNotNull(contents);
        assertEquals(0, contents.length, "Directory should be empty");
    }
}

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

    // ========== copyInstaller validation tests ==========

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

    // ========== copyInstaller single file copy tests ==========

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

    // ========== copyInstaller directory copy tests ==========

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

    // ========== copyContextDirectory tests ==========

    @Test
    public void testCopyContextDirectory_NullSource() {
        File destination = new File(tempDir, "dest");

        assertThrows(IllegalArgumentException.class, () -> {
            service.copyContextDirectory(null, destination);
        });
    }

    @Test
    public void testCopyContextDirectory_NonExistentSource() {
        File source = new File(tempDir, "nonexistent");
        File destination = new File(tempDir, "dest");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.copyContextDirectory(source, destination);
        });

        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    public void testCopyContextDirectory_SourceNotDirectory() throws IOException {
        File source = new File(tempDir, "file.txt");
        assertTrue(source.createNewFile());
        File destination = new File(tempDir, "dest");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.copyContextDirectory(source, destination);
        });

        assertTrue(exception.getMessage().contains("must be a directory"));
    }

    @Test
    public void testCopyContextDirectory_NullDestination() throws IOException {
        File source = new File(tempDir, ".jdeploy-files");
        assertTrue(source.mkdir());

        assertThrows(IllegalArgumentException.class, () -> {
            service.copyContextDirectory(source, null);
        });
    }

    @Test
    public void testCopyContextDirectory_CopiesAllFiles() throws IOException {
        // Create source .jdeploy-files directory with typical content
        File source = new File(tempDir, ".jdeploy-files");
        assertTrue(source.mkdir());

        File appXml = new File(source, "app.xml");
        File iconPng = new File(source, "icon.png");
        try (FileWriter w1 = new FileWriter(appXml); FileWriter w2 = new FileWriter(iconPng)) {
            w1.write("<app/>");
            w2.write("PNG_DATA");
        }

        File destination = new File(tempDir, "dest/.jdeploy-files");

        service.copyContextDirectory(source, destination);

        assertTrue(destination.exists(), "Destination should exist");
        assertTrue(destination.isDirectory(), "Destination should be a directory");
        assertTrue(new File(destination, "app.xml").exists(), "app.xml should be copied");
        assertTrue(new File(destination, "icon.png").exists(), "icon.png should be copied");
    }

    @Test
    public void testCopyContextDirectory_CopiesNestedStructure() throws IOException {
        // Create source with nested structure
        File source = new File(tempDir, ".jdeploy-files");
        File subDir = new File(source, "resources/images");
        assertTrue(subDir.mkdirs());

        File rootFile = new File(source, "app.xml");
        File nestedFile = new File(subDir, "logo.png");
        try (FileWriter w1 = new FileWriter(rootFile); FileWriter w2 = new FileWriter(nestedFile)) {
            w1.write("<app/>");
            w2.write("PNG");
        }

        File destination = new File(tempDir, "dest");

        service.copyContextDirectory(source, destination);

        assertTrue(new File(destination, "app.xml").exists(), "Root file should be copied");
        assertTrue(new File(destination, "resources/images/logo.png").exists(), "Nested file should be copied");
    }

    @Test
    public void testCopyContextDirectory_OverwritesExisting() throws IOException {
        // Create source
        File source = new File(tempDir, "source");
        assertTrue(source.mkdir());
        File newFile = new File(source, "new.txt");
        try (FileWriter w = new FileWriter(newFile)) {
            w.write("New");
        }

        // Create existing destination
        File destination = new File(tempDir, "dest");
        assertTrue(destination.mkdir());
        File oldFile = new File(destination, "old.txt");
        try (FileWriter w = new FileWriter(oldFile)) {
            w.write("Old");
        }

        service.copyContextDirectory(source, destination);

        assertTrue(new File(destination, "new.txt").exists(), "New file should exist");
        assertFalse(new File(destination, "old.txt").exists(), "Old file should be removed");
    }

    @Test
    public void testCopyContextDirectory_EmptyDirectory() throws IOException {
        File source = new File(tempDir, "emptySource");
        assertTrue(source.mkdir());

        File destination = new File(tempDir, "emptyDest");

        service.copyContextDirectory(source, destination);

        assertTrue(destination.exists(), "Empty directory should be copied");
        assertTrue(destination.isDirectory(), "Should be a directory");
        String[] contents = destination.list();
        assertNotNull(contents);
        assertEquals(0, contents.length, "Directory should be empty");
    }

    // ========== Platform-specific method selection tests ==========

    @Test
    public void testCopyInstaller_MacOS_LogsDittoUsage() throws IOException {
        if (!Platform.getSystemPlatform().isMac()) {
            return; // Skip on non-Mac
        }

        File source = new File(tempDir, "source.txt");
        assertTrue(source.createNewFile());
        File destination = new File(tempDir, "dest.txt");

        service.copyInstaller(source, destination);

        verify(mockLogger, atLeastOnce()).logInfo(contains("ditto"));
    }

    @Test
    public void testCopyInstaller_Windows_LogsFilesCopyUsage() throws IOException {
        if (!Platform.getSystemPlatform().isWindows()) {
            return; // Skip on non-Windows
        }

        File source = new File(tempDir, "source.txt");
        assertTrue(source.createNewFile());
        File destination = new File(tempDir, "dest.txt");

        service.copyInstaller(source, destination);

        verify(mockLogger, atLeastOnce()).logInfo(contains("Windows"));
    }

    @Test
    public void testCopyInstaller_Linux_LogsFilesCopyUsage() throws IOException {
        if (!Platform.getSystemPlatform().isLinux()) {
            return; // Skip on non-Linux
        }

        File source = new File(tempDir, "source.txt");
        assertTrue(source.createNewFile());
        File destination = new File(tempDir, "dest.txt");

        service.copyInstaller(source, destination);

        verify(mockLogger, atLeastOnce()).logInfo(contains("Linux"));
    }

    // ========== Executable permission tests ==========

    @Test
    public void testCopyInstaller_Linux_PreservesExecutablePermission() throws IOException {
        if (!Platform.getSystemPlatform().isLinux()) {
            return; // Skip on non-Linux
        }

        File source = new File(tempDir, "executable");
        assertTrue(source.createNewFile());
        assertTrue(source.setExecutable(true));

        File destination = new File(tempDir, "copied_executable");

        service.copyInstaller(source, destination);

        assertTrue(destination.canExecute(), "Executable permission should be preserved on Linux");
    }

    @Test
    public void testCopyInstaller_Linux_DirectoryWithExecutables() throws IOException {
        if (!Platform.getSystemPlatform().isLinux()) {
            return; // Skip on non-Linux
        }

        // Create source directory with an executable
        File sourceDir = new File(tempDir, "source");
        assertTrue(sourceDir.mkdir());

        File executable = new File(sourceDir, "run.sh");
        try (FileWriter w = new FileWriter(executable)) {
            w.write("#!/bin/bash\necho hello");
        }
        assertTrue(executable.setExecutable(true));

        File destDir = new File(tempDir, "dest");

        service.copyInstaller(sourceDir, destDir);

        File copiedExecutable = new File(destDir, "run.sh");
        assertTrue(copiedExecutable.exists(), "Executable should be copied");
        assertTrue(copiedExecutable.canExecute(), "Executable permission should be preserved");
    }

    // ========== macOS ditto tests ==========

    @Test
    public void testCopyInstaller_MacOS_AppBundle() throws IOException {
        if (!Platform.getSystemPlatform().isMac()) {
            return; // Skip on non-Mac
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

    // ========== Null logger tests ==========

    @Test
    public void testCopyInstaller_NullLoggerDoesNotThrow() throws IOException {
        HelperCopyService serviceWithNullLogger = new HelperCopyService(null);

        File source = new File(tempDir, "source.txt");
        assertTrue(source.createNewFile());
        File destination = new File(tempDir, "dest.txt");

        assertDoesNotThrow(() -> {
            serviceWithNullLogger.copyInstaller(source, destination);
        });

        assertTrue(destination.exists());
    }

    @Test
    public void testCopyContextDirectory_NullLoggerDoesNotThrow() throws IOException {
        HelperCopyService serviceWithNullLogger = new HelperCopyService(null);

        File source = new File(tempDir, "sourceDir");
        assertTrue(source.mkdir());
        File destination = new File(tempDir, "destDir");

        assertDoesNotThrow(() -> {
            serviceWithNullLogger.copyContextDirectory(source, destination);
        });

        assertTrue(destination.exists());
    }

    // ========== Logging verification tests ==========

    @Test
    public void testCopyInstaller_LogsSection() throws IOException {
        File source = new File(tempDir, "source.txt");
        assertTrue(source.createNewFile());
        File destination = new File(tempDir, "dest.txt");

        service.copyInstaller(source, destination);

        verify(mockLogger).logSection(contains("Copying Installer"));
    }

    @Test
    public void testCopyContextDirectory_LogsSection() throws IOException {
        File source = new File(tempDir, "sourceDir");
        assertTrue(source.mkdir());
        File destination = new File(tempDir, "destDir");

        service.copyContextDirectory(source, destination);

        verify(mockLogger).logSection(contains("Context Directory"));
    }

    @Test
    public void testCopyInstaller_LogsCompletionMessage() throws IOException {
        File source = new File(tempDir, "source.txt");
        assertTrue(source.createNewFile());
        File destination = new File(tempDir, "dest.txt");

        service.copyInstaller(source, destination);

        // Use atLeastOnce() because on macOS, quarantine removal also logs "completed"
        verify(mockLogger, atLeastOnce()).logInfo(contains("completed"));
    }

    @Test
    public void testCopyContextDirectory_LogsCompletionMessage() throws IOException {
        File source = new File(tempDir, "sourceDir");
        assertTrue(source.mkdir());
        File destination = new File(tempDir, "destDir");

        service.copyContextDirectory(source, destination);

        verify(mockLogger).logInfo(contains("completed"));
    }

    // ========== Error logging tests ==========

    @Test
    public void testCopyInstaller_DeleteFailure_LogsErrorBeforeThrowing() throws IOException {
        // Create a destination that can't be deleted
        File source = new File(tempDir, "source.txt");
        assertTrue(source.createNewFile());

        // Create destination directory with restricted permissions
        File destination = new File(tempDir, "dest");
        assertTrue(destination.mkdir());

        // Create a file inside the destination
        File lockedFile = new File(destination, "locked.txt");
        assertTrue(lockedFile.createNewFile());

        // On macOS/Linux, setting a file read-only doesn't prevent deletion if you have
        // write permission on the parent directory. We need to set the parent read-only.
        if (destination.setReadOnly()) {
            try {
                // First verify that we actually can't delete the file
                boolean canDelete = lockedFile.delete();
                if (!canDelete) {
                    // Reset for copy test
                    destination.setWritable(true);
                    lockedFile.createNewFile();
                    destination.setReadOnly();

                    // Try to copy - this should fail when trying to delete the directory
                    IOException thrown = assertThrows(IOException.class, () -> {
                        service.copyInstaller(source, destination);
                    });

                    // Verify error was logged
                    verify(mockLogger, atLeastOnce()).logError(anyString(), any());

                    assertTrue(thrown.getMessage().contains("delete") ||
                               thrown.getMessage().contains("Failed"),
                        "Error message should mention deletion failure");
                }
                // If we could delete the file, skip the test as we can't prevent deletion
            } finally {
                // Cleanup
                destination.setWritable(true);
                lockedFile.delete();
                destination.delete();
            }
        }
        // If setReadOnly failed, skip the test silently
    }

    @Test
    public void testCopyInstaller_ParentDirectoryCreationFailure_LogsError() throws IOException {
        // Skip on systems where we can't restrict permissions
        File restrictedDir = new File(tempDir, "restricted");
        assertTrue(restrictedDir.mkdir());

        if (restrictedDir.setReadOnly()) {
            try {
                File source = new File(tempDir, "source.txt");
                assertTrue(source.createNewFile());

                // Try to copy to a location where parent can't be created
                File destination = new File(restrictedDir, "sub/dest.txt");

                IOException thrown = assertThrows(IOException.class, () -> {
                    service.copyInstaller(source, destination);
                });

                // Verify error was logged (might be for directory creation or file access)
                verify(mockLogger, atLeastOnce()).logError(anyString(), any());
            } finally {
                restrictedDir.setWritable(true);
            }
        }
    }

    @Test
    public void testCopyContextDirectory_DirectoryCopyFails_LogsError() throws IOException {
        // Create source directory
        File source = new File(tempDir, "source");
        assertTrue(source.mkdir());
        File sourceFile = new File(source, "test.txt");
        try (FileWriter w = new FileWriter(sourceFile)) {
            w.write("test");
        }

        // Create destination where we can't write
        File destParent = new File(tempDir, "restricted");
        assertTrue(destParent.mkdir());

        if (destParent.setReadOnly()) {
            try {
                File destination = new File(destParent, "dest");

                // This may throw or may succeed depending on OS permissions
                try {
                    service.copyContextDirectory(source, destination);
                } catch (IOException e) {
                    // Verify error was logged
                    verify(mockLogger, atLeastOnce()).logError(anyString(), any());
                }
            } finally {
                destParent.setWritable(true);
            }
        }
    }
}

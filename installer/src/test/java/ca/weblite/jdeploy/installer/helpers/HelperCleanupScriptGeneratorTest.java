package ca.weblite.jdeploy.installer.helpers;

import ca.weblite.tools.platform.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HelperCleanupScriptGenerator.
 */
public class HelperCleanupScriptGeneratorTest {

    @TempDir
    File tempDir;

    private HelperCleanupScriptGenerator generator;
    private File helperPath;
    private File helperContextDir;
    private File helperDir;

    @BeforeEach
    public void setUp() {
        generator = new HelperCleanupScriptGenerator();

        // Set up test paths
        helperPath = new File(tempDir, "My App Helper.app");
        helperContextDir = new File(tempDir, ".jdeploy-files");
        helperDir = new File(tempDir, "My App Helper");
    }

    // ========== generateCleanupScript validation tests ==========

    @Test
    public void testGenerateCleanupScript_NullHelperPath_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            generator.generateCleanupScript(null, helperContextDir, helperDir);
        });
    }

    @Test
    public void testGenerateCleanupScript_NullHelperContextDir_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            generator.generateCleanupScript(helperPath, null, helperDir);
        });
    }

    @Test
    public void testGenerateCleanupScript_NullHelperDir_DoesNotThrow() throws IOException {
        assertDoesNotThrow(() -> {
            File script = generator.generateCleanupScript(helperPath, helperContextDir, null);
            script.delete(); // Clean up
        });
    }

    @Test
    public void testGenerateCleanupScript_CreatesFile() throws IOException {
        File script = generator.generateCleanupScript(helperPath, helperContextDir, helperDir);

        try {
            assertTrue(script.exists(), "Script file should be created");
            assertTrue(script.length() > 0, "Script file should have content");
        } finally {
            script.delete();
        }
    }

    @Test
    public void testGenerateCleanupScript_CorrectExtension() throws IOException {
        File script = generator.generateCleanupScript(helperPath, helperContextDir, helperDir);

        try {
            if (Platform.getSystemPlatform().isWindows()) {
                assertTrue(script.getName().endsWith(".bat"), "Windows script should have .bat extension");
            } else {
                assertTrue(script.getName().endsWith(".sh"), "Unix script should have .sh extension");
            }
        } finally {
            script.delete();
        }
    }

    @Test
    public void testGenerateCleanupScript_UnixScriptIsExecutable() throws IOException {
        if (Platform.getSystemPlatform().isWindows()) {
            return; // Skip on Windows
        }

        File script = generator.generateCleanupScript(helperPath, helperContextDir, helperDir);

        try {
            assertTrue(script.canExecute(), "Unix script should be executable");
        } finally {
            script.delete();
        }
    }

    // ========== Unix script content tests ==========

    @Test
    public void testGenerateUnixScriptContent_ContainsShebang() {
        String content = generator.generateUnixScriptContent(helperPath, helperContextDir, helperDir);

        assertTrue(content.startsWith("#!/bin/bash"), "Script should start with shebang");
    }

    @Test
    public void testGenerateUnixScriptContent_ContainsSleep() {
        String content = generator.generateUnixScriptContent(helperPath, helperContextDir, helperDir);

        assertTrue(content.contains("sleep 2"), "Script should contain sleep command");
    }

    @Test
    public void testGenerateUnixScriptContent_ContainsHelperPath() {
        // Skip on Windows - Unix scripts use different path escaping than Windows paths
        if (Platform.getSystemPlatform().isWindows()) {
            return;
        }

        String content = generator.generateUnixScriptContent(helperPath, helperContextDir, helperDir);

        assertTrue(content.contains("rm -rf"), "Script should contain rm -rf command");
        assertTrue(content.contains(helperPath.getAbsolutePath()), "Script should contain helper path");
    }

    @Test
    public void testGenerateUnixScriptContent_ContainsContextDir() {
        // Skip on Windows - Unix scripts use different path escaping than Windows paths
        if (Platform.getSystemPlatform().isWindows()) {
            return;
        }

        String content = generator.generateUnixScriptContent(helperPath, helperContextDir, helperDir);

        assertTrue(content.contains(helperContextDir.getAbsolutePath()), "Script should contain context dir path");
    }

    @Test
    public void testGenerateUnixScriptContent_ContainsHelperDir() {
        // Skip on Windows - Unix scripts use different path escaping than Windows paths
        if (Platform.getSystemPlatform().isWindows()) {
            return;
        }

        String content = generator.generateUnixScriptContent(helperPath, helperContextDir, helperDir);

        assertTrue(content.contains("rmdir"), "Script should contain rmdir command");
        assertTrue(content.contains(helperDir.getAbsolutePath()), "Script should contain helper dir path");
        assertTrue(content.contains("2>/dev/null"), "Script should redirect rmdir errors to null");
    }

    @Test
    public void testGenerateUnixScriptContent_OmitsHelperDirWhenNull() {
        String content = generator.generateUnixScriptContent(helperPath, helperContextDir, null);

        assertFalse(content.contains("rmdir"), "Script should not contain rmdir when helperDir is null");
    }

    @Test
    public void testGenerateUnixScriptContent_ContainsSelfDelete() {
        String content = generator.generateUnixScriptContent(helperPath, helperContextDir, helperDir);

        assertTrue(content.contains("rm -- \"$0\""), "Script should delete itself");
    }

    @Test
    public void testGenerateUnixScriptContent_EscapesSpecialCharacters() {
        File pathWithSpecialChars = new File(tempDir, "My $pecial `App` \"Helper\"");
        String content = generator.generateUnixScriptContent(pathWithSpecialChars, helperContextDir, helperDir);

        // Check that special characters are escaped
        assertTrue(content.contains("\\$pecial"), "Dollar sign should be escaped");
        assertTrue(content.contains("\\`App\\`"), "Backticks should be escaped");
        assertTrue(content.contains("\\\"Helper\\\""), "Quotes should be escaped");
    }

    // ========== Windows script content tests ==========

    @Test
    public void testGenerateWindowsScriptContent_ContainsEchoOff() {
        String content = generator.generateWindowsScriptContent(helperPath, helperContextDir, helperDir, false);

        assertTrue(content.startsWith("@echo off"), "Script should start with @echo off");
    }

    @Test
    public void testGenerateWindowsScriptContent_ContainsTimeout() {
        String content = generator.generateWindowsScriptContent(helperPath, helperContextDir, helperDir, false);

        assertTrue(content.contains("timeout /t 2 /nobreak > nul"), "Script should contain timeout command");
    }

    @Test
    public void testGenerateWindowsScriptContent_UsesDelForFile() {
        String content = generator.generateWindowsScriptContent(helperPath, helperContextDir, helperDir, false);

        assertTrue(content.contains("del /f /q"), "Script should use del for file");
        assertTrue(content.contains(helperPath.getAbsolutePath()), "Script should contain helper path");
    }

    @Test
    public void testGenerateWindowsScriptContent_UsesRmdirForDirectory() {
        String content = generator.generateWindowsScriptContent(helperPath, helperContextDir, helperDir, true);

        // First rmdir should be for the helper directory (when isHelperDirectory=true)
        String[] lines = content.split("\n");
        boolean foundRmdirForHelper = false;
        for (String line : lines) {
            if (line.contains("rmdir /s /q") && line.contains(helperPath.getAbsolutePath())) {
                foundRmdirForHelper = true;
                break;
            }
        }
        assertTrue(foundRmdirForHelper, "Script should use rmdir /s /q for directory");
    }

    @Test
    public void testGenerateWindowsScriptContent_ContainsContextDir() {
        String content = generator.generateWindowsScriptContent(helperPath, helperContextDir, helperDir, false);

        assertTrue(content.contains("rmdir /s /q"), "Script should contain rmdir /s /q for context dir");
        assertTrue(content.contains(helperContextDir.getAbsolutePath()), "Script should contain context dir path");
    }

    @Test
    public void testGenerateWindowsScriptContent_ContainsHelperDir() {
        String content = generator.generateWindowsScriptContent(helperPath, helperContextDir, helperDir, false);

        assertTrue(content.contains(helperDir.getAbsolutePath()), "Script should contain helper dir path");
        assertTrue(content.contains("2>nul"), "Script should redirect rmdir errors to nul");
    }

    @Test
    public void testGenerateWindowsScriptContent_OmitsHelperDirWhenNull() {
        String content = generator.generateWindowsScriptContent(helperPath, helperContextDir, null, false);

        // Count occurrences of rmdir - should be only for context dir
        int rmdirCount = content.split("rmdir", -1).length - 1;
        assertEquals(1, rmdirCount, "Should only have one rmdir (for context dir) when helperDir is null");
    }

    @Test
    public void testGenerateWindowsScriptContent_ContainsSelfDelete() {
        String content = generator.generateWindowsScriptContent(helperPath, helperContextDir, helperDir, false);

        assertTrue(content.contains("del \"%~f0\""), "Script should delete itself");
    }

    @Test
    public void testGenerateWindowsScriptContent_EscapesPercent() {
        File pathWithPercent = new File(tempDir, "My %TEMP% App");
        String content = generator.generateWindowsScriptContent(pathWithPercent, helperContextDir, helperDir, false);

        assertTrue(content.contains("%%TEMP%%"), "Percent signs should be doubled");
    }

    // ========== executeCleanupScript validation tests ==========

    @Test
    public void testExecuteCleanupScript_NullScript_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            generator.executeCleanupScript(null);
        });
    }

    @Test
    public void testExecuteCleanupScript_NonExistentScript_ThrowsException() {
        File nonExistent = new File(tempDir, "nonexistent.sh");

        assertThrows(IllegalArgumentException.class, () -> {
            generator.executeCleanupScript(nonExistent);
        });
    }

    // ========== Integration tests (script generation and file content) ==========

    @Test
    public void testGenerateCleanupScript_FileContainsCorrectPaths() throws IOException {
        File script = generator.generateCleanupScript(helperPath, helperContextDir, helperDir);

        try {
            String content = new String(Files.readAllBytes(script.toPath()));

            assertTrue(content.contains(helperPath.getAbsolutePath()),
                    "Script should contain helper path");
            assertTrue(content.contains(helperContextDir.getAbsolutePath()),
                    "Script should contain context dir path");
            assertTrue(content.contains(helperDir.getAbsolutePath()),
                    "Script should contain helper dir path");
        } finally {
            script.delete();
        }
    }

    @Test
    public void testGenerateCleanupScript_WithoutHelperDir_OmitsRmdir() throws IOException {
        File script = generator.generateCleanupScript(helperPath, helperContextDir, null);

        try {
            String content = new String(Files.readAllBytes(script.toPath()));

            if (Platform.getSystemPlatform().isWindows()) {
                // Should have only one rmdir (for context dir)
                int rmdirCount = content.split("rmdir", -1).length - 1;
                assertEquals(1, rmdirCount, "Should only have one rmdir when helperDir is null");
            } else {
                // Should not contain standalone rmdir command (only rm -rf)
                assertFalse(content.contains("\nrmdir "), "Should not have rmdir when helperDir is null");
            }
        } finally {
            script.delete();
        }
    }

    @Test
    public void testGenerateCleanupScript_PathsWithSpaces() throws IOException {
        File pathWithSpaces = new File(tempDir, "My App With Spaces Helper.app");
        File contextWithSpaces = new File(tempDir, "path with spaces/.jdeploy-files");
        File dirWithSpaces = new File(tempDir, "My App With Spaces Helper");

        File script = generator.generateCleanupScript(pathWithSpaces, contextWithSpaces, dirWithSpaces);

        try {
            String content = new String(Files.readAllBytes(script.toPath()));

            // Paths should be quoted to handle spaces
            assertTrue(content.contains("\"" + pathWithSpaces.getAbsolutePath()),
                    "Path with spaces should be quoted");
            assertTrue(content.contains("\"" + contextWithSpaces.getAbsolutePath()),
                    "Context dir with spaces should be quoted");
            assertTrue(content.contains("\"" + dirWithSpaces.getAbsolutePath()),
                    "Helper dir with spaces should be quoted");
        } finally {
            script.delete();
        }
    }

    // ========== Script correctness tests ==========

    @Test
    public void testUnixScript_CorrectCommandOrder() {
        String content = generator.generateUnixScriptContent(helperPath, helperContextDir, helperDir);
        String[] lines = content.split("\n");

        // Verify order: shebang, sleep, rm helper, rm context, rmdir helper dir, rm self
        assertTrue(lines[0].contains("#!/bin/bash"), "Line 1 should be shebang");
        assertTrue(lines[1].contains("sleep"), "Line 2 should be sleep");
        assertTrue(lines[2].contains("rm -rf") && lines[2].contains(helperPath.getName()),
                "Line 3 should delete helper");
        assertTrue(lines[3].contains("rm -rf") && lines[3].contains(helperContextDir.getName()),
                "Line 4 should delete context dir");
        assertTrue(lines[4].contains("rmdir"),
                "Line 5 should attempt to remove helper dir");
        assertTrue(lines[5].contains("rm -- \"$0\""),
                "Line 6 should delete self");
    }

    @Test
    public void testWindowsScript_CorrectCommandOrder() {
        String content = generator.generateWindowsScriptContent(helperPath, helperContextDir, helperDir, false);
        String[] lines = content.split("\n");

        // Verify order: echo off, timeout, del helper, rmdir context, rmdir helper dir, del self
        assertTrue(lines[0].contains("@echo off"), "Line 1 should be @echo off");
        assertTrue(lines[1].contains("timeout"), "Line 2 should be timeout");
        assertTrue(lines[2].contains("del /f /q") && lines[2].contains(helperPath.getName()),
                "Line 3 should delete helper");
        assertTrue(lines[3].contains("rmdir /s /q") && lines[3].contains(helperContextDir.getName()),
                "Line 4 should delete context dir");
        assertTrue(lines[4].contains("rmdir") && lines[4].contains(helperDir.getName()),
                "Line 5 should attempt to remove helper dir");
        assertTrue(lines[5].contains("del \"%~f0\""),
                "Line 6 should delete self");
    }
}

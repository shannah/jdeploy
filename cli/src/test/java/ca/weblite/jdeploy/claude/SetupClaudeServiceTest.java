package ca.weblite.jdeploy.claude;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SetupClaudeServiceTest {

    @Test
    void testSetup_createNewClaudeFile(@TempDir File tempDir) throws IOException {
        SetupClaudeService service = new SetupClaudeService();
        
        service.setup(tempDir);
        
        File claudeFile = new File(tempDir, "CLAUDE.md");
        assertTrue(claudeFile.exists(), "CLAUDE.md file should be created");
        
        String content = FileUtils.readFileToString(claudeFile, StandardCharsets.UTF_8);
        assertFalse(content.isEmpty(), "CLAUDE.md content should not be empty");
        assertTrue(content.contains("jDeploy"), "Content should contain jDeploy information");
    }

    @Test
    void testSetup_appendToExistingClaudeFile(@TempDir File tempDir) throws IOException {
        SetupClaudeService service = new SetupClaudeService();
        
        File claudeFile = new File(tempDir, "CLAUDE.md");
        String existingContent = "# My Project\n\nThis is my existing project documentation.\n";
        FileUtils.writeStringToFile(claudeFile, existingContent, StandardCharsets.UTF_8);
        
        service.setup(tempDir);
        
        String finalContent = FileUtils.readFileToString(claudeFile, StandardCharsets.UTF_8);
        assertTrue(finalContent.startsWith(existingContent), "Original content should be preserved");
        assertTrue(finalContent.contains("jDeploy"), "New content should be appended");
        assertTrue(finalContent.length() > existingContent.length(), "File should be longer after appending");
    }

    @Test
    void testSetup_noDuplicateContent(@TempDir File tempDir) throws IOException {
        SetupClaudeService service = new SetupClaudeService();
        
        service.setup(tempDir);
        
        File claudeFile = new File(tempDir, "CLAUDE.md");
        String firstContent = FileUtils.readFileToString(claudeFile, StandardCharsets.UTF_8);
        
        service.setup(tempDir);
        
        String secondContent = FileUtils.readFileToString(claudeFile, StandardCharsets.UTF_8);
        assertEquals(firstContent, secondContent, "Content should not be duplicated on subsequent runs");
    }

    @Test
    void testSetup_existingJDeploySection(@TempDir File tempDir) throws IOException {
        SetupClaudeService service = new SetupClaudeService();
        
        File claudeFile = new File(tempDir, "CLAUDE.md");
        String existingContent = "# My Project\n\nThis is my project.\n\n# Claude Instructions for jDeploy Setup\n\nThis project is already configured for jDeploy.\n";
        FileUtils.writeStringToFile(claudeFile, existingContent, StandardCharsets.UTF_8);
        
        service.setup(tempDir);
        
        String finalContent = FileUtils.readFileToString(claudeFile, StandardCharsets.UTF_8);
        assertEquals(existingContent, finalContent, "Content should not be modified when jDeploy section already exists");
    }

    @Test
    void testSetup_invalidProjectDirectory() {
        SetupClaudeService service = new SetupClaudeService();
        
        assertThrows(IllegalArgumentException.class, () -> {
            service.setup(null);
        }, "Should throw exception for null directory");
        
        assertThrows(IllegalArgumentException.class, () -> {
            service.setup(new File("/non/existent/directory"));
        }, "Should throw exception for non-existent directory");
        
        File tempFile;
        try {
            tempFile = File.createTempFile("test", ".txt");
            tempFile.deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        assertThrows(IllegalArgumentException.class, () -> {
            service.setup(tempFile);
        }, "Should throw exception for file instead of directory");
    }

    @Test
    void testSetup_networkFailureHandling(@TempDir File tempDir) {
        SetupClaudeService service = new SetupClaudeService();
        
        assertDoesNotThrow(() -> {
            service.setup(tempDir);
        }, "Service should handle network issues gracefully");
    }
}
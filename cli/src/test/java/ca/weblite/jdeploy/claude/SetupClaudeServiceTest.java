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
    void testSetup_createsSkillFile(@TempDir File tempDir) throws IOException {
        SetupClaudeService service = new SetupClaudeService();

        service.setup(tempDir);

        File skillFile = new File(tempDir, ".claude/skills/setup-jdeploy.md");
        assertTrue(skillFile.exists(), "Skill file should be created at .claude/skills/setup-jdeploy.md");

        String content = FileUtils.readFileToString(skillFile, StandardCharsets.UTF_8);
        assertFalse(content.isEmpty(), "Skill content should not be empty");
        assertTrue(content.contains("jDeploy"), "Content should contain jDeploy information");
    }

    @Test
    void testSetup_createsSkillsDirectory(@TempDir File tempDir) throws IOException {
        SetupClaudeService service = new SetupClaudeService();

        File skillsDir = new File(tempDir, ".claude/skills");
        assertFalse(skillsDir.exists(), "Skills directory should not exist before setup");

        service.setup(tempDir);

        assertTrue(skillsDir.exists(), "Skills directory should be created");
        assertTrue(skillsDir.isDirectory(), "Skills should be a directory");
    }

    @Test
    void testSetup_overwritesExistingSkillFile(@TempDir File tempDir) throws IOException {
        SetupClaudeService service = new SetupClaudeService();

        File skillsDir = new File(tempDir, ".claude/skills");
        skillsDir.mkdirs();
        File skillFile = new File(skillsDir, "setup-jdeploy.md");
        String oldContent = "# Old content\n\nThis is old content.";
        FileUtils.writeStringToFile(skillFile, oldContent, StandardCharsets.UTF_8);

        service.setup(tempDir);

        String newContent = FileUtils.readFileToString(skillFile, StandardCharsets.UTF_8);
        assertNotEquals(oldContent, newContent, "Skill file should be overwritten with new content");
        assertTrue(newContent.contains("jDeploy"), "New content should contain jDeploy information");
    }

    @Test
    void testIsInstalled_returnsTrueWhenSkillExists(@TempDir File tempDir) throws IOException {
        SetupClaudeService service = new SetupClaudeService();

        assertFalse(service.isInstalled(tempDir), "Should return false before setup");

        service.setup(tempDir);

        assertTrue(service.isInstalled(tempDir), "Should return true after setup");
    }

    @Test
    void testIsInstalled_returnsFalseForInvalidDirectory() {
        SetupClaudeService service = new SetupClaudeService();

        assertFalse(service.isInstalled(null), "Should return false for null directory");
        assertFalse(service.isInstalled(new File("/non/existent/directory")), "Should return false for non-existent directory");
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

package ca.weblite.jdeploy.installer.cli;

import ca.weblite.tools.io.IOUtil;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class UnixPathManagerTest {

    private File tempDir;
    private File homeDir;
    private File binDir;

    @BeforeEach
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("jdeploy-unix-path-test-").toFile();
        homeDir = new File(tempDir, "home");
        homeDir.mkdirs();
        binDir = new File(tempDir, "bin");
        binDir.mkdirs();
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (tempDir != null && tempDir.exists()) {
            FileUtils.deleteDirectory(tempDir);
        }
    }

    @Test
    public void testSelectConfigFileBashWithBashrc() {
        File bashrc = new File(homeDir, ".bashrc");
        bashrc.getParentFile().mkdirs();
        try {
            bashrc.createNewFile();
        } catch (IOException ignored) { }

        File configFile = UnixPathManager.selectConfigFile("/bin/bash", homeDir);

        assertNotNull(configFile);
        assertEquals(".bashrc", configFile.getName());
    }

    @Test
    public void testSelectConfigFileBashWithBashProfile() {
        File bashProfile = new File(homeDir, ".bash_profile");
        bashProfile.getParentFile().mkdirs();
        try {
            bashProfile.createNewFile();
        } catch (IOException ignored) { }

        File configFile = UnixPathManager.selectConfigFile("/bin/bash", homeDir);

        assertNotNull(configFile);
        assertEquals(".bash_profile", configFile.getName());
    }

    @Test
    public void testSelectConfigFileBashNoExistingConfig() {
        // Neither .bashrc nor .bash_profile exist
        assertFalse(new File(homeDir, ".bashrc").exists());
        assertFalse(new File(homeDir, ".bash_profile").exists());

        File configFile = UnixPathManager.selectConfigFile("/bin/bash", homeDir);

        assertNotNull(configFile);
        assertEquals(".bashrc", configFile.getName());
    }

    @Test
    public void testSelectConfigFileZsh() {
        File configFile = UnixPathManager.selectConfigFile("/bin/zsh", homeDir);

        assertNotNull(configFile);
        assertEquals(".zshrc", configFile.getName());
    }

    @Test
    public void testSelectConfigFileFish() {
        File configFile = UnixPathManager.selectConfigFile("/usr/bin/fish", homeDir);

        assertNotNull(configFile);
        assertEquals(".profile", configFile.getName());
    }

    @Test
    public void testSelectConfigFileUnknownShell() {
        File configFile = UnixPathManager.selectConfigFile("/bin/unknown", homeDir);

        assertNotNull(configFile);
        assertEquals(".profile", configFile.getName());
    }

    @Test
    public void testAddToPathBash() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        File bashrc = new File(homeDir, ".bashrc");
        assertTrue(bashrc.exists());
        String content = IOUtil.readToString(new FileInputStream(bashrc));
        assertTrue(content.contains("$HOME/.local/bin"));
    }

    @Test
    public void testAddToPathZsh() throws IOException {
        String shell = "/bin/zsh";
        String pathEnv = "/usr/bin:/bin";

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        File zshrc = new File(homeDir, ".zshrc");
        assertTrue(zshrc.exists());
        String content = IOUtil.readToString(new FileInputStream(zshrc));
        assertTrue(content.contains("$HOME/.local/bin"));
    }

    @Test
    public void testAddToPathFish() throws IOException {
        String shell = "/usr/bin/fish";
        String pathEnv = "/usr/bin:/bin";

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        File profile = new File(homeDir, ".profile");
        assertTrue(profile.exists());
    }

    @Test
    public void testAddToPathUnknownShell() throws IOException {
        String shell = "/bin/unknown";
        String pathEnv = "/usr/bin:/bin";

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        File profile = new File(homeDir, ".profile");
        assertTrue(profile.exists());
    }

    @Test
    public void testAddToPathNullShell() throws IOException {
        String shell = null;
        String pathEnv = "/usr/bin:/bin";

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        // Should default to bash and create .bashrc
        File bashrc = new File(homeDir, ".bashrc");
        assertTrue(bashrc.exists());
    }

    @Test
    public void testAddToPathAlreadyInPathEnv() {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:" + binDir.getAbsolutePath() + ":/bin";

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        // Should not create config file since already in PATH
        File bashProfile = new File(homeDir, ".bash_profile");
        assertFalse(bashProfile.exists());
    }

    @Test
    public void testAddToPathAlreadyInConfig() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";
        File bashrc = new File(homeDir, ".bashrc");
        bashrc.createNewFile();

        // Pre-populate bashrc with the path
        Files.write(bashrc.toPath(), ("export PATH=\"$HOME/.local/bin:$PATH\"\n").getBytes(StandardCharsets.UTF_8));

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        // Should not append again; file should not be modified beyond original content
        String content = IOUtil.readToString(new FileInputStream(bashrc));
        int occurrences = countOccurrences(content, "$HOME/.local/bin");
        assertEquals(1, occurrences, "PATH should appear exactly once");
    }

    @Test
    public void testAddToPathWithAbsolutePathInConfig() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";
        File bashrc = new File(homeDir, ".bashrc");
        bashrc.createNewFile();

        // Pre-populate bashrc with absolute path
        String absolutePath = binDir.getAbsolutePath();
        Files.write(bashrc.toPath(), ("export PATH=\"" + absolutePath + ":$PATH\"\n").getBytes(StandardCharsets.UTF_8));

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        String content = IOUtil.readToString(new FileInputStream(bashrc));
        int occurrences = countOccurrences(content, absolutePath);
        assertEquals(1, occurrences, "Absolute path should appear exactly once");
    }

    @Test
    public void testAddToPathCreatesParentDirectories() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";
        File nonExistentHome = new File(new File(new File(tempDir, "nested"), "nonexistent"), "home");

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, nonExistentHome);

        assertTrue(result);
        File bashrc = new File(nonExistentHome, ".bashrc");
        assertTrue(bashrc.exists());
    }

    @Test
    public void testAddToPathAppendFormat() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";
        File bashrc = new File(homeDir, ".bashrc");

        UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        String content = IOUtil.readToString(new FileInputStream(bashrc));
        assertTrue(content.contains("# Added by jDeploy installer"));
        assertTrue(content.contains("export PATH=\"$HOME/.local/bin:$PATH\""));
    }

    @Test
    public void testAddToPathEmptyShellString() throws IOException {
        String shell = "";
        String pathEnv = "/usr/bin:/bin";

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        // Should default to bash and create .bashrc
        File bashrc = new File(homeDir, ".bashrc");
        assertTrue(bashrc.exists());
    }

    @Test
    public void testAddToPathNullPathEnv() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = null;

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        File bashrc = new File(homeDir, ".bashrc");
        assertTrue(bashrc.exists());
    }

    @Test
    public void testAddToPathNonExistentBinDir() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";
        File nonExistentBinDir = new File(tempDir, "nonexistent-bin");
        // Do not create the directory - it should not exist
        assertFalse(nonExistentBinDir.exists());

        boolean result = UnixPathManager.addToPath(nonExistentBinDir, shell, pathEnv, homeDir);

        assertFalse(result, "addToPath should return false when binDir does not exist");
        // Verify no config file was created
        File profile = new File(homeDir, ".profile");
        assertFalse(profile.exists(), "Config file should not be created when binDir does not exist");
    }

    @Test
    public void testAddToPathBinDirIsFile() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";
        File fileMasqueradingAsBinDir = new File(tempDir, "not-a-directory");
        // Create a file instead of a directory
        fileMasqueradingAsBinDir.createNewFile();
        assertTrue(fileMasqueradingAsBinDir.exists());
        assertFalse(fileMasqueradingAsBinDir.isDirectory());

        boolean result = UnixPathManager.addToPath(fileMasqueradingAsBinDir, shell, pathEnv, homeDir);

        assertFalse(result, "addToPath should return false when binDir is a file, not a directory");
        // Verify no config file was created
        File profile = new File(homeDir, ".profile");
        assertFalse(profile.exists(), "Config file should not be created when binDir is not a directory");
    }

    @Test
    public void testAddToPathNullBinDir() {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";

        boolean result = UnixPathManager.addToPath(null, shell, pathEnv, homeDir);

        assertFalse(result, "addToPath should return false when binDir is null");
        // Verify no config file was created
        File profile = new File(homeDir, ".profile");
        assertFalse(profile.exists(), "Config file should not be created when binDir is null");
    }

    @Test
    public void testAddToPathExistingConfigNotModifiedWhenBinDirMissing() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";
        File nonExistentBinDir = new File(tempDir, "nonexistent-bin");
        assertFalse(nonExistentBinDir.exists());

        // Pre-create a config file with existing content
        File bashrc = new File(homeDir, ".bashrc");
        String originalContent = "# Existing content\nexport FOO=bar\n";
        Files.write(bashrc.toPath(), originalContent.getBytes(StandardCharsets.UTF_8));
        assertTrue(bashrc.exists());

        boolean result = UnixPathManager.addToPath(nonExistentBinDir, shell, pathEnv, homeDir);

        assertFalse(result, "addToPath should return false when binDir does not exist");
        // Verify config file was not modified
        String contentAfter = IOUtil.readToString(new FileInputStream(bashrc));
        assertEquals(originalContent, contentAfter, "Config file should not be modified when binDir does not exist");
    }

    @Test
    public void testAddToPathWithExistingContent() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";
        File bashrc = new File(homeDir, ".bashrc");
        bashrc.createNewFile();

        // Pre-populate with other content
        Files.write(bashrc.toPath(), ("# Some existing comment\nexport FOO=bar\n").getBytes(StandardCharsets.UTF_8));

        UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        String content = IOUtil.readToString(new FileInputStream(bashrc));
        assertTrue(content.contains("export FOO=bar"));
        assertTrue(content.contains("export PATH=\"$HOME/.local/bin:$PATH\""));
    }

    @Test
    public void testAddToPathIdempotency() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";

        // First call to addToPath
        boolean result1 = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);
        assertTrue(result1);

        File bashrc = new File(homeDir, ".bashrc");
        assertTrue(bashrc.exists());
        String contentAfterFirstCall = IOUtil.readToString(new FileInputStream(bashrc));
        int occurrencesAfterFirst = countOccurrences(contentAfterFirstCall, "$HOME/.local/bin");
        assertEquals(1, occurrencesAfterFirst, "PATH should appear exactly once after first call");

        // Second call to addToPath
        boolean result2 = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);
        assertTrue(result2);

        String contentAfterSecondCall = IOUtil.readToString(new FileInputStream(bashrc));
        int occurrencesAfterSecond = countOccurrences(contentAfterSecondCall, "$HOME/.local/bin");
        assertEquals(1, occurrencesAfterSecond, "PATH should still appear exactly once after second call (idempotent)");
    }

    /**
     * Helper method to count occurrences of a substring in a string.
     */
    private int countOccurrences(String text, String substring) {
        if (substring == null || substring.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
}

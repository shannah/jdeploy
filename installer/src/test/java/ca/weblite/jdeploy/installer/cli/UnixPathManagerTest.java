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
        assertFalse(bashProfile.exists());

        File configFile = UnixPathManager.selectConfigFile("/bin/bash", homeDir);

        assertNotNull(configFile);
        assertEquals(".bash_profile", configFile.getName());
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

        assertNull(configFile);
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
        File bashProfile = new File(homeDir, ".bash_profile");
        assertTrue(bashProfile.exists());
        String content = IOUtil.readToString(new FileInputStream(bashProfile));
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
    public void testAddToPathFish() {
        String shell = "/usr/bin/fish";
        String pathEnv = "/usr/bin:/bin";

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertFalse(result);
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
        // Should default to bash and create .bash_profile
        File bashProfile = new File(homeDir, ".bash_profile");
        assertTrue(bashProfile.exists());
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
        File bashProfile = new File(nonExistentHome, ".bash_profile");
        assertTrue(bashProfile.exists());
    }

    @Test
    public void testAddToPathAppendFormat() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";
        File bashProfile = new File(homeDir, ".bash_profile");

        UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        String content = IOUtil.readToString(new FileInputStream(bashProfile));
        assertTrue(content.contains("# Added by jDeploy installer"));
        assertTrue(content.contains("export PATH=\"$HOME/.local/bin:$PATH\""));
    }

    @Test
    public void testAddToPathEmptyShellString() throws IOException {
        String shell = "";
        String pathEnv = "/usr/bin:/bin";

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        // Should default to bash
        File bashProfile = new File(homeDir, ".bash_profile");
        assertTrue(bashProfile.exists());
    }

    @Test
    public void testAddToPathNullPathEnv() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = null;

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        File bashProfile = new File(homeDir, ".bash_profile");
        assertTrue(bashProfile.exists());
    }

    @Test
    public void testAddToPathWithExistingContent() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";
        File bashProfile = new File(homeDir, ".bash_profile");
        bashProfile.createNewFile();

        // Pre-populate with other content
        Files.write(bashProfile.toPath(), ("# Some existing comment\nexport FOO=bar\n").getBytes(StandardCharsets.UTF_8));

        UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        String content = IOUtil.readToString(new FileInputStream(bashProfile));
        assertTrue(content.contains("export FOO=bar"));
        assertTrue(content.contains("export PATH=\"$HOME/.local/bin:$PATH\""));
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

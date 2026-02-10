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
        // binDir is not under homeDir (it's in tempDir), so absolute path should be used
        assertTrue(content.contains(binDir.getAbsolutePath()));
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
        // binDir is not under homeDir (it's in tempDir), so absolute path should be used
        assertTrue(content.contains(binDir.getAbsolutePath()));
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
    public void testAddToPathAlreadyInPathEnv() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:" + binDir.getAbsolutePath() + ":/bin";

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        // For bash, we should still create/update config files even if already in PATH env
        // because the current PATH might come from .bashrc while .bash_profile is still missing it.
        File bashProfile = new File(homeDir, ".bash_profile");
        File bashrc = new File(homeDir, ".bashrc");
        assertTrue(bashProfile.exists(), ".bash_profile should be created even if binDir is in PATH env");
        assertTrue(bashrc.exists(), ".bashrc should be created even if binDir is in PATH env");

        String profileContent = IOUtil.readToString(new FileInputStream(bashProfile));
        assertTrue(profileContent.contains(binDir.getAbsolutePath()));
    }

    @Test
    public void testAddToPathAlreadyInPathEnvNonBash() {
        String shell = "/bin/zsh";
        String pathEnv = "/usr/bin:" + binDir.getAbsolutePath() + ":/bin";

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        // For non-bash shells, we optimize and skip if already in PATH env
        File zshrc = new File(homeDir, ".zshrc");
        assertFalse(zshrc.exists(), ".zshrc should not be created if already in PATH env for zsh");
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

        // Pre-populate bashrc with absolute path (without jDeploy comment)
        String absolutePath = binDir.getAbsolutePath();
        Files.write(bashrc.toPath(), ("export PATH=\"" + absolutePath + ":$PATH\"\n").getBytes(StandardCharsets.UTF_8));

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        String content = IOUtil.readToString(new FileInputStream(bashrc));
        int occurrences = countOccurrences(content, absolutePath);
        assertEquals(1, occurrences, "Absolute path should appear exactly once");
        // With remove-then-add strategy, the jDeploy comment should now be present
        assertEquals(1, countOccurrences(content, "Added by jDeploy installer"), "jDeploy comment should be added");
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
        // binDir is not under homeDir, so absolute path should be used
        assertTrue(content.contains("export PATH=\"" + binDir.getAbsolutePath() + ":$PATH\""));
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
        // binDir is not under homeDir, so absolute path should be used
        assertTrue(content.contains("export PATH=\"" + binDir.getAbsolutePath() + ":$PATH\""));
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
        // binDir is not under homeDir, so absolute path should be used
        String absolutePath = binDir.getAbsolutePath();
        int occurrencesAfterFirst = countOccurrences(contentAfterFirstCall, absolutePath);
        assertEquals(1, occurrencesAfterFirst, "PATH should appear exactly once after first call");

        // Second call to addToPath
        boolean result2 = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);
        assertTrue(result2);

        String contentAfterSecondCall = IOUtil.readToString(new FileInputStream(bashrc));
        int occurrencesAfterSecond = countOccurrences(contentAfterSecondCall, absolutePath);
        assertEquals(1, occurrencesAfterSecond, "PATH should still appear exactly once after second call (idempotent)");
    }

    @Test
    public void testAddToPathWithHomeBinDir() throws IOException {
        // Create binDir directly under homeDir (simulating ~/bin)
        File homeBinDir = new File(homeDir, "bin");
        homeBinDir.mkdirs();
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";

        boolean result = UnixPathManager.addToPath(homeBinDir, shell, pathEnv, homeDir);

        assertTrue(result);
        File bashrc = new File(homeDir, ".bashrc");
        assertTrue(bashrc.exists());
        String content = IOUtil.readToString(new FileInputStream(bashrc));
        // Should use $HOME/bin since it's directly under home
        assertTrue(content.contains("$HOME/bin"), "Expected $HOME/bin in PATH export but got: " + content);
        assertTrue(content.contains("export PATH=\"$HOME/bin:$PATH\""));
    }

    @Test
    public void testAddToPathWithLocalBinDir() throws IOException {
        // Create binDir at ~/.local/bin (simulating the Linux default)
        File localDir = new File(homeDir, ".local");
        File localBinDir = new File(localDir, "bin");
        localBinDir.mkdirs();
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";

        boolean result = UnixPathManager.addToPath(localBinDir, shell, pathEnv, homeDir);

        assertTrue(result);
        File bashrc = new File(homeDir, ".bashrc");
        assertTrue(bashrc.exists());
        String content = IOUtil.readToString(new FileInputStream(bashrc));
        // Should use $HOME/.local/bin
        assertTrue(content.contains("$HOME/.local/bin"), "Expected $HOME/.local/bin in PATH export but got: " + content);
        assertTrue(content.contains("export PATH=\"$HOME/.local/bin:$PATH\""));
    }

    @Test
    public void testAddToPathWithAbsoluteDir() throws IOException {
        // binDir is NOT under homeDir, so absolute path should be used
        // The existing binDir from setUp() is under tempDir, not homeDir
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        File bashrc = new File(homeDir, ".bashrc");
        assertTrue(bashrc.exists());
        String content = IOUtil.readToString(new FileInputStream(bashrc));
        // Should use absolute path since binDir is not under homeDir
        assertTrue(content.contains(binDir.getAbsolutePath()), "Expected absolute path in PATH export but got: " + content);
        assertTrue(content.contains("export PATH=\"" + binDir.getAbsolutePath() + ":$PATH\""));
    }

    @Test
    public void testAddToPathIdempotencyWithHomeBinDir() throws IOException {
        File homeBinDir = new File(homeDir, "bin");
        homeBinDir.mkdirs();
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";

        // First call to addToPath
        boolean result1 = UnixPathManager.addToPath(homeBinDir, shell, pathEnv, homeDir);
        assertTrue(result1);

        File bashrc = new File(homeDir, ".bashrc");
        assertTrue(bashrc.exists());
        String contentAfterFirstCall = IOUtil.readToString(new FileInputStream(bashrc));
        int occurrencesAfterFirst = countOccurrences(contentAfterFirstCall, "$HOME/bin");
        assertEquals(1, occurrencesAfterFirst, "PATH should appear exactly once after first call");

        // Second call to addToPath
        boolean result2 = UnixPathManager.addToPath(homeBinDir, shell, pathEnv, homeDir);
        assertTrue(result2);

        String contentAfterSecondCall = IOUtil.readToString(new FileInputStream(bashrc));
        int occurrencesAfterSecond = countOccurrences(contentAfterSecondCall, "$HOME/bin");
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

    @Test
    public void testHasNoAutoPathMarkerWhenPresent() throws IOException {
        File bashrc = new File(homeDir, ".bashrc");
        Files.write(bashrc.toPath(), ("# Some config\n" + UnixPathManager.NO_AUTO_PATH_MARKER + "\nexport FOO=bar\n").getBytes(StandardCharsets.UTF_8));

        assertTrue(UnixPathManager.hasNoAutoPathMarker(bashrc), "Should detect no-auto-path marker");
    }

    @Test
    public void testHasNoAutoPathMarkerWhenAbsent() throws IOException {
        File bashrc = new File(homeDir, ".bashrc");
        Files.write(bashrc.toPath(), ("# Some config\nexport FOO=bar\n").getBytes(StandardCharsets.UTF_8));

        assertFalse(UnixPathManager.hasNoAutoPathMarker(bashrc), "Should not detect marker when absent");
    }

    @Test
    public void testHasNoAutoPathMarkerNonExistentFile() {
        File nonExistent = new File(homeDir, ".nonexistent");
        assertFalse(UnixPathManager.hasNoAutoPathMarker(nonExistent), "Should return false for non-existent file");
    }

    @Test
    public void testHasNoAutoPathMarkerNullFile() {
        assertFalse(UnixPathManager.hasNoAutoPathMarker(null), "Should return false for null file");
    }

    @Test
    public void testAddToPathSkipsWhenMarkerPresent() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";
        
        // Create bashrc with the no-auto-path marker
        File bashrc = new File(homeDir, ".bashrc");
        String originalContent = "# My custom config\n" + UnixPathManager.NO_AUTO_PATH_MARKER + "\nexport MYVAR=myvalue\n";
        Files.write(bashrc.toPath(), originalContent.getBytes(StandardCharsets.UTF_8));

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result, "Should return true (success) when marker is present");
        String content = IOUtil.readToString(new FileInputStream(bashrc));
        assertEquals(originalContent, content, "File should not be modified when marker is present");
        assertFalse(content.contains(binDir.getAbsolutePath()), "PATH should not be added when marker is present");
    }

    @Test
    public void testRemovePathFromConfigFile() throws IOException {
        File bashrc = new File(homeDir, ".bashrc");
        String existingPath = binDir.getAbsolutePath();
        String originalContent = "# Some config\n# Added by jDeploy installer\nexport PATH=\"" + existingPath + ":$PATH\"\nexport FOO=bar\n";
        Files.write(bashrc.toPath(), originalContent.getBytes(StandardCharsets.UTF_8));

        boolean removed = UnixPathManager.removePathFromConfigFile(bashrc, binDir, homeDir);

        assertTrue(removed, "Should return true when entry was removed");
        String content = IOUtil.readToString(new FileInputStream(bashrc));
        assertFalse(content.contains(existingPath), "PATH entry should be removed");
        assertFalse(content.contains("# Added by jDeploy installer"), "Comment should be removed");
        assertTrue(content.contains("export FOO=bar"), "Other content should be preserved");
    }

    @Test
    public void testRemovePathFromConfigFileNoEntry() throws IOException {
        File bashrc = new File(homeDir, ".bashrc");
        String originalContent = "# Some config\nexport FOO=bar\n";
        Files.write(bashrc.toPath(), originalContent.getBytes(StandardCharsets.UTF_8));

        boolean removed = UnixPathManager.removePathFromConfigFile(bashrc, binDir, homeDir);

        assertFalse(removed, "Should return false when no entry to remove");
        String content = IOUtil.readToString(new FileInputStream(bashrc));
        assertEquals(originalContent, content, "File should not be modified");
    }

    @Test
    public void testRemovePathFromConfigFileNonExistent() {
        File nonExistent = new File(homeDir, ".nonexistent");
        
        boolean removed = UnixPathManager.removePathFromConfigFile(nonExistent, binDir, homeDir);

        assertFalse(removed, "Should return false for non-existent file");
    }

    @Test
    public void testAddToPathRemovesThenAddsAtEnd() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";
        File bashrc = new File(homeDir, ".bashrc");
        
        // Create bashrc with existing jDeploy PATH entry in the middle
        String existingPath = binDir.getAbsolutePath();
        String originalContent = "# First config\nexport FIRST=1\n# Added by jDeploy installer\nexport PATH=\"" + existingPath + ":$PATH\"\n# More config\nexport SECOND=2\n";
        Files.write(bashrc.toPath(), originalContent.getBytes(StandardCharsets.UTF_8));

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        String content = IOUtil.readToString(new FileInputStream(bashrc));
        
        // Verify the old entry was removed and new one added at the end
        int pathOccurrences = countOccurrences(content, existingPath);
        assertEquals(1, pathOccurrences, "PATH should appear exactly once");
        
        // The new PATH entry should be at the end of the file
        assertTrue(content.trim().endsWith("export PATH=\"" + existingPath + ":$PATH\""), 
            "PATH export should be at the end of the file");
    }

    @Test
    public void testAddToPathWithHomeRelativePath() throws IOException {
        // Create a bin directory under homeDir
        File homeBinDir = new File(homeDir, ".local/bin");
        homeBinDir.mkdirs();

        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";
        File bashrc = new File(homeDir, ".bashrc");

        // Create bashrc with existing $HOME-relative PATH entry
        String originalContent = "# First config\n# Added by jDeploy installer\nexport PATH=\"$HOME/.local/bin:$PATH\"\n# More config\n";
        Files.write(bashrc.toPath(), originalContent.getBytes(StandardCharsets.UTF_8));

        boolean result = UnixPathManager.addToPath(homeBinDir, shell, pathEnv, homeDir);

        assertTrue(result);
        String content = IOUtil.readToString(new FileInputStream(bashrc));

        // Verify the old entry was removed and new one added at the end
        int pathOccurrences = countOccurrences(content, "$HOME/.local/bin");
        assertEquals(1, pathOccurrences, "PATH should appear exactly once");
    }

    @Test
    public void testAddToPathSourcesProfileWhenCreatingBashProfile() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";

        // Create a .profile with existing content (simulating a user who had aliases there)
        File profile = new File(homeDir, ".profile");
        String profileContent = "# User's existing profile\nalias ll='ls -la'\nexport MY_VAR=value\n";
        Files.write(profile.toPath(), profileContent.getBytes(StandardCharsets.UTF_8));

        // Verify no .bash_profile exists yet
        File bashProfile = new File(homeDir, ".bash_profile");
        assertFalse(bashProfile.exists(), ".bash_profile should not exist initially");

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        assertTrue(bashProfile.exists(), ".bash_profile should be created");

        String bashProfileContent = IOUtil.readToString(new FileInputStream(bashProfile));

        // Verify .bash_profile sources .profile
        assertTrue(bashProfileContent.contains("if [ -f ~/.profile ]; then"),
                ".bash_profile should contain conditional to source .profile");
        assertTrue(bashProfileContent.contains(". ~/.profile"),
                ".bash_profile should source .profile");

        // Verify the PATH was also added
        assertTrue(bashProfileContent.contains(binDir.getAbsolutePath()),
                ".bash_profile should contain the PATH export");
    }

    @Test
    public void testAddToPathDoesNotSourceProfileWhenProfileMissing() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";

        // Verify no .profile exists
        File profile = new File(homeDir, ".profile");
        assertFalse(profile.exists(), ".profile should not exist");

        // Verify no .bash_profile exists yet
        File bashProfile = new File(homeDir, ".bash_profile");
        assertFalse(bashProfile.exists(), ".bash_profile should not exist initially");

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        assertTrue(bashProfile.exists(), ".bash_profile should be created");

        String bashProfileContent = IOUtil.readToString(new FileInputStream(bashProfile));

        // Verify .bash_profile does NOT try to source .profile (since it doesn't exist)
        assertFalse(bashProfileContent.contains(". ~/.profile"),
                ".bash_profile should not source .profile when it doesn't exist");

        // Verify the PATH was added
        assertTrue(bashProfileContent.contains(binDir.getAbsolutePath()),
                ".bash_profile should contain the PATH export");
    }

    @Test
    public void testAddToPathDoesNotAddDuplicateProfileSourcing() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";

        // Create a .profile
        File profile = new File(homeDir, ".profile");
        Files.write(profile.toPath(), "# User profile\n".getBytes(StandardCharsets.UTF_8));

        // Create a .bash_profile that already sources .profile
        File bashProfile = new File(homeDir, ".bash_profile");
        String existingContent = "# Existing bash_profile\nif [ -f ~/.profile ]; then\n    . ~/.profile\nfi\n";
        Files.write(bashProfile.toPath(), existingContent.getBytes(StandardCharsets.UTF_8));

        boolean result = UnixPathManager.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        String bashProfileContent = IOUtil.readToString(new FileInputStream(bashProfile));

        // Verify .profile is only sourced once (the existing sourcing should remain)
        int sourceOccurrences = countOccurrences(bashProfileContent, ". ~/.profile");
        assertEquals(1, sourceOccurrences, ".profile should only be sourced once");

        // Verify the PATH was added
        assertTrue(bashProfileContent.contains(binDir.getAbsolutePath()),
                ".bash_profile should contain the PATH export");
    }
}

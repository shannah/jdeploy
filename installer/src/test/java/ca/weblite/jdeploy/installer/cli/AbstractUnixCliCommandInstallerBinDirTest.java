package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.models.InstallationSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying correct bin directory path computation
 * in AbstractUnixCliCommandInstaller using CliCommandBinDirResolver.
 */
public class AbstractUnixCliCommandInstallerBinDirTest {

    private AbstractUnixCliCommandInstaller installer;
    private InstallationSettings settings;

    @BeforeEach
    public void setUp() {
        // Create a concrete implementation for testing
        installer = new AbstractUnixCliCommandInstaller() {
            @Override
            protected void writeCommandScript(File scriptPath, String launcherPath, ca.weblite.jdeploy.models.CommandSpec command) throws java.io.IOException {
                // No-op for this test
            }

            @Override
            public java.util.List<File> installCommands(File launcherPath, java.util.List<ca.weblite.jdeploy.models.CommandSpec> commands, InstallationSettings settings) {
                // No-op for this test
                return new java.util.ArrayList<>();
            }
        };
        settings = new InstallationSettings();
    }

    @Test
    public void testGetBinDirWithNpmPackage() {
        settings.setPackageName("my-app");
        settings.setSource(null); // NPM package has no source

        File binDir = installer.getBinDir(settings);

        assertNotNull(binDir);
        String binPath = binDir.getAbsolutePath();
        assertTrue(binPath.contains(".jdeploy"),
            "NPM package bin dir should use .jdeploy structure");
        assertTrue(binPath.contains(".jdeploy" + File.separator + "bin-"),
            "Bin directory should use per-app bin-{arch} structure");
        assertTrue(binPath.endsWith(File.separator + "my-app"),
            "Bin directory should end with package name");
    }

    @Test
    public void testGetBinDirWithGithubPackage() {
        settings.setPackageName("my-app");
        settings.setSource("https://github.com/user/my-repo");

        File binDir = installer.getBinDir(settings);

        assertNotNull(binDir);
        String binPath = binDir.getAbsolutePath();
        assertTrue(binPath.contains(".jdeploy"),
            "GitHub package bin dir should use .jdeploy structure");
        assertTrue(binPath.contains(".jdeploy" + File.separator + "bin-"),
            "Bin directory should use per-app bin-{arch} structure");
        // For GitHub packages, the directory includes MD5 hash, so we just check it doesn't end with "my-app" alone
        assertFalse(binPath.endsWith(File.separator + "my-app"),
            "GitHub package bin dir should include MD5 hash prefix");
        assertTrue(binPath.contains("my-app"),
            "GitHub package bin dir should contain package name");
    }

    @Test
    public void testGetBinDirWithDifferentPackageNames() {
        File binDir1 = installer.getBinDir(createSettings("app1", null));
        File binDir2 = installer.getBinDir(createSettings("app2", null));

        assertNotNull(binDir1);
        assertNotNull(binDir2);
        // Each package should have its own per-app directory
        assertNotEquals(binDir1.getAbsolutePath(), binDir2.getAbsolutePath(),
            "Different packages should have separate per-app directories");
        assertTrue(binDir1.getAbsolutePath().contains(".jdeploy" + File.separator + "bin-"),
            "Bin directory should use per-app bin-{arch} structure");
        assertTrue(binDir2.getAbsolutePath().contains(".jdeploy" + File.separator + "bin-"),
            "Bin directory should use per-app bin-{arch} structure");
        assertTrue(binDir1.getAbsolutePath().endsWith(File.separator + "app1"),
            "Bin directory should end with package name app1");
        assertTrue(binDir2.getAbsolutePath().endsWith(File.separator + "app2"),
            "Bin directory should end with package name app2");
    }

    @Test
    public void testGetBinDirWithGithubCollisionAvoidance() {
        // Two different GitHub sources with the same package name
        // should resolve to DIFFERENT per-app directories
        File binDir1 = installer.getBinDir(
            createSettings("my-app", "https://github.com/user1/my-repo"));
        File binDir2 = installer.getBinDir(
            createSettings("my-app", "https://github.com/user2/my-repo"));

        assertNotNull(binDir1);
        assertNotNull(binDir2);
        // Each package should have its own per-app directory
        assertNotEquals(binDir1.getAbsolutePath(), binDir2.getAbsolutePath(),
            "Different GitHub sources should have separate per-app directories to avoid collisions");

        // Both should contain the bin-{arch} pattern
        assertTrue(binDir1.getAbsolutePath().contains(".jdeploy" + File.separator + "bin-"),
            "Should use per-app bin-{arch} structure");
        assertTrue(binDir2.getAbsolutePath().contains(".jdeploy" + File.separator + "bin-"),
            "Should use per-app bin-{arch} structure");
    }

    @Test
    public void testGetBinDirWithNullSettings() {
        try {
            installer.getBinDir(null);
            fail("getBinDir should throw IllegalArgumentException when settings is null");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("packageName"),
                "Exception message should mention packageName");
        }
    }

    @Test
    public void testGetBinDirWithNullPackageName() {
        settings.setPackageName(null);
        settings.setSource(null);

        try {
            installer.getBinDir(settings);
            fail("getBinDir should throw IllegalArgumentException when packageName is null");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("packageName"),
                "Exception message should mention packageName");
        }
    }

    @Test
    public void testGetBinDirWithEmptyPackageName() {
        settings.setPackageName("");
        settings.setSource(null);

        try {
            installer.getBinDir(settings);
            fail("getBinDir should throw IllegalArgumentException when packageName is empty");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("packageName"),
                "Exception message should mention packageName");
        }
    }

    @Test
    public void testGetBinDirWithWhitespacePackageName() {
        settings.setPackageName("   ");
        settings.setSource(null);

        try {
            installer.getBinDir(settings);
            fail("getBinDir should throw IllegalArgumentException when packageName is whitespace");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("packageName"),
                "Exception message should mention packageName");
        }
    }

    @Test
    public void testGetBinDirConsistency() {
        settings.setPackageName("my-app");
        settings.setSource("https://github.com/user/repo");
        
        // Call getBinDir multiple times with same settings
        File binDir1 = installer.getBinDir(settings);
        File binDir2 = installer.getBinDir(settings);
        
        assertNotNull(binDir1);
        assertNotNull(binDir2);
        assertEquals(binDir1.getAbsolutePath(), binDir2.getAbsolutePath(),
            "Same settings should always produce the same bin directory");
    }

    @Test
    public void testGetBinDirWithScopedNpmPackage() {
        settings.setPackageName("@myorg/my-app");
        settings.setSource(null);

        File binDir = installer.getBinDir(settings);

        assertNotNull(binDir);
        String binPath = binDir.getAbsolutePath();
        assertTrue(binPath.contains(".jdeploy"),
            "Scoped NPM package should use .jdeploy structure");
        assertTrue(binPath.contains(".jdeploy" + File.separator + "bin-"),
            "Scoped NPM package should use per-app bin-{arch} structure");
        // Use File to construct the expected path suffix to handle platform-specific separators
        String expectedSuffix = new File("@myorg/my-app").getPath();
        assertTrue(binPath.endsWith(File.separator + expectedSuffix),
            "Scoped NPM package bin directory should end with scoped package name");
    }

    @Test
    public void testBinDirIsUnderUserHome() {
        settings.setPackageName("test-app");
        settings.setSource(null);
        
        File binDir = installer.getBinDir(settings);
        String userHome = System.getProperty("user.home");
        
        assertNotNull(binDir);
        assertTrue(binDir.getAbsolutePath().startsWith(userHome),
            "Bin directory should be under user home directory");
    }

    /**
     * Helper method to create InstallationSettings with package name and source.
     */
    private InstallationSettings createSettings(String packageName, String source) {
        InstallationSettings s = new InstallationSettings();
        s.setPackageName(packageName);
        s.setSource(source);
        return s;
    }
}

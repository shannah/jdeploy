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
            protected void writeCommandScript(File scriptPath, String launcherPath, String commandName, java.util.List<String> args) throws java.io.IOException {
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
        assertTrue(binPath.endsWith(".jdeploy" + File.separator + "bin"), 
            "Bin directory should be the shared .jdeploy/bin directory");
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
        assertTrue(binPath.endsWith(".jdeploy" + File.separator + "bin"), 
            "Bin directory should be the shared .jdeploy/bin directory");
    }

    @Test
    public void testGetBinDirWithDifferentPackageNames() {
        File binDir1 = installer.getBinDir(createSettings("app1", null));
        File binDir2 = installer.getBinDir(createSettings("app2", null));
        
        assertNotNull(binDir1);
        assertNotNull(binDir2);
        // All packages share the same bin directory
        assertEquals(binDir1.getAbsolutePath(), binDir2.getAbsolutePath(),
            "Different packages should share the same bin directory");
        assertTrue(binDir1.getAbsolutePath().endsWith(".jdeploy" + File.separator + "bin"),
            "Bin directory should be the shared .jdeploy/bin directory");
    }

    @Test
    public void testGetBinDirWithGithubCollisionAvoidance() {
        // Two different GitHub sources with the same package name
        // should resolve to the same shared bin directory
        File binDir1 = installer.getBinDir(
            createSettings("my-app", "https://github.com/user1/my-repo"));
        File binDir2 = installer.getBinDir(
            createSettings("my-app", "https://github.com/user2/my-repo"));
        
        assertNotNull(binDir1);
        assertNotNull(binDir2);
        // All packages share the same bin directory
        assertEquals(binDir1.getAbsolutePath(), binDir2.getAbsolutePath(),
            "All packages should share the same bin directory");
    }

    @Test
    public void testGetBinDirWithNullSettings() {
        File binDir = installer.getBinDir(null);
        
        assertNotNull(binDir);
        String binPath = binDir.getAbsolutePath();
        assertTrue(binPath.contains(".jdeploy" + File.separator + "bin"),
            "Null settings should fall back to default .jdeploy/bin");
    }

    @Test
    public void testGetBinDirWithNullPackageName() {
        settings.setPackageName(null);
        settings.setSource(null);
        
        File binDir = installer.getBinDir(settings);
        
        assertNotNull(binDir);
        String binPath = binDir.getAbsolutePath();
        assertTrue(binPath.contains(".jdeploy" + File.separator + "bin"),
            "Null package name should fall back to default .jdeploy/bin");
    }

    @Test
    public void testGetBinDirWithEmptyPackageName() {
        settings.setPackageName("");
        settings.setSource(null);
        
        File binDir = installer.getBinDir(settings);
        
        assertNotNull(binDir);
        String binPath = binDir.getAbsolutePath();
        assertTrue(binPath.contains(".jdeploy" + File.separator + "bin"),
            "Empty package name should fall back to default .jdeploy/bin");
    }

    @Test
    public void testGetBinDirWithWhitespacePackageName() {
        settings.setPackageName("   ");
        settings.setSource(null);
        
        File binDir = installer.getBinDir(settings);
        
        assertNotNull(binDir);
        String binPath = binDir.getAbsolutePath();
        // Whitespace-only package names are treated as empty, falling back to default
        assertTrue(binPath.endsWith(".jdeploy" + File.separator + "bin"),
            "Whitespace-only package name should fall back to default .jdeploy/bin");
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
        assertTrue(binPath.endsWith(".jdeploy" + File.separator + "bin"),
            "Scoped NPM package should use shared .jdeploy/bin directory");
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

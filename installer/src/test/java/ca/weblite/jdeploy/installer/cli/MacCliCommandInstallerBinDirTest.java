package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.models.InstallationSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * macOS-specific tests for MacCliCommandInstaller bin directory resolution.
 * 
 * Verifies that MacCliCommandInstaller correctly delegates to the abstract
 * base class implementation which uses CliCommandBinDirResolver for unified
 * bin directory structure (~/.jdeploy/bin).
 */
public class MacCliCommandInstallerBinDirTest {

    private MacCliCommandInstaller installer;
    private InstallationSettings settings;

    @BeforeEach
    public void setUp() {
        installer = new MacCliCommandInstaller();
        settings = new InstallationSettings();
    }

    @Test
    public void testMacGetBinDirWithNpmPackage() {
        settings.setPackageName("my-app");
        settings.setSource(null); // NPM package has no source

        File binDir = installer.getBinDir(settings);

        assertNotNull(binDir);
        String binPath = binDir.getAbsolutePath();
        assertTrue(binPath.contains(".jdeploy"),
            "macOS NPM package bin dir should use .jdeploy structure");
        assertTrue(binPath.contains(".jdeploy" + File.separator + "bin-"),
            "macOS bin directory should use per-app bin-{arch} structure");
        assertTrue(binPath.endsWith(File.separator + "my-app"),
            "macOS bin directory should end with package name");
    }

    @Test
    public void testMacGetBinDirWithGithubPackage() {
        settings.setPackageName("my-app");
        settings.setSource("https://github.com/user/my-repo");

        File binDir = installer.getBinDir(settings);

        assertNotNull(binDir);
        String binPath = binDir.getAbsolutePath();
        assertTrue(binPath.contains(".jdeploy"),
            "macOS GitHub package bin dir should use .jdeploy structure");
        assertTrue(binPath.contains(".jdeploy" + File.separator + "bin-"),
            "macOS bin directory should use per-app bin-{arch} structure");
        // GitHub packages include MD5 hash prefix
        assertFalse(binPath.endsWith(File.separator + "my-app"),
            "GitHub package bin dir should include MD5 hash prefix");
        assertTrue(binPath.contains("my-app"),
            "GitHub package bin dir should contain package name");
    }

    @Test
    public void testMacGetBinDirWithScopedNpmPackage() {
        settings.setPackageName("@myorg/my-app");
        settings.setSource(null);

        File binDir = installer.getBinDir(settings);

        assertNotNull(binDir);
        String binPath = binDir.getAbsolutePath();
        assertTrue(binPath.contains(".jdeploy"),
            "macOS scoped NPM package should use .jdeploy structure");
        assertTrue(binPath.contains(".jdeploy" + File.separator + "bin-"),
            "macOS scoped NPM package should use per-app bin-{arch} structure");
        assertTrue(binPath.endsWith(File.separator + "@myorg/my-app"),
            "macOS scoped NPM package bin directory should end with scoped package name");
    }

    @Test
    public void testMacGetBinDirWithNullSettings() {
        try {
            installer.getBinDir(null);
            fail("getBinDir should throw IllegalArgumentException when settings is null");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("packageName"),
                "Exception message should mention packageName");
        }
    }

    @Test
    public void testMacGetBinDirWithNullPackageName() {
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
    public void testMacGetBinDirWithEmptyPackageName() {
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
    public void testMacGetBinDirUnifiedStructure() {
        // Verify that macOS uses the per-app bin directory structure
        settings.setPackageName("test-app");
        settings.setSource(null);

        File binDir = installer.getBinDir(settings);
        String userHome = System.getProperty("user.home");

        assertNotNull(binDir);
        assertTrue(binDir.getAbsolutePath().startsWith(userHome),
            "macOS bin directory should be under user home");
        assertTrue(binDir.getAbsolutePath().contains(".jdeploy" + File.separator + "bin-"),
            "macOS should use per-app .jdeploy/bin-{arch}/{fqpn} structure");
        assertTrue(binDir.getAbsolutePath().endsWith(File.separator + "test-app"),
            "macOS bin directory should end with package name");
        assertFalse(binDir.getAbsolutePath().equals(new File(userHome, "bin").getAbsolutePath()),
            "macOS should not use legacy ~/bin directory");
    }

    @Test
    public void testMacGetBinDirMultiplePackagesDifferentDirectories() {
        // Different packages should use different per-app directories on macOS
        File binDir1 = installer.getBinDir(createSettings("app1", null));
        File binDir2 = installer.getBinDir(createSettings("app2", null));
        File binDir3 = installer.getBinDir(createSettings("app3",
            "https://github.com/user/app3-repo"));

        assertNotNull(binDir1);
        assertNotNull(binDir2);
        assertNotNull(binDir3);

        // Each should have its own per-app directory
        assertNotEquals(binDir1.getAbsolutePath(), binDir2.getAbsolutePath(),
            "macOS: different NPM packages should have separate per-app directories");
        assertNotEquals(binDir1.getAbsolutePath(), binDir3.getAbsolutePath(),
            "macOS: NPM and GitHub packages should have separate per-app directories");

        // Verify they all contain the bin-{arch} pattern
        assertTrue(binDir1.getAbsolutePath().contains(".jdeploy" + File.separator + "bin-"),
            "All directories should use bin-{arch} pattern");
        assertTrue(binDir2.getAbsolutePath().contains(".jdeploy" + File.separator + "bin-"),
            "All directories should use bin-{arch} pattern");
        assertTrue(binDir3.getAbsolutePath().contains(".jdeploy" + File.separator + "bin-"),
            "All directories should use bin-{arch} pattern");
    }

    @Test
    public void testMacGetBinDirConsistency() {
        settings.setPackageName("consistent-app");
        settings.setSource("https://github.com/user/repo");
        
        // Call getBinDir multiple times
        File binDir1 = installer.getBinDir(settings);
        File binDir2 = installer.getBinDir(settings);
        
        assertNotNull(binDir1);
        assertNotNull(binDir2);
        assertEquals(binDir1.getAbsolutePath(), binDir2.getAbsolutePath(),
            "macOS: same settings should always produce the same bin directory");
    }

    @Test
    public void testMacInstallerDelegatesCorrectly() {
        // Verify that MacCliCommandInstaller delegates to the abstract implementation
        // by comparing behavior with direct instantiation of abstract class
        AbstractUnixCliCommandInstaller abstractInstaller = new AbstractUnixCliCommandInstaller() {
            @Override
            protected void writeCommandScript(File scriptPath, String launcherPath, String commandName, java.util.List<String> args) throws java.io.IOException {
                // No-op for this test
            }

            @Override
            public java.util.List<java.io.File> installCommands(File launcherPath, java.util.List<ca.weblite.jdeploy.models.CommandSpec> commands, InstallationSettings settings) {
                return new java.util.ArrayList<>();
            }
        };

        settings.setPackageName("delegate-test");
        settings.setSource(null);
        
        File macBinDir = installer.getBinDir(settings);
        File abstractBinDir = abstractInstaller.getBinDir(settings);
        
        assertNotNull(macBinDir);
        assertNotNull(abstractBinDir);
        assertEquals(macBinDir.getAbsolutePath(), abstractBinDir.getAbsolutePath(),
            "MacCliCommandInstaller should delegate to abstract class implementation");
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

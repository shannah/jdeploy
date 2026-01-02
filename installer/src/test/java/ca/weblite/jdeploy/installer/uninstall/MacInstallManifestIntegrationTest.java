package ca.weblite.jdeploy.installer.uninstall;

import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for macOS installation manifest creation.
 * 
 * Verifies that the UninstallManifestBuilder and UninstallManifestWriter
 * correctly collect and persist all macOS installation artifacts.
 */
@DisabledOnOs(OS.WINDOWS)
public class MacInstallManifestIntegrationTest {
    private File tempDir;
    private File manifestDir;
    private File appDir;

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("mac-manifest-test-").toFile();
        manifestDir = new File(tempDir, ".jdeploy" + File.separator + "manifests" + File.separator + "x64");
        manifestDir.mkdirs();
        appDir = new File(tempDir, "Applications");
        appDir.mkdirs();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (tempDir != null && tempDir.exists()) {
            deleteRecursive(tempDir);
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    @Test
    public void testCreateManifestWithAppBundle() throws Exception {
        // Create mock installation artifacts
        File testApp = new File(appDir, "TestApp.app");
        testApp.mkdirs();
        new File(testApp, "Contents").mkdirs();

        // Build manifest
        UninstallManifestBuilder builder = new UninstallManifestBuilder();
        builder.withPackageInfo("test-app", null, "1.0.0", "x64");
        builder.withInstallerVersion("1.0");
        builder.addDirectory(testApp.getAbsolutePath(), 
                UninstallManifest.CleanupStrategy.ALWAYS,
                "Test application bundle");

        UninstallManifest manifest = builder.build();

        // Verify manifest content
        assertNotNull(manifest);
        assertEquals("1.0", manifest.getVersion());
        assertEquals("test-app", manifest.getPackageInfo().getName());
        assertEquals("1.0.0", manifest.getPackageInfo().getVersion());
        assertEquals("x64", manifest.getPackageInfo().getArchitecture());
        assertEquals(1, manifest.getDirectories().size());
        
        UninstallManifest.InstalledDirectory dir = manifest.getDirectories().get(0);
        assertTrue(dir.getPath().contains("TestApp.app"));
        assertEquals(UninstallManifest.CleanupStrategy.ALWAYS, dir.getCleanup());
    }

    @Test
    public void testCreateManifestWithAdminWrapper() throws Exception {
        File testApp = new File(appDir, "TestApp.app");
        testApp.mkdirs();
        
        File adminWrapper = new File(appDir, "TestApp Admin.app");
        adminWrapper.mkdirs();

        UninstallManifestBuilder builder = new UninstallManifestBuilder();
        builder.withPackageInfo("test-app", null, "1.0.0", "x64");
        builder.addDirectory(testApp.getAbsolutePath(), 
                UninstallManifest.CleanupStrategy.ALWAYS,
                "Application bundle");
        builder.addDirectory(adminWrapper.getAbsolutePath(),
                UninstallManifest.CleanupStrategy.ALWAYS,
                "Admin launcher wrapper");

        UninstallManifest manifest = builder.build();

        assertEquals(2, manifest.getDirectories().size());
        assertTrue(manifest.getDirectories().stream()
            .anyMatch(d -> d.getPath().contains("TestApp.app")));
        assertTrue(manifest.getDirectories().stream()
            .anyMatch(d -> d.getPath().contains("TestApp Admin.app")));
    }

    @Test
    public void testCreateManifestWithDesktopAlias() throws Exception {
        File desktopDir = new File(tempDir, "Desktop");
        desktopDir.mkdirs();
        
        File desktopAlias = new File(desktopDir, "TestApp.app");
        desktopAlias.createNewFile();

        UninstallManifestBuilder builder = new UninstallManifestBuilder();
        builder.withPackageInfo("test-app", null, "1.0.0", "x64");
        builder.addFile(desktopAlias.getAbsolutePath(),
                UninstallManifest.FileType.LINK,
                "Desktop alias");

        UninstallManifest manifest = builder.build();

        assertEquals(1, manifest.getFiles().size());
        UninstallManifest.InstalledFile file = manifest.getFiles().get(0);
        assertEquals(UninstallManifest.FileType.LINK, file.getType());
        assertTrue(file.getPath().contains("Desktop"));
    }

    @Test
    public void testCreateManifestWithCLIScripts() throws Exception {
        File binDir = new File(tempDir, ".jdeploy" + File.separator + "bin-x64" + File.separator + "test.app");
        binDir.mkdirs();
        
        File script1 = new File(binDir, "test-cmd1");
        File script2 = new File(binDir, "test-cmd2");
        script1.createNewFile();
        script2.createNewFile();

        UninstallManifestBuilder builder = new UninstallManifestBuilder();
        builder.withPackageInfo("test-app", null, "1.0.0", "x64");
        builder.addFile(script1.getAbsolutePath(), 
                UninstallManifest.FileType.SCRIPT,
                "CLI command script 1");
        builder.addFile(script2.getAbsolutePath(),
                UninstallManifest.FileType.SCRIPT,
                "CLI command script 2");
        builder.addDirectory(binDir.getAbsolutePath(),
                UninstallManifest.CleanupStrategy.IF_EMPTY,
                "CLI bin directory");

        UninstallManifest manifest = builder.build();

        assertEquals(2, manifest.getFiles().size());
        assertEquals(1, manifest.getDirectories().size());
        assertTrue(manifest.getFiles().stream()
            .allMatch(f -> f.getType() == UninstallManifest.FileType.SCRIPT));
    }

    @Test
    public void testCreateManifestWithShellProfileEntries() throws Exception {
        UninstallManifestBuilder builder = new UninstallManifestBuilder();
        builder.withPackageInfo("test-app", null, "1.0.0", "x64");
        
        String userHome = System.getProperty("user.home");
        String pathLine = "export PATH=\"" + userHome + "/.jdeploy/bin-x64:$PATH\"";
        
        builder.addShellProfileEntry(userHome + "/.bashrc", pathLine, "Bash PATH");
        builder.addShellProfileEntry(userHome + "/.zprofile", pathLine, "Zsh PATH");
        builder.addShellProfileEntry(userHome + "/.config/fish/config.fish", 
                "set -gx PATH \"" + userHome + "/.jdeploy/bin-x64\" $PATH",
                "Fish PATH");

        UninstallManifest manifest = builder.build();

        assertNotNull(manifest.getPathModifications());
        assertEquals(3, manifest.getPathModifications().getShellProfiles().size());
        
        assertTrue(manifest.getPathModifications().getShellProfiles().stream()
            .anyMatch(e -> e.getFile().contains(".bashrc")));
        assertTrue(manifest.getPathModifications().getShellProfiles().stream()
            .anyMatch(e -> e.getFile().contains(".zprofile")));
        assertTrue(manifest.getPathModifications().getShellProfiles().stream()
            .anyMatch(e -> e.getFile().contains("fish")));
    }

    @Test
    public void testWriteManifestToDisk() throws Exception {
        File testApp = new File(appDir, "TestApp.app");
        testApp.mkdirs();

        UninstallManifestBuilder builder = new UninstallManifestBuilder();
        builder.withPackageInfo("test-package", null, "2.0.0", "x64");
        builder.withInstallerVersion("1.0");
        builder.addDirectory(testApp.getAbsolutePath(),
                UninstallManifest.CleanupStrategy.ALWAYS,
                "Application bundle");

        UninstallManifest manifest = builder.build();

        // Write manifest using a custom destination to avoid needing real ~/.jdeploy
        File destDir = new File(manifestDir, "test.package");
        destDir.mkdirs();
        File destFile = new File(destDir, "uninstall-manifest.xml");

        UninstallManifestWriter writer = new UninstallManifestWriter(true); // Skip schema validation for test
        File written = writer.write(manifest, destFile);

        // Verify file was written
        assertTrue(written.exists());
        assertTrue(written.isFile());
        assertTrue(written.length() > 0);

        // Verify XML content structure
        String content = new String(Files.readAllBytes(written.toPath()));
        assertTrue(content.contains("<uninstallManifest"));
        assertTrue(content.contains("version=\"1.0\""));
        assertTrue(content.contains("<name>test-package</name>"));
        assertTrue(content.contains("<version>2.0.0</version>"));
        assertTrue(content.contains("TestApp.app"));
    }

    @Test
    public void testManifestWithCompleteInstallation() throws Exception {
        // Simulate a complete macOS installation with all artifacts
        File appBundle = new File(appDir, "MyApp.app");
        appBundle.mkdirs();
        new File(appBundle, "Contents").mkdirs();
        
        File adminWrapper = new File(appDir, "MyApp Admin.app");
        adminWrapper.mkdirs();
        
        File desktopDir = new File(tempDir, "Desktop");
        desktopDir.mkdirs();
        File desktopAlias = new File(desktopDir, "MyApp.app");
        desktopAlias.createNewFile();
        
        File binDir = new File(tempDir, ".jdeploy" + File.separator + "bin-x64" + File.separator + "myapp");
        binDir.mkdirs();
        File cliScript = new File(binDir, "myapp");
        cliScript.createNewFile();

        // Build comprehensive manifest
        UninstallManifestBuilder builder = new UninstallManifestBuilder();
        builder.withPackageInfo("my-app", null, "1.5.0", "x64");
        builder.withInstallerVersion("1.0.0");
        
        builder.addDirectory(appBundle.getAbsolutePath(),
                UninstallManifest.CleanupStrategy.ALWAYS,
                "Application bundle");
        builder.addDirectory(adminWrapper.getAbsolutePath(),
                UninstallManifest.CleanupStrategy.ALWAYS,
                "Admin wrapper");
        builder.addFile(desktopAlias.getAbsolutePath(),
                UninstallManifest.FileType.LINK,
                "Desktop shortcut");
        builder.addFile(cliScript.getAbsolutePath(),
                UninstallManifest.FileType.SCRIPT,
                "CLI command");
        builder.addDirectory(binDir.getParentFile().getAbsolutePath(),
                UninstallManifest.CleanupStrategy.IF_EMPTY,
                "CLI bin directory");
        
        String userHome = System.getProperty("user.home");
        builder.addShellProfileEntry(userHome + "/.bashrc",
                "export PATH=\"" + userHome + "/.jdeploy/bin-x64:$PATH\"",
                "PATH modification");

        UninstallManifest manifest = builder.build();

        // Verify complete manifest
        assertNotNull(manifest);
        assertEquals("my-app", manifest.getPackageInfo().getName());
        assertEquals("1.5.0", manifest.getPackageInfo().getVersion());
        assertEquals(3, manifest.getDirectories().size());
        assertEquals(2, manifest.getFiles().size());
        assertEquals(1, manifest.getPathModifications().getShellProfiles().size());
    }
}

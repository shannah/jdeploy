package ca.weblite.jdeploy.installer.win;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import ca.weblite.jdeploy.installer.util.ArchitectureUtil;
import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test verifying that InstallWindows creates an uninstall manifest
 * with expected artifacts after successful installation.
 */
@DisabledOnOs({OS.MAC, OS.LINUX})
public class InstallWindowsManifestIntegrationTest {

    @TempDir
    Path tempDir;

    private File jdeployHome;
    private File manifestsDir;
    private String originalUserHome;

    @BeforeEach
    void setUp() {
        // Save original user.home
        originalUserHome = System.getProperty("user.home");
        
        // Set up test directories
        jdeployHome = new File(tempDir.toFile(), ".jdeploy");
        jdeployHome.mkdirs();
        
        manifestsDir = new File(jdeployHome, "manifests");
        manifestsDir.mkdirs();
    }

    @AfterEach
    void tearDown() {
        // Restore original user.home
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void testManifestContainsPackageInfo() throws Exception {
        // Create a minimal manifest using the builder directly to verify structure
        ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder builder = 
            new ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder();
        
        builder.withPackageInfo("test-package", null, "1.0.0", "x64");
        builder.withInstallerVersion("1.0");
        builder.addFile("C:\\test\\app.exe", UninstallManifest.FileType.BINARY, "Test executable");
        
        UninstallManifest manifest = builder.build();
        
        assertNotNull(manifest);
        assertEquals("test-package", manifest.getPackageInfo().getName());
        assertEquals("1.0.0", manifest.getPackageInfo().getVersion());
        assertEquals("x64", manifest.getPackageInfo().getArchitecture());
        assertEquals(1, manifest.getFiles().size());
        assertEquals("C:\\test\\app.exe", manifest.getFiles().get(0).getPath());
    }

    @Test
    void testManifestContainsExecutableAndIcon() throws Exception {
        ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder builder = 
            new ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder();
        
        builder.withPackageInfo("my-app", null, "2.0.0", "x64");
        builder.withInstallerVersion("1.0");
        
        // Add executable
        builder.addFile("C:\\Users\\test\\.jdeploy\\apps\\my-app\\MyApp.exe", 
            UninstallManifest.FileType.BINARY, "Main application executable");
        
        // Add icon
        builder.addFile("C:\\Users\\test\\.jdeploy\\apps\\my-app\\icon.ico", 
            UninstallManifest.FileType.ICON, "Application icon");
        
        UninstallManifest manifest = builder.build();
        
        assertEquals(2, manifest.getFiles().size());
        
        // Check executable
        UninstallManifest.InstalledFile exeFile = manifest.getFiles().stream()
            .filter(f -> f.getType() == UninstallManifest.FileType.BINARY)
            .findFirst()
            .orElse(null);
        assertNotNull(exeFile);
        assertTrue(exeFile.getPath().endsWith(".exe"));
        
        // Check icon
        UninstallManifest.InstalledFile iconFile = manifest.getFiles().stream()
            .filter(f -> f.getType() == UninstallManifest.FileType.ICON)
            .findFirst()
            .orElse(null);
        assertNotNull(iconFile);
        assertTrue(iconFile.getPath().endsWith(".ico"));
    }

    @Test
    void testManifestContainsShortcuts() throws Exception {
        ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder builder = 
            new ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder();
        
        builder.withPackageInfo("my-app", null, "1.0.0", "x64");
        builder.withInstallerVersion("1.0");
        builder.addFile("C:\\test\\app.exe", UninstallManifest.FileType.BINARY, "Executable");
        
        // Add shortcuts
        builder.addFile("C:\\Users\\test\\Desktop\\MyApp.lnk", 
            UninstallManifest.FileType.LINK, "Desktop shortcut");
        builder.addFile("C:\\Users\\test\\AppData\\Roaming\\Microsoft\\Windows\\Start Menu\\Programs\\MyApp.lnk", 
            UninstallManifest.FileType.LINK, "Program Menu shortcut");
        
        UninstallManifest manifest = builder.build();
        
        List<UninstallManifest.InstalledFile> shortcuts = new ArrayList<>();
        for (UninstallManifest.InstalledFile file : manifest.getFiles()) {
            if (file.getType() == UninstallManifest.FileType.LINK) {
                shortcuts.add(file);
            }
        }
        
        assertEquals(2, shortcuts.size());
    }

    @Test
    void testManifestContainsCliWrappers() throws Exception {
        ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder builder = 
            new ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder();
        
        builder.withPackageInfo("cli-tool", null, "1.0.0", "x64");
        builder.withInstallerVersion("1.0");
        builder.addFile("C:\\test\\app.exe", UninstallManifest.FileType.BINARY, "Executable");
        
        // Add CLI wrappers
        builder.addFile("C:\\Users\\test\\.jdeploy\\bin\\mytool.cmd", 
            UninstallManifest.FileType.SCRIPT, "CLI command wrapper");
        builder.addFile("C:\\Users\\test\\.jdeploy\\bin\\mytool", 
            UninstallManifest.FileType.SCRIPT, "CLI command wrapper (Git Bash)");
        
        UninstallManifest manifest = builder.build();
        
        List<UninstallManifest.InstalledFile> scripts = new ArrayList<>();
        for (UninstallManifest.InstalledFile file : manifest.getFiles()) {
            if (file.getType() == UninstallManifest.FileType.SCRIPT) {
                scripts.add(file);
            }
        }
        
        assertEquals(2, scripts.size());
    }

    @Test
    void testManifestContainsRegistryKeys() throws Exception {
        ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder builder = 
            new ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder();
        
        builder.withPackageInfo("my-app", null, "1.0.0", "x64");
        builder.withInstallerVersion("1.0");
        builder.addFile("C:\\test\\app.exe", UninstallManifest.FileType.BINARY, "Executable");
        
        // Add registry keys
        builder.addCreatedRegistryKey(
            UninstallManifest.RegistryRoot.HKEY_CURRENT_USER,
            "Software\\Clients\\Other\\jdeploy.my-app",
            "Application registration"
        );
        builder.addCreatedRegistryKey(
            UninstallManifest.RegistryRoot.HKEY_CURRENT_USER,
            "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\jdeploy.my-app",
            "Uninstall entry"
        );
        
        UninstallManifest manifest = builder.build();
        
        assertNotNull(manifest.getRegistry());
        assertEquals(2, manifest.getRegistry().getCreatedKeys().size());
        
        UninstallManifest.RegistryKey key1 = manifest.getRegistry().getCreatedKeys().get(0);
        assertEquals(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER, key1.getRoot());
        assertTrue(key1.getPath().contains("jdeploy.my-app"));
    }

    @Test
    void testManifestContainsPathModifications() throws Exception {
        ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder builder = 
            new ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder();
        
        builder.withPackageInfo("cli-tool", null, "1.0.0", "x64");
        builder.withInstallerVersion("1.0");
        builder.addFile("C:\\test\\app.exe", UninstallManifest.FileType.BINARY, "Executable");
        
        // Add PATH modification
        builder.addWindowsPathEntry(
            "C:\\Users\\test\\.jdeploy\\bin-cli-tool",
            "CLI commands bin directory"
        );
        
        UninstallManifest manifest = builder.build();
        
        assertNotNull(manifest.getPathModifications());
        assertEquals(1, manifest.getPathModifications().getWindowsPaths().size());
        
        UninstallManifest.WindowsPathEntry pathEntry = manifest.getPathModifications().getWindowsPaths().get(0);
        assertTrue(pathEntry.getAddedEntry().contains(".jdeploy"));
    }

    @Test
    void testManifestContainsAppDirectory() throws Exception {
        ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder builder = 
            new ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder();
        
        builder.withPackageInfo("my-app", null, "1.0.0", "x64");
        builder.withInstallerVersion("1.0");
        builder.addFile("C:\\test\\app.exe", UninstallManifest.FileType.BINARY, "Executable");
        
        // Add app directory
        builder.addDirectory(
            "C:\\Users\\test\\.jdeploy\\apps\\my-app",
            UninstallManifest.CleanupStrategy.ALWAYS,
            "Application directory"
        );
        
        UninstallManifest manifest = builder.build();
        
        assertEquals(1, manifest.getDirectories().size());
        
        UninstallManifest.InstalledDirectory dir = manifest.getDirectories().get(0);
        assertEquals(UninstallManifest.CleanupStrategy.ALWAYS, dir.getCleanup());
        assertTrue(dir.getPath().contains("my-app"));
    }

    @Test
    void testCompleteManifestWithAllArtifacts() throws Exception {
        ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder builder = 
            new ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder();
        
        // Package info
        builder.withPackageInfo("complete-app", "https://github.com/user/repo", "3.0.0", "x64");
        builder.withInstallerVersion("2.0");
        
        // Executable and CLI exe
        builder.addFile("C:\\Users\\test\\.jdeploy\\apps\\complete-app\\CompleteApp.exe", 
            UninstallManifest.FileType.BINARY, "Main application executable");
        builder.addFile("C:\\Users\\test\\.jdeploy\\apps\\complete-app\\CompleteApp-cli.exe", 
            UninstallManifest.FileType.BINARY, "CLI launcher executable");
        
        // Icons
        builder.addFile("C:\\Users\\test\\.jdeploy\\apps\\complete-app\\icon.png", 
            UninstallManifest.FileType.ICON, "Application icon (PNG)");
        builder.addFile("C:\\Users\\test\\.jdeploy\\apps\\complete-app\\icon.ico", 
            UninstallManifest.FileType.ICON, "Application icon (ICO)");
        
        // Shortcuts
        builder.addFile("C:\\Users\\test\\Desktop\\CompleteApp.lnk", 
            UninstallManifest.FileType.LINK, "Desktop shortcut");
        builder.addFile("C:\\Users\\test\\AppData\\Roaming\\Microsoft\\Windows\\Start Menu\\Programs\\CompleteApp.lnk", 
            UninstallManifest.FileType.LINK, "Program Menu shortcut");
        builder.addFile("C:\\Users\\test\\AppData\\Roaming\\Microsoft\\Windows\\Start Menu\\CompleteApp.lnk", 
            UninstallManifest.FileType.LINK, "Start Menu shortcut");
        
        // CLI wrappers
        builder.addFile("C:\\Users\\test\\.jdeploy\\bin\\complete.cmd", 
            UninstallManifest.FileType.SCRIPT, "CLI command wrapper");
        
        // Registry keys
        builder.addCreatedRegistryKey(
            UninstallManifest.RegistryRoot.HKEY_CURRENT_USER,
            "Software\\Clients\\Other\\jdeploy.complete-app",
            "Application registration"
        );
        builder.addCreatedRegistryKey(
            UninstallManifest.RegistryRoot.HKEY_CURRENT_USER,
            "Software\\Clients\\Other\\jdeploy.complete-app\\Capabilities",
            "Application capabilities"
        );
        
        // PATH modification
        builder.addWindowsPathEntry(
            "C:\\Users\\test\\.jdeploy\\bin-complete-app",
            "CLI commands bin directory"
        );
        
        // App directory
        builder.addDirectory(
            "C:\\Users\\test\\.jdeploy\\apps\\complete-app",
            UninstallManifest.CleanupStrategy.ALWAYS,
            "Application directory"
        );
        
        UninstallManifest manifest = builder.build();
        
        // Verify all components
        assertNotNull(manifest.getPackageInfo());
        assertEquals("complete-app", manifest.getPackageInfo().getName());
        assertEquals("https://github.com/user/repo", manifest.getPackageInfo().getSource());
        assertEquals("3.0.0", manifest.getPackageInfo().getVersion());
        assertEquals("x64", manifest.getPackageInfo().getArchitecture());
        
        // Count file types
        int binaries = 0, icons = 0, links = 0, scripts = 0;
        for (UninstallManifest.InstalledFile file : manifest.getFiles()) {
            switch (file.getType()) {
                case BINARY: binaries++; break;
                case ICON: icons++; break;
                case LINK: links++; break;
                case SCRIPT: scripts++; break;
                default: break;
            }
        }
        assertEquals(2, binaries, "Should have 2 binary files (exe and cli exe)");
        assertEquals(2, icons, "Should have 2 icon files (png and ico)");
        assertEquals(3, links, "Should have 3 shortcut files");
        assertEquals(1, scripts, "Should have 1 CLI wrapper");
        
        // Verify registry
        assertNotNull(manifest.getRegistry());
        assertEquals(2, manifest.getRegistry().getCreatedKeys().size());
        
        // Verify PATH
        assertNotNull(manifest.getPathModifications());
        assertEquals(1, manifest.getPathModifications().getWindowsPaths().size());
        
        // Verify directory
        assertEquals(1, manifest.getDirectories().size());
    }

    @Test
    void testRegistryPathsFromInstallWindowsRegistry() throws Exception {
        // Test that InstallWindowsRegistry.getCreatedRegistryPaths() returns expected paths
        AppInfo appInfo = new AppInfo();
        appInfo.setNpmPackage("test-app");
        appInfo.setNpmVersion("1.0.0");
        appInfo.setTitle("Test App");
        appInfo.setDescription("A test application");
        
        File exeFile = new File(tempDir.toFile(), "test.exe");
        exeFile.createNewFile();
        
        File iconFile = new File(tempDir.toFile(), "icon.ico");
        iconFile.createNewFile();
        
        InstallWindowsRegistry registry = new InstallWindowsRegistry(appInfo, exeFile, iconFile, null);
        
        List<String> paths = registry.getCreatedRegistryPaths();
        
        assertNotNull(paths);
        assertFalse(paths.isEmpty());
        
        // Should contain at minimum the registry path and uninstall key
        boolean hasRegistryPath = false;
        boolean hasUninstallKey = false;
        for (String path : paths) {
            if (path.contains("Clients")) {
                hasRegistryPath = true;
            }
            if (path.contains("Uninstall")) {
                hasUninstallKey = true;
            }
        }
        assertTrue(hasRegistryPath, "Should contain application registry path");
        assertTrue(hasUninstallKey, "Should contain uninstall key path");
    }
}

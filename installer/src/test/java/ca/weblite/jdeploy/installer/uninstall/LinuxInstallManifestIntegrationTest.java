package ca.weblite.jdeploy.installer.uninstall;

import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import ca.weblite.jdeploy.installer.util.ArchitectureUtil;
import ca.weblite.tools.io.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Linux uninstall manifest creation.
 * Verifies that the manifest is properly created and persisted during Linux installation.
 * This test simulates the manifest creation process without requiring a full installation.
 */
@DisabledOnOs(OS.WINDOWS)
public class LinuxInstallManifestIntegrationTest {
    private File tempDir;
    private File testAppDir;
    private File testLauncher;
    private File testIcon;
    private File manifestDir;

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("linux-manifest-test-").toFile();
        testAppDir = new File(tempDir, "apps/example.myapp");
        testAppDir.mkdirs();

        // Create test files
        testLauncher = new File(testAppDir, "myapp");
        testIcon = new File(testAppDir, "icon.png");
        
        // Create dummy files
        Files.write(testLauncher.toPath(), "#!/bin/sh\necho test".getBytes(StandardCharsets.UTF_8));
        Files.write(testIcon.toPath(), "PNG_DATA".getBytes(StandardCharsets.UTF_8));
        testLauncher.setExecutable(true);

        // Create manifest directory
        manifestDir = new File(tempDir, ".jdeploy/manifests");
        manifestDir.mkdirs();

        // Set home directory system property for test
        System.setProperty("user.home", tempDir.getAbsolutePath());
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (tempDir != null && tempDir.exists()) {
            org.apache.commons.io.FileUtils.deleteDirectory(tempDir);
        }
    }

    @Test
    public void testLinuxManifestCreation() throws Exception {
        // Build manifest with Linux-specific artifacts
        UninstallManifestBuilder builder = new UninstallManifestBuilder();
        
        String packageName = "example";
        String packageSource = null;
        String version = "1.0.0";
        String arch = ArchitectureUtil.getArchitecture();
        
        builder.withPackageInfo(packageName, packageSource, version, arch);
        builder.withInstallerVersion("2.0.0");
        
        // Add launcher executable
        builder.addFile(testLauncher.getAbsolutePath(),
                UninstallManifest.FileType.BINARY,
                "Application launcher executable");
        
        // Add icon file
        builder.addFile(testIcon.getAbsolutePath(),
                UninstallManifest.FileType.ICON,
                "Application icon");
        
        // Add app directory for cleanup
        builder.addDirectory(testAppDir.getAbsolutePath(),
                UninstallManifest.CleanupStrategy.IF_EMPTY,
                "Application installation directory");
        
        // Add shell profile PATH modifications
        String userHome = tempDir.getAbsolutePath();
        String bashrcPath = userHome + File.separator + ".bashrc";
        String pathLine = "export PATH=\"" + userHome + File.separator + ".local/bin:$PATH\"";
        builder.addShellProfileEntry(bashrcPath, pathLine, "PATH modification for bash");
        
        // Build and write manifest
        UninstallManifest manifest = builder.build();
        assertNotNull(manifest);
        assertEquals(packageName, manifest.getPackageInfo().getName());
        assertEquals(version, manifest.getPackageInfo().getVersion());
        assertEquals(arch, manifest.getPackageInfo().getArchitecture());
        
        // Write manifest to disk
        UninstallManifestWriter writer = new UninstallManifestWriter(true); // Skip schema validation for test
        File manifestFile = writer.write(manifest);
        
        assertTrue(manifestFile.exists(), "Manifest file should exist");
        String content = new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains(packageName), "Manifest should contain package name");
        assertTrue(content.contains(version), "Manifest should contain version");
        assertTrue(content.contains(testLauncher.getAbsolutePath()), "Manifest should contain launcher path");
        assertTrue(content.contains(testIcon.getAbsolutePath()), "Manifest should contain icon path");
    }

    @Test
    public void testLinuxManifestWithDesktopFiles() throws Exception {
        // Create desktop files
        File desktopDir = new File(tempDir, "Desktop");
        desktopDir.mkdirs();
        File desktopFile = new File(desktopDir, "myapp.desktop");
        Files.write(desktopFile.toPath(), "[Desktop Entry]\nName=MyApp".getBytes(StandardCharsets.UTF_8));
        
        File applicationsDir = new File(tempDir, ".local/share/applications");
        applicationsDir.mkdirs();
        File appMenuFile = new File(applicationsDir, "myapp.desktop");
        Files.write(appMenuFile.toPath(), "[Desktop Entry]\nName=MyApp".getBytes(StandardCharsets.UTF_8));
        
        UninstallManifestBuilder builder = new UninstallManifestBuilder();
        builder.withPackageInfo("example", null, "1.0.0", ArchitectureUtil.getArchitecture());
        
        // Add desktop files
        builder.addFile(desktopFile.getAbsolutePath(),
                UninstallManifest.FileType.CONFIG,
                "Desktop shortcut");
        builder.addFile(appMenuFile.getAbsolutePath(),
                UninstallManifest.FileType.CONFIG,
                "Applications menu entry");
        
        UninstallManifest manifest = builder.build();
        assertEquals(2, manifest.getFiles().size());
        assertTrue(manifest.getFiles().stream()
                .anyMatch(f -> f.getPath().equals(desktopFile.getAbsolutePath())));
        assertTrue(manifest.getFiles().stream()
                .anyMatch(f -> f.getPath().equals(appMenuFile.getAbsolutePath())));
    }

    @Test
    public void testLinuxManifestWithCliScripts() throws Exception {
        // Create CLI scripts
        File binDir = new File(tempDir, ".local/bin");
        binDir.mkdirs();
        File cliScript = new File(binDir, "myapp-cmd");
        Files.write(cliScript.toPath(), "#!/bin/sh\nexec myapp \"$@\"".getBytes(StandardCharsets.UTF_8));
        cliScript.setExecutable(true);
        
        UninstallManifestBuilder builder = new UninstallManifestBuilder();
        builder.withPackageInfo("example", null, "1.0.0", ArchitectureUtil.getArchitecture());
        
        // Add CLI script
        builder.addFile(cliScript.getAbsolutePath(),
                UninstallManifest.FileType.SCRIPT,
                "CLI command script");
        
        // Add bin directory for cleanup
        builder.addDirectory(binDir.getAbsolutePath(),
                UninstallManifest.CleanupStrategy.IF_EMPTY,
                "CLI bin directory");
        
        UninstallManifest manifest = builder.build();
        assertEquals(1, manifest.getFiles().size());
        assertEquals(1, manifest.getDirectories().size());
        assertEquals(UninstallManifest.FileType.SCRIPT, manifest.getFiles().get(0).getType());
        assertEquals(UninstallManifest.CleanupStrategy.IF_EMPTY, manifest.getDirectories().get(0).getCleanup());
    }

    @Test
    public void testLinuxManifestWithPathModifications() throws Exception {
        UninstallManifestBuilder builder = new UninstallManifestBuilder();
        builder.withPackageInfo("example", null, "1.0.0", ArchitectureUtil.getArchitecture());
        
        String userHome = tempDir.getAbsolutePath();
        
        // Add multiple shell profile entries
        String bashrcPath = userHome + File.separator + ".bashrc";
        String zprofilePath = userHome + File.separator + ".zprofile";
        String localBinPath = userHome + File.separator + ".local/bin";
        String pathLine = "export PATH=\"" + localBinPath + ":$PATH\"";
        
        builder.addShellProfileEntry(bashrcPath, pathLine, "bash PATH entry");
        builder.addShellProfileEntry(zprofilePath, pathLine, "zsh PATH entry");
        
        UninstallManifest manifest = builder.build();
        assertNotNull(manifest.getPathModifications());
        assertEquals(2, manifest.getPathModifications().getShellProfiles().size());
        
        // Verify shell profile entries
        assertTrue(manifest.getPathModifications().getShellProfiles().stream()
                .anyMatch(e -> e.getFile().equals(bashrcPath)));
        assertTrue(manifest.getPathModifications().getShellProfiles().stream()
                .anyMatch(e -> e.getFile().equals(zprofilePath)));
    }

    @Test
    public void testLinuxManifestPersistenceAndRetrieval() throws Exception {
        // Create a complete manifest
        UninstallManifestBuilder builder = new UninstallManifestBuilder();
        String packageName = "test-app";
        String version = "2.5.1";
        String arch = ArchitectureUtil.getArchitecture();
        
        builder.withPackageInfo(packageName, null, version, arch);
        builder.withInstallerVersion("3.0.0");
        builder.addFile(testLauncher.getAbsolutePath(), UninstallManifest.FileType.BINARY, "Launcher");
        builder.addDirectory(testAppDir.getAbsolutePath(), UninstallManifest.CleanupStrategy.IF_EMPTY, "App dir");
        
        UninstallManifest manifest = builder.build();
        
        // Write to disk
        UninstallManifestWriter writer = new UninstallManifestWriter(true);
        File manifestFile = writer.write(manifest);
        
        // Verify file structure
        assertTrue(manifestFile.exists());
        assertTrue(manifestFile.getAbsolutePath().contains("manifests"));
        assertTrue(manifestFile.getAbsolutePath().contains(arch));
        assertTrue(manifestFile.getName().equals("uninstall-manifest.xml"));
        
        // Verify content
        String xmlContent = new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(xmlContent.contains("<uninstall-manifest"));
        assertTrue(xmlContent.contains(packageName));
        assertTrue(xmlContent.contains(version));
        assertTrue(xmlContent.contains(arch));
        assertTrue(xmlContent.contains("3.0.0")); // installer version
    }

    @Test
    public void testLinuxManifestWithAdminLauncher() throws Exception {
        // Create an admin launcher
        File adminLauncher = new File(testAppDir, "myapp-admin");
        Files.write(adminLauncher.toPath(), "#!/bin/sh\npkexec myapp".getBytes(StandardCharsets.UTF_8));
        adminLauncher.setExecutable(true);
        
        UninstallManifestBuilder builder = new UninstallManifestBuilder();
        builder.withPackageInfo("example", null, "1.0.0", ArchitectureUtil.getArchitecture());
        
        // Add admin launcher
        builder.addFile(adminLauncher.getAbsolutePath(),
                UninstallManifest.FileType.SCRIPT,
                "Admin launcher wrapper");
        
        UninstallManifest manifest = builder.build();
        assertEquals(1, manifest.getFiles().size());
        assertTrue(manifest.getFiles().get(0).getPath().equals(adminLauncher.getAbsolutePath()));
        assertTrue(manifest.getFiles().get(0).getDescription().contains("Admin launcher"));
    }

    @Test
    public void testLinuxManifestValidationFailsWithoutPackageInfo() {
        UninstallManifestBuilder builder = new UninstallManifestBuilder();
        
        // Try to build without calling withPackageInfo
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    public void testLinuxManifestVariableSubstitution() throws Exception {
        UninstallManifestBuilder builder = new UninstallManifestBuilder();
        builder.withPackageInfo("example", null, "1.0.0", ArchitectureUtil.getArchitecture());
        
        // Add paths with variable substitution
        String userHome = System.getProperty("user.home");
        builder.addFile("${USER_HOME}/.local/bin/myapp",
                UninstallManifest.FileType.SCRIPT,
                "CLI script");
        
        UninstallManifest manifest = builder.build();
        assertEquals(1, manifest.getFiles().size());
        // Variable should be substituted
        assertTrue(manifest.getFiles().get(0).getPath().contains(userHome));
        assertFalse(manifest.getFiles().get(0).getPath().contains("${"));
    }
}

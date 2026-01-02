package ca.weblite.jdeploy.installer.uninstall;

import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.CleanupStrategy;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.FileType;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.InstalledDirectory;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.InstalledFile;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.PackageInfo;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.PathModifications;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.RegistryInfo;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.ShellProfileEntry;
import ca.weblite.jdeploy.installer.win.InMemoryRegistryOperations;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for UninstallService on macOS.
 *
 * These tests verify the full uninstall workflow with real file system operations,
 * focusing on macOS-specific features:
 * - Shell profile modifications (.bashrc, .zshrc, .bash_profile)
 * - Symlink handling
 * - macOS directory structure and conventions
 * - Idempotency of uninstall operations
 *
 * Only runs on macOS systems.
 */
@EnabledOnOs(OS.MAC)
public class UninstallServiceMacOSIT {

    private static final String TEST_PACKAGE_NAME = "test-macos-app";
    private static final String TEST_SOURCE = "npm";
    private static final String TEST_VERSION = "1.2.3";

    private Path testHomeDir;
    private Path testAppDir;
    private Path testBinDir;
    private UninstallService service;
    private FileUninstallManifestRepository manifestRepository;
    private InMemoryRegistryOperations registryOps;

    private String originalUserHome;

    @BeforeEach
    public void setUp() throws IOException {
        // Create temporary home directory to avoid interfering with real system
        testHomeDir = Files.createTempDirectory("macos-uninstall-test-home-");
        testAppDir = testHomeDir.resolve("Applications").resolve("TestApp.app");
        testBinDir = testHomeDir.resolve(".local").resolve("bin");
        Files.createDirectories(testBinDir);

        // Override user.home for testing
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", testHomeDir.toString());

        // Create services with real repository
        registryOps = new InMemoryRegistryOperations();
        manifestRepository = new FileUninstallManifestRepository(true); // Skip validation
        service = new UninstallService(manifestRepository, registryOps);
    }

    @AfterEach
    public void tearDown() throws IOException {
        // Restore original user.home
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }

        // Clean up temporary directories
        if (testHomeDir != null && Files.exists(testHomeDir)) {
            FileUtils.deleteDirectory(testHomeDir.toFile());
        }
    }

    // ==================== Shell Profile Integration Tests ====================

    @Test
    public void testUninstallRemovesFromBashrcOnMacOS() throws Exception {
        // Setup: Create .bashrc with PATH export
        File bashrc = testHomeDir.resolve(".bashrc").toFile();
        String binPath = testBinDir.toAbsolutePath().toString();
        String exportLine = "export PATH=\"" + binPath + ":$PATH\"";
        String bashrcContent = "# Existing config\n" +
                               exportLine + "\n" +
                               "# More config\n";
        Files.write(bashrc.toPath(), bashrcContent.getBytes(StandardCharsets.UTF_8));

        // Verify export line exists
        String beforeContent = new String(Files.readAllBytes(bashrc.toPath()), StandardCharsets.UTF_8);
        assertTrue(beforeContent.contains(exportLine), "Export line should exist before uninstall");

        // Create manifest
        UninstallManifest manifest = createManifestWithShellProfile(
            bashrc.getAbsolutePath(),
            exportLine
        );
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Export line removed from .bashrc
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        String afterContent = new String(Files.readAllBytes(bashrc.toPath()), StandardCharsets.UTF_8);
        assertFalse(afterContent.contains(exportLine), "Export line should be removed from .bashrc");
        assertTrue(afterContent.contains("# Existing config"), "Other content should remain");
        assertTrue(afterContent.contains("# More config"), "Other content should remain");
    }

    @Test
    public void testUninstallRemovesFromZshrcOnMacOS() throws Exception {
        // Setup: Create .zshrc with PATH export (zsh is default shell on macOS)
        File zshrc = testHomeDir.resolve(".zshrc").toFile();
        String binPath = testBinDir.toAbsolutePath().toString();
        String exportLine = "export PATH=\"" + binPath + ":$PATH\"";
        String zshrcContent = "# zsh configuration\n" +
                              exportLine + "\n" +
                              "# aliases\nalias ll='ls -la'\n";
        Files.write(zshrc.toPath(), zshrcContent.getBytes(StandardCharsets.UTF_8));

        // Create manifest
        UninstallManifest manifest = createManifestWithShellProfile(
            zshrc.getAbsolutePath(),
            exportLine
        );
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Export line removed from .zshrc
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        String afterContent = new String(Files.readAllBytes(zshrc.toPath()), StandardCharsets.UTF_8);
        assertFalse(afterContent.contains(exportLine), "Export line should be removed from .zshrc");
        assertTrue(afterContent.contains("# zsh configuration"), "Other content should remain");
        assertTrue(afterContent.contains("alias ll='ls -la'"), "Aliases should remain");
    }

    @Test
    public void testUninstallRemovesFromBashProfileOnMacOS() throws Exception {
        // Setup: Create .bash_profile with PATH export
        File bashProfile = testHomeDir.resolve(".bash_profile").toFile();
        String binPath = testBinDir.toAbsolutePath().toString();
        String exportLine = "export PATH=\"" + binPath + ":$PATH\"";
        String profileContent = "# macOS bash profile\n" +
                                "export EDITOR=vim\n" +
                                exportLine + "\n";
        Files.write(bashProfile.toPath(), profileContent.getBytes(StandardCharsets.UTF_8));

        // Create manifest
        UninstallManifest manifest = createManifestWithShellProfile(
            bashProfile.getAbsolutePath(),
            exportLine
        );
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Export line removed from .bash_profile
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        String afterContent = new String(Files.readAllBytes(bashProfile.toPath()), StandardCharsets.UTF_8);
        assertFalse(afterContent.contains(exportLine), "Export line should be removed");
        assertTrue(afterContent.contains("export EDITOR=vim"), "Other exports should remain");
    }

    @Test
    public void testUninstallRemovesFromMultipleShellProfiles() throws Exception {
        // Setup: Create multiple shell profiles with PATH exports
        File bashrc = testHomeDir.resolve(".bashrc").toFile();
        File zshrc = testHomeDir.resolve(".zshrc").toFile();
        String binPath = testBinDir.toAbsolutePath().toString();
        String exportLine = "export PATH=\"" + binPath + ":$PATH\"";

        Files.write(bashrc.toPath(),
            ("# bashrc\n" + exportLine + "\n").getBytes(StandardCharsets.UTF_8));
        Files.write(zshrc.toPath(),
            ("# zshrc\n" + exportLine + "\n").getBytes(StandardCharsets.UTF_8));

        // Create manifest with multiple shell profile entries
        List<ShellProfileEntry> shellProfiles = Arrays.asList(
            ShellProfileEntry.builder()
                .file(bashrc.getAbsolutePath())
                .exportLine(exportLine)
                .description("Bash PATH")
                .build(),
            ShellProfileEntry.builder()
                .file(zshrc.getAbsolutePath())
                .exportLine(exportLine)
                .description("Zsh PATH")
                .build()
        );

        UninstallManifest manifest = createManifestWithShellProfiles(shellProfiles);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Export line removed from both files
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        String bashrcContent = new String(Files.readAllBytes(bashrc.toPath()), StandardCharsets.UTF_8);
        String zshrcContent = new String(Files.readAllBytes(zshrc.toPath()), StandardCharsets.UTF_8);
        assertFalse(bashrcContent.contains(exportLine), "Export should be removed from .bashrc");
        assertFalse(zshrcContent.contains(exportLine), "Export should be removed from .zshrc");
    }

    @Test
    public void testUninstallHandlesHomeExpansionInPaths() throws Exception {
        // Setup: Create .bashrc with $HOME in the export line
        File bashrc = testHomeDir.resolve(".bashrc").toFile();
        String exportLine = "export PATH=\"$HOME/.local/bin:$PATH\"";
        Files.write(bashrc.toPath(),
            ("# Config\n" + exportLine + "\n").getBytes(StandardCharsets.UTF_8));

        // Create manifest
        UninstallManifest manifest = createManifestWithShellProfile(
            bashrc.getAbsolutePath(),
            exportLine
        );
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Export line with $HOME removed
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        String afterContent = new String(Files.readAllBytes(bashrc.toPath()), StandardCharsets.UTF_8);
        assertFalse(afterContent.contains(exportLine), "$HOME-based export should be removed");
    }

    // ==================== Symlink Handling Tests ====================

    @Test
    public void testUninstallDeletesSymlinks() throws Exception {
        // Setup: Create a real binary and a symlink to it
        File realBinary = testBinDir.resolve("real-app").toFile();
        Files.write(realBinary.toPath(), "#!/bin/bash\necho 'test'".getBytes(StandardCharsets.UTF_8));
        realBinary.setExecutable(true);

        Path symlinkPath = testBinDir.resolve("app-symlink");
        Files.createSymbolicLink(symlinkPath, realBinary.toPath());

        assertTrue(Files.exists(symlinkPath), "Symlink should exist");
        assertTrue(Files.isSymbolicLink(symlinkPath), "Should be a symlink");

        // Create manifest with symlink as installed file
        List<InstalledFile> files = Arrays.asList(
            InstalledFile.builder()
                .path(realBinary.getAbsolutePath())
                .type(FileType.BINARY)
                .build(),
            InstalledFile.builder()
                .path(symlinkPath.toAbsolutePath().toString())
                .type(FileType.LINK)
                .build()
        );

        UninstallManifest manifest = createManifestWithFiles(files);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Both symlink and real file deleted
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        assertFalse(Files.exists(symlinkPath), "Symlink should be deleted");
        assertFalse(realBinary.exists(), "Real binary should be deleted");
    }

    @Test
    public void testUninstallHandlesBrokenSymlinks() throws Exception {
        // Setup: Create a symlink to a non-existent file (broken symlink)
        Path brokenSymlink = testBinDir.resolve("broken-link");
        Path nonExistentTarget = testBinDir.resolve("non-existent-target");
        Files.createSymbolicLink(brokenSymlink, nonExistentTarget);

        assertTrue(Files.exists(brokenSymlink, java.nio.file.LinkOption.NOFOLLOW_LINKS),
            "Broken symlink should exist");
        assertFalse(Files.exists(brokenSymlink), "Symlink target should not exist");

        // Create manifest
        List<InstalledFile> files = Collections.singletonList(
            InstalledFile.builder()
                .path(brokenSymlink.toAbsolutePath().toString())
                .type(FileType.LINK)
                .build()
        );

        UninstallManifest manifest = createManifestWithFiles(files);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Broken symlink deleted successfully
        assertTrue(result.isSuccess(), "Uninstall should handle broken symlinks");
        assertFalse(Files.exists(brokenSymlink, java.nio.file.LinkOption.NOFOLLOW_LINKS),
            "Broken symlink should be deleted");
    }

    // ==================== macOS Directory Structure Tests ====================

    @Test
    public void testUninstallMacOSApplicationBundle() throws Exception {
        // Setup: Create a macOS .app bundle structure
        Files.createDirectories(testAppDir);
        Path contentsDir = testAppDir.resolve("Contents");
        Path macOSDir = contentsDir.resolve("MacOS");
        Path resourcesDir = contentsDir.resolve("Resources");
        Files.createDirectories(macOSDir);
        Files.createDirectories(resourcesDir);

        // Create bundle files
        File executable = macOSDir.resolve("TestApp").toFile();
        File infoPlist = contentsDir.resolve("Info.plist").toFile();
        File icon = resourcesDir.resolve("AppIcon.icns").toFile();

        assertTrue(executable.createNewFile());
        assertTrue(infoPlist.createNewFile());
        assertTrue(icon.createNewFile());

        // Create manifest to delete entire .app bundle
        List<InstalledDirectory> directories = Collections.singletonList(
            InstalledDirectory.builder()
                .path(testAppDir.toAbsolutePath().toString())
                .cleanup(CleanupStrategy.ALWAYS)
                .build()
        );

        UninstallManifest manifest = createManifestWithDirectories(directories);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Entire .app bundle deleted
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        assertFalse(Files.exists(testAppDir), ".app bundle should be completely removed");
        assertFalse(executable.exists(), "Executable should be removed");
        assertFalse(infoPlist.exists(), "Info.plist should be removed");
        assertFalse(icon.exists(), "Resources should be removed");
    }

    @Test
    public void testUninstallMacOSLocalBinDirectory() throws Exception {
        // Setup: Create typical ~/.local/bin structure with multiple binaries
        File bin1 = testBinDir.resolve("app-binary").toFile();
        File bin2 = testBinDir.resolve("app-helper").toFile();
        File script = testBinDir.resolve("app-script.sh").toFile();

        assertTrue(bin1.createNewFile());
        assertTrue(bin2.createNewFile());
        assertTrue(script.createNewFile());
        bin1.setExecutable(true);
        bin2.setExecutable(true);
        script.setExecutable(true);

        // Create manifest with IF_EMPTY cleanup (should not delete .local/bin itself)
        List<InstalledFile> files = Arrays.asList(
            InstalledFile.builder().path(bin1.getAbsolutePath()).type(FileType.BINARY).build(),
            InstalledFile.builder().path(bin2.getAbsolutePath()).type(FileType.BINARY).build(),
            InstalledFile.builder().path(script.getAbsolutePath()).type(FileType.SCRIPT).build()
        );

        List<InstalledDirectory> directories = Collections.singletonList(
            InstalledDirectory.builder()
                .path(testBinDir.toAbsolutePath().toString())
                .cleanup(CleanupStrategy.IF_EMPTY)
                .build()
        );

        UninstallManifest manifest = createManifestWithFilesAndDirectories(files, directories);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Files deleted, empty directory removed
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        assertFalse(bin1.exists(), "Binary 1 should be deleted");
        assertFalse(bin2.exists(), "Binary 2 should be deleted");
        assertFalse(script.exists(), "Script should be deleted");
        assertFalse(Files.exists(testBinDir), "Empty bin directory should be removed");
    }

    @Test
    public void testUninstallPreservesSharedLocalBinDirectory() throws Exception {
        // Setup: Create ~/.local/bin with app binaries and other user's binaries
        File appBinary = testBinDir.resolve("my-app").toFile();
        File otherBinary = testBinDir.resolve("other-user-tool").toFile();

        assertTrue(appBinary.createNewFile());
        assertTrue(otherBinary.createNewFile());

        // Create manifest that only references app binary, with IF_EMPTY cleanup
        List<InstalledFile> files = Collections.singletonList(
            InstalledFile.builder().path(appBinary.getAbsolutePath()).type(FileType.BINARY).build()
        );

        List<InstalledDirectory> directories = Collections.singletonList(
            InstalledDirectory.builder()
                .path(testBinDir.toAbsolutePath().toString())
                .cleanup(CleanupStrategy.IF_EMPTY)
                .build()
        );

        UninstallManifest manifest = createManifestWithFilesAndDirectories(files, directories);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: App binary deleted, but directory preserved because other binary exists
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        assertFalse(appBinary.exists(), "App binary should be deleted");
        assertTrue(otherBinary.exists(), "Other user's binary should remain");
        assertTrue(Files.exists(testBinDir), "Shared bin directory should be preserved");
    }

    // ==================== Idempotency Tests ====================

    @Test
    public void testRepeatedUninstallIsIdempotent() throws Exception {
        // Setup: Create files and shell profile
        File binary = testBinDir.resolve("app").toFile();
        File bashrc = testHomeDir.resolve(".bashrc").toFile();
        String exportLine = "export PATH=\"" + testBinDir + ":$PATH\"";

        assertTrue(binary.createNewFile());
        Files.write(bashrc.toPath(),
            ("# Config\n" + exportLine + "\n").getBytes(StandardCharsets.UTF_8));

        // Create manifest
        List<InstalledFile> files = Collections.singletonList(
            InstalledFile.builder().path(binary.getAbsolutePath()).type(FileType.BINARY).build()
        );

        UninstallManifest manifest = createManifestWithFilesAndShellProfile(
            files,
            bashrc.getAbsolutePath(),
            exportLine
        );
        manifestRepository.save(manifest);

        // Execute first uninstall
        UninstallService.UninstallResult result1 = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);
        assertTrue(result1.isSuccess(), "First uninstall should succeed");
        assertFalse(binary.exists(), "Binary should be deleted");

        // Re-save manifest and execute second uninstall (idempotency test)
        manifestRepository.save(manifest);
        UninstallService.UninstallResult result2 = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Second uninstall succeeds even though files already deleted
        assertTrue(result2.isSuccess(), "Second uninstall should succeed (idempotent)");
        assertFalse(binary.exists(), "Binary should still not exist");
    }

    @Test
    public void testPartialUninstallCanBeCompleted() throws Exception {
        // Setup: Simulate partial uninstall where some files were manually deleted
        File file1 = testBinDir.resolve("file1").toFile();
        File file2 = testBinDir.resolve("file2").toFile();
        File file3 = testBinDir.resolve("file3").toFile();

        assertTrue(file1.createNewFile());
        // file2 intentionally not created (simulating manual deletion)
        assertTrue(file3.createNewFile());

        // Create manifest with all three files
        List<InstalledFile> files = Arrays.asList(
            InstalledFile.builder().path(file1.getAbsolutePath()).type(FileType.CONFIG).build(),
            InstalledFile.builder().path(file2.getAbsolutePath()).type(FileType.CONFIG).build(),
            InstalledFile.builder().path(file3.getAbsolutePath()).type(FileType.CONFIG).build()
        );

        UninstallManifest manifest = createManifestWithFiles(files);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Uninstall completes successfully despite missing file
        assertTrue(result.isSuccess(), "Uninstall should complete successfully");
        assertFalse(file1.exists(), "File1 should be deleted");
        assertFalse(file2.exists(), "File2 should remain non-existent");
        assertFalse(file3.exists(), "File3 should be deleted");
    }

    // ==================== Full Workflow Tests ====================

    @Test
    public void testCompleteUninstallWorkflowOnMacOS() throws Exception {
        // Setup: Create a complete installation scenario

        // 1. Application files
        Path appInstallDir = testHomeDir.resolve("Library").resolve("Application Support")
            .resolve("TestApp");
        Files.createDirectories(appInstallDir);
        File jarFile = appInstallDir.resolve("app.jar").toFile();
        File configFile = appInstallDir.resolve("config.json").toFile();
        assertTrue(jarFile.createNewFile());
        assertTrue(configFile.createNewFile());

        // 2. Binary launcher in ~/.local/bin
        File launcher = testBinDir.resolve("testapp").toFile();
        assertTrue(launcher.createNewFile());
        launcher.setExecutable(true);

        // 3. Shell profile modifications
        File zshrc = testHomeDir.resolve(".zshrc").toFile();
        String exportLine = "export PATH=\"" + testBinDir + ":$PATH\"";
        Files.write(zshrc.toPath(),
            ("# User config\n" + exportLine + "\nalias ls='ls -G'\n").getBytes(StandardCharsets.UTF_8));

        // 4. User data directory (should be preserved with CONTENTS_ONLY)
        Path userDataDir = testHomeDir.resolve("Library").resolve("Application Support")
            .resolve("TestApp").resolve("userdata");
        Files.createDirectories(userDataDir);
        File userData = userDataDir.resolve("data.db").toFile();
        assertTrue(userData.createNewFile());

        // Create comprehensive manifest
        List<InstalledFile> files = Arrays.asList(
            InstalledFile.builder().path(jarFile.getAbsolutePath()).type(FileType.BINARY).build(),
            InstalledFile.builder().path(configFile.getAbsolutePath()).type(FileType.CONFIG).build(),
            InstalledFile.builder().path(launcher.getAbsolutePath()).type(FileType.BINARY).build(),
            InstalledFile.builder().path(userData.getAbsolutePath()).type(FileType.CONFIG).build()
        );

        List<InstalledDirectory> directories = Arrays.asList(
            InstalledDirectory.builder()
                .path(appInstallDir.toAbsolutePath().toString())
                .cleanup(CleanupStrategy.ALWAYS)
                .build(),
            InstalledDirectory.builder()
                .path(testBinDir.toAbsolutePath().toString())
                .cleanup(CleanupStrategy.IF_EMPTY)
                .build()
        );

        List<ShellProfileEntry> shellProfiles = Collections.singletonList(
            ShellProfileEntry.builder()
                .file(zshrc.getAbsolutePath())
                .exportLine(exportLine)
                .description("TestApp PATH")
                .build()
        );

        UninstallManifest manifest = createCompleteManifest(files, directories, shellProfiles);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify comprehensive cleanup
        assertTrue(result.isSuccess(), "Complete uninstall should succeed");

        // Files should be deleted
        assertFalse(jarFile.exists(), "JAR file should be deleted");
        assertFalse(configFile.exists(), "Config file should be deleted");
        assertFalse(launcher.exists(), "Launcher should be deleted");
        assertFalse(userData.exists(), "User data should be deleted");

        // Directories cleaned up
        assertFalse(Files.exists(appInstallDir), "App install dir should be deleted");
        assertFalse(Files.exists(testBinDir), "Empty bin dir should be deleted");

        // Shell profile cleaned
        String zshrcContent = new String(Files.readAllBytes(zshrc.toPath()), StandardCharsets.UTF_8);
        assertFalse(zshrcContent.contains(exportLine), "PATH export should be removed");
        assertTrue(zshrcContent.contains("alias ls='ls -G'"), "Other config should remain");

        // Manifest should be deleted
        assertFalse(manifestRepository.load(TEST_PACKAGE_NAME, TEST_SOURCE).isPresent(),
            "Manifest should be deleted after uninstall");
    }

    // ==================== Helper Methods ====================

    private UninstallManifest createManifestWithShellProfile(String profileFile, String exportLine) {
        List<ShellProfileEntry> shellProfiles = Collections.singletonList(
            ShellProfileEntry.builder()
                .file(profileFile)
                .exportLine(exportLine)
                .description("PATH modification")
                .build()
        );
        return createManifestWithShellProfiles(shellProfiles);
    }

    private UninstallManifest createManifestWithShellProfiles(List<ShellProfileEntry> shellProfiles) {
        PathModifications pathMods = PathModifications.builder()
            .windowsPaths(Collections.emptyList())
            .shellProfiles(shellProfiles)
            .gitBashProfiles(Collections.emptyList())
            .build();

        return UninstallManifest.builder()
            .version("1.0")
            .packageInfo(createPackageInfo())
            .files(Collections.emptyList())
            .directories(Collections.emptyList())
            .registry(createEmptyRegistryInfo())
            .pathModifications(pathMods)
            .build();
    }

    private UninstallManifest createManifestWithFiles(List<InstalledFile> files) {
        return UninstallManifest.builder()
            .version("1.0")
            .packageInfo(createPackageInfo())
            .files(files)
            .directories(Collections.emptyList())
            .registry(createEmptyRegistryInfo())
            .pathModifications(createEmptyPathModifications())
            .build();
    }

    private UninstallManifest createManifestWithDirectories(List<InstalledDirectory> directories) {
        return UninstallManifest.builder()
            .version("1.0")
            .packageInfo(createPackageInfo())
            .files(Collections.emptyList())
            .directories(directories)
            .registry(createEmptyRegistryInfo())
            .pathModifications(createEmptyPathModifications())
            .build();
    }

    private UninstallManifest createManifestWithFilesAndDirectories(
            List<InstalledFile> files,
            List<InstalledDirectory> directories) {
        return UninstallManifest.builder()
            .version("1.0")
            .packageInfo(createPackageInfo())
            .files(files)
            .directories(directories)
            .registry(createEmptyRegistryInfo())
            .pathModifications(createEmptyPathModifications())
            .build();
    }

    private UninstallManifest createManifestWithFilesAndShellProfile(
            List<InstalledFile> files,
            String profileFile,
            String exportLine) {
        List<ShellProfileEntry> shellProfiles = Collections.singletonList(
            ShellProfileEntry.builder()
                .file(profileFile)
                .exportLine(exportLine)
                .description("PATH modification")
                .build()
        );

        PathModifications pathMods = PathModifications.builder()
            .windowsPaths(Collections.emptyList())
            .shellProfiles(shellProfiles)
            .gitBashProfiles(Collections.emptyList())
            .build();

        return UninstallManifest.builder()
            .version("1.0")
            .packageInfo(createPackageInfo())
            .files(files)
            .directories(Collections.emptyList())
            .registry(createEmptyRegistryInfo())
            .pathModifications(pathMods)
            .build();
    }

    private UninstallManifest createCompleteManifest(
            List<InstalledFile> files,
            List<InstalledDirectory> directories,
            List<ShellProfileEntry> shellProfiles) {
        PathModifications pathMods = PathModifications.builder()
            .windowsPaths(Collections.emptyList())
            .shellProfiles(shellProfiles)
            .gitBashProfiles(Collections.emptyList())
            .build();

        return UninstallManifest.builder()
            .version("1.0")
            .packageInfo(createPackageInfo())
            .files(files)
            .directories(directories)
            .registry(createEmptyRegistryInfo())
            .pathModifications(pathMods)
            .build();
    }

    private PackageInfo createPackageInfo() {
        return PackageInfo.builder()
            .name(TEST_PACKAGE_NAME)
            .source(TEST_SOURCE)
            .version(TEST_VERSION)
            .fullyQualifiedName("npm." + TEST_PACKAGE_NAME)
            .architecture("arm64")
            .installedAt(Instant.now())
            .installerVersion("2.0.0")
            .build();
    }

    private RegistryInfo createEmptyRegistryInfo() {
        return RegistryInfo.builder()
            .createdKeys(Collections.emptyList())
            .modifiedValues(Collections.emptyList())
            .build();
    }

    private PathModifications createEmptyPathModifications() {
        return PathModifications.builder()
            .windowsPaths(Collections.emptyList())
            .shellProfiles(Collections.emptyList())
            .gitBashProfiles(Collections.emptyList())
            .build();
    }
}

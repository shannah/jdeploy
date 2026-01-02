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
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for UninstallService on Linux.
 *
 * These tests verify the full uninstall workflow with real file system operations,
 * focusing on Linux-specific features:
 * - Shell profile modifications (.bashrc, .zshrc, .profile, .bash_profile)
 * - Symlink handling (including broken symlinks)
 * - XDG directory structure conventions (~/.config, ~/.local/share, ~/.local/bin)
 * - Desktop entry cleanup (.desktop files)
 * - Idempotency of uninstall operations
 */
@EnabledOnOs(OS.LINUX)
public class UninstallServiceLinuxIT {

    private static final String TEST_PACKAGE_NAME = "test-linux-app";
    private static final String TEST_SOURCE = "npm";
    private static final String TEST_VERSION = "1.2.3";

    private Path testHomeDir;
    private Path testBinDir;
    private Path testConfigDir;
    private Path testDataDir;
    private UninstallService service;
    private FileUninstallManifestRepository manifestRepository;
    private InMemoryRegistryOperations registryOps;

    private String originalUserHome;

    @BeforeEach
    public void setUp() throws IOException {
        // Create temporary home directory
        testHomeDir = Files.createTempDirectory("linux-uninstall-test-home-");
        testBinDir = testHomeDir.resolve(".local").resolve("bin");
        testConfigDir = testHomeDir.resolve(".config").resolve(TEST_PACKAGE_NAME);
        testDataDir = testHomeDir.resolve(".local").resolve("share").resolve(TEST_PACKAGE_NAME);
        
        Files.createDirectories(testBinDir);
        Files.createDirectories(testConfigDir);
        Files.createDirectories(testDataDir);

        // Override user.home for testing
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", testHomeDir.toString());

        // Create services
        registryOps = new InMemoryRegistryOperations();
        manifestRepository = new FileUninstallManifestRepository(true);
        service = new UninstallService(manifestRepository, registryOps);
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }
        if (testHomeDir != null && Files.exists(testHomeDir)) {
            FileUtils.deleteDirectory(testHomeDir.toFile());
        }
    }

    // ==================== Shell Profile Tests ====================

    @Test
    public void testUninstallRemovesFromBashrc() throws Exception {
        performShellProfileTest(".bashrc");
    }

    @Test
    public void testUninstallRemovesFromZshrc() throws Exception {
        performShellProfileTest(".zshrc");
    }

    @Test
    public void testUninstallRemovesFromProfile() throws Exception {
        performShellProfileTest(".profile");
    }

    @Test
    public void testUninstallRemovesFromBashProfile() throws Exception {
        performShellProfileTest(".bash_profile");
    }

    private void performShellProfileTest(String fileName) throws Exception {
        File profileFile = testHomeDir.resolve(fileName).toFile();
        String exportLine = "export PATH=\"" + testBinDir.toAbsolutePath() + ":$PATH\"";
        Files.write(profileFile.toPath(), ("# Header\n" + exportLine + "\n# Footer\n").getBytes(StandardCharsets.UTF_8));

        UninstallManifest manifest = createManifestWithShellProfile(profileFile.getAbsolutePath(), exportLine);
        manifestRepository.save(manifest);

        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        assertTrue(result.isSuccess());
        String content = new String(Files.readAllBytes(profileFile.toPath()), StandardCharsets.UTF_8);
        assertFalse(content.contains(exportLine));
        assertTrue(content.contains("# Header"));
    }

    @Test
    public void testUninstallRemovesFromMultipleShellProfiles() throws Exception {
        File bashrc = testHomeDir.resolve(".bashrc").toFile();
        File zshrc = testHomeDir.resolve(".zshrc").toFile();
        String exportLine = "export PATH=\"" + testBinDir.toAbsolutePath() + ":$PATH\"";

        Files.write(bashrc.toPath(), (exportLine + "\n").getBytes(StandardCharsets.UTF_8));
        Files.write(zshrc.toPath(), (exportLine + "\n").getBytes(StandardCharsets.UTF_8));

        List<ShellProfileEntry> shellProfiles = Arrays.asList(
            ShellProfileEntry.builder().file(bashrc.getAbsolutePath()).exportLine(exportLine).build(),
            ShellProfileEntry.builder().file(zshrc.getAbsolutePath()).exportLine(exportLine).build()
        );

        UninstallManifest manifest = createManifestWithShellProfiles(shellProfiles);
        manifestRepository.save(manifest);

        service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        assertFalse(new String(Files.readAllBytes(bashrc.toPath())).contains(exportLine));
        assertFalse(new String(Files.readAllBytes(zshrc.toPath())).contains(exportLine));
    }

    @Test
    public void testUninstallHandlesHomeExpansionInPaths() throws Exception {
        File bashrc = testHomeDir.resolve(".bashrc").toFile();
        String exportLine = "export PATH=\"$HOME/.local/bin:$PATH\"";
        Files.write(bashrc.toPath(), (exportLine + "\n").getBytes(StandardCharsets.UTF_8));

        UninstallManifest manifest = createManifestWithShellProfile(bashrc.getAbsolutePath(), exportLine);
        manifestRepository.save(manifest);

        service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        assertFalse(new String(Files.readAllBytes(bashrc.toPath())).contains(exportLine));
    }

    // ==================== Symlink Handling Tests ====================

    @Test
    public void testUninstallDeletesSymlinks() throws Exception {
        Path target = testDataDir.resolve("app.bin");
        Files.write(target, "binary".getBytes());
        Path link = testBinDir.resolve(TEST_PACKAGE_NAME);
        Files.createSymbolicLink(link, target);

        List<InstalledFile> files = Arrays.asList(
            InstalledFile.builder().path(target.toString()).type(FileType.BINARY).build(),
            InstalledFile.builder().path(link.toString()).type(FileType.LINK).build()
        );

        manifestRepository.save(createManifestWithFiles(files));
        service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        assertFalse(Files.exists(link));
        assertFalse(Files.exists(target));
    }

    @Test
    public void testUninstallHandlesBrokenSymlinks() throws Exception {
        Path brokenLink = testBinDir.resolve("broken");
        Files.createSymbolicLink(brokenLink, testHomeDir.resolve("non-existent"));

        List<InstalledFile> files = Collections.singletonList(
            InstalledFile.builder().path(brokenLink.toString()).type(FileType.LINK).build()
        );

        manifestRepository.save(createManifestWithFiles(files));
        service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        assertFalse(Files.exists(brokenLink, java.nio.file.LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    public void testUninstallDeletesSymlinkInUsrLocalBin() throws Exception {
        Path target = testDataDir.resolve("app.jar");
        Files.write(target, "jar".getBytes());
        Path link = testBinDir.resolve(TEST_PACKAGE_NAME);
        Files.createSymbolicLink(link, target);

        List<InstalledFile> files = Collections.singletonList(
            InstalledFile.builder().path(link.toString()).type(FileType.LINK).build()
        );

        manifestRepository.save(createManifestWithFiles(files));
        service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        assertFalse(Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS));
    }

    // ==================== Linux Directory Structure Tests ====================

    @Test
    public void testUninstallDeletesLocalBinDirectory() throws Exception {
        File bin = testBinDir.resolve("app-bin").toFile();
        assertTrue(bin.createNewFile());

        List<InstalledFile> files = Collections.singletonList(
            InstalledFile.builder().path(bin.getAbsolutePath()).type(FileType.BINARY).build()
        );
        List<InstalledDirectory> dirs = Collections.singletonList(
            InstalledDirectory.builder().path(testBinDir.toString()).cleanup(CleanupStrategy.IF_EMPTY).build()
        );

        manifestRepository.save(createManifestWithFilesAndDirectories(files, dirs));
        service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        assertFalse(Files.exists(testBinDir));
    }

    @Test
    public void testUninstallPreservesSharedLocalBinDirectory() throws Exception {
        File appBin = testBinDir.resolve("app-bin").toFile();
        File otherBin = testBinDir.resolve("other-bin").toFile();
        assertTrue(appBin.createNewFile());
        assertTrue(otherBin.createNewFile());

        List<InstalledFile> files = Collections.singletonList(
            InstalledFile.builder().path(appBin.getAbsolutePath()).type(FileType.BINARY).build()
        );
        List<InstalledDirectory> dirs = Collections.singletonList(
            InstalledDirectory.builder().path(testBinDir.toString()).cleanup(CleanupStrategy.IF_EMPTY).build()
        );

        manifestRepository.save(createManifestWithFilesAndDirectories(files, dirs));
        service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        assertFalse(appBin.exists());
        assertTrue(otherBin.exists());
        assertTrue(Files.exists(testBinDir));
    }

    @Test
    public void testUninstallCleansUpXdgConfigDirectory() throws Exception {
        File config = testConfigDir.resolve("settings.json").toFile();
        assertTrue(config.createNewFile());

        List<InstalledDirectory> dirs = Collections.singletonList(
            InstalledDirectory.builder().path(testConfigDir.toString()).cleanup(CleanupStrategy.ALWAYS).build()
        );

        manifestRepository.save(createManifestWithDirectories(dirs));
        service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        assertFalse(Files.exists(testConfigDir));
    }

    @Test
    public void testUninstallCleansUpXdgDataDirectory() throws Exception {
        File data = testDataDir.resolve("data.db").toFile();
        assertTrue(data.createNewFile());

        List<InstalledDirectory> dirs = Collections.singletonList(
            InstalledDirectory.builder().path(testDataDir.toString()).cleanup(CleanupStrategy.ALWAYS).build()
        );

        manifestRepository.save(createManifestWithDirectories(dirs));
        service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        assertFalse(Files.exists(testDataDir));
    }

    @Test
    public void testUninstallWithContentsOnlyStrategy() throws Exception {
        Path workDir = testDataDir.resolve("work");
        Files.createDirectories(workDir);
        Files.write(workDir.resolve("tmp"), "tmp".getBytes());

        List<InstalledDirectory> dirs = Collections.singletonList(
            InstalledDirectory.builder().path(workDir.toString()).cleanup(CleanupStrategy.CONTENTS_ONLY).build()
        );

        manifestRepository.save(createManifestWithDirectories(dirs));
        service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        assertTrue(Files.exists(workDir));
        assertEquals(0, workDir.toFile().list().length);
    }

    // ==================== File Operations Tests ====================

    @Test
    public void testUninstallDeletesExecutableScripts() throws Exception {
        File script = testBinDir.resolve("run.sh").toFile();
        Files.write(script.toPath(), "#!/bin/sh\necho 1".getBytes());
        script.setExecutable(true);

        List<InstalledFile> files = Collections.singletonList(
            InstalledFile.builder().path(script.getAbsolutePath()).type(FileType.SCRIPT).build()
        );

        manifestRepository.save(createManifestWithFiles(files));
        service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        assertFalse(script.exists());
    }

    @Test
    public void testUninstallDeletesDesktopEntry() throws Exception {
        Path appsDir = testHomeDir.resolve(".local/share/applications");
        Files.createDirectories(appsDir);
        File desktopFile = appsDir.resolve(TEST_PACKAGE_NAME + ".desktop").toFile();
        assertTrue(desktopFile.createNewFile());

        List<InstalledFile> files = Collections.singletonList(
            InstalledFile.builder().path(desktopFile.getAbsolutePath()).type(FileType.CONFIG).build()
        );

        manifestRepository.save(createManifestWithFiles(files));
        service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        assertFalse(desktopFile.exists());
    }

    @Test
    public void testUninstallDeletesJarAndLibFiles() throws Exception {
        Path libDir = testDataDir.resolve("lib");
        Files.createDirectories(libDir);
        File jar = testDataDir.resolve("app.jar").toFile();
        File lib = libDir.resolve("dep.jar").toFile();
        assertTrue(jar.createNewFile());
        assertTrue(lib.createNewFile());

        List<InstalledFile> files = Arrays.asList(
            InstalledFile.builder().path(jar.getAbsolutePath()).type(FileType.BINARY).build(),
            InstalledFile.builder().path(lib.getAbsolutePath()).type(FileType.BINARY).build()
        );
        List<InstalledDirectory> dirs = Collections.singletonList(
            InstalledDirectory.builder().path(libDir.toString()).cleanup(CleanupStrategy.IF_EMPTY).build()
        );

        manifestRepository.save(createManifestWithFilesAndDirectories(files, dirs));
        service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        assertFalse(jar.exists());
        assertFalse(lib.exists());
        assertFalse(Files.exists(libDir));
    }

    @Test
    public void testUninstallHandlesConfigFiles() throws Exception {
        File json = testConfigDir.resolve("c.json").toFile();
        File yaml = testConfigDir.resolve("c.yaml").toFile();
        assertTrue(json.createNewFile());
        assertTrue(yaml.createNewFile());

        List<InstalledFile> files = Arrays.asList(
            InstalledFile.builder().path(json.getAbsolutePath()).type(FileType.CONFIG).build(),
            InstalledFile.builder().path(yaml.getAbsolutePath()).type(FileType.CONFIG).build()
        );

        manifestRepository.save(createManifestWithFiles(files));
        service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        assertFalse(json.exists());
        assertFalse(yaml.exists());
    }

    // ==================== Idempotency Tests ====================

    @Test
    public void testRepeatedUninstallIsIdempotent() throws Exception {
        File f = testDataDir.resolve("file").toFile();
        assertTrue(f.createNewFile());

        UninstallManifest m = createManifestWithFiles(Collections.singletonList(
            InstalledFile.builder().path(f.getAbsolutePath()).type(FileType.BINARY).build()
        ));
        
        manifestRepository.save(m);
        assertTrue(service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE).isSuccess());
        assertFalse(f.exists());

        manifestRepository.save(m);
        assertTrue(service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE).isSuccess());
    }

    @Test
    public void testPartialUninstallCanBeCompleted() throws Exception {
        File f1 = testDataDir.resolve("f1").toFile();
        File f2 = testDataDir.resolve("f2").toFile();
        assertTrue(f1.createNewFile());
        // f2 is missing

        List<InstalledFile> files = Arrays.asList(
            InstalledFile.builder().path(f1.getAbsolutePath()).type(FileType.BINARY).build(),
            InstalledFile.builder().path(f2.getAbsolutePath()).type(FileType.BINARY).build()
        );

        manifestRepository.save(createManifestWithFiles(files));
        assertTrue(service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE).isSuccess());
        assertFalse(f1.exists());
    }

    // ==================== Complete Workflow Test ====================

    @Test
    public void testCompleteUninstallWorkflowOnLinux() throws Exception {
        // Setup complex installation
        File bin = testBinDir.resolve("app").toFile();
        assertTrue(bin.createNewFile());
        
        File bashrc = testHomeDir.resolve(".bashrc").toFile();
        String exportLine = "export PATH=\"" + testBinDir.toAbsolutePath() + ":$PATH\"";
        Files.write(bashrc.toPath(), (exportLine + "\n").getBytes(StandardCharsets.UTF_8));

        Path desktopDir = testHomeDir.resolve(".local/share/applications");
        Files.createDirectories(desktopDir);
        File desktop = desktopDir.resolve("app.desktop").toFile();
        assertTrue(desktop.createNewFile());

        List<InstalledFile> files = Arrays.asList(
            InstalledFile.builder().path(bin.getAbsolutePath()).type(FileType.BINARY).build(),
            InstalledFile.builder().path(desktop.getAbsolutePath()).type(FileType.CONFIG).build()
        );
        List<InstalledDirectory> dirs = Arrays.asList(
            InstalledDirectory.builder().path(testConfigDir.toString()).cleanup(CleanupStrategy.ALWAYS).build(),
            InstalledDirectory.builder().path(testBinDir.toString()).cleanup(CleanupStrategy.IF_EMPTY).build()
        );
        List<ShellProfileEntry> shells = Collections.singletonList(
            ShellProfileEntry.builder().file(bashrc.getAbsolutePath()).exportLine(exportLine).build()
        );

        manifestRepository.save(createCompleteManifest(files, dirs, shells));
        
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);
        
        assertTrue(result.isSuccess());
        assertFalse(bin.exists());
        assertFalse(desktop.exists());
        assertFalse(Files.exists(testConfigDir));
        assertFalse(new String(Files.readAllBytes(bashrc.toPath())).contains(exportLine));
    }

    // ==================== Helper Methods ====================

    private UninstallManifest createManifestWithShellProfile(String profileFile, String exportLine) throws Exception {
        return createManifestWithShellProfiles(Collections.singletonList(
            ShellProfileEntry.builder().file(profileFile).exportLine(exportLine).build()
        ));
    }

    private UninstallManifest createManifestWithShellProfiles(List<ShellProfileEntry> shellProfiles) throws Exception {
        PathModifications pathMods = PathModifications.builder()
            .shellProfiles(shellProfiles)
            .build();
        return UninstallManifest.builder()
            .version("1.0")
            .packageInfo(createPackageInfo())
            .pathModifications(pathMods)
            .registry(createEmptyRegistryInfo())
            .build();
    }

    private UninstallManifest createManifestWithFiles(List<InstalledFile> files) {
        return UninstallManifest.builder()
            .version("1.0")
            .packageInfo(createPackageInfo())
            .files(files)
            .registry(createEmptyRegistryInfo())
            .pathModifications(createEmptyPathModifications())
            .build();
    }

    private UninstallManifest createManifestWithDirectories(List<InstalledDirectory> directories) {
        return UninstallManifest.builder()
            .version("1.0")
            .packageInfo(createPackageInfo())
            .directories(directories)
            .registry(createEmptyRegistryInfo())
            .pathModifications(createEmptyPathModifications())
            .build();
    }

    private UninstallManifest createManifestWithFilesAndDirectories(List<InstalledFile> files, List<InstalledDirectory> directories) {
        return UninstallManifest.builder()
            .version("1.0")
            .packageInfo(createPackageInfo())
            .files(files)
            .directories(directories)
            .registry(createEmptyRegistryInfo())
            .pathModifications(createEmptyPathModifications())
            .build();
    }

    private UninstallManifest createCompleteManifest(List<InstalledFile> files, List<InstalledDirectory> directories, List<ShellProfileEntry> shellProfiles) throws Exception {
        return UninstallManifest.builder()
            .version("1.0")
            .packageInfo(createPackageInfo())
            .files(files)
            .directories(directories)
            .registry(createEmptyRegistryInfo())
            .pathModifications(createEmptyPathModifications())
            .pathModifications(PathModifications.builder().shellProfiles(shellProfiles).build())
            .build();
    }

    private PackageInfo createPackageInfo() {
        return PackageInfo.builder()
            .name(TEST_PACKAGE_NAME)
            .source(TEST_SOURCE)
            .version(TEST_VERSION)
            .fullyQualifiedName("npm." + TEST_PACKAGE_NAME)
            .architecture("x86_64")
            .installedAt(Instant.now())
            .installerVersion("2.0.0")
            .build();
    }

    private RegistryInfo createEmptyRegistryInfo() {
        return RegistryInfo.builder().build();
    }

    private PathModifications createEmptyPathModifications() {
        return PathModifications.builder().build();
    }
}

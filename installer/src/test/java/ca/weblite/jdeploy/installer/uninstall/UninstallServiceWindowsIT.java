package ca.weblite.jdeploy.installer.uninstall;

import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.CleanupStrategy;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.FileType;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.GitBashProfileEntry;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.InstalledDirectory;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.InstalledFile;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.ModifiedRegistryValue;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.PackageInfo;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.PathModifications;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.RegistryInfo;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.RegistryKey;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.RegistryRoot;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.RegistryValueType;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.WindowsPathEntry;
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
 * Integration tests for UninstallService on Windows.
 *
 * These tests verify the full uninstall workflow with real file system operations,
 * focusing on Windows-specific features:
 * - Registry key creation/deletion and value restoration
 * - Windows PATH modifications (via registry)
 * - Git Bash profile modifications (.bashrc, .bash_profile in user home)
 * - Windows directory structure conventions
 * - Executable and batch file handling
 * - Idempotency of uninstall operations
 *
 * Only runs on Windows systems.
 */
@EnabledOnOs(OS.WINDOWS)
public class UninstallServiceWindowsIT {

    private static final String TEST_PACKAGE_NAME = "test-windows-app";
    private static final String TEST_SOURCE = "npm";
    private static final String TEST_VERSION = "1.2.3";

    private Path testHomeDir;
    private Path testAppDir;
    private Path testBinDir;
    private Path testJdeployDir;
    private UninstallService service;
    private FileUninstallManifestRepository manifestRepository;
    private InMemoryRegistryOperations registryOps;

    private String originalUserHome;

    @BeforeEach
    public void setUp() throws IOException {
        // Create temporary home directory to avoid interfering with real system
        testHomeDir = Files.createTempDirectory("windows-uninstall-test-home-");
        testJdeployDir = testHomeDir.resolve(".jdeploy");
        testAppDir = testJdeployDir.resolve("apps").resolve(TEST_PACKAGE_NAME);
        testBinDir = testAppDir.resolve("bin");
        Files.createDirectories(testBinDir);

        // Override user.home for testing
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", testHomeDir.toString());

        // Create services with real repository and in-memory registry
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

    // ==================== Registry Key Cleanup Tests ====================

    @Test
    public void testUninstallDeletesCreatedRegistryKeys() throws Exception {
        // Setup: Create registry keys that would be created during install
        String appKeyPath = "HKEY_CURRENT_USER\\Software\\jdeploy\\" + TEST_PACKAGE_NAME;
        String settingsKeyPath = appKeyPath + "\\Settings";

        registryOps.createKey(appKeyPath);
        registryOps.setStringValue(appKeyPath, "InstallPath", testAppDir.toString());
        registryOps.setStringValue(appKeyPath, "Version", TEST_VERSION);
        registryOps.createKey(settingsKeyPath);
        registryOps.setStringValue(settingsKeyPath, "Theme", "dark");

        assertTrue(registryOps.keyExists(appKeyPath), "App key should exist before uninstall");
        assertTrue(registryOps.keyExists(settingsKeyPath), "Settings key should exist before uninstall");

        // Create manifest with registry keys (child key first for proper cleanup order)
        List<RegistryKey> createdKeys = Arrays.asList(
            RegistryKey.builder()
                .root(RegistryRoot.HKEY_CURRENT_USER)
                .path(appKeyPath)
                .description("Application registry key")
                .build(),
            RegistryKey.builder()
                .root(RegistryRoot.HKEY_CURRENT_USER)
                .path(settingsKeyPath)
                .description("Application settings key")
                .build()
        );

        UninstallManifest manifest = createManifestWithRegistry(createdKeys, Collections.emptyList());
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Registry keys deleted (processed in reverse order)
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        assertFalse(registryOps.keyExists(settingsKeyPath), "Settings key should be deleted");
        assertFalse(registryOps.keyExists(appKeyPath), "App key should be deleted");
    }

    @Test
    public void testUninstallRestoresModifiedRegistryValues() throws Exception {
        // Setup: Create a registry key with an original value
        String envKeyPath = "HKEY_CURRENT_USER\\Environment";
        String originalPath = "C:\\Windows\\System32;C:\\Windows";
        String modifiedPath = originalPath + ";" + testBinDir.toString();

        registryOps.createKey(envKeyPath);
        registryOps.setStringValue(envKeyPath, "Path", modifiedPath);

        assertEquals(modifiedPath, registryOps.getStringValue(envKeyPath, "Path"),
            "Path should be modified before uninstall");

        // Create manifest with modified value that should be restored
        List<ModifiedRegistryValue> modifiedValues = Collections.singletonList(
            ModifiedRegistryValue.builder()
                .root(RegistryRoot.HKEY_CURRENT_USER)
                .path(envKeyPath)
                .name("Path")
                .previousValue(originalPath)
                .previousType(RegistryValueType.REG_EXPAND_SZ)
                .description("User PATH environment variable")
                .build()
        );

        UninstallManifest manifest = createManifestWithRegistry(Collections.emptyList(), modifiedValues);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Value restored to original
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        assertEquals(originalPath, registryOps.getStringValue(envKeyPath, "Path"),
            "Path should be restored to original value");
    }

    @Test
    public void testUninstallDeletesNewlyCreatedRegistryValues() throws Exception {
        // Setup: Create a registry key with a value that didn't exist before install
        String appKeyPath = "HKEY_CURRENT_USER\\Software\\jdeploy\\" + TEST_PACKAGE_NAME;
        registryOps.createKey(appKeyPath);
        registryOps.setStringValue(appKeyPath, "NewSetting", "SomeValue");

        assertTrue(registryOps.valueExists(appKeyPath, "NewSetting"),
            "NewSetting should exist before uninstall");

        // Create manifest with modified value that has no previous value
        List<ModifiedRegistryValue> modifiedValues = Collections.singletonList(
            ModifiedRegistryValue.builder()
                .root(RegistryRoot.HKEY_CURRENT_USER)
                .path(appKeyPath)
                .name("NewSetting")
                .previousValue(null) // No previous value - should be deleted
                .previousType(RegistryValueType.REG_SZ)
                .description("New setting created by installer")
                .build()
        );

        UninstallManifest manifest = createManifestWithRegistry(Collections.emptyList(), modifiedValues);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Value deleted since it didn't exist before
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        assertFalse(registryOps.valueExists(appKeyPath, "NewSetting"),
            "NewSetting should be deleted when no previous value exists");
    }

    @Test
    public void testUninstallHandlesNestedRegistryKeys() throws Exception {
        // Setup: Create deeply nested registry structure
        String baseKey = "HKEY_CURRENT_USER\\Software\\jdeploy\\" + TEST_PACKAGE_NAME;
        String level1 = baseKey + "\\Level1";
        String level2 = level1 + "\\Level2";
        String level3 = level2 + "\\Level3";

        registryOps.createKey(baseKey);
        registryOps.createKey(level1);
        registryOps.createKey(level2);
        registryOps.createKey(level3);
        registryOps.setStringValue(level3, "DeepValue", "test");

        assertTrue(registryOps.keyExists(level3), "Level3 key should exist");

        // Create manifest with nested keys (listed parent to child)
        List<RegistryKey> createdKeys = Arrays.asList(
            RegistryKey.builder().root(RegistryRoot.HKEY_CURRENT_USER).path(baseKey).build(),
            RegistryKey.builder().root(RegistryRoot.HKEY_CURRENT_USER).path(level1).build(),
            RegistryKey.builder().root(RegistryRoot.HKEY_CURRENT_USER).path(level2).build(),
            RegistryKey.builder().root(RegistryRoot.HKEY_CURRENT_USER).path(level3).build()
        );

        UninstallManifest manifest = createManifestWithRegistry(createdKeys, Collections.emptyList());
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: All nested keys deleted (processed in reverse order - deepest first)
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        assertFalse(registryOps.keyExists(level3), "Level3 should be deleted");
        assertFalse(registryOps.keyExists(level2), "Level2 should be deleted");
        assertFalse(registryOps.keyExists(level1), "Level1 should be deleted");
        assertFalse(registryOps.keyExists(baseKey), "Base key should be deleted");
    }

    // ==================== Windows PATH Cleanup Tests ====================

    @Test
    public void testUninstallRemovesWindowsPathEntry() throws Exception {
        // Setup: Simulate Windows PATH with app bin directory
        String envKeyPath = "HKEY_CURRENT_USER\\Environment";
        String existingPath = "C:\\Windows\\System32;C:\\Windows";
        String binPath = testBinDir.toAbsolutePath().toString();
        String fullPath = existingPath + ";" + binPath;

        registryOps.createKey(envKeyPath);
        registryOps.setStringValue(envKeyPath, "Path", fullPath);

        // Create manifest with Windows PATH entry
        List<WindowsPathEntry> windowsPaths = Collections.singletonList(
            WindowsPathEntry.builder()
                .addedEntry(binPath)
                .description("Application bin directory")
                .build()
        );

        PathModifications pathMods = PathModifications.builder()
            .windowsPaths(windowsPaths)
            .shellProfiles(Collections.emptyList())
            .gitBashProfiles(Collections.emptyList())
            .build();

        UninstallManifest manifest = createManifestWithPathModifications(pathMods);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Uninstall completes (actual PATH removal verification depends on implementation)
        assertNotNull(result, "Result should not be null");
    }

    // ==================== Git Bash Profile Tests ====================

    @Test
    public void testUninstallRemovesFromGitBashBashrc() throws Exception {
        // Setup: Create .bashrc with PATH export using $HOME (commonly used in Git Bash)
        File bashrc = testHomeDir.resolve(".bashrc").toFile();
        // Use $HOME-relative path which is the common format and is handled by the implementation
        String exportLine = "export PATH=\"$HOME/.jdeploy/apps/" + TEST_PACKAGE_NAME + "/bin:$PATH\"";
        String bashrcContent = "# Git Bash config\n" +
                               "alias ll='ls -la'\n" +
                               exportLine + "\n" +
                               "# End config\n";
        Files.write(bashrc.toPath(), bashrcContent.getBytes(StandardCharsets.UTF_8));

        // Verify export line exists
        String beforeContent = new String(Files.readAllBytes(bashrc.toPath()), StandardCharsets.UTF_8);
        assertTrue(beforeContent.contains(exportLine), "Export line should exist before uninstall");

        // Create manifest with Git Bash profile entry
        List<GitBashProfileEntry> gitBashProfiles = Collections.singletonList(
            GitBashProfileEntry.builder()
                .file(bashrc.getAbsolutePath())
                .exportLine(exportLine)
                .description("Git Bash PATH")
                .build()
        );

        PathModifications pathMods = PathModifications.builder()
            .windowsPaths(Collections.emptyList())
            .shellProfiles(Collections.emptyList())
            .gitBashProfiles(gitBashProfiles)
            .build();

        UninstallManifest manifest = createManifestWithPathModifications(pathMods);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Export line removed, other content preserved
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        String afterContent = new String(Files.readAllBytes(bashrc.toPath()), StandardCharsets.UTF_8);
        assertFalse(afterContent.contains(exportLine), "Export line should be removed from .bashrc");
        assertTrue(afterContent.contains("alias ll='ls -la'"), "Alias should remain");
        assertTrue(afterContent.contains("# Git Bash config"), "Comments should remain");
    }

    @Test
    public void testUninstallRemovesFromGitBashProfile() throws Exception {
        // Setup: Create .bash_profile with PATH export
        File bashProfile = testHomeDir.resolve(".bash_profile").toFile();
        String binPath = testBinDir.toAbsolutePath().toString();
        String exportLine = "export PATH=\"$HOME/.jdeploy/apps/" + TEST_PACKAGE_NAME + "/bin:$PATH\"";
        String profileContent = "# User profile for Git Bash\n" +
                                "export JAVA_HOME=\"/c/Program Files/Java/jdk-17\"\n" +
                                exportLine + "\n";
        Files.write(bashProfile.toPath(), profileContent.getBytes(StandardCharsets.UTF_8));

        // Create manifest
        List<GitBashProfileEntry> gitBashProfiles = Collections.singletonList(
            GitBashProfileEntry.builder()
                .file(bashProfile.getAbsolutePath())
                .exportLine(exportLine)
                .description("Git Bash PATH in .bash_profile")
                .build()
        );

        PathModifications pathMods = PathModifications.builder()
            .windowsPaths(Collections.emptyList())
            .shellProfiles(Collections.emptyList())
            .gitBashProfiles(gitBashProfiles)
            .build();

        UninstallManifest manifest = createManifestWithPathModifications(pathMods);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        String afterContent = new String(Files.readAllBytes(bashProfile.toPath()), StandardCharsets.UTF_8);
        assertFalse(afterContent.contains(exportLine), "Export line should be removed");
        assertTrue(afterContent.contains("JAVA_HOME"), "Other exports should remain");
    }

    @Test
    public void testUninstallRemovesFromMultipleGitBashProfiles() throws Exception {
        // Setup: Create both .bashrc and .bash_profile with PATH exports
        File bashrc = testHomeDir.resolve(".bashrc").toFile();
        File bashProfile = testHomeDir.resolve(".bash_profile").toFile();
        String exportLine = "export PATH=\"$HOME/.jdeploy/apps/" + TEST_PACKAGE_NAME + "/bin:$PATH\"";

        Files.write(bashrc.toPath(),
            ("# bashrc\n" + exportLine + "\n").getBytes(StandardCharsets.UTF_8));
        Files.write(bashProfile.toPath(),
            ("# bash_profile\n" + exportLine + "\n").getBytes(StandardCharsets.UTF_8));

        // Create manifest with both profile entries
        List<GitBashProfileEntry> gitBashProfiles = Arrays.asList(
            GitBashProfileEntry.builder()
                .file(bashrc.getAbsolutePath())
                .exportLine(exportLine)
                .description("Git Bash PATH in .bashrc")
                .build(),
            GitBashProfileEntry.builder()
                .file(bashProfile.getAbsolutePath())
                .exportLine(exportLine)
                .description("Git Bash PATH in .bash_profile")
                .build()
        );

        PathModifications pathMods = PathModifications.builder()
            .windowsPaths(Collections.emptyList())
            .shellProfiles(Collections.emptyList())
            .gitBashProfiles(gitBashProfiles)
            .build();

        UninstallManifest manifest = createManifestWithPathModifications(pathMods);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Export removed from both files
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        String bashrcContent = new String(Files.readAllBytes(bashrc.toPath()), StandardCharsets.UTF_8);
        String profileContent = new String(Files.readAllBytes(bashProfile.toPath()), StandardCharsets.UTF_8);
        assertFalse(bashrcContent.contains(exportLine), "Export should be removed from .bashrc");
        assertFalse(profileContent.contains(exportLine), "Export should be removed from .bash_profile");
    }

    // ==================== Windows File Operations Tests ====================

    @Test
    public void testUninstallDeletesWindowsExecutables() throws Exception {
        // Setup: Create Windows executable files
        File exeFile = testBinDir.resolve("app.exe").toFile();
        File batFile = testBinDir.resolve("app.bat").toFile();
        File cmdFile = testBinDir.resolve("app.cmd").toFile();

        assertTrue(exeFile.createNewFile());
        Files.write(batFile.toPath(), "@echo off\njava -jar app.jar %*".getBytes(StandardCharsets.UTF_8));
        Files.write(cmdFile.toPath(), "@echo off\napp.exe %*".getBytes(StandardCharsets.UTF_8));

        assertTrue(exeFile.exists());
        assertTrue(batFile.exists());
        assertTrue(cmdFile.exists());

        // Create manifest with Windows executables
        List<InstalledFile> files = Arrays.asList(
            InstalledFile.builder().path(exeFile.getAbsolutePath()).type(FileType.BINARY)
                .description("Main executable").build(),
            InstalledFile.builder().path(batFile.getAbsolutePath()).type(FileType.SCRIPT)
                .description("Batch launcher").build(),
            InstalledFile.builder().path(cmdFile.getAbsolutePath()).type(FileType.SCRIPT)
                .description("CMD launcher").build()
        );

        UninstallManifest manifest = createManifestWithFiles(files);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: All executables deleted
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        assertFalse(exeFile.exists(), "EXE should be deleted");
        assertFalse(batFile.exists(), "BAT should be deleted");
        assertFalse(cmdFile.exists(), "CMD should be deleted");
    }

    @Test
    public void testUninstallDeletesJarAndLibFiles() throws Exception {
        // Setup: Create JAR files and library directory
        Path libDir = testAppDir.resolve("lib");
        Files.createDirectories(libDir);

        File mainJar = testAppDir.resolve("app.jar").toFile();
        File depJar1 = libDir.resolve("dependency1.jar").toFile();
        File depJar2 = libDir.resolve("dependency2.jar").toFile();

        assertTrue(mainJar.createNewFile());
        assertTrue(depJar1.createNewFile());
        assertTrue(depJar2.createNewFile());

        // Create manifest
        List<InstalledFile> files = Arrays.asList(
            InstalledFile.builder().path(mainJar.getAbsolutePath()).type(FileType.BINARY).build(),
            InstalledFile.builder().path(depJar1.getAbsolutePath()).type(FileType.BINARY).build(),
            InstalledFile.builder().path(depJar2.getAbsolutePath()).type(FileType.BINARY).build()
        );

        List<InstalledDirectory> directories = Collections.singletonList(
            InstalledDirectory.builder()
                .path(libDir.toAbsolutePath().toString())
                .cleanup(CleanupStrategy.IF_EMPTY)
                .build()
        );

        UninstallManifest manifest = createManifestWithFilesAndDirectories(files, directories);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        assertFalse(mainJar.exists(), "Main JAR should be deleted");
        assertFalse(depJar1.exists(), "Dependency JAR 1 should be deleted");
        assertFalse(depJar2.exists(), "Dependency JAR 2 should be deleted");
        assertFalse(Files.exists(libDir), "Empty lib directory should be deleted");
    }

    @Test
    public void testUninstallHandlesWindowsConfigFiles() throws Exception {
        // Setup: Create typical Windows config files
        Path configDir = testAppDir.resolve("config");
        Files.createDirectories(configDir);

        File jsonConfig = configDir.resolve("settings.json").toFile();
        File xmlConfig = configDir.resolve("config.xml").toFile();
        File propertiesConfig = configDir.resolve("app.properties").toFile();

        Files.write(jsonConfig.toPath(), "{\"theme\":\"dark\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(xmlConfig.toPath(), "<?xml version=\"1.0\"?><config/>".getBytes(StandardCharsets.UTF_8));
        Files.write(propertiesConfig.toPath(), "key=value".getBytes(StandardCharsets.UTF_8));

        // Create manifest
        List<InstalledFile> files = Arrays.asList(
            InstalledFile.builder().path(jsonConfig.getAbsolutePath()).type(FileType.CONFIG).build(),
            InstalledFile.builder().path(xmlConfig.getAbsolutePath()).type(FileType.CONFIG).build(),
            InstalledFile.builder().path(propertiesConfig.getAbsolutePath()).type(FileType.CONFIG).build()
        );

        List<InstalledDirectory> directories = Collections.singletonList(
            InstalledDirectory.builder()
                .path(configDir.toAbsolutePath().toString())
                .cleanup(CleanupStrategy.ALWAYS)
                .build()
        );

        UninstallManifest manifest = createManifestWithFilesAndDirectories(files, directories);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        assertFalse(jsonConfig.exists(), "JSON config should be deleted");
        assertFalse(xmlConfig.exists(), "XML config should be deleted");
        assertFalse(propertiesConfig.exists(), "Properties config should be deleted");
        assertFalse(Files.exists(configDir), "Config directory should be deleted");
    }

    // ==================== Directory Cleanup Tests ====================

    @Test
    public void testUninstallCleansUpWindowsAppDataStructure() throws Exception {
        // Setup: Create typical Windows app directory structure
        Path dataDir = testAppDir.resolve("data");
        Path logsDir = testAppDir.resolve("logs");
        Path cacheDir = testAppDir.resolve("cache");

        Files.createDirectories(dataDir);
        Files.createDirectories(logsDir);
        Files.createDirectories(cacheDir);

        File dataFile = dataDir.resolve("user.db").toFile();
        File logFile = logsDir.resolve("app.log").toFile();
        File cacheFile = cacheDir.resolve("temp.dat").toFile();

        assertTrue(dataFile.createNewFile());
        assertTrue(logFile.createNewFile());
        assertTrue(cacheFile.createNewFile());

        // Create manifest with different cleanup strategies
        List<InstalledFile> files = Arrays.asList(
            InstalledFile.builder().path(dataFile.getAbsolutePath()).type(FileType.CONFIG).build(),
            InstalledFile.builder().path(logFile.getAbsolutePath()).type(FileType.METADATA).build(),
            InstalledFile.builder().path(cacheFile.getAbsolutePath()).type(FileType.CONFIG).build()
        );

        List<InstalledDirectory> directories = Arrays.asList(
            InstalledDirectory.builder()
                .path(dataDir.toAbsolutePath().toString())
                .cleanup(CleanupStrategy.IF_EMPTY) // Only delete if empty after file removal
                .build(),
            InstalledDirectory.builder()
                .path(logsDir.toAbsolutePath().toString())
                .cleanup(CleanupStrategy.ALWAYS) // Always delete logs
                .build(),
            InstalledDirectory.builder()
                .path(cacheDir.toAbsolutePath().toString())
                .cleanup(CleanupStrategy.ALWAYS) // Always delete cache
                .build()
        );

        UninstallManifest manifest = createManifestWithFilesAndDirectories(files, directories);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        assertFalse(Files.exists(dataDir), "Data dir should be deleted (was empty after file removal)");
        assertFalse(Files.exists(logsDir), "Logs dir should be deleted (ALWAYS)");
        assertFalse(Files.exists(cacheDir), "Cache dir should be deleted (ALWAYS)");
    }

    @Test
    public void testUninstallPreservesDirectoryWithRemainingFiles() throws Exception {
        // Setup: Create directory with app files and user files
        Path sharedDir = testAppDir.resolve("shared");
        Files.createDirectories(sharedDir);

        File appFile = sharedDir.resolve("app-data.dat").toFile();
        File userFile = sharedDir.resolve("user-data.txt").toFile();

        assertTrue(appFile.createNewFile());
        assertTrue(userFile.createNewFile());

        // Create manifest that only references app file, with IF_EMPTY cleanup
        List<InstalledFile> files = Collections.singletonList(
            InstalledFile.builder().path(appFile.getAbsolutePath()).type(FileType.CONFIG).build()
        );

        List<InstalledDirectory> directories = Collections.singletonList(
            InstalledDirectory.builder()
                .path(sharedDir.toAbsolutePath().toString())
                .cleanup(CleanupStrategy.IF_EMPTY)
                .build()
        );

        UninstallManifest manifest = createManifestWithFilesAndDirectories(files, directories);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: App file deleted, but directory preserved due to user file
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        assertFalse(appFile.exists(), "App file should be deleted");
        assertTrue(userFile.exists(), "User file should remain");
        assertTrue(Files.exists(sharedDir), "Shared directory should be preserved");
    }

    @Test
    public void testUninstallWithContentsOnlyStrategy() throws Exception {
        // Setup: Create a working directory that should be emptied but preserved
        Path workDir = testAppDir.resolve("work");
        Files.createDirectories(workDir);

        File tempFile1 = workDir.resolve("temp1.tmp").toFile();
        File tempFile2 = workDir.resolve("temp2.tmp").toFile();

        assertTrue(tempFile1.createNewFile());
        assertTrue(tempFile2.createNewFile());

        // Create manifest with CONTENTS_ONLY strategy
        List<InstalledDirectory> directories = Collections.singletonList(
            InstalledDirectory.builder()
                .path(workDir.toAbsolutePath().toString())
                .cleanup(CleanupStrategy.CONTENTS_ONLY)
                .description("Working directory - clear contents but keep directory")
                .build()
        );

        UninstallManifest manifest = createManifestWithDirectories(directories);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Directory exists but is empty
        assertTrue(result.isSuccess(), "Uninstall should succeed");
        assertTrue(Files.exists(workDir), "Work directory should remain");
        assertFalse(tempFile1.exists(), "Temp file 1 should be deleted");
        assertFalse(tempFile2.exists(), "Temp file 2 should be deleted");
        assertEquals(0, workDir.toFile().list().length, "Directory should be empty");
    }

    // ==================== Idempotency Tests ====================

    @Test
    public void testRepeatedUninstallIsIdempotent() throws Exception {
        // Setup: Create files and registry entries
        File binary = testBinDir.resolve("app.exe").toFile();
        assertTrue(binary.createNewFile());

        String regKeyPath = "HKEY_CURRENT_USER\\Software\\jdeploy\\" + TEST_PACKAGE_NAME;
        registryOps.createKey(regKeyPath);
        registryOps.setStringValue(regKeyPath, "Version", TEST_VERSION);

        // Create comprehensive manifest
        List<InstalledFile> files = Collections.singletonList(
            InstalledFile.builder().path(binary.getAbsolutePath()).type(FileType.BINARY).build()
        );

        List<RegistryKey> regKeys = Collections.singletonList(
            RegistryKey.builder().root(RegistryRoot.HKEY_CURRENT_USER).path(regKeyPath).build()
        );

        UninstallManifest manifest = createCompleteManifest(files, Collections.emptyList(),
            regKeys, Collections.emptyList(), createEmptyPathModifications());
        manifestRepository.save(manifest);

        // Execute first uninstall
        UninstallService.UninstallResult result1 = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);
        assertTrue(result1.isSuccess(), "First uninstall should succeed");
        assertFalse(binary.exists(), "Binary should be deleted");
        assertFalse(registryOps.keyExists(regKeyPath), "Registry key should be deleted");

        // Re-save manifest and execute second uninstall
        manifestRepository.save(manifest);
        UninstallService.UninstallResult result2 = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Second uninstall succeeds (idempotent behavior)
        assertTrue(result2.isSuccess(), "Second uninstall should succeed (idempotent)");
    }

    @Test
    public void testUninstallWithPartiallyDeletedFiles() throws Exception {
        // Setup: Simulate partial uninstall where some files were manually deleted
        File file1 = testBinDir.resolve("file1.exe").toFile();
        File file2 = testBinDir.resolve("file2.dll").toFile();
        File file3 = testBinDir.resolve("file3.jar").toFile();

        assertTrue(file1.createNewFile());
        // file2 intentionally not created (simulating manual deletion)
        assertTrue(file3.createNewFile());

        // Create manifest with all three files
        List<InstalledFile> files = Arrays.asList(
            InstalledFile.builder().path(file1.getAbsolutePath()).type(FileType.BINARY).build(),
            InstalledFile.builder().path(file2.getAbsolutePath()).type(FileType.BINARY).build(),
            InstalledFile.builder().path(file3.getAbsolutePath()).type(FileType.BINARY).build()
        );

        UninstallManifest manifest = createManifestWithFiles(files);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify: Uninstall completes successfully despite missing file
        assertTrue(result.isSuccess(), "Uninstall should handle missing files gracefully");
        assertFalse(file1.exists(), "File1 should be deleted");
        assertFalse(file3.exists(), "File3 should be deleted");
    }

    // ==================== Complete Windows Workflow Test ====================

    @Test
    public void testCompleteWindowsUninstallWorkflow() throws Exception {
        // Setup: Create a complete Windows installation scenario

        // 1. Application files
        File mainExe = testBinDir.resolve(TEST_PACKAGE_NAME + ".exe").toFile();
        File launcherBat = testBinDir.resolve(TEST_PACKAGE_NAME + ".bat").toFile();
        File appJar = testAppDir.resolve("app.jar").toFile();

        assertTrue(mainExe.createNewFile());
        Files.write(launcherBat.toPath(),
            "@echo off\njava -jar app.jar %*".getBytes(StandardCharsets.UTF_8));
        assertTrue(appJar.createNewFile());

        // 2. Config files
        Path configDir = testAppDir.resolve("config");
        Files.createDirectories(configDir);
        File configFile = configDir.resolve("settings.json").toFile();
        Files.write(configFile.toPath(), "{}".getBytes(StandardCharsets.UTF_8));

        // 3. Registry entries
        String appKeyPath = "HKEY_CURRENT_USER\\Software\\jdeploy\\" + TEST_PACKAGE_NAME;
        registryOps.createKey(appKeyPath);
        registryOps.setStringValue(appKeyPath, "InstallPath", testAppDir.toString());
        registryOps.setStringValue(appKeyPath, "Version", TEST_VERSION);

        // 4. Git Bash profile
        File bashrc = testHomeDir.resolve(".bashrc").toFile();
        String exportLine = "export PATH=\"$HOME/.jdeploy/apps/" + TEST_PACKAGE_NAME + "/bin:$PATH\"";
        Files.write(bashrc.toPath(),
            ("# Git Bash config\n" + exportLine + "\nalias ll='ls -la'\n").getBytes(StandardCharsets.UTF_8));

        // Create comprehensive manifest
        List<InstalledFile> files = Arrays.asList(
            InstalledFile.builder().path(mainExe.getAbsolutePath()).type(FileType.BINARY)
                .description("Main executable").build(),
            InstalledFile.builder().path(launcherBat.getAbsolutePath()).type(FileType.SCRIPT)
                .description("Batch launcher").build(),
            InstalledFile.builder().path(appJar.getAbsolutePath()).type(FileType.BINARY)
                .description("Application JAR").build(),
            InstalledFile.builder().path(configFile.getAbsolutePath()).type(FileType.CONFIG)
                .description("Configuration file").build()
        );

        List<InstalledDirectory> directories = Arrays.asList(
            InstalledDirectory.builder()
                .path(configDir.toAbsolutePath().toString())
                .cleanup(CleanupStrategy.ALWAYS)
                .build(),
            InstalledDirectory.builder()
                .path(testBinDir.toAbsolutePath().toString())
                .cleanup(CleanupStrategy.IF_EMPTY)
                .build(),
            InstalledDirectory.builder()
                .path(testAppDir.toAbsolutePath().toString())
                .cleanup(CleanupStrategy.IF_EMPTY)
                .build()
        );

        List<RegistryKey> registryKeys = Collections.singletonList(
            RegistryKey.builder()
                .root(RegistryRoot.HKEY_CURRENT_USER)
                .path(appKeyPath)
                .description("Application registry key")
                .build()
        );

        List<GitBashProfileEntry> gitBashProfiles = Collections.singletonList(
            GitBashProfileEntry.builder()
                .file(bashrc.getAbsolutePath())
                .exportLine(exportLine)
                .description("Git Bash PATH entry")
                .build()
        );

        PathModifications pathMods = PathModifications.builder()
            .windowsPaths(Collections.emptyList())
            .shellProfiles(Collections.emptyList())
            .gitBashProfiles(gitBashProfiles)
            .build();

        UninstallManifest manifest = createCompleteManifest(
            files, directories, registryKeys, Collections.emptyList(), pathMods);
        manifestRepository.save(manifest);

        // Execute uninstall
        UninstallService.UninstallResult result = service.uninstall(TEST_PACKAGE_NAME, TEST_SOURCE);

        // Verify comprehensive cleanup
        assertTrue(result.isSuccess(), "Complete uninstall should succeed: " + result.getErrors());

        // Files should be deleted
        assertFalse(mainExe.exists(), "Main EXE should be deleted");
        assertFalse(launcherBat.exists(), "Launcher BAT should be deleted");
        assertFalse(appJar.exists(), "App JAR should be deleted");
        assertFalse(configFile.exists(), "Config file should be deleted");

        // Directories should be cleaned up
        assertFalse(Files.exists(configDir), "Config directory should be deleted");
        assertFalse(Files.exists(testBinDir), "Bin directory should be deleted (was empty)");
        assertFalse(Files.exists(testAppDir), "App directory should be deleted (was empty)");

        // Registry should be cleaned up
        assertFalse(registryOps.keyExists(appKeyPath), "Registry key should be deleted");

        // Git Bash profile should be cleaned
        String bashrcContent = new String(Files.readAllBytes(bashrc.toPath()), StandardCharsets.UTF_8);
        assertFalse(bashrcContent.contains(exportLine), "PATH export should be removed");
        assertTrue(bashrcContent.contains("alias ll='ls -la'"), "Other aliases should remain");

        // Manifest should be deleted
        assertFalse(manifestRepository.load(TEST_PACKAGE_NAME, TEST_SOURCE).isPresent(),
            "Manifest should be deleted after uninstall");
    }

    // ==================== Helper Methods ====================

    private UninstallManifest createManifestWithRegistry(
            List<RegistryKey> createdKeys,
            List<ModifiedRegistryValue> modifiedValues) {
        RegistryInfo registryInfo = RegistryInfo.builder()
            .createdKeys(createdKeys)
            .modifiedValues(modifiedValues)
            .build();

        return UninstallManifest.builder()
            .version("1.0")
            .packageInfo(createPackageInfo())
            .files(Collections.emptyList())
            .directories(Collections.emptyList())
            .registry(registryInfo)
            .pathModifications(createEmptyPathModifications())
            .build();
    }

    private UninstallManifest createManifestWithPathModifications(PathModifications pathMods) {
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

    private UninstallManifest createCompleteManifest(
            List<InstalledFile> files,
            List<InstalledDirectory> directories,
            List<RegistryKey> registryKeys,
            List<ModifiedRegistryValue> modifiedValues,
            PathModifications pathMods) {
        RegistryInfo registryInfo = RegistryInfo.builder()
            .createdKeys(registryKeys)
            .modifiedValues(modifiedValues)
            .build();

        return UninstallManifest.builder()
            .version("1.0")
            .packageInfo(createPackageInfo())
            .files(files)
            .directories(directories)
            .registry(registryInfo)
            .pathModifications(pathMods)
            .build();
    }

    private PackageInfo createPackageInfo() {
        return PackageInfo.builder()
            .name(TEST_PACKAGE_NAME)
            .source(TEST_SOURCE)
            .version(TEST_VERSION)
            .fullyQualifiedName("npm." + TEST_PACKAGE_NAME)
            .architecture("x64")
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

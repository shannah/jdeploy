package ca.weblite.jdeploy.installer.uninstall.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the UninstallManifest domain model.
 * Tests immutability, builder pattern, equality, and hash code behavior.
 */
public class UninstallManifestTest {

    private static final String MANIFEST_VERSION = "1.0";
    private static final String PACKAGE_NAME = "testapp";
    private static final String PACKAGE_SOURCE = "https://github.com/user/testapp";
    private static final String PACKAGE_VERSION = "1.2.3";
    private static final String FQPN = "356a192b7913b04c54574d18c28d46e6.testapp";
    private static final String ARCHITECTURE = "x64";
    private static final Instant INSTALLED_AT = Instant.parse("2024-01-15T10:30:45Z");
    private static final String INSTALLER_VERSION = "2.1.0";

    @Test
    public void testPackageInfoBuilder() {
        UninstallManifest.PackageInfo packageInfo = UninstallManifest.PackageInfo.builder()
            .name(PACKAGE_NAME)
            .source(PACKAGE_SOURCE)
            .version(PACKAGE_VERSION)
            .fullyQualifiedName(FQPN)
            .architecture(ARCHITECTURE)
            .installedAt(INSTALLED_AT)
            .installerVersion(INSTALLER_VERSION)
            .build();

        assertEquals(PACKAGE_NAME, packageInfo.getName());
        assertEquals(PACKAGE_SOURCE, packageInfo.getSource());
        assertEquals(PACKAGE_VERSION, packageInfo.getVersion());
        assertEquals(FQPN, packageInfo.getFullyQualifiedName());
        assertEquals(ARCHITECTURE, packageInfo.getArchitecture());
        assertEquals(INSTALLED_AT, packageInfo.getInstalledAt());
        assertEquals(INSTALLER_VERSION, packageInfo.getInstallerVersion());
    }

    @Test
    public void testPackageInfoEquality() {
        UninstallManifest.PackageInfo info1 = createPackageInfo();
        UninstallManifest.PackageInfo info2 = createPackageInfo();
        UninstallManifest.PackageInfo info3 = UninstallManifest.PackageInfo.builder()
            .name("differentapp")
            .source(PACKAGE_SOURCE)
            .version(PACKAGE_VERSION)
            .fullyQualifiedName("different.fqpn")
            .architecture(ARCHITECTURE)
            .installedAt(INSTALLED_AT)
            .installerVersion(INSTALLER_VERSION)
            .build();

        assertEquals(info1, info2);
        assertEquals(info1.hashCode(), info2.hashCode());
        assertNotEquals(info1, info3);
        assertNotEquals(info1.hashCode(), info3.hashCode());
    }

    @Test
    public void testInstalledFileBuilder() {
        UninstallManifest.InstalledFile file = UninstallManifest.InstalledFile.builder()
            .path("${JDEPLOY_HOME}/apps/testapp/testapp.exe")
            .type(UninstallManifest.FileType.BINARY)
            .description("Main application executable")
            .build();

        assertEquals("${JDEPLOY_HOME}/apps/testapp/testapp.exe", file.getPath());
        assertEquals(UninstallManifest.FileType.BINARY, file.getType());
        assertEquals("Main application executable", file.getDescription());
    }

    @Test
    public void testInstalledFileEquality() {
        UninstallManifest.InstalledFile file1 = createInstalledFile();
        UninstallManifest.InstalledFile file2 = createInstalledFile();
        UninstallManifest.InstalledFile file3 = UninstallManifest.InstalledFile.builder()
            .path("${JDEPLOY_HOME}/apps/testapp/different.exe")
            .type(UninstallManifest.FileType.BINARY)
            .description("Different file")
            .build();

        assertEquals(file1, file2);
        assertEquals(file1.hashCode(), file2.hashCode());
        assertNotEquals(file1, file3);
    }

    @Test
    public void testFileTypeEnum() {
        assertEquals("binary", UninstallManifest.FileType.BINARY.getValue());
        assertEquals("script", UninstallManifest.FileType.SCRIPT.getValue());
        assertEquals("link", UninstallManifest.FileType.LINK.getValue());
        assertEquals("config", UninstallManifest.FileType.CONFIG.getValue());
        assertEquals("icon", UninstallManifest.FileType.ICON.getValue());
        assertEquals("metadata", UninstallManifest.FileType.METADATA.getValue());

        assertEquals(UninstallManifest.FileType.BINARY, UninstallManifest.FileType.fromValue("binary"));
        assertEquals(UninstallManifest.FileType.SCRIPT, UninstallManifest.FileType.fromValue("script"));
    }

    @Test
    public void testFileTypeEnumInvalidValue() {
        assertThrows(IllegalArgumentException.class, () ->
            UninstallManifest.FileType.fromValue("invalid"));
    }

    @Test
    public void testInstalledDirectoryBuilder() {
        UninstallManifest.InstalledDirectory dir = UninstallManifest.InstalledDirectory.builder()
            .path("${JDEPLOY_HOME}/apps/testapp")
            .cleanup(UninstallManifest.CleanupStrategy.ALWAYS)
            .description("Application directory")
            .build();

        assertEquals("${JDEPLOY_HOME}/apps/testapp", dir.getPath());
        assertEquals(UninstallManifest.CleanupStrategy.ALWAYS, dir.getCleanup());
        assertEquals("Application directory", dir.getDescription());
    }

    @Test
    public void testCleanupStrategyEnum() {
        assertEquals("always", UninstallManifest.CleanupStrategy.ALWAYS.getValue());
        assertEquals("ifEmpty", UninstallManifest.CleanupStrategy.IF_EMPTY.getValue());
        assertEquals("contentsOnly", UninstallManifest.CleanupStrategy.CONTENTS_ONLY.getValue());

        assertEquals(UninstallManifest.CleanupStrategy.ALWAYS,
            UninstallManifest.CleanupStrategy.fromValue("always"));
        assertEquals(UninstallManifest.CleanupStrategy.IF_EMPTY,
            UninstallManifest.CleanupStrategy.fromValue("ifEmpty"));
        assertEquals(UninstallManifest.CleanupStrategy.CONTENTS_ONLY,
            UninstallManifest.CleanupStrategy.fromValue("contentsOnly"));
    }

    @Test
    public void testInstalledDirectoryEquality() {
        UninstallManifest.InstalledDirectory dir1 = createInstalledDirectory();
        UninstallManifest.InstalledDirectory dir2 = createInstalledDirectory();
        UninstallManifest.InstalledDirectory dir3 = UninstallManifest.InstalledDirectory.builder()
            .path("${JDEPLOY_HOME}/different")
            .cleanup(UninstallManifest.CleanupStrategy.IF_EMPTY)
            .description("Different directory")
            .build();

        assertEquals(dir1, dir2);
        assertEquals(dir1.hashCode(), dir2.hashCode());
        assertNotEquals(dir1, dir3);
    }

    @Test
    public void testRegistryRootEnum() {
        assertEquals("HKEY_CURRENT_USER", UninstallManifest.RegistryRoot.HKEY_CURRENT_USER.getValue());
        assertEquals("HKEY_LOCAL_MACHINE", UninstallManifest.RegistryRoot.HKEY_LOCAL_MACHINE.getValue());

        assertEquals(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER,
            UninstallManifest.RegistryRoot.fromValue("HKEY_CURRENT_USER"));
        assertEquals(UninstallManifest.RegistryRoot.HKEY_LOCAL_MACHINE,
            UninstallManifest.RegistryRoot.fromValue("HKEY_LOCAL_MACHINE"));
    }

    @Test
    public void testRegistryKeyBuilder() {
        UninstallManifest.RegistryKey key = UninstallManifest.RegistryKey.builder()
            .root(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER)
            .path("Software\\jdeploy\\testapp")
            .description("Application registry key")
            .build();

        assertEquals(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER, key.getRoot());
        assertEquals("Software\\jdeploy\\testapp", key.getPath());
        assertEquals("Application registry key", key.getDescription());
    }

    @Test
    public void testRegistryKeyEquality() {
        UninstallManifest.RegistryKey key1 = createRegistryKey();
        UninstallManifest.RegistryKey key2 = createRegistryKey();
        UninstallManifest.RegistryKey key3 = UninstallManifest.RegistryKey.builder()
            .root(UninstallManifest.RegistryRoot.HKEY_LOCAL_MACHINE)
            .path("Software\\different")
            .description("Different key")
            .build();

        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
        assertNotEquals(key1, key3);
    }

    @Test
    public void testRegistryValueTypeEnum() {
        assertEquals("REG_SZ", UninstallManifest.RegistryValueType.REG_SZ.getValue());
        assertEquals("REG_EXPAND_SZ", UninstallManifest.RegistryValueType.REG_EXPAND_SZ.getValue());
        assertEquals("REG_DWORD", UninstallManifest.RegistryValueType.REG_DWORD.getValue());
        assertEquals("REG_QWORD", UninstallManifest.RegistryValueType.REG_QWORD.getValue());
        assertEquals("REG_BINARY", UninstallManifest.RegistryValueType.REG_BINARY.getValue());
        assertEquals("REG_MULTI_SZ", UninstallManifest.RegistryValueType.REG_MULTI_SZ.getValue());

        assertEquals(UninstallManifest.RegistryValueType.REG_SZ,
            UninstallManifest.RegistryValueType.fromValue("REG_SZ"));
        assertEquals(UninstallManifest.RegistryValueType.REG_EXPAND_SZ,
            UninstallManifest.RegistryValueType.fromValue("REG_EXPAND_SZ"));
    }

    @Test
    public void testModifiedRegistryValueBuilder() {
        UninstallManifest.ModifiedRegistryValue value = UninstallManifest.ModifiedRegistryValue.builder()
            .root(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER)
            .path("Environment")
            .name("Path")
            .previousValue("C:\\Windows\\System32")
            .previousType(UninstallManifest.RegistryValueType.REG_EXPAND_SZ)
            .description("User PATH")
            .build();

        assertEquals(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER, value.getRoot());
        assertEquals("Environment", value.getPath());
        assertEquals("Path", value.getName());
        assertEquals("C:\\Windows\\System32", value.getPreviousValue());
        assertEquals(UninstallManifest.RegistryValueType.REG_EXPAND_SZ, value.getPreviousType());
        assertEquals("User PATH", value.getDescription());
    }

    @Test
    public void testModifiedRegistryValueEquality() {
        UninstallManifest.ModifiedRegistryValue value1 = createModifiedRegistryValue();
        UninstallManifest.ModifiedRegistryValue value2 = createModifiedRegistryValue();
        UninstallManifest.ModifiedRegistryValue value3 = UninstallManifest.ModifiedRegistryValue.builder()
            .root(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER)
            .path("Different")
            .name("Different")
            .previousValue("OtherValue")
            .previousType(UninstallManifest.RegistryValueType.REG_SZ)
            .description("Different value")
            .build();

        assertEquals(value1, value2);
        assertEquals(value1.hashCode(), value2.hashCode());
        assertNotEquals(value1, value3);
    }

    @Test
    public void testWindowsPathEntryBuilder() {
        UninstallManifest.WindowsPathEntry entry = UninstallManifest.WindowsPathEntry.builder()
            .addedEntry("C:\\Users\\alice\\.jdeploy\\bin-x64\\testapp")
            .description("CLI commands directory")
            .build();

        assertEquals("C:\\Users\\alice\\.jdeploy\\bin-x64\\testapp", entry.getAddedEntry());
        assertEquals("CLI commands directory", entry.getDescription());
    }

    @Test
    public void testWindowsPathEntryEquality() {
        UninstallManifest.WindowsPathEntry entry1 = createWindowsPathEntry();
        UninstallManifest.WindowsPathEntry entry2 = createWindowsPathEntry();
        UninstallManifest.WindowsPathEntry entry3 = UninstallManifest.WindowsPathEntry.builder()
            .addedEntry("C:\\Different\\Path")
            .description("Different")
            .build();

        assertEquals(entry1, entry2);
        assertEquals(entry1.hashCode(), entry2.hashCode());
        assertNotEquals(entry1, entry3);
    }

    @Test
    public void testShellProfileEntryBuilder() {
        UninstallManifest.ShellProfileEntry entry = UninstallManifest.ShellProfileEntry.builder()
            .file("${USER_HOME}/.bashrc")
            .exportLine("export PATH=\"${PATH}:/home/alice/.jdeploy/bin-x64/testapp\"")
            .description("Bash PATH configuration")
            .build();

        assertEquals("${USER_HOME}/.bashrc", entry.getFile());
        assertEquals("export PATH=\"${PATH}:/home/alice/.jdeploy/bin-x64/testapp\"", entry.getExportLine());
        assertEquals("Bash PATH configuration", entry.getDescription());
    }

    @Test
    public void testShellProfileEntryEquality() {
        UninstallManifest.ShellProfileEntry entry1 = createShellProfileEntry();
        UninstallManifest.ShellProfileEntry entry2 = createShellProfileEntry();
        UninstallManifest.ShellProfileEntry entry3 = UninstallManifest.ShellProfileEntry.builder()
            .file("${USER_HOME}/.zprofile")
            .exportLine("export PATH=\"${PATH}:/home/alice/.jdeploy/bin-arm64/testapp\"")
            .description("Zsh PATH")
            .build();

        assertEquals(entry1, entry2);
        assertEquals(entry1.hashCode(), entry2.hashCode());
        assertNotEquals(entry1, entry3);
    }

    @Test
    public void testGitBashProfileEntryBuilder() {
        UninstallManifest.GitBashProfileEntry entry = UninstallManifest.GitBashProfileEntry.builder()
            .file("${USER_HOME}/.bash_profile")
            .exportLine("export PATH=\"${PATH}:/c/Users/alice/.jdeploy/bin-x64/testapp\"")
            .description("Git Bash PATH configuration")
            .build();

        assertEquals("${USER_HOME}/.bash_profile", entry.getFile());
        assertEquals("export PATH=\"${PATH}:/c/Users/alice/.jdeploy/bin-x64/testapp\"", entry.getExportLine());
        assertEquals("Git Bash PATH configuration", entry.getDescription());
    }

    @Test
    public void testGitBashProfileEntryEquality() {
        UninstallManifest.GitBashProfileEntry entry1 = createGitBashProfileEntry();
        UninstallManifest.GitBashProfileEntry entry2 = createGitBashProfileEntry();
        UninstallManifest.GitBashProfileEntry entry3 = UninstallManifest.GitBashProfileEntry.builder()
            .file("${USER_HOME}/.bash_profile")
            .exportLine("export PATH=\"${PATH}:/c/Users/alice/.jdeploy/bin-arm64/testapp\"")
            .description("Git Bash ARM64")
            .build();

        assertEquals(entry1, entry2);
        assertEquals(entry1.hashCode(), entry2.hashCode());
        assertNotEquals(entry1, entry3);
    }

    @Test
    public void testPathModificationsBuilder() {
        List<UninstallManifest.WindowsPathEntry> windowsPaths = Arrays.asList(createWindowsPathEntry());
        List<UninstallManifest.ShellProfileEntry> shellProfiles = Arrays.asList(createShellProfileEntry());
        List<UninstallManifest.GitBashProfileEntry> gitBashProfiles = Arrays.asList(createGitBashProfileEntry());

        UninstallManifest.PathModifications pathMods = UninstallManifest.PathModifications.builder()
            .windowsPaths(windowsPaths)
            .shellProfiles(shellProfiles)
            .gitBashProfiles(gitBashProfiles)
            .build();

        assertEquals(1, pathMods.getWindowsPaths().size());
        assertEquals(1, pathMods.getShellProfiles().size());
        assertEquals(1, pathMods.getGitBashProfiles().size());
    }

    @Test
    public void testPathModificationsEquality() {
        List<UninstallManifest.WindowsPathEntry> paths = Arrays.asList(createWindowsPathEntry());
        UninstallManifest.PathModifications mods1 = UninstallManifest.PathModifications.builder()
            .windowsPaths(paths)
            .build();
        UninstallManifest.PathModifications mods2 = UninstallManifest.PathModifications.builder()
            .windowsPaths(paths)
            .build();

        assertEquals(mods1, mods2);
        assertEquals(mods1.hashCode(), mods2.hashCode());
    }

    @Test
    public void testRegistryInfoBuilder() {
        List<UninstallManifest.RegistryKey> keys = Arrays.asList(createRegistryKey());
        List<UninstallManifest.ModifiedRegistryValue> values = Arrays.asList(createModifiedRegistryValue());

        UninstallManifest.RegistryInfo registry = UninstallManifest.RegistryInfo.builder()
            .createdKeys(keys)
            .modifiedValues(values)
            .build();

        assertEquals(1, registry.getCreatedKeys().size());
        assertEquals(1, registry.getModifiedValues().size());
    }

    @Test
    public void testRegistryInfoEquality() {
        List<UninstallManifest.RegistryKey> keys = Arrays.asList(createRegistryKey());
        UninstallManifest.RegistryInfo registry1 = UninstallManifest.RegistryInfo.builder()
            .createdKeys(keys)
            .build();
        UninstallManifest.RegistryInfo registry2 = UninstallManifest.RegistryInfo.builder()
            .createdKeys(keys)
            .build();

        assertEquals(registry1, registry2);
        assertEquals(registry1.hashCode(), registry2.hashCode());
    }

    @Test
    public void testUninstallManifestBuilder() {
        UninstallManifest manifest = createUninstallManifest();

        assertEquals(MANIFEST_VERSION, manifest.getVersion());
        assertEquals(PACKAGE_NAME, manifest.getPackageInfo().getName());
        assertEquals(1, manifest.getFiles().size());
        assertEquals(1, manifest.getDirectories().size());
    }

    @Test
    public void testUninstallManifestEquality() {
        UninstallManifest manifest1 = createUninstallManifest();
        UninstallManifest manifest2 = createUninstallManifest();

        assertEquals(manifest1, manifest2);
        assertEquals(manifest1.hashCode(), manifest2.hashCode());
    }

    @Test
    public void testUninstallManifestWithEmptyCollections() {
        UninstallManifest manifest = UninstallManifest.builder()
            .version(MANIFEST_VERSION)
            .packageInfo(createPackageInfo())
            .build();

        assertNotNull(manifest.getFiles());
        assertNotNull(manifest.getDirectories());
        assertTrue(manifest.getFiles().isEmpty());
        assertTrue(manifest.getDirectories().isEmpty());
    }

    @Test
    public void testUninstallManifestImmutability() {
        List<UninstallManifest.InstalledFile> files = Arrays.asList(createInstalledFile());
        UninstallManifest manifest = UninstallManifest.builder()
            .version(MANIFEST_VERSION)
            .packageInfo(createPackageInfo())
            .files(files)
            .build();

        // Verify that the returned list is unmodifiable
        assertThrows(UnsupportedOperationException.class, () ->
            manifest.getFiles().add(UninstallManifest.InstalledFile.builder()
                .path("${JDEPLOY_HOME}/test")
                .type(UninstallManifest.FileType.BINARY)
                .build()));
    }

    @Test
    public void testPackageInfoRequiredFields() {
        assertThrows(NullPointerException.class, () ->
            UninstallManifest.PackageInfo.builder()
                .source(PACKAGE_SOURCE)
                .build());
    }

    @Test
    public void testInstalledFileRequiredFields() {
        assertThrows(NullPointerException.class, () ->
            UninstallManifest.InstalledFile.builder()
                .description("Test")
                .build());
    }

    @Test
    public void testInstalledDirectoryRequiredFields() {
        assertThrows(NullPointerException.class, () ->
            UninstallManifest.InstalledDirectory.builder()
                .description("Test")
                .build());
    }

    @Test
    public void testComplexManifest() {
        UninstallManifest manifest = UninstallManifest.builder()
            .version(MANIFEST_VERSION)
            .packageInfo(createPackageInfo())
            .files(Arrays.asList(
                createInstalledFile(),
                UninstallManifest.InstalledFile.builder()
                    .path("${JDEPLOY_HOME}/apps/testapp/testapp.properties")
                    .type(UninstallManifest.FileType.CONFIG)
                    .description("Configuration file")
                    .build()
            ))
            .directories(Arrays.asList(
                createInstalledDirectory(),
                UninstallManifest.InstalledDirectory.builder()
                    .path("${JDEPLOY_HOME}/apps")
                    .cleanup(UninstallManifest.CleanupStrategy.IF_EMPTY)
                    .description("Apps directory parent")
                    .build()
            ))
            .registry(UninstallManifest.RegistryInfo.builder()
                .createdKeys(Arrays.asList(createRegistryKey()))
                .modifiedValues(Arrays.asList(createModifiedRegistryValue()))
                .build())
            .pathModifications(UninstallManifest.PathModifications.builder()
                .windowsPaths(Arrays.asList(createWindowsPathEntry()))
                .shellProfiles(Arrays.asList(createShellProfileEntry()))
                .gitBashProfiles(Arrays.asList(createGitBashProfileEntry()))
                .build())
            .build();

        assertEquals(MANIFEST_VERSION, manifest.getVersion());
        assertEquals(2, manifest.getFiles().size());
        assertEquals(2, manifest.getDirectories().size());
        assertEquals(1, manifest.getRegistry().getCreatedKeys().size());
        assertEquals(1, manifest.getRegistry().getModifiedValues().size());
        assertEquals(1, manifest.getPathModifications().getWindowsPaths().size());
    }

    // Helper methods for creating test objects

    private UninstallManifest.PackageInfo createPackageInfo() {
        return UninstallManifest.PackageInfo.builder()
            .name(PACKAGE_NAME)
            .source(PACKAGE_SOURCE)
            .version(PACKAGE_VERSION)
            .fullyQualifiedName(FQPN)
            .architecture(ARCHITECTURE)
            .installedAt(INSTALLED_AT)
            .installerVersion(INSTALLER_VERSION)
            .build();
    }

    private UninstallManifest.InstalledFile createInstalledFile() {
        return UninstallManifest.InstalledFile.builder()
            .path("${JDEPLOY_HOME}/apps/testapp/testapp.exe")
            .type(UninstallManifest.FileType.BINARY)
            .description("Main application executable")
            .build();
    }

    private UninstallManifest.InstalledDirectory createInstalledDirectory() {
        return UninstallManifest.InstalledDirectory.builder()
            .path("${JDEPLOY_HOME}/apps/testapp")
            .cleanup(UninstallManifest.CleanupStrategy.ALWAYS)
            .description("Application installation directory")
            .build();
    }

    private UninstallManifest.RegistryKey createRegistryKey() {
        return UninstallManifest.RegistryKey.builder()
            .root(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER)
            .path("Software\\jdeploy\\testapp")
            .description("Application registry key")
            .build();
    }

    private UninstallManifest.ModifiedRegistryValue createModifiedRegistryValue() {
        return UninstallManifest.ModifiedRegistryValue.builder()
            .root(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER)
            .path("Environment")
            .name("Path")
            .previousValue("C:\\Windows\\System32")
            .previousType(UninstallManifest.RegistryValueType.REG_EXPAND_SZ)
            .description("User PATH variable")
            .build();
    }

    private UninstallManifest.WindowsPathEntry createWindowsPathEntry() {
        return UninstallManifest.WindowsPathEntry.builder()
            .addedEntry("C:\\Users\\alice\\.jdeploy\\bin-x64\\testapp")
            .description("CLI commands directory")
            .build();
    }

    private UninstallManifest.ShellProfileEntry createShellProfileEntry() {
        return UninstallManifest.ShellProfileEntry.builder()
            .file("${USER_HOME}/.bashrc")
            .exportLine("export PATH=\"${PATH}:/home/alice/.jdeploy/bin-x64/testapp\"")
            .description("Bash PATH configuration")
            .build();
    }

    private UninstallManifest.GitBashProfileEntry createGitBashProfileEntry() {
        return UninstallManifest.GitBashProfileEntry.builder()
            .file("${USER_HOME}/.bash_profile")
            .exportLine("export PATH=\"${PATH}:/c/Users/alice/.jdeploy/bin-x64/testapp\"")
            .description("Git Bash PATH configuration")
            .build();
    }

    private UninstallManifest createUninstallManifest() {
        return UninstallManifest.builder()
            .version(MANIFEST_VERSION)
            .packageInfo(createPackageInfo())
            .files(Arrays.asList(createInstalledFile()))
            .directories(Arrays.asList(createInstalledDirectory()))
            .build();
    }
}

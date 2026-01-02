package ca.weblite.jdeploy.installer.uninstall;

import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UninstallManifestBuilder.
 * Verifies builder fluency, path variable substitution, and validation of required fields.
 */
public class UninstallManifestBuilderTest {
    
    private UninstallManifestBuilder builder;
    private static final String USER_HOME = System.getProperty("user.home");
    private static final String JDEPLOY_HOME = USER_HOME + File.separator + ".jdeploy";
    
    @BeforeEach
    public void setUp() {
        builder = new UninstallManifestBuilder();
    }
    
    // ==================== Builder Fluency Tests ====================
    
    @Test
    public void testBuilderFluency() {
        UninstallManifest manifest = builder
            .withPackageInfo("testapp", "npm", "1.0.0", "x64")
            .withInstallerVersion("2.0.0")
            .addFile("${JDEPLOY_HOME}/testapp.exe", UninstallManifest.FileType.BINARY, "Main binary")
            .addDirectory("${JDEPLOY_HOME}/testapp", UninstallManifest.CleanupStrategy.ALWAYS, "App dir")
            .addWindowsPathEntry("${JDEPLOY_HOME}/bin")
            .addShellProfileEntry("${USER_HOME}/.bashrc", "export PATH=\"${PATH}:${APP_DIR}/bin\"")
            .build();
        
        assertNotNull(manifest);
        assertEquals("testapp", manifest.getPackageInfo().getName());
        assertEquals(1, manifest.getFiles().size());
        assertEquals(1, manifest.getDirectories().size());
    }
    
    @Test
    public void testChainedMethodCalls() {
        UninstallManifestBuilder result1 = builder.withPackageInfo("app", "npm", "1.0", "x64");
        UninstallManifestBuilder result2 = result1.addFile("${JDEPLOY_HOME}/app", UninstallManifest.FileType.BINARY, null);
        UninstallManifestBuilder result3 = result2.addDirectory("${APP_DIR}", UninstallManifest.CleanupStrategy.ALWAYS, null);
        
        assertSame(builder, result1);
        assertSame(builder, result2);
        assertSame(builder, result3);
    }
    
    // ==================== Package Info Tests ====================
    
    @Test
    public void testWithPackageInfo() {
        builder.withPackageInfo("myapp", "github", "2.5.0", "arm64");
        UninstallManifest manifest = builder.build();
        
        UninstallManifest.PackageInfo info = manifest.getPackageInfo();
        assertEquals("myapp", info.getName());
        assertEquals("github", info.getSource());
        assertEquals("2.5.0", info.getVersion());
        assertEquals("arm64", info.getArchitecture());
        assertNotNull(info.getFullyQualifiedName());
    }
    
    @Test
    public void testPackageInfoNullValidation() {
        assertThrows(NullPointerException.class, () ->
            builder.withPackageInfo(null, "npm", "1.0", "x64"));
        
        assertThrows(NullPointerException.class, () ->
            builder.withPackageInfo("app", "npm", null, "x64"));
        
        assertThrows(NullPointerException.class, () ->
            builder.withPackageInfo("app", "npm", "1.0", null));
    }
    
    @Test
    public void testInstallerVersion() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .withInstallerVersion("3.0.0");
        UninstallManifest manifest = builder.build();
        
        assertEquals("3.0.0", manifest.getPackageInfo().getInstallerVersion());
    }
    
    @Test
    public void testInstallerVersionDefaultIfNotSet() {
        builder.withPackageInfo("app", "npm", "1.0", "x64");
        UninstallManifest manifest = builder.build();
        
        assertEquals("1.0", manifest.getPackageInfo().getInstallerVersion());
    }
    
    @Test
    public void testInstalledAt() {
        Instant now = Instant.now();
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .withInstalledAt(now);
        UninstallManifest manifest = builder.build();
        
        assertEquals(now, manifest.getPackageInfo().getInstalledAt());
    }
    
    // ==================== Path Variable Substitution Tests ====================
    
    @Test
    public void testUserHomeSubstitution() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .addFile("${USER_HOME}/.config/app.conf", UninstallManifest.FileType.CONFIG, null);
        
        UninstallManifest manifest = builder.build();
        String filePath = manifest.getFiles().get(0).getPath();
        
        assertTrue(filePath.contains(USER_HOME), "USER_HOME variable should be substituted");
        assertFalse(filePath.contains("${USER_HOME}"), "USER_HOME placeholder should be replaced");
    }
    
    @Test
    public void testJDeployHomeSubstitution() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .addFile("${JDEPLOY_HOME}/apps/app/binary.exe", UninstallManifest.FileType.BINARY, null);
        
        UninstallManifest manifest = builder.build();
        String filePath = manifest.getFiles().get(0).getPath();
        
        assertTrue(filePath.contains(JDEPLOY_HOME), "JDEPLOY_HOME variable should be substituted");
        assertFalse(filePath.contains("${JDEPLOY_HOME}"), "JDEPLOY_HOME placeholder should be replaced");
    }
    
    @Test
    public void testAppDirSubstitution() {
        String appName = "testapp";
        builder.withPackageInfo(appName, "npm", "1.0", "x64")
            .addDirectory("${APP_DIR}", UninstallManifest.CleanupStrategy.ALWAYS, null);
        
        UninstallManifest manifest = builder.build();
        String dirPath = manifest.getDirectories().get(0).getPath();
        
        assertTrue(dirPath.contains(".jdeploy"), "APP_DIR should contain .jdeploy path");
        assertFalse(dirPath.contains("${APP_DIR}"), "APP_DIR placeholder should be replaced");
    }
    
    @Test
    public void testMultipleVariableSubstitution() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .addShellProfileEntry("${USER_HOME}/.bashrc", "export PATH=\"${PATH}:${JDEPLOY_HOME}/bin\"");
        
        UninstallManifest manifest = builder.build();
        UninstallManifest.ShellProfileEntry entry = manifest.getPathModifications().getShellProfiles().get(0);
        
        String file = entry.getFile();
        String exportLine = entry.getExportLine();
        
        assertTrue(file.contains(USER_HOME), "USER_HOME should be substituted in file");
        assertFalse(file.contains("${USER_HOME}"), "USER_HOME placeholder should be replaced");
        
        assertTrue(exportLine.contains(JDEPLOY_HOME), "JDEPLOY_HOME should be substituted in export line");
        assertFalse(exportLine.contains("${JDEPLOY_HOME}"), "JDEPLOY_HOME placeholder should be replaced");
    }
    
    @Test
    public void testCustomVariableSubstitution() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .withVariable("CUSTOM_DIR", "/custom/path")
            .addFile("${CUSTOM_DIR}/myfile.txt", UninstallManifest.FileType.CONFIG, null);
        
        UninstallManifest manifest = builder.build();
        String filePath = manifest.getFiles().get(0).getPath();
        
        assertEquals("/custom/path/myfile.txt", filePath);
    }
    
    @Test
    public void testVariableSubstitutionInAllFields() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .addFile("${JDEPLOY_HOME}/app.exe", UninstallManifest.FileType.BINARY, "Binary at ${JDEPLOY_HOME}")
            .addDirectory("${APP_DIR}", UninstallManifest.CleanupStrategy.ALWAYS, "App dir at ${APP_DIR}")
            .addWindowsPathEntry("${JDEPLOY_HOME}/bin")
            .addShellProfileEntry("${USER_HOME}/.bashrc", "export CUSTOM=${JDEPLOY_HOME}");
        
        UninstallManifest manifest = builder.build();
        
        // File path substitution
        String filePath = manifest.getFiles().get(0).getPath();
        assertTrue(filePath.startsWith(JDEPLOY_HOME));
        
        // Directory path substitution
        String dirPath = manifest.getDirectories().get(0).getPath();
        assertTrue(dirPath.contains(".jdeploy"));
        
        // Windows PATH substitution
        String winPath = manifest.getPathModifications().getWindowsPaths().get(0).getAddedEntry();
        assertTrue(winPath.contains(JDEPLOY_HOME));
        
        // Shell profile substitution
        UninstallManifest.ShellProfileEntry entry = manifest.getPathModifications().getShellProfiles().get(0);
        assertTrue(entry.getFile().contains(USER_HOME));
        assertTrue(entry.getExportLine().contains(JDEPLOY_HOME));
    }
    
    // ==================== File Addition Tests ====================
    
    @Test
    public void testAddFile() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .addFile("${JDEPLOY_HOME}/app.exe", UninstallManifest.FileType.BINARY, "Main executable");
        
        UninstallManifest manifest = builder.build();
        assertEquals(1, manifest.getFiles().size());
        
        UninstallManifest.InstalledFile file = manifest.getFiles().get(0);
        assertTrue(file.getPath().contains("app.exe"));
        assertEquals(UninstallManifest.FileType.BINARY, file.getType());
        assertEquals("Main executable", file.getDescription());
    }
    
    @Test
    public void testAddFileNullValidation() {
        builder.withPackageInfo("app", "npm", "1.0", "x64");
        
        assertThrows(NullPointerException.class, () ->
            builder.addFile(null, UninstallManifest.FileType.BINARY, "desc"));
        
        assertThrows(NullPointerException.class, () ->
            builder.addFile("${JDEPLOY_HOME}/app", null, "desc"));
    }
    
    @Test
    public void testAddMultipleFiles() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .addFile("${JDEPLOY_HOME}/app.exe", UninstallManifest.FileType.BINARY, null)
            .addFile("${JDEPLOY_HOME}/app.dll", UninstallManifest.FileType.BINARY, null)
            .addFile("${JDEPLOY_HOME}/config.json", UninstallManifest.FileType.CONFIG, null);
        
        UninstallManifest manifest = builder.build();
        assertEquals(3, manifest.getFiles().size());
    }
    
    // ==================== Directory Addition Tests ====================
    
    @Test
    public void testAddDirectory() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .addDirectory("${APP_DIR}", UninstallManifest.CleanupStrategy.ALWAYS, "App directory");
        
        UninstallManifest manifest = builder.build();
        assertEquals(1, manifest.getDirectories().size());
        
        UninstallManifest.InstalledDirectory dir = manifest.getDirectories().get(0);
        assertTrue(dir.getPath().contains(".jdeploy"));
        assertEquals(UninstallManifest.CleanupStrategy.ALWAYS, dir.getCleanup());
        assertEquals("App directory", dir.getDescription());
    }
    
    @Test
    public void testAddDirectoryNullValidation() {
        builder.withPackageInfo("app", "npm", "1.0", "x64");
        
        assertThrows(NullPointerException.class, () ->
            builder.addDirectory(null, UninstallManifest.CleanupStrategy.ALWAYS, "desc"));
        
        assertThrows(NullPointerException.class, () ->
            builder.addDirectory("${APP_DIR}", null, "desc"));
    }
    
    @Test
    public void testAddMultipleDirectories() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .addDirectory("${APP_DIR}", UninstallManifest.CleanupStrategy.ALWAYS, null)
            .addDirectory("${JDEPLOY_HOME}/apps", UninstallManifest.CleanupStrategy.IF_EMPTY, null);
        
        UninstallManifest manifest = builder.build();
        assertEquals(2, manifest.getDirectories().size());
    }
    
    @Test
    public void testCleanupStrategies() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .addDirectory("${APP_DIR}/always", UninstallManifest.CleanupStrategy.ALWAYS, null)
            .addDirectory("${APP_DIR}/ifEmpty", UninstallManifest.CleanupStrategy.IF_EMPTY, null)
            .addDirectory("${APP_DIR}/contentsOnly", UninstallManifest.CleanupStrategy.CONTENTS_ONLY, null);
        
        UninstallManifest manifest = builder.build();
        assertEquals(3, manifest.getDirectories().size());
        
        assertEquals(UninstallManifest.CleanupStrategy.ALWAYS, manifest.getDirectories().get(0).getCleanup());
        assertEquals(UninstallManifest.CleanupStrategy.IF_EMPTY, manifest.getDirectories().get(1).getCleanup());
        assertEquals(UninstallManifest.CleanupStrategy.CONTENTS_ONLY, manifest.getDirectories().get(2).getCleanup());
    }
    
    // ==================== Registry Tests ====================
    
    @Test
    public void testAddCreatedRegistryKey() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .addCreatedRegistryKey(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER, "Software\\jdeploy\\app");
        
        UninstallManifest manifest = builder.build();
        assertEquals(1, manifest.getRegistry().getCreatedKeys().size());
        
        UninstallManifest.RegistryKey key = manifest.getRegistry().getCreatedKeys().get(0);
        assertEquals(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER, key.getRoot());
        assertEquals("Software\\jdeploy\\app", key.getPath());
    }
    
    @Test
    public void testAddCreatedRegistryKeyNullValidation() {
        builder.withPackageInfo("app", "npm", "1.0", "x64");
        
        assertThrows(NullPointerException.class, () ->
            builder.addCreatedRegistryKey(null, "Software\\app"));
        
        assertThrows(NullPointerException.class, () ->
            builder.addCreatedRegistryKey(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER, null));
    }
    
    @Test
    public void testAddCreatedRegistryKeyWithDescription() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .addCreatedRegistryKey(UninstallManifest.RegistryRoot.HKEY_LOCAL_MACHINE, "Software\\app", "System-wide settings");
        
        UninstallManifest manifest = builder.build();
        UninstallManifest.RegistryKey key = manifest.getRegistry().getCreatedKeys().get(0);
        assertEquals("System-wide settings", key.getDescription());
    }
    
    @Test
    public void testAddModifiedRegistryValue() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .addModifiedRegistryValue(
                UninstallManifest.RegistryRoot.HKEY_CURRENT_USER,
                "Environment",
                "Path",
                "C:\\Windows\\System32",
                UninstallManifest.RegistryValueType.REG_EXPAND_SZ);
        
        UninstallManifest manifest = builder.build();
        assertEquals(1, manifest.getRegistry().getModifiedValues().size());
        
        UninstallManifest.ModifiedRegistryValue value = manifest.getRegistry().getModifiedValues().get(0);
        assertEquals(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER, value.getRoot());
        assertEquals("Environment", value.getPath());
        assertEquals("Path", value.getName());
        assertEquals("C:\\Windows\\System32", value.getPreviousValue());
        assertEquals(UninstallManifest.RegistryValueType.REG_EXPAND_SZ, value.getPreviousType());
    }
    
    @Test
    public void testAddModifiedRegistryValueNullValidation() {
        builder.withPackageInfo("app", "npm", "1.0", "x64");
        
        assertThrows(NullPointerException.class, () ->
            builder.addModifiedRegistryValue(null, "path", "name", "value", UninstallManifest.RegistryValueType.REG_SZ));
        
        assertThrows(NullPointerException.class, () ->
            builder.addModifiedRegistryValue(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER, null, "name", "value", UninstallManifest.RegistryValueType.REG_SZ));
        
        assertThrows(NullPointerException.class, () ->
            builder.addModifiedRegistryValue(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER, "path", null, "value", UninstallManifest.RegistryValueType.REG_SZ));
        
        assertThrows(NullPointerException.class, () ->
            builder.addModifiedRegistryValue(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER, "path", "name", "value", null));
    }
    
    // ==================== Windows PATH Tests ====================
    
    @Test
    public void testAddWindowsPathEntry() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .addWindowsPathEntry("C:\\Users\\alice\\.jdeploy\\bin-x64");
        
        UninstallManifest manifest = builder.build();
        assertEquals(1, manifest.getPathModifications().getWindowsPaths().size());
        
        UninstallManifest.WindowsPathEntry entry = manifest.getPathModifications().getWindowsPaths().get(0);
        assertEquals("C:\\Users\\alice\\.jdeploy\\bin-x64", entry.getAddedEntry());
    }
    
    @Test
    public void testAddWindowsPathEntryWithVariables() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .addWindowsPathEntry("${JDEPLOY_HOME}/bin-x64");
        
        UninstallManifest manifest = builder.build();
        String entry = manifest.getPathModifications().getWindowsPaths().get(0).getAddedEntry();
        
        assertTrue(entry.contains(JDEPLOY_HOME));
    }
    
    @Test
    public void testAddWindowsPathEntryNullValidation() {
        builder.withPackageInfo("app", "npm", "1.0", "x64");
        
        assertThrows(NullPointerException.class, () ->
            builder.addWindowsPathEntry(null));
    }
    
    // ==================== Shell Profile Tests ====================
    
    @Test
    public void testAddShellProfileEntry() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .addShellProfileEntry("${USER_HOME}/.bashrc", "export PATH=\"${PATH}:/home/alice/.jdeploy/bin\"");
        
        UninstallManifest manifest = builder.build();
        assertEquals(1, manifest.getPathModifications().getShellProfiles().size());
        
        UninstallManifest.ShellProfileEntry entry = manifest.getPathModifications().getShellProfiles().get(0);
        assertTrue(entry.getFile().contains(".bashrc"));
        assertTrue(entry.getExportLine().contains("export PATH"));
    }
    
    @Test
    public void testAddShellProfileEntryWithDescription() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .addShellProfileEntry("${USER_HOME}/.zprofile", "export PATH=\"${PATH}:${APP_DIR}/bin\"", "Zsh shell configuration");
        
        UninstallManifest manifest = builder.build();
        UninstallManifest.ShellProfileEntry entry = manifest.getPathModifications().getShellProfiles().get(0);
        assertEquals("Zsh shell configuration", entry.getDescription());
    }
    
    @Test
    public void testAddShellProfileEntryNullValidation() {
        builder.withPackageInfo("app", "npm", "1.0", "x64");
        
        assertThrows(NullPointerException.class, () ->
            builder.addShellProfileEntry(null, "export PATH"));
        
        assertThrows(NullPointerException.class, () ->
            builder.addShellProfileEntry("${USER_HOME}/.bashrc", null));
    }
    
    @Test
    public void testAddMultipleShellProfileEntries() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .addShellProfileEntry("${USER_HOME}/.bashrc", "export PATH=\"${PATH}:${APP_DIR}\"")
            .addShellProfileEntry("${USER_HOME}/.zprofile", "export PATH=\"${PATH}:${APP_DIR}\"");
        
        UninstallManifest manifest = builder.build();
        assertEquals(2, manifest.getPathModifications().getShellProfiles().size());
    }
    
    // ==================== Git Bash Profile Tests ====================
    
    @Test
    public void testAddGitBashProfileEntry() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .addGitBashProfileEntry("${USER_HOME}/.bash_profile", "export PATH=\"${PATH}:/c/Users/alice/.jdeploy/bin\"");
        
        UninstallManifest manifest = builder.build();
        assertEquals(1, manifest.getPathModifications().getGitBashProfiles().size());
        
        UninstallManifest.GitBashProfileEntry entry = manifest.getPathModifications().getGitBashProfiles().get(0);
        assertTrue(entry.getFile().contains(".bash_profile"));
        assertTrue(entry.getExportLine().contains("export PATH"));
    }
    
    @Test
    public void testAddGitBashProfileEntryWithDescription() {
        builder.withPackageInfo("app", "npm", "1.0", "x64")
            .addGitBashProfileEntry("${USER_HOME}/.bash_profile", "export PATH=\"${PATH}:${APP_DIR}\"", "Git Bash configuration");
        
        UninstallManifest manifest = builder.build();
        UninstallManifest.GitBashProfileEntry entry = manifest.getPathModifications().getGitBashProfiles().get(0);
        assertEquals("Git Bash configuration", entry.getDescription());
    }
    
    @Test
    public void testAddGitBashProfileEntryNullValidation() {
        builder.withPackageInfo("app", "npm", "1.0", "x64");
        
        assertThrows(NullPointerException.class, () ->
            builder.addGitBashProfileEntry(null, "export PATH"));
        
        assertThrows(NullPointerException.class, () ->
            builder.addGitBashProfileEntry("${USER_HOME}/.bash_profile", null));
    }
    
    // ==================== Validation Tests ====================
    
    @Test
    public void testBuildWithoutPackageInfoThrows() {
        assertThrows(IllegalStateException.class, () ->
            builder.build());
    }
    
    @Test
    public void testBuildWithoutNameThrows() {
        assertThrows(IllegalStateException.class, () ->
            new UninstallManifestBuilder()
                .withPackageInfo("", "npm", "1.0", "x64"));
    }
    
    @Test
    public void testBuildWithoutVersionThrows() {
        assertThrows(IllegalStateException.class, () ->
            new UninstallManifestBuilder()
                .withPackageInfo("app", "npm", "", "x64"));
    }
    
    @Test
    public void testBuildWithoutArchitectureThrows() {
        assertThrows(IllegalStateException.class, () ->
            new UninstallManifestBuilder()
                .withPackageInfo("app", "npm", "1.0", ""));
    }
    
    // ==================== Complex Scenario Tests ====================
    
    @Test
    public void testComplexInstallationScenario() {
        UninstallManifest manifest = builder
            .withPackageInfo("myapp", "npm", "2.5.3", "x64")
            .withInstallerVersion("1.5.0")
            .addFile("${JDEPLOY_HOME}/apps/myapp/myapp.exe", UninstallManifest.FileType.BINARY, "Main executable")
            .addFile("${JDEPLOY_HOME}/apps/myapp/config.json", UninstallManifest.FileType.CONFIG, "Configuration")
            .addDirectory("${APP_DIR}", UninstallManifest.CleanupStrategy.ALWAYS, "Application root")
            .addDirectory("${JDEPLOY_HOME}/apps", UninstallManifest.CleanupStrategy.IF_EMPTY, "Apps directory")
            .addCreatedRegistryKey(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER, "Software\\jdeploy\\myapp")
            .addModifiedRegistryValue(
                UninstallManifest.RegistryRoot.HKEY_CURRENT_USER,
                "Environment",
                "Path",
                "C:\\old\\path",
                UninstallManifest.RegistryValueType.REG_EXPAND_SZ,
                "User PATH"
            )
            .addWindowsPathEntry("${JDEPLOY_HOME}/bin-x64", "CLI commands")
            .addShellProfileEntry("${USER_HOME}/.bashrc", "export PATH=\"${PATH}:${APP_DIR}/bin\"", "Bash")
            .addGitBashProfileEntry("${USER_HOME}/.bash_profile", "export PATH=\"${PATH}:${APP_DIR}/bin\"", "Git Bash")
            .build();
        
        // Verify the manifest is complete
        assertEquals("myapp", manifest.getPackageInfo().getName());
        assertEquals("2.5.3", manifest.getPackageInfo().getVersion());
        assertEquals("x64", manifest.getPackageInfo().getArchitecture());
        assertEquals("1.5.0", manifest.getPackageInfo().getInstallerVersion());
        
        assertEquals(2, manifest.getFiles().size());
        assertEquals(2, manifest.getDirectories().size());
        assertEquals(1, manifest.getRegistry().getCreatedKeys().size());
        assertEquals(1, manifest.getRegistry().getModifiedValues().size());
        assertEquals(1, manifest.getPathModifications().getWindowsPaths().size());
        assertEquals(1, manifest.getPathModifications().getShellProfiles().size());
        assertEquals(1, manifest.getPathModifications().getGitBashProfiles().size());
        
        // Verify variable substitutions
        for (UninstallManifest.InstalledFile file : manifest.getFiles()) {
            assertFalse(file.getPath().contains("${"), "Variables should be substituted in file paths");
        }
        
        for (UninstallManifest.InstalledDirectory dir : manifest.getDirectories()) {
            assertFalse(dir.getPath().contains("${"), "Variables should be substituted in directory paths");
        }
    }
    
    @Test
    public void testBuilderReuse() {
        // First build
        UninstallManifest manifest1 = builder
            .withPackageInfo("app1", "npm", "1.0", "x64")
            .addFile("${JDEPLOY_HOME}/app1", UninstallManifest.FileType.BINARY, null)
            .build();
        
        assertEquals(1, manifest1.getFiles().size());
        assertEquals("app1", manifest1.getPackageInfo().getName());
        
        // Reusing builder for another manifest (accumulates)
        UninstallManifest manifest2 = builder
            .withPackageInfo("app2", "npm", "2.0", "arm64")
            .addFile("${JDEPLOY_HOME}/app2", UninstallManifest.FileType.BINARY, null)
            .build();
        
        // Note: The second manifest will have accumulated files from both invocations
        assertEquals("app2", manifest2.getPackageInfo().getName());
        assertEquals("arm64", manifest2.getPackageInfo().getArchitecture());
    }
}

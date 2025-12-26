package ca.weblite.jdeploy.installer.win;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.models.DocumentTypeAssociation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for InstallWindowsRegistry using in-memory registry operations.
 * Tests registry state changes without touching the live Windows registry.
 * These tests are skipped on non-Windows platforms to avoid JNA native library loading errors.
 */
@DisabledOnOs({OS.MAC, OS.LINUX})
public class InstallWindowsRegistryIntegrationTest {

    private AppInfo appInfo;
    private File exeFile;
    private File iconFile;
    private File backupLogFile;
    private InMemoryRegistryOperations registryOps;
    private InstallWindowsRegistry installer;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        // Create test files
        exeFile = Files.createFile(tempDir.resolve("app.exe")).toFile();
        iconFile = Files.createFile(tempDir.resolve("app.ico")).toFile();
        backupLogFile = Files.createFile(tempDir.resolve("backup.reg")).toFile();

        // Set the required system property for InstallWindowsRegistry.register()
        System.setProperty("client4j.launcher.path", exeFile.getAbsolutePath());

        // Set up AppInfo with realistic data
        appInfo = new AppInfo();
        appInfo.setTitle("Test Application");
        appInfo.setVendor("Test Vendor");
        appInfo.setVersion("1.0.0");
        appInfo.setDescription("Test application for registry integration tests");
        appInfo.setNpmPackage("test-app");
        appInfo.setNpmVersion("1.0.0");
        appInfo.setNpmSource("https://registry.npmjs.org/test-app");

        // Add document type associations
        appInfo.addDocumentMimetype("txt", "text/plain");
        appInfo.addDocumentMimetype("json", "application/json");

        // Add URL schemes
        appInfo.addUrlScheme("http");
        appInfo.addUrlScheme("https");
        appInfo.addUrlScheme("myapp");

        // Add directory association
        appInfo.setDirectoryAssociation("OpenAs", "Open with Test Application", iconFile.getAbsolutePath());

        // Set up registry operations and installer
        registryOps = new InMemoryRegistryOperations();
        ByteArrayOutputStream backupLog = new ByteArrayOutputStream();
        installer = new InstallWindowsRegistry(appInfo, exeFile, iconFile, backupLog, registryOps);
        
        // Skip WinRegistry operations when testing with in-memory registry
        installer.setSkipWinRegistryOperations(true);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("client4j.launcher.path");
    }

    @Test
    void testAddToUserPath_AddsPathToEmptyEnvironment() {
        File binDir = new File("C:\\Program Files\\TestApp\\bin");
        boolean changed = installer.addToUserPath(binDir);

        assertTrue(changed, "addToUserPath should return true when adding to empty PATH");
        assertTrue(registryOps.keyExists("Environment"), "Environment key should exist");
        String path = registryOps.getStringValue("Environment", "Path");
        assertNotNull(path, "Path value should exist");
        assertTrue(path.contains(binDir.getAbsolutePath()), "Path should contain the bin directory");
    }

    @Test
    void testAddToUserPath_AppendsToDependentPath() {
        // Pre-populate PATH with an existing entry
        registryOps.setStringValue("Environment", "Path", "C:\\Windows\\System32");

        File binDir = new File("C:\\Program Files\\TestApp\\bin");
        boolean changed = installer.addToUserPath(binDir);

        assertTrue(changed, "addToUserPath should return true when appending");
        String path = registryOps.getStringValue("Environment", "Path");
        assertTrue(path.contains("C:\\Windows\\System32"), "Original PATH entry should be preserved");
        assertTrue(path.contains(binDir.getAbsolutePath()), "New bin directory should be added");
        assertTrue(path.indexOf("C:\\Windows\\System32") < path.indexOf(binDir.getAbsolutePath()),
            "Original entry should come before new entry");
    }

    @Test
    void testAddToUserPath_IdempotentWhenAlreadyPresent() {
        File binDir = new File("C:\\Program Files\\TestApp\\bin");
        
        // Add first time
        boolean firstChange = installer.addToUserPath(binDir);
        assertTrue(firstChange, "First add should return true");
        String pathAfterFirst = registryOps.getStringValue("Environment", "Path");

        // Add same path again
        boolean secondChange = installer.addToUserPath(binDir);
        assertFalse(secondChange, "Second add of same path should return false");
        String pathAfterSecond = registryOps.getStringValue("Environment", "Path");

        assertEquals(pathAfterFirst, pathAfterSecond, "PATH should not change when adding duplicate entry");
    }

    @Test
    void testRemoveFromUserPath_RemovesPathEntry() {
        File binDir = new File("C:\\Program Files\\TestApp\\bin");
        
        // First add the path
        installer.addToUserPath(binDir);
        String pathBefore = registryOps.getStringValue("Environment", "Path");
        assertTrue(pathBefore.contains(binDir.getAbsolutePath()), "Path should contain bin directory");

        // Now remove it
        boolean changed = installer.removeFromUserPath(binDir);
        
        assertTrue(changed, "removeFromUserPath should return true when removing existing path");
        String pathAfter = registryOps.getStringValue("Environment", "Path");
        assertFalse(pathAfter.contains(binDir.getAbsolutePath()), "Path should not contain bin directory after removal");
    }

    @Test
    void testRemoveFromUserPath_HandlesMultipleEntries() {
        File binDir1 = new File("C:\\Program Files\\App1\\bin");
        File binDir2 = new File("C:\\Program Files\\App2\\bin");
        
        // Add both paths
        installer.addToUserPath(binDir1);
        installer.addToUserPath(binDir2);
        
        // Remove first one
        boolean changed = installer.removeFromUserPath(binDir1);
        
        assertTrue(changed, "Should return true when removing");
        String pathAfter = registryOps.getStringValue("Environment", "Path");
        assertFalse(pathAfter.contains(binDir1.getAbsolutePath()), "First path should be removed");
        assertTrue(pathAfter.contains(binDir2.getAbsolutePath()), "Second path should remain");
    }

    @Test
    void testRemoveFromUserPath_IdempotentWhenNotPresent() {
        File binDir = new File("C:\\Program Files\\TestApp\\bin");
        
        boolean changed = installer.removeFromUserPath(binDir);
        
        assertFalse(changed, "Removing non-existent path should return false");
    }

    @Test
    void testRegister_CreatesCapabilitiesKey() throws IOException {
        installer.register();

        String capabilitiesPath = "Software\\Clients\\Other\\jdeploy.test-app\\Capabilities";
        assertTrue(registryOps.keyExists(capabilitiesPath), "Capabilities key should be created");
        assertEquals("Test Application", 
            registryOps.getStringValue(capabilitiesPath, "ApplicationName"),
            "ApplicationName should be set");
        assertEquals("Test application for registry integration tests",
            registryOps.getStringValue(capabilitiesPath, "ApplicationDescription"),
            "ApplicationDescription should be set");
    }

    @Test
    void testRegister_RegistersFileAssociations() throws IOException {
        installer.register();

        // Check file associations path exists
        String fileAssociationsPath = "Software\\Clients\\Other\\jdeploy.test-app\\Capabilities\\FileAssociations";
        assertTrue(registryOps.keyExists(fileAssociationsPath), "FileAssociations key should be created");
        
        // Verify extensions are registered
        Map<String, Object> values = registryOps.getValues(fileAssociationsPath);
        assertTrue(values.containsKey(".txt"), ".txt should be registered");
        assertTrue(values.containsKey(".json"), ".json should be registered");
    }

    @Test
    void testRegister_RegistersURLSchemes() throws IOException {
        installer.register();

        String urlAssociationsPath = "Software\\Clients\\Other\\jdeploy.test-app\\Capabilities\\URLAssociations";
        assertTrue(registryOps.keyExists(urlAssociationsPath), "URLAssociations key should be created");
        
        Map<String, Object> values = registryOps.getValues(urlAssociationsPath);
        assertTrue(values.containsKey("http"), "http scheme should be registered");
        assertTrue(values.containsKey("https"), "https scheme should be registered");
        assertTrue(values.containsKey("myapp"), "myapp scheme should be registered");
    }

    @Test
    void testRegister_RegistersDirectoryAssociations() throws IOException {
        installer.register();

        String directoryShellKey = "Software\\Classes\\Directory\\shell\\jdeploy.test-app.file";
        assertTrue(registryOps.keyExists(directoryShellKey), "Directory shell key should be created");
        assertEquals("Open with Test Application", 
            registryOps.getStringValue(directoryShellKey, null),
            "Directory context menu text should be set");

        String commandKey = directoryShellKey + "\\command";
        assertTrue(registryOps.keyExists(commandKey), "Directory command key should be created");
        String command = registryOps.getStringValue(commandKey, null);
        assertTrue(command.contains(exeFile.getAbsolutePath()), "Command should reference exe file");
    }

    @Test
    void testRegister_RegistersUninstallEntry() throws IOException {
        installer.register();

        String uninstallKey = "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\jdeploy.test-app";
        assertTrue(registryOps.keyExists(uninstallKey), "Uninstall key should be created");
        
        assertEquals("Test Application", 
            registryOps.getStringValue(uninstallKey, "DisplayName"),
            "DisplayName should be set");
        assertEquals("1.0.0",
            registryOps.getStringValue(uninstallKey, "DisplayVersion"),
            "DisplayVersion should be set");
        assertEquals("Test Vendor",
            registryOps.getStringValue(uninstallKey, "Publisher"),
            "Publisher should be set");
    }

    @Test
    void testRegister_RegistersApplication() throws IOException {
        installer.register();

        assertTrue(registryOps.keyExists("Software\\RegisteredApplications"), 
            "RegisteredApplications key should exist");
        
        String appName = registryOps.getStringValue("Software\\RegisteredApplications", "jdeploy.test-app");
        assertNotNull(appName, "Application should be registered");
        assertTrue(appName.contains("Capabilities"), "Registration should point to Capabilities key");
    }

    @Test
    void testUnregister_RemovesFileAssociations() throws IOException {
        // First register
        installer.register();
        assertTrue(registryOps.keyExists("Software\\Clients\\Other\\jdeploy.test-app\\Capabilities\\FileAssociations"),
            "FileAssociations should exist after register");

        // Then unregister
        installer.unregister(backupLogFile);

        // File associations path should be removed
        assertFalse(registryOps.keyExists("Software\\Clients\\Other\\jdeploy.test-app\\Capabilities\\FileAssociations"),
            "FileAssociations should be removed after unregister");
    }

    @Test
    void testUnregister_RemovesURLAssociations() throws IOException {
        installer.register();
        assertTrue(registryOps.keyExists("Software\\Clients\\Other\\jdeploy.test-app\\Capabilities\\URLAssociations"),
            "URLAssociations should exist after register");

        installer.unregister(backupLogFile);

        assertFalse(registryOps.keyExists("Software\\Clients\\Other\\jdeploy.test-app\\Capabilities\\URLAssociations"),
            "URLAssociations should be removed after unregister");
    }

    @Test
    void testUnregister_RemovesDirectoryAssociations() throws IOException {
        installer.register();
        assertTrue(registryOps.keyExists("Software\\Classes\\Directory\\shell\\jdeploy.test-app.file"),
            "Directory shell key should exist after register");

        installer.unregister(backupLogFile);

        assertFalse(registryOps.keyExists("Software\\Classes\\Directory\\shell\\jdeploy.test-app.file"),
            "Directory shell key should be removed after unregister");
    }

    @Test
    void testUnregister_RemovesUninstallEntry() throws IOException {
        installer.register();
        assertTrue(registryOps.keyExists("Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\jdeploy.test-app"),
            "Uninstall key should exist after register");

        installer.unregister(backupLogFile);

        assertFalse(registryOps.keyExists("Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\jdeploy.test-app"),
            "Uninstall key should be removed after unregister");
    }

    @Test
    void testUnregister_RemovesRegistryKey() throws IOException {
        installer.register();
        assertTrue(registryOps.keyExists("Software\\Clients\\Other\\jdeploy.test-app"),
            "Registry key should exist after register");

        installer.unregister(backupLogFile);

        assertFalse(registryOps.keyExists("Software\\Clients\\Other\\jdeploy.test-app"),
            "Registry key should be removed after unregister");
    }

    @Test
    void testComputePathWithAdded_HandlesNullAndEmpty() {
        assertEquals("C:\\bin", InstallWindowsRegistry.computePathWithAdded(null, "C:\\bin"));
        assertEquals("C:\\bin", InstallWindowsRegistry.computePathWithAdded("", "C:\\bin"));
        assertEquals("C:\\Windows", InstallWindowsRegistry.computePathWithAdded("C:\\Windows", null));
        assertEquals("C:\\Windows", InstallWindowsRegistry.computePathWithAdded("C:\\Windows", ""));
    }

    @Test
    void testComputePathWithAdded_IsCaseInsensitive() {
        String result = InstallWindowsRegistry.computePathWithAdded("C:\\WINDOWS\\SYSTEM32", "c:\\windows\\system32");
        assertEquals("C:\\WINDOWS\\SYSTEM32", result, "Should detect duplicate with case-insensitive comparison");
    }

    @Test
    void testComputePathWithRemoved_HandlesNullAndEmpty() {
        assertEquals("C:\\Windows", InstallWindowsRegistry.computePathWithRemoved("C:\\Windows", null));
        assertEquals("C:\\Windows", InstallWindowsRegistry.computePathWithRemoved("C:\\Windows", ""));
        assertNull(InstallWindowsRegistry.computePathWithRemoved(null, "C:\\bin"));
        assertEquals("", InstallWindowsRegistry.computePathWithRemoved("", "C:\\bin"));
    }

    @Test
    void testComputePathWithRemoved_IsCaseInsensitive() {
        String result = InstallWindowsRegistry.computePathWithRemoved("C:\\BIN;C:\\WINDOWS", "c:\\bin");
        assertEquals("C:\\WINDOWS", result, "Should remove entry with case-insensitive comparison");
    }
}

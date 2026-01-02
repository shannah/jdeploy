package ca.weblite.jdeploy.installer.uninstall;

import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.ParserConfigurationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UninstallManifestXmlGenerator.
 * Verifies that the generated XML document structure matches XSD expectations.
 */
public class UninstallManifestXmlGeneratorTest {
    private static final String NAMESPACE = "http://jdeploy.ca/uninstall-manifest/1.0";
    private UninstallManifestXmlGenerator generator;

    @BeforeEach
    public void setUp() {
        generator = new UninstallManifestXmlGenerator();
    }

    @Test
    public void testGenerateMinimalManifest() throws ParserConfigurationException {
        UninstallManifest manifest = UninstallManifest.builder()
                .version("1.0")
                .packageInfo(createMinimalPackageInfo())
                .files(Collections.emptyList())
                .directories(Collections.emptyList())
                .build();

        Document doc = generator.generate(manifest);
        assertNotNull(doc);
        assertEquals(1, doc.getChildNodes().getLength());
        
        Element root = doc.getDocumentElement();
        assertEquals("uninstall-manifest", root.getLocalName());
        assertEquals(NAMESPACE, root.getNamespaceURI());
        assertEquals("1.0", root.getAttribute("version"));
    }

    @Test
    public void testRootElementNamespaceAndVersion() throws ParserConfigurationException {
        UninstallManifest manifest = createCompleteManifest();
        Document doc = generator.generate(manifest);

        Element root = doc.getDocumentElement();
        assertEquals("uninstall-manifest", root.getLocalName());
        assertEquals(NAMESPACE, root.getNamespaceURI());
        assertEquals("1.0", root.getAttribute("version"));
    }

    @Test
    public void testPackageInfoElementExists() throws ParserConfigurationException {
        UninstallManifest manifest = createCompleteManifest();
        Document doc = generator.generate(manifest);

        NodeList packageInfos = doc.getElementsByTagNameNS(NAMESPACE, "package-info");
        assertEquals(1, packageInfos.getLength());
        
        Element packageInfo = (Element) packageInfos.item(0);
        assertNotNull(packageInfo);
    }

    @Test
    public void testPackageInfoContent() throws ParserConfigurationException {
        UninstallManifest.PackageInfo info = createCompletePackageInfo();
        UninstallManifest manifest = UninstallManifest.builder()
                .version("1.0")
                .packageInfo(info)
                .files(Collections.emptyList())
                .directories(Collections.emptyList())
                .build();

        Document doc = generator.generate(manifest);
        Element packageInfo = (Element) doc.getElementsByTagNameNS(NAMESPACE, "package-info").item(0);

        assertEquals(info.getName(), getElementTextContent(packageInfo, "name"));
        assertEquals(info.getSource(), getElementTextContent(packageInfo, "source"));
        assertEquals(info.getVersion(), getElementTextContent(packageInfo, "version"));
        assertEquals(info.getFullyQualifiedName(), getElementTextContent(packageInfo, "fully-qualified-name"));
        assertEquals(info.getArchitecture(), getElementTextContent(packageInfo, "architecture"));
        assertEquals(info.getInstalledAt().toString(), getElementTextContent(packageInfo, "installed-at"));
        assertEquals(info.getInstallerVersion(), getElementTextContent(packageInfo, "installer-version"));
    }

    @Test
    public void testFilesElementExists() throws ParserConfigurationException {
        UninstallManifest manifest = createCompleteManifest();
        Document doc = generator.generate(manifest);

        NodeList files = doc.getElementsByTagNameNS(NAMESPACE, "files");
        assertEquals(1, files.getLength());
    }

    @Test
    public void testFileElements() throws ParserConfigurationException {
        List<UninstallManifest.InstalledFile> fileList = new ArrayList<>();
        fileList.add(UninstallManifest.InstalledFile.builder()
                .path("/usr/local/bin/myapp")
                .type(UninstallManifest.FileType.BINARY)
                .description("Main executable")
                .build());
        fileList.add(UninstallManifest.InstalledFile.builder()
                .path("/etc/myapp.conf")
                .type(UninstallManifest.FileType.CONFIG)
                .build());

        UninstallManifest manifest = UninstallManifest.builder()
                .version("1.0")
                .packageInfo(createMinimalPackageInfo())
                .files(fileList)
                .directories(Collections.emptyList())
                .build();

        Document doc = generator.generate(manifest);
        NodeList fileElements = doc.getElementsByTagNameNS(NAMESPACE, "file");
        assertEquals(2, fileElements.getLength());

        Element file1 = (Element) fileElements.item(0);
        assertEquals("binary", file1.getAttribute("type"));
        assertEquals("/usr/local/bin/myapp", getElementTextContent(file1, "path"));
        assertEquals("Main executable", getElementTextContent(file1, "description"));

        Element file2 = (Element) fileElements.item(1);
        assertEquals("config", file2.getAttribute("type"));
        assertEquals("/etc/myapp.conf", getElementTextContent(file2, "path"));
    }

    @Test
    public void testDirectoriesElementExists() throws ParserConfigurationException {
        UninstallManifest manifest = createCompleteManifest();
        Document doc = generator.generate(manifest);

        NodeList directories = doc.getElementsByTagNameNS(NAMESPACE, "directories");
        assertEquals(1, directories.getLength());
    }

    @Test
    public void testDirectoryElements() throws ParserConfigurationException {
        List<UninstallManifest.InstalledDirectory> dirList = new ArrayList<>();
        dirList.add(UninstallManifest.InstalledDirectory.builder()
                .path("/opt/myapp")
                .cleanup(UninstallManifest.CleanupStrategy.ALWAYS)
                .description("Main app directory")
                .build());
        dirList.add(UninstallManifest.InstalledDirectory.builder()
                .path("/var/myapp/logs")
                .cleanup(UninstallManifest.CleanupStrategy.IF_EMPTY)
                .build());

        UninstallManifest manifest = UninstallManifest.builder()
                .version("1.0")
                .packageInfo(createMinimalPackageInfo())
                .files(Collections.emptyList())
                .directories(dirList)
                .build();

        Document doc = generator.generate(manifest);
        NodeList dirElements = doc.getElementsByTagNameNS(NAMESPACE, "directory");
        assertEquals(2, dirElements.getLength());

        Element dir1 = (Element) dirElements.item(0);
        assertEquals("always", dir1.getAttribute("cleanup"));
        assertEquals("/opt/myapp", getElementTextContent(dir1, "path"));
        assertEquals("Main app directory", getElementTextContent(dir1, "description"));

        Element dir2 = (Element) dirElements.item(1);
        assertEquals("ifEmpty", dir2.getAttribute("cleanup"));
        assertEquals("/var/myapp/logs", getElementTextContent(dir2, "path"));
    }

    @Test
    public void testRegistryElementExists() throws ParserConfigurationException {
        UninstallManifest manifest = createCompleteManifest();
        Document doc = generator.generate(manifest);

        NodeList registry = doc.getElementsByTagNameNS(NAMESPACE, "registry");
        assertEquals(1, registry.getLength());
    }

    @Test
    public void testRegistryCreatedKeys() throws ParserConfigurationException {
        List<UninstallManifest.RegistryKey> keys = new ArrayList<>();
        keys.add(UninstallManifest.RegistryKey.builder()
                .root(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER)
                .path("Software\\MyApp")
                .description("Application registry key")
                .build());

        UninstallManifest.RegistryInfo registry = UninstallManifest.RegistryInfo.builder()
                .createdKeys(keys)
                .modifiedValues(Collections.emptyList())
                .build();

        UninstallManifest manifest = UninstallManifest.builder()
                .version("1.0")
                .packageInfo(createMinimalPackageInfo())
                .files(Collections.emptyList())
                .directories(Collections.emptyList())
                .registry(registry)
                .build();

        Document doc = generator.generate(manifest);
        NodeList keyElements = doc.getElementsByTagNameNS(NAMESPACE, "key");
        assertEquals(1, keyElements.getLength());

        Element key = (Element) keyElements.item(0);
        assertEquals("HKEY_CURRENT_USER", key.getAttribute("root"));
        assertEquals("Software\\MyApp", getElementTextContent(key, "path"));
        assertEquals("Application registry key", getElementTextContent(key, "description"));
    }

    @Test
    public void testRegistryModifiedValues() throws ParserConfigurationException {
        List<UninstallManifest.ModifiedRegistryValue> values = new ArrayList<>();
        values.add(UninstallManifest.ModifiedRegistryValue.builder()
                .root(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER)
                .path("Environment")
                .name("PATH")
                .previousValue("C:\\Program Files\\OldApp;")
                .previousType(UninstallManifest.RegistryValueType.REG_EXPAND_SZ)
                .description("PATH environment variable modification")
                .build());

        UninstallManifest.RegistryInfo registry = UninstallManifest.RegistryInfo.builder()
                .createdKeys(Collections.emptyList())
                .modifiedValues(values)
                .build();

        UninstallManifest manifest = UninstallManifest.builder()
                .version("1.0")
                .packageInfo(createMinimalPackageInfo())
                .files(Collections.emptyList())
                .directories(Collections.emptyList())
                .registry(registry)
                .build();

        Document doc = generator.generate(manifest);
        NodeList valueElements = doc.getElementsByTagNameNS(NAMESPACE, "value");
        assertEquals(1, valueElements.getLength());

        Element value = (Element) valueElements.item(0);
        assertEquals("HKEY_CURRENT_USER", value.getAttribute("root"));
        assertEquals("REG_EXPAND_SZ", value.getAttribute("previous-type"));
        assertEquals("Environment", getElementTextContent(value, "path"));
        assertEquals("PATH", getElementTextContent(value, "name"));
        assertEquals("C:\\Program Files\\OldApp;", getElementTextContent(value, "previous-value"));
        assertEquals("PATH environment variable modification", getElementTextContent(value, "description"));
    }

    @Test
    public void testPathModificationsElementExists() throws ParserConfigurationException {
        UninstallManifest manifest = createCompleteManifest();
        Document doc = generator.generate(manifest);

        NodeList pathMods = doc.getElementsByTagNameNS(NAMESPACE, "path-modifications");
        assertEquals(1, pathMods.getLength());
    }

    @Test
    public void testWindowsPathEntries() throws ParserConfigurationException {
        List<UninstallManifest.WindowsPathEntry> entries = new ArrayList<>();
        entries.add(UninstallManifest.WindowsPathEntry.builder()
                .addedEntry("C:\\Program Files\\MyApp\\bin")
                .description("Main bin directory")
                .build());

        UninstallManifest.PathModifications pathMods = UninstallManifest.PathModifications.builder()
                .windowsPaths(entries)
                .shellProfiles(Collections.emptyList())
                .gitBashProfiles(Collections.emptyList())
                .build();

        UninstallManifest manifest = UninstallManifest.builder()
                .version("1.0")
                .packageInfo(createMinimalPackageInfo())
                .files(Collections.emptyList())
                .directories(Collections.emptyList())
                .pathModifications(pathMods)
                .build();

        Document doc = generator.generate(manifest);
        NodeList winPathEntries = doc.getElementsByTagNameNS(NAMESPACE, "entry");
        assertTrue(winPathEntries.getLength() >= 1);

        // Find the windows-paths element and its entry
        NodeList windowsElements = doc.getElementsByTagNameNS(NAMESPACE, "windows-paths");
        assertEquals(1, windowsElements.getLength());
        Element windowsElement = (Element) windowsElements.item(0);
        NodeList windowsEntries = windowsElement.getElementsByTagNameNS(NAMESPACE, "entry");
        assertEquals(1, windowsEntries.getLength());

        Element entry = (Element) windowsEntries.item(0);
        assertEquals("C:\\Program Files\\MyApp\\bin", getElementTextContent(entry, "added-entry"));
        assertEquals("Main bin directory", getElementTextContent(entry, "description"));
    }

    @Test
    public void testShellProfileEntries() throws ParserConfigurationException {
        List<UninstallManifest.ShellProfileEntry> entries = new ArrayList<>();
        entries.add(UninstallManifest.ShellProfileEntry.builder()
                .file("~/.bashrc")
                .exportLine("export PATH=$PATH:/usr/local/myapp/bin")
                .description("Bash profile entry")
                .build());

        UninstallManifest.PathModifications pathMods = UninstallManifest.PathModifications.builder()
                .windowsPaths(Collections.emptyList())
                .shellProfiles(entries)
                .gitBashProfiles(Collections.emptyList())
                .build();

        UninstallManifest manifest = UninstallManifest.builder()
                .version("1.0")
                .packageInfo(createMinimalPackageInfo())
                .files(Collections.emptyList())
                .directories(Collections.emptyList())
                .pathModifications(pathMods)
                .build();

        Document doc = generator.generate(manifest);
        NodeList shellElements = doc.getElementsByTagNameNS(NAMESPACE, "shell-profiles");
        assertEquals(1, shellElements.getLength());

        Element shellElement = (Element) shellElements.item(0);
        NodeList shellEntries = shellElement.getElementsByTagNameNS(NAMESPACE, "entry");
        assertEquals(1, shellEntries.getLength());

        Element entry = (Element) shellEntries.item(0);
        assertEquals("~/.bashrc", getElementTextContent(entry, "file"));
        assertEquals("export PATH=$PATH:/usr/local/myapp/bin", getElementTextContent(entry, "export-line"));
        assertEquals("Bash profile entry", getElementTextContent(entry, "description"));
    }

    @Test
    public void testGitBashProfileEntries() throws ParserConfigurationException {
        List<UninstallManifest.GitBashProfileEntry> entries = new ArrayList<>();
        entries.add(UninstallManifest.GitBashProfileEntry.builder()
                .file("~/.bash_profile")
                .exportLine("export PATH=$PATH:/usr/local/myapp/bin")
                .description("Git Bash profile entry")
                .build());

        UninstallManifest.PathModifications pathMods = UninstallManifest.PathModifications.builder()
                .windowsPaths(Collections.emptyList())
                .shellProfiles(Collections.emptyList())
                .gitBashProfiles(entries)
                .build();

        UninstallManifest manifest = UninstallManifest.builder()
                .version("1.0")
                .packageInfo(createMinimalPackageInfo())
                .files(Collections.emptyList())
                .directories(Collections.emptyList())
                .pathModifications(pathMods)
                .build();

        Document doc = generator.generate(manifest);
        NodeList gitBashElements = doc.getElementsByTagNameNS(NAMESPACE, "git-bash-profiles");
        assertEquals(1, gitBashElements.getLength());

        Element gitBashElement = (Element) gitBashElements.item(0);
        NodeList gitBashEntries = gitBashElement.getElementsByTagNameNS(NAMESPACE, "entry");
        assertEquals(1, gitBashEntries.getLength());

        Element entry = (Element) gitBashEntries.item(0);
        assertEquals("~/.bash_profile", getElementTextContent(entry, "file"));
        assertEquals("export PATH=$PATH:/usr/local/myapp/bin", getElementTextContent(entry, "export-line"));
        assertEquals("Git Bash profile entry", getElementTextContent(entry, "description"));
    }

    @Test
    public void testEmptyPathModifications() throws ParserConfigurationException {
        UninstallManifest.PathModifications pathMods = UninstallManifest.PathModifications.builder()
                .windowsPaths(Collections.emptyList())
                .shellProfiles(Collections.emptyList())
                .gitBashProfiles(Collections.emptyList())
                .build();

        UninstallManifest manifest = UninstallManifest.builder()
                .version("1.0")
                .packageInfo(createMinimalPackageInfo())
                .files(Collections.emptyList())
                .directories(Collections.emptyList())
                .pathModifications(pathMods)
                .build();

        Document doc = generator.generate(manifest);
        NodeList pathModsElements = doc.getElementsByTagNameNS(NAMESPACE, "path-modifications");
        assertEquals(1, pathModsElements.getLength());

        Element pathModsElement = (Element) pathModsElements.item(0);
        NodeList children = pathModsElement.getChildNodes();
        assertEquals(0, children.getLength());
    }

    @Test
    public void testCompleteManifestStructure() throws ParserConfigurationException {
        UninstallManifest manifest = createCompleteManifest();
        Document doc = generator.generate(manifest);

        Element root = doc.getDocumentElement();
        assertEquals("uninstall-manifest", root.getLocalName());
        assertEquals("1.0", root.getAttribute("version"));

        NodeList packageInfos = root.getElementsByTagNameNS(NAMESPACE, "package-info");
        assertEquals(1, packageInfos.getLength());

        NodeList filesElements = root.getElementsByTagNameNS(NAMESPACE, "files");
        assertEquals(1, filesElements.getLength());

        NodeList directoriesElements = root.getElementsByTagNameNS(NAMESPACE, "directories");
        assertEquals(1, directoriesElements.getLength());

        NodeList registryElements = root.getElementsByTagNameNS(NAMESPACE, "registry");
        assertEquals(1, registryElements.getLength());

        NodeList pathModsElements = root.getElementsByTagNameNS(NAMESPACE, "path-modifications");
        assertEquals(1, pathModsElements.getLength());
    }

    @Test
    public void testNullRegistryHandling() throws ParserConfigurationException {
        UninstallManifest manifest = UninstallManifest.builder()
                .version("1.0")
                .packageInfo(createMinimalPackageInfo())
                .files(Collections.emptyList())
                .directories(Collections.emptyList())
                .registry(null)
                .build();

        Document doc = generator.generate(manifest);
        NodeList registryElements = doc.getElementsByTagNameNS(NAMESPACE, "registry");
        assertEquals(0, registryElements.getLength());
    }

    @Test
    public void testNullPathModificationsHandling() throws ParserConfigurationException {
        UninstallManifest manifest = UninstallManifest.builder()
                .version("1.0")
                .packageInfo(createMinimalPackageInfo())
                .files(Collections.emptyList())
                .directories(Collections.emptyList())
                .pathModifications(null)
                .build();

        Document doc = generator.generate(manifest);
        NodeList pathModsElements = doc.getElementsByTagNameNS(NAMESPACE, "path-modifications");
        assertEquals(0, pathModsElements.getLength());
    }

    @Test
    public void testMultipleFileTypes() throws ParserConfigurationException {
        List<UninstallManifest.InstalledFile> files = new ArrayList<>();
        for (UninstallManifest.FileType type : UninstallManifest.FileType.values()) {
            files.add(UninstallManifest.InstalledFile.builder()
                    .path("/path/to/" + type.getValue())
                    .type(type)
                    .build());
        }

        UninstallManifest manifest = UninstallManifest.builder()
                .version("1.0")
                .packageInfo(createMinimalPackageInfo())
                .files(files)
                .directories(Collections.emptyList())
                .build();

        Document doc = generator.generate(manifest);
        NodeList fileElements = doc.getElementsByTagNameNS(NAMESPACE, "file");
        assertEquals(UninstallManifest.FileType.values().length, fileElements.getLength());

        for (int i = 0; i < fileElements.getLength(); i++) {
            Element file = (Element) fileElements.item(i);
            assertTrue(file.hasAttribute("type"));
            String typeValue = file.getAttribute("type");
            assertTrue(typeValue.matches("binary|script|link|config|icon|metadata"));
        }
    }

    @Test
    public void testMultipleCleanupStrategies() throws ParserConfigurationException {
        List<UninstallManifest.InstalledDirectory> directories = new ArrayList<>();
        for (UninstallManifest.CleanupStrategy strategy : UninstallManifest.CleanupStrategy.values()) {
            directories.add(UninstallManifest.InstalledDirectory.builder()
                    .path("/path/" + strategy.getValue())
                    .cleanup(strategy)
                    .build());
        }

        UninstallManifest manifest = UninstallManifest.builder()
                .version("1.0")
                .packageInfo(createMinimalPackageInfo())
                .files(Collections.emptyList())
                .directories(directories)
                .build();

        Document doc = generator.generate(manifest);
        NodeList dirElements = doc.getElementsByTagNameNS(NAMESPACE, "directory");
        assertEquals(UninstallManifest.CleanupStrategy.values().length, dirElements.getLength());

        for (int i = 0; i < dirElements.getLength(); i++) {
            Element dir = (Element) dirElements.item(i);
            assertTrue(dir.hasAttribute("cleanup"));
            String cleanupValue = dir.getAttribute("cleanup");
            assertTrue(cleanupValue.matches("always|ifEmpty|contentsOnly"));
        }
    }

    // Helper methods
    private UninstallManifest.PackageInfo createMinimalPackageInfo() {
        return UninstallManifest.PackageInfo.builder()
                .name("test-app")
                .version("1.0.0")
                .fullyQualifiedName("hash.test-app")
                .architecture("x64")
                .installedAt(Instant.parse("2024-01-15T10:30:45Z"))
                .installerVersion("2.0.0")
                .build();
    }

    private UninstallManifest.PackageInfo createCompletePackageInfo() {
        return UninstallManifest.PackageInfo.builder()
                .name("test-app")
                .source("https://github.com/user/test-app")
                .version("1.0.0")
                .fullyQualifiedName("hash.test-app")
                .architecture("x64")
                .installedAt(Instant.parse("2024-01-15T10:30:45Z"))
                .installerVersion("2.0.0")
                .build();
    }

    private UninstallManifest createCompleteManifest() {
        List<UninstallManifest.InstalledFile> files = new ArrayList<>();
        files.add(UninstallManifest.InstalledFile.builder()
                .path("/usr/local/bin/app")
                .type(UninstallManifest.FileType.BINARY)
                .build());

        List<UninstallManifest.InstalledDirectory> directories = new ArrayList<>();
        directories.add(UninstallManifest.InstalledDirectory.builder()
                .path("/opt/app")
                .cleanup(UninstallManifest.CleanupStrategy.ALWAYS)
                .build());

        List<UninstallManifest.RegistryKey> keys = new ArrayList<>();
        keys.add(UninstallManifest.RegistryKey.builder()
                .root(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER)
                .path("Software\\TestApp")
                .build());

        UninstallManifest.RegistryInfo registry = UninstallManifest.RegistryInfo.builder()
                .createdKeys(keys)
                .modifiedValues(Collections.emptyList())
                .build();

        List<UninstallManifest.WindowsPathEntry> windowsPaths = new ArrayList<>();
        windowsPaths.add(UninstallManifest.WindowsPathEntry.builder()
                .addedEntry("C:\\Program Files\\TestApp\\bin")
                .build());

        UninstallManifest.PathModifications pathMods = UninstallManifest.PathModifications.builder()
                .windowsPaths(windowsPaths)
                .shellProfiles(Collections.emptyList())
                .gitBashProfiles(Collections.emptyList())
                .build();

        return UninstallManifest.builder()
                .version("1.0")
                .packageInfo(createCompletePackageInfo())
                .files(files)
                .directories(directories)
                .registry(registry)
                .pathModifications(pathMods)
                .build();
    }

    private String getElementTextContent(Element parent, String elementName) {
        NodeList nodeList = parent.getElementsByTagNameNS(NAMESPACE, elementName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }
}

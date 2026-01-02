package ca.weblite.jdeploy.installer.uninstall;

import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UninstallManifestValidator.
 * Verifies basic structural validation and error handling.
 * Schema validation tests verify the validator properly reports schema errors.
 */
public class UninstallManifestValidatorTest {
    private static final String NAMESPACE = "http://jdeploy.ca/uninstall-manifest/1.0";
    
    private UninstallManifestValidator validator;
    private UninstallManifestXmlGenerator generator;

    @BeforeEach
    public void setUp() {
        validator = new UninstallManifestValidator();
        generator = new UninstallManifestXmlGenerator();
    }

    @Test
    public void testBasicStructureValidation() throws Exception {
        // Test that basic structure validation passes for well-formed documents
        Document doc = createDocumentBuilder().newDocument();
        Element root = doc.createElementNS(NAMESPACE, "uninstallManifest");
        root.setAttribute("version", "1.0");
        doc.appendChild(root);
        
        // Basic structure validation should pass (root element and version attribute present)
        // Schema validation may fail but that's tested separately
        try {
            validator.validate(doc);
        } catch (ManifestValidationException e) {
            // If schema validation fails, verify it's a schema error not basic validation
            assertTrue(e.getMessage().contains("schema") || e.getDetailedMessage().contains("schema"),
                "Expected schema validation error, not basic validation error");
        }
    }

    @Test
    public void testGeneratedManifestHasValidBasicStructure() throws Exception {
        UninstallManifest manifest = UninstallManifest.builder()
                .version("1.0")
                .packageInfo(createMinimalPackageInfo())
                .files(Collections.emptyList())
                .directories(Collections.emptyList())
                .build();

        Document doc = generator.generate(manifest);
        
        // Verify document has correct root element and version
        assertNotNull(doc.getDocumentElement());
        assertEquals("uninstallManifest", doc.getDocumentElement().getLocalName());
        assertTrue(doc.getDocumentElement().hasAttribute("version"));
    }

    @Test
    public void testMissingRootElement() {
        Document doc = createEmptyDocument();
        
        ManifestValidationException ex = assertThrows(
            ManifestValidationException.class,
            () -> validator.validate(doc)
        );
        
        assertTrue(ex.getMessage().contains("root element") || ex.getMessage().contains("Document has no"),
            "Expected error about missing root element, got: " + ex.getMessage());
    }

    @Test
    public void testMissingPackageInfoCaughtBySchemaValidation() throws Exception {
        Document doc = createDocumentBuilder().newDocument();
        Element root = doc.createElementNS(NAMESPACE, "uninstallManifest");
        root.setAttribute("version", "1.0");
        doc.appendChild(root);
        
        Element files = doc.createElementNS(NAMESPACE, "files");
        root.appendChild(files);
        
        Element directories = doc.createElementNS(NAMESPACE, "directories");
        root.appendChild(directories);
        
        // This document has valid basic structure but missing package-info element.
        // Schema validation should catch this as an error.
        ManifestValidationException ex = assertThrows(
            ManifestValidationException.class,
            () -> validator.validate(doc),
            "Schema validation should reject document missing required package-info"
        );
        
        assertNotNull(ex.getDetailedMessage());
    }

    @Test
    public void testMissingMandatoryManifestVersionAttribute() throws Exception {
        Document doc = createDocumentBuilder().newDocument();
        Element root = doc.createElementNS(NAMESPACE, "uninstallManifest");
        // Missing version attribute - should fail basic validation
        doc.appendChild(root);
        
        ManifestValidationException ex = assertThrows(
            ManifestValidationException.class,
            () -> validator.validate(doc)
        );
        
        assertTrue(ex.getMessage().contains("version"),
            "Expected error about missing version, got: " + ex.getMessage());
    }

    @Test
    public void testSchemaValidationRejectsInvalidFileType() throws Exception {
        Document doc = createDocumentBuilder().newDocument();
        Element root = doc.createElementNS(NAMESPACE, "uninstallManifest");
        root.setAttribute("version", "1.0");
        doc.appendChild(root);
        
        Element packageInfo = doc.createElementNS(NAMESPACE, "packageInfo");
        appendTextElement(doc, packageInfo, "name", "test-app");
        appendTextElement(doc, packageInfo, "version", "1.0.0");
        appendTextElement(doc, packageInfo, "fullyQualifiedName", "hash.test-app");
        appendTextElement(doc, packageInfo, "architecture", "x64");
        appendTextElement(doc, packageInfo, "installedAt", "2024-01-15T10:30:45Z");
        appendTextElement(doc, packageInfo, "installerVersion", "2.0.0");
        root.appendChild(packageInfo);
        
        Element files = doc.createElementNS(NAMESPACE, "files");
        Element invalidFile = doc.createElementNS(NAMESPACE, "file");
        appendTextElement(doc, invalidFile, "type", "invalidType");
        Element path = doc.createElementNS(NAMESPACE, "path");
        path.setTextContent("/path/to/file");
        invalidFile.appendChild(path);
        files.appendChild(invalidFile);
        root.appendChild(files);
        
        Element directories = doc.createElementNS(NAMESPACE, "directories");
        root.appendChild(directories);
        
        // Schema validation should reject the invalid file type
        ManifestValidationException ex = assertThrows(
            ManifestValidationException.class,
            () -> validator.validate(doc),
            "Schema validation should reject invalid file type"
        );
        
        assertNotNull(ex.getDetailedMessage());
    }

    @Test
    public void testSchemaValidationRejectsInvalidCleanupStrategy() throws Exception {
        Document doc = createDocumentBuilder().newDocument();
        Element root = doc.createElementNS(NAMESPACE, "uninstallManifest");
        root.setAttribute("version", "1.0");
        doc.appendChild(root);
        
        Element packageInfo = doc.createElementNS(NAMESPACE, "packageInfo");
        appendTextElement(doc, packageInfo, "name", "test-app");
        appendTextElement(doc, packageInfo, "version", "1.0.0");
        appendTextElement(doc, packageInfo, "fullyQualifiedName", "hash.test-app");
        appendTextElement(doc, packageInfo, "architecture", "x64");
        appendTextElement(doc, packageInfo, "installedAt", "2024-01-15T10:30:45Z");
        appendTextElement(doc, packageInfo, "installerVersion", "2.0.0");
        root.appendChild(packageInfo);
        
        Element files = doc.createElementNS(NAMESPACE, "files");
        root.appendChild(files);
        
        Element directories = doc.createElementNS(NAMESPACE, "directories");
        Element invalidDir = doc.createElementNS(NAMESPACE, "directory");
        appendTextElement(doc, invalidDir, "cleanup", "invalidStrategy");
        Element dirPath = doc.createElementNS(NAMESPACE, "path");
        dirPath.setTextContent("/path/to/dir");
        invalidDir.appendChild(dirPath);
        directories.appendChild(invalidDir);
        root.appendChild(directories);
        
        // Schema validation should reject the invalid cleanup strategy
        ManifestValidationException ex = assertThrows(
            ManifestValidationException.class,
            () -> validator.validate(doc),
            "Schema validation should reject invalid cleanup strategy"
        );
        
        assertNotNull(ex.getDetailedMessage());
    }

    @Test
    public void testSchemaValidationRejectsInvalidRegistryRoot() throws Exception {
        Document doc = createDocumentBuilder().newDocument();
        Element root = doc.createElementNS(NAMESPACE, "uninstallManifest");
        root.setAttribute("version", "1.0");
        doc.appendChild(root);
        
        Element packageInfo = doc.createElementNS(NAMESPACE, "packageInfo");
        appendTextElement(doc, packageInfo, "name", "test-app");
        appendTextElement(doc, packageInfo, "version", "1.0.0");
        appendTextElement(doc, packageInfo, "fullyQualifiedName", "hash.test-app");
        appendTextElement(doc, packageInfo, "architecture", "x64");
        appendTextElement(doc, packageInfo, "installedAt", "2024-01-15T10:30:45Z");
        appendTextElement(doc, packageInfo, "installerVersion", "2.0.0");
        root.appendChild(packageInfo);
        
        Element files = doc.createElementNS(NAMESPACE, "files");
        root.appendChild(files);
        
        Element directories = doc.createElementNS(NAMESPACE, "directories");
        root.appendChild(directories);
        
        Element registry = doc.createElementNS(NAMESPACE, "registry");
        Element createdKeys = doc.createElementNS(NAMESPACE, "createdKeys");
        Element invalidKey = doc.createElementNS(NAMESPACE, "createdKey");
        appendTextElement(doc, invalidKey, "root", "INVALID_ROOT");
        Element keyPath = doc.createElementNS(NAMESPACE, "path");
        keyPath.setTextContent("Software\\Test");
        invalidKey.appendChild(keyPath);
        createdKeys.appendChild(invalidKey);
        registry.appendChild(createdKeys);
        
        Element modifiedValues = doc.createElementNS(NAMESPACE, "modifiedValues");
        registry.appendChild(modifiedValues);
        
        root.appendChild(registry);
        
        // Schema validation should reject the invalid registry root
        ManifestValidationException ex = assertThrows(
            ManifestValidationException.class,
            () -> validator.validate(doc),
            "Schema validation should reject invalid registry root"
        );
        
        assertNotNull(ex.getDetailedMessage());
    }

    @Test
    public void testSchemaValidationRejectsInvalidRegistryValueType() throws Exception {
        Document doc = createDocumentBuilder().newDocument();
        Element root = doc.createElementNS(NAMESPACE, "uninstallManifest");
        root.setAttribute("version", "1.0");
        doc.appendChild(root);
        
        Element packageInfo = doc.createElementNS(NAMESPACE, "packageInfo");
        appendTextElement(doc, packageInfo, "name", "test-app");
        appendTextElement(doc, packageInfo, "version", "1.0.0");
        appendTextElement(doc, packageInfo, "fullyQualifiedName", "hash.test-app");
        appendTextElement(doc, packageInfo, "architecture", "x64");
        appendTextElement(doc, packageInfo, "installedAt", "2024-01-15T10:30:45Z");
        appendTextElement(doc, packageInfo, "installerVersion", "2.0.0");
        root.appendChild(packageInfo);
        
        Element files = doc.createElementNS(NAMESPACE, "files");
        root.appendChild(files);
        
        Element directories = doc.createElementNS(NAMESPACE, "directories");
        root.appendChild(directories);
        
        Element registry = doc.createElementNS(NAMESPACE, "registry");
        Element createdKeys = doc.createElementNS(NAMESPACE, "createdKeys");
        registry.appendChild(createdKeys);
        
        Element modifiedValues = doc.createElementNS(NAMESPACE, "modifiedValues");
        Element invalidValue = doc.createElementNS(NAMESPACE, "modifiedValue");
        appendTextElement(doc, invalidValue, "root", "HKEY_CURRENT_USER");
        appendTextElement(doc, invalidValue, "previousType", "INVALID_TYPE");
        Element valuePath = doc.createElementNS(NAMESPACE, "path");
        valuePath.setTextContent("Environment");
        Element valueName = doc.createElementNS(NAMESPACE, "name");
        valueName.setTextContent("TEST");
        invalidValue.appendChild(valuePath);
        invalidValue.appendChild(valueName);
        modifiedValues.appendChild(invalidValue);
        registry.appendChild(modifiedValues);
        
        root.appendChild(registry);
        
        // Schema validation should reject the invalid value type
        ManifestValidationException ex = assertThrows(
            ManifestValidationException.class,
            () -> validator.validate(doc),
            "Schema validation should reject invalid registry value type"
        );
        
        assertNotNull(ex.getDetailedMessage());
    }

    @Test
    public void testDetailedErrorMessageOnMissingVersion() throws Exception {
        Document doc = createDocumentBuilder().newDocument();
        Element root = doc.createElementNS(NAMESPACE, "uninstallManifest");
        // Missing version attribute intentionally
        doc.appendChild(root);
        
        ManifestValidationException ex = assertThrows(
            ManifestValidationException.class,
            () -> validator.validate(doc)
        );
        
        String message = ex.getMessage();
        assertNotNull(message);
        assertTrue(message.contains("version"),
            "Expected error about missing version, got: " + message);
    }

    @Test
    public void testGeneratedCompleteManifestStructure() throws Exception {
        UninstallManifest manifest = UninstallManifest.builder()
                .version("1.0")
                .packageInfo(
                    UninstallManifest.PackageInfo.builder()
                            .name("test-app")
                            .source("https://github.com/user/test-app")
                            .version("1.0.0")
                            .fullyQualifiedName("hash.test-app")
                            .architecture("x64")
                            .installedAt(Instant.parse("2024-01-15T10:30:45Z"))
                            .installerVersion("2.0.0")
                            .build()
                )
                .files(Collections.singletonList(
                    UninstallManifest.InstalledFile.builder()
                            .path("/usr/local/bin/app")
                            .type(UninstallManifest.FileType.BINARY)
                            .description("Main executable")
                            .build()
                ))
                .directories(Collections.singletonList(
                    UninstallManifest.InstalledDirectory.builder()
                            .path("/opt/app")
                            .cleanup(UninstallManifest.CleanupStrategy.ALWAYS)
                            .description("Application directory")
                            .build()
                ))
                .registry(
                    UninstallManifest.RegistryInfo.builder()
                            .createdKeys(Collections.singletonList(
                                UninstallManifest.RegistryKey.builder()
                                        .root(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER)
                                        .path("Software\\TestApp")
                                        .description("Test app registry key")
                                        .build()
                            ))
                            .modifiedValues(Collections.singletonList(
                                UninstallManifest.ModifiedRegistryValue.builder()
                                        .root(UninstallManifest.RegistryRoot.HKEY_CURRENT_USER)
                                        .path("Environment")
                                        .name("PATH")
                                        .previousValue("C:\\OldPath;")
                                        .previousType(UninstallManifest.RegistryValueType.REG_EXPAND_SZ)
                                        .description("PATH modification")
                                        .build()
                            ))
                            .build()
                )
                .pathModifications(
                    UninstallManifest.PathModifications.builder()
                            .windowsPaths(Collections.singletonList(
                                UninstallManifest.WindowsPathEntry.builder()
                                        .addedEntry("C:\\Program Files\\TestApp\\bin")
                                        .description("Windows PATH entry")
                                        .build()
                            ))
                            .shellProfiles(Collections.singletonList(
                                UninstallManifest.ShellProfileEntry.builder()
                                        .file("~/.bashrc")
                                        .exportLine("export PATH=$PATH:/usr/local/app/bin")
                                        .description("Bash profile entry")
                                        .build()
                            ))
                            .gitBashProfiles(Collections.singletonList(
                                UninstallManifest.GitBashProfileEntry.builder()
                                        .file("~/.bash_profile")
                                        .exportLine("export PATH=$PATH:/usr/local/app/bin")
                                        .description("Git Bash profile entry")
                                        .build()
                            ))
                            .build()
                )
                .build();

        Document doc = generator.generate(manifest);
        
        // Verify basic structure is correct
        assertNotNull(doc.getDocumentElement());
        assertEquals("uninstallManifest", doc.getDocumentElement().getLocalName());
        assertEquals("1.0", doc.getDocumentElement().getAttribute("version"));
    }

    @Test
    public void testSchemaValidationErrorIncludesDetails() throws Exception {
        Document doc = createDocumentBuilder().newDocument();
        Element root = doc.createElementNS(NAMESPACE, "uninstallManifest");
        root.setAttribute("version", "1.0");
        doc.appendChild(root);
        
        // Missing required elements - schema should catch this
        Element files = doc.createElementNS(NAMESPACE, "files");
        root.appendChild(files);
        
        Element directories = doc.createElementNS(NAMESPACE, "directories");
        root.appendChild(directories);
        
        // Should fail schema validation due to missing package-info
        ManifestValidationException ex = assertThrows(
            ManifestValidationException.class,
            () -> validator.validate(doc),
            "Schema validation should reject document missing required elements"
        );
        
        // Verify detailed message is provided
        assertNotNull(ex.getDetailedMessage());
        assertTrue(ex.getDetailedMessage().length() > 0,
            "Detailed message should contain validation error info");
    }

    @Test
    public void testNullDocumentThrowsException() {
        ManifestValidationException ex = assertThrows(
            ManifestValidationException.class,
            () -> validator.validate(null)
        );
        
        assertTrue(ex.getMessage().contains("null"),
            "Expected error about null document, got: " + ex.getMessage());
    }

    @Test
    public void testWrongRootElementName() throws Exception {
        Document doc = createDocumentBuilder().newDocument();
        Element root = doc.createElementNS(NAMESPACE, "wrongElement");
        root.setAttribute("version", "1.0");
        doc.appendChild(root);
        
        ManifestValidationException ex = assertThrows(
            ManifestValidationException.class,
            () -> validator.validate(doc)
        );
        
        assertTrue(ex.getMessage().contains("uninstallManifest") || ex.getMessage().contains("wrongElement"),
            "Expected error about wrong root element, got: " + ex.getMessage());
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

    private Document createEmptyDocument() {
        try {
            return createDocumentBuilder().newDocument();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void appendTextElement(Document doc, Element parent, String elementName, String textContent) {
        Element child = doc.createElementNS(NAMESPACE, elementName);
        child.setTextContent(textContent);
        parent.appendChild(child);
    }

    private DocumentBuilder createDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder();
    }

}

package ca.weblite.jdeploy.installer.uninstall;

import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import ca.weblite.jdeploy.installer.util.ArchitectureUtil;
import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;

/**
 * File-based implementation of UninstallManifestRepository.
 * 
 * Stores manifests as XML files at: ~/.jdeploy/manifests/{arch}/{fqpn}/uninstall-manifest.xml
 * 
 * Where:
 * - {arch} is the system architecture (e.g., "x64" or "arm64")
 * - {fqpn} is the fully-qualified package name (computed from packageName and source)
 */
public class FileUninstallManifestRepository implements UninstallManifestRepository {

    private static final String MANIFEST_DIR_NAME = "manifests";
    private static final String JDEPLOY_HOME = ".jdeploy";
    private static final String MANIFEST_FILENAME = "uninstall-manifest.xml";

    private final UninstallManifestXmlGenerator xmlGenerator;
    private final UninstallManifestValidator validator;
    private final UninstallManifestXmlParser xmlParser;
    private final boolean skipSchemaValidation;

    /**
     * Creates a repository with default XML generator and validator.
     */
    public FileUninstallManifestRepository() {
        this(new UninstallManifestXmlGenerator(), 
             new UninstallManifestValidator(), 
             new UninstallManifestXmlParser(), 
             false);
    }

    /**
     * Creates a repository with custom schema validation setting.
     */
    public FileUninstallManifestRepository(boolean skipSchemaValidation) {
        this(new UninstallManifestXmlGenerator(), 
             new UninstallManifestValidator(), 
             new UninstallManifestXmlParser(), 
             skipSchemaValidation);
    }

    /**
     * Creates a repository with custom XML generator and validator.
     */
    public FileUninstallManifestRepository(UninstallManifestXmlGenerator xmlGenerator,
                                           UninstallManifestValidator validator) {
        this(xmlGenerator, validator, new UninstallManifestXmlParser(), false);
    }

    /**
     * Creates a repository with all dependencies provided.
     */
    public FileUninstallManifestRepository(UninstallManifestXmlGenerator xmlGenerator,
                                           UninstallManifestValidator validator,
                                           UninstallManifestXmlParser xmlParser,
                                           boolean skipSchemaValidation) {
        this.xmlGenerator = xmlGenerator;
        this.validator = validator;
        this.xmlParser = xmlParser;
        this.skipSchemaValidation = skipSchemaValidation;
    }

    /**
     * Saves an uninstall manifest to disk.
     *
     * @param manifest the manifest to save
     * @throws Exception if an error occurs during save
     */
    @Override
    public void save(UninstallManifest manifest) throws Exception {
        File manifestFile = getManifestFile(manifest.getPackageInfo().getName(), 
                                            manifest.getPackageInfo().getSource());
        
        // Create parent directories if they don't exist
        File parentDir = manifestFile.getParentFile();
        if (!parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Failed to create manifest directory: " + parentDir);
            }
        }

        // Generate XML document
        Document doc = xmlGenerator.generate(manifest);
        
        // Validate the document (unless skipped)
        if (!skipSchemaValidation) {
            validator.validate(doc);
        }

        // Write to file
        String xmlContent = documentToString(doc);
        FileUtils.writeStringToFile(manifestFile, xmlContent, "UTF-8");
    }

    /**
     * Loads an uninstall manifest for the given package.
     *
     * @param packageName the package name
     * @param source the GitHub source URL (null for NPM packages)
     * @return an Optional containing the manifest if found, empty otherwise
     * @throws Exception if an error occurs during load
     */
    @Override
    public Optional<UninstallManifest> load(String packageName, String source) throws Exception {
        File manifestFile = getManifestFile(packageName, source);
        
        if (!manifestFile.exists()) {
            return Optional.empty();
        }

        try {
            String xmlContent = FileUtils.readFileToString(manifestFile, "UTF-8");
            Document doc = parseXml(xmlContent);
            
            // Validate the document (unless skipped)
            if (!skipSchemaValidation) {
                validator.validate(doc);
            }
            
            UninstallManifest manifest = xmlParser.parse(doc);
            return Optional.of(manifest);
        } catch (Exception e) {
            // If parsing or validation fails, return empty
            return Optional.empty();
        }
    }

    /**
     * Deletes the uninstall manifest for the given package.
     *
     * @param packageName the package name
     * @param source the GitHub source URL (null for NPM packages)
     * @throws Exception if an error occurs during delete
     */
    @Override
    public void delete(String packageName, String source) throws Exception {
        File manifestDir = getManifestDirectory(packageName, source);
        
        if (manifestDir.exists()) {
            FileUtils.deleteDirectory(manifestDir);
        }
    }

    /**
     * Gets the directory where manifests for a package are stored.
     * 
     * Path: ~/.jdeploy/manifests/{arch}/{fqpn}/
     *
     * @param packageName the package name
     * @param source the GitHub source URL (null for NPM packages)
     * @return the manifest directory
     */
    private File getManifestDirectory(String packageName, String source) {
        String arch = ArchitectureUtil.getArchitecture();
        String fqpn = CliCommandBinDirResolver.computeFullyQualifiedPackageName(packageName, source);
        
        File jdeployHome = new File(System.getProperty("user.home"), JDEPLOY_HOME);
        File manifestsDir = new File(jdeployHome, MANIFEST_DIR_NAME);
        File archDir = new File(manifestsDir, arch);
        
        return new File(archDir, fqpn);
    }

    /**
     * Gets the file path for the manifest.
     * 
     * Path: ~/.jdeploy/manifests/{arch}/{fqpn}/uninstall-manifest.xml
     *
     * @param packageName the package name
     * @param source the GitHub source URL (null for NPM packages)
     * @return the manifest file
     */
    private File getManifestFile(String packageName, String source) {
        File manifestDir = getManifestDirectory(packageName, source);
        return new File(manifestDir, MANIFEST_FILENAME);
    }

    /**
     * Converts an XML Document to a string.
     *
     * @param doc the XML document
     * @return the XML as a string
     * @throws Exception if conversion fails
     */
    private String documentToString(Document doc) throws Exception {
        javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
        javax.xml.transform.Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8");
        
        javax.xml.transform.dom.DOMSource source = new javax.xml.transform.dom.DOMSource(doc);
        java.io.StringWriter writer = new java.io.StringWriter();
        javax.xml.transform.stream.StreamResult result = new javax.xml.transform.stream.StreamResult(writer);
        transformer.transform(source, result);
        return writer.toString();
    }

    /**
     * Parses an XML string into a Document.
     *
     * @param xmlContent the XML content as a string
     * @return the parsed Document
     * @throws ParserConfigurationException if parser configuration fails
     * @throws SAXException if XML parsing fails
     * @throws IOException if I/O error occurs
     */
    private Document parseXml(String xmlContent) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xmlContent)));
    }
}

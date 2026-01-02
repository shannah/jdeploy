package ca.weblite.jdeploy.installer.uninstall;

import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import ca.weblite.jdeploy.installer.util.ArchitectureUtil;
import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
import org.w3c.dom.Document;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;

/**
 * High-level coordinator for writing UninstallManifest objects to disk.
 * 
 * Orchestrates:
 * 1. XML generation via UninstallManifestXmlGenerator
 * 2. Validation via UninstallManifestValidator
 * 3. Disk writing using javax.xml.transform.Transformer
 * 
 * Manifests are stored at: ~/.jdeploy/manifests/{arch}/{fqpn}/uninstall-manifest.xml
 * 
 * Where:
 * - {arch} is the system architecture (e.g., "x64" or "arm64")
 * - {fqpn} is the fully-qualified package name
 */
public class UninstallManifestWriter {
    private static final String MANIFEST_DIR_NAME = "manifests";
    private static final String JDEPLOY_HOME = ".jdeploy";
    private static final String MANIFEST_FILENAME = "uninstall-manifest.xml";

    private final UninstallManifestXmlGenerator xmlGenerator;
    private final UninstallManifestValidator validator;
    private final boolean skipSchemaValidation;

    /**
     * Constructs an UninstallManifestWriter with default generator and validator.
     */
    public UninstallManifestWriter() {
        this(new UninstallManifestXmlGenerator(), new UninstallManifestValidator(), false);
    }

    /**
     * Constructs an UninstallManifestWriter with option to skip schema validation.
     * Useful for testing or when schema is not available.
     *
     * @param skipSchemaValidation if true, skip XSD schema validation (basic structure validation still runs)
     */
    public UninstallManifestWriter(boolean skipSchemaValidation) {
        this(new UninstallManifestXmlGenerator(), new UninstallManifestValidator(), skipSchemaValidation);
    }

    /**
     * Constructs an UninstallManifestWriter with provided generator and validator.
     * Useful for testing with mocks.
     *
     * @param xmlGenerator the XML generator to use
     * @param validator the validator to use
     */
    public UninstallManifestWriter(UninstallManifestXmlGenerator xmlGenerator, 
                                   UninstallManifestValidator validator) {
        this(xmlGenerator, validator, false);
    }

    /**
     * Constructs an UninstallManifestWriter with all parameters.
     *
     * @param xmlGenerator the XML generator to use
     * @param validator the validator to use
     * @param skipSchemaValidation if true, skip XSD schema validation
     */
    public UninstallManifestWriter(UninstallManifestXmlGenerator xmlGenerator, 
                                   UninstallManifestValidator validator,
                                   boolean skipSchemaValidation) {
        this.xmlGenerator = xmlGenerator;
        this.validator = validator;
        this.skipSchemaValidation = skipSchemaValidation;
    }

    /**
     * Writes an UninstallManifest to the default location.
     * 
     * The destination directory is computed as:
     * ~/.jdeploy/manifests/{arch}/{fqpn}/
     * 
     * The manifest file is named: uninstall-manifest.xml
     *
     * @param manifest the UninstallManifest to write
     * @return the File where the manifest was written
     * @throws IOException if directory creation fails
     * @throws ManifestValidationException if the manifest fails validation
     * @throws TransformerException if XML writing fails
     * @throws ParserConfigurationException if XML generation fails
     */
    public File write(UninstallManifest manifest) throws IOException, ManifestValidationException, 
                                                         TransformerException, ParserConfigurationException {
        File destination = computeDefaultDestinationFile(manifest.getPackageInfo());
        return write(manifest, destination);
    }

    /**
     * Writes an UninstallManifest to a specified destination file.
     * 
     * Creates parent directories if they don't exist, generates XML, validates it,
     * then writes to disk with proper formatting.
     *
     * @param manifest the UninstallManifest to write
     * @param destination the File where the manifest should be written
     * @return the File where the manifest was written
     * @throws IOException if directory creation or file writing fails
     * @throws ManifestValidationException if the manifest fails validation
     * @throws TransformerException if XML writing fails
     * @throws ParserConfigurationException if XML generation fails
     */
    public File write(UninstallManifest manifest, File destination) throws IOException, 
                                                                           ManifestValidationException, 
                                                                           TransformerException,
                                                                           ParserConfigurationException {
        // Create parent directories if they don't exist
        File parentDir = destination.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Failed to create manifest directory: " + parentDir.getAbsolutePath());
            }
        }

        // Generate XML from manifest
        Document document = xmlGenerator.generate(manifest);

        // Validate the generated XML (basic structure validation always runs)
        if (!skipSchemaValidation) {
            validator.validate(document);
        } else {
            // Still perform basic structural validation even when skipping schema validation
            validateBasicStructure(document);
        }

        // Write to disk using Transformer
        writeDocumentToFile(document, destination);

        return destination;
    }

    /**
     * Writes a DOM Document to a file with pretty-printing.
     *
     * @param document the XML Document to write
     * @param file the File to write to
     * @throws TransformerException if XML transformation fails
     * @throws IOException if file writing fails
     */
    private void writeDocumentToFile(Document document, File file) throws TransformerException, IOException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        
        // Configure for pretty-printing
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.VERSION, "1.0");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        DOMSource source = new DOMSource(document);
        StreamResult result = new StreamResult(file);

        transformer.transform(source, result);
    }

    /**
     * Converts a Document to a pretty-printed string for debugging.
     *
     * @param document the XML Document to convert
     * @return the XML as a string
     * @throws TransformerException if transformation fails
     */
    private String documentToString(Document document) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.VERSION, "1.0");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        DOMSource source = new DOMSource(document);
        javax.xml.transform.stream.StreamResult result = new javax.xml.transform.stream.StreamResult(new java.io.StringWriter());
        transformer.transform(source, result);
        
        return result.getWriter().toString();
    }

    /**
     * Performs basic structural validation of the manifest document.
     * This runs even when schema validation is skipped.
     *
     * @param document the XML Document to validate
     * @throws ManifestValidationException if basic structure is invalid
     */
    private void validateBasicStructure(Document document) throws ManifestValidationException {
        if (document == null) {
            throw new ManifestValidationException("Document cannot be null");
        }
        
        if (document.getDocumentElement() == null) {
            throw new ManifestValidationException("Document has no root element");
        }
        
        String rootName = document.getDocumentElement().getLocalName();
        if (rootName == null) {
            rootName = document.getDocumentElement().getNodeName();
        }
        
        if (!"uninstallManifest".equals(rootName)) {
            throw new ManifestValidationException(
                "Expected root element 'uninstallManifest', got '" + rootName + "'"
            );
        }
        
        if (!document.getDocumentElement().hasAttribute("version")) {
            throw new ManifestValidationException("Root element missing required 'version' attribute");
        }
    }

    /**
     * Computes the default destination file for a manifest based on package info.
     * 
     * Path: ~/.jdeploy/manifests/{arch}/{fqpn}/uninstall-manifest.xml
     *
     * @param packageInfo the package info containing name and source
     * @return the File path where the manifest should be written
     */
    private File computeDefaultDestinationFile(UninstallManifest.PackageInfo packageInfo) {
        String arch = ArchitectureUtil.getArchitecture();
        String fqpn = CliCommandBinDirResolver.computeFullyQualifiedPackageName(
            packageInfo.getName(), 
            packageInfo.getSource()
        );

        File jdeployHome = new File(System.getProperty("user.home"), JDEPLOY_HOME);
        File manifestsDir = new File(jdeployHome, MANIFEST_DIR_NAME);
        File archDir = new File(manifestsDir, arch);
        File fqpnDir = new File(archDir, fqpn);

        return new File(fqpnDir, MANIFEST_FILENAME);
    }
}

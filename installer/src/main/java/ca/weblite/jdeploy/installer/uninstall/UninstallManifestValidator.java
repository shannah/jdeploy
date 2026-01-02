package ca.weblite.jdeploy.installer.uninstall;

import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates uninstall manifest XML documents against the uninstall-manifest XSD schema.
 * Uses javax.xml.validation.SchemaFactory to load and apply the schema from classpath.
 */
public class UninstallManifestValidator {
    private static final String SCHEMA_RESOURCE = "/ca/weblite/jdeploy/installer/schemas/uninstall-manifest.xsd";
    private static final String SCHEMA_FACTORY_CLASS = "http://www.w3.org/2001/XMLSchema";
    
    private final Schema schema;
    private final boolean schemaAvailable;

    /**
     * Constructs an UninstallManifestValidator by loading the XSD schema from classpath.
     * If the schema is not available, validation will perform basic structural checks only.
     */
    public UninstallManifestValidator() {
        Schema loadedSchema = null;
        boolean available = false;
        
        try {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(SCHEMA_FACTORY_CLASS);
            InputStream schemaStream = UninstallManifestValidator.class.getResourceAsStream(SCHEMA_RESOURCE);
            
            if (schemaStream != null) {
                Source schemaSource = new javax.xml.transform.stream.StreamSource(schemaStream);
                loadedSchema = schemaFactory.newSchema(schemaSource);
                available = true;
            }
        } catch (Exception e) {
            // Schema not available, will use basic validation only
        }
        
        this.schema = loadedSchema;
        this.schemaAvailable = available;
    }

    /**
     * Validates an XML document against the uninstall manifest schema.
     * Performs basic structural validation even if XSD schema is not available.
     *
     * @param document the XML document to validate
     * @throws ManifestValidationException if validation fails, containing detailed error messages
     */
    public void validate(Document document) throws ManifestValidationException {
        if (document == null) {
            throw new ManifestValidationException("Document cannot be null");
        }
        
        // Perform basic structural validation
        validateBasicStructure(document);
        
        // If schema is available, perform full schema validation
        if (schemaAvailable && schema != null) {
            validateAgainstSchema(document);
        }
    }

    /**
     * Performs basic structural validation of the manifest document.
     */
    private void validateBasicStructure(Document document) throws ManifestValidationException {
        if (document.getDocumentElement() == null) {
            throw new ManifestValidationException("Document has no root element");
        }
        
        String rootName = document.getDocumentElement().getLocalName();
        if (rootName == null) {
            rootName = document.getDocumentElement().getNodeName();
        }
        
        if (!rootName.equals("uninstall-manifest")) {
            throw new ManifestValidationException(
                "Expected root element 'uninstall-manifest', got '" + rootName + "'"
            );
        }
        
        if (!document.getDocumentElement().hasAttribute("version")) {
            throw new ManifestValidationException("Root element missing required 'version' attribute");
        }
    }

    /**
     * Validates the document against the XSD schema.
     */
    private void validateAgainstSchema(Document document) throws ManifestValidationException {
        try {
            Validator validator = schema.newValidator();
            
            // Collect validation errors
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            
            validator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) {
                    warnings.add(formatException(exception));
                }

                @Override
                public void error(SAXParseException exception) {
                    errors.add(formatException(exception));
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    errors.add(formatException(exception));
                    throw exception;
                }
                
                private String formatException(SAXParseException e) {
                    return String.format("Line %d, Column %d: %s", 
                        e.getLineNumber(), e.getColumnNumber(), e.getMessage());
                }
            });
            
            Source source = new DOMSource(document);
            validator.validate(source);
            
            // If there were errors, throw exception with detailed messages
            if (!errors.isEmpty()) {
                StringBuilder detailedMessage = new StringBuilder();
                detailedMessage.append("Schema validation failed with ").append(errors.size()).append(" error(s):\n");
                for (int i = 0; i < errors.size(); i++) {
                    detailedMessage.append(i + 1).append(". ").append(errors.get(i)).append("\n");
                }
                
                if (!warnings.isEmpty()) {
                    detailedMessage.append("\nWarnings (").append(warnings.size()).append("):\n");
                    for (int i = 0; i < warnings.size(); i++) {
                        detailedMessage.append(i + 1).append(". ").append(warnings.get(i)).append("\n");
                    }
                }
                
                String errorMessage = "Manifest XML does not conform to schema:\n" + detailedMessage.toString();
                System.err.println(errorMessage);
                
                throw new ManifestValidationException(
                    "Manifest XML does not conform to schema",
                    detailedMessage.toString()
                );
            }
            
        } catch (ManifestValidationException e) {
            throw e;
        } catch (SAXException e) {
            String errorMessage = "XML parsing error during validation: " + e.getMessage();
            System.err.println(errorMessage);
            if (e.getCause() != null) {
                System.err.println("Cause: " + e.getCause().getMessage());
            }
            throw new ManifestValidationException(errorMessage, e);
        } catch (IOException e) {
            throw new ManifestValidationException("IO error during validation: " + e.getMessage(), e);
        }
    }
}

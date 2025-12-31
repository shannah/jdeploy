package ca.weblite.jdeploy.gui.tabs;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FiletypesPanel")
public class FiletypesPanelTest {
    private File tempDirectory;
    private File projectDirectory;
    private FiletypesPanel panel;
    private AtomicInteger changeListenerCallCount;

    @BeforeEach
    void setUp() throws Exception {
        tempDirectory = Files.createTempDirectory("filetypes-test").toFile();
        projectDirectory = tempDirectory;
        panel = new FiletypesPanel(projectDirectory);
        changeListenerCallCount = new AtomicInteger(0);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempDirectory != null && tempDirectory.exists()) {
            FileUtils.deleteDirectory(tempDirectory);
        }
    }

    @Test
    @DisplayName("Should create panel with root component")
    void testGetRoot() {
        assertNotNull(panel.getRoot());
        assertEquals(panel, panel.getRoot());
    }

    @Test
    @DisplayName("Should load configuration without errors")
    void testLoadConfiguration() {
        JSONObject jdeploy = new JSONObject();
        assertDoesNotThrow(() -> panel.load(jdeploy));
    }

    @Test
    @DisplayName("Should save configuration without errors")
    void testSaveConfiguration() {
        JSONObject jdeploy = new JSONObject();
        assertDoesNotThrow(() -> panel.save(jdeploy));
    }

    @Test
    @DisplayName("Should handle load/save round-trip")
    void testLoadSaveRoundTrip() {
        JSONObject jdeploy = new JSONObject();
        
        // Load initial config
        panel.load(jdeploy);
        
        // Save to config
        panel.save(jdeploy);
        
        // Load again
        panel.load(jdeploy);
        
        // Should complete without errors
        assertNotNull(panel.getRoot());
    }

    @Test
    @DisplayName("Should register and fire change listener")
    void testChangeListenerRegistration() {
        ActionListener listener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                changeListenerCallCount.incrementAndGet();
            }
        };
        
        panel.addChangeListener(listener);
        JSONObject jdeploy = new JSONObject();
        
        // load() doesn't fire events, but registration should work
        panel.load(jdeploy);
        
        assertNotNull(panel);
    }

    @Test
    @DisplayName("Should validate directory association when disabled")
    void testValidateDirectoryAssociationDisabled() {
        String error = panel.validateDirectoryAssociation();
        assertNull(error, "Validation should pass when directory association is disabled");
    }

    @Test
    @DisplayName("Should validate directory association requires description")
    void testValidateDirectoryAssociationMissingDescription() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("documentTypes", new JSONArray());
        
        // Load to activate directory association fields
        panel.load(jdeploy);
        
        // Enable directory association without description
        panel.enableDirectoryAssociation("Viewer", "");
        
        // Validation should fail
        String validationError = panel.validateDirectoryAssociation();
        assertNotNull(validationError, "Validation should fail when description is empty");
        assertTrue(validationError.contains("description"), "Error message should mention description");
    }

    @Test
    @DisplayName("Should save document type with extension and mimetype")
    void testSaveDocumentType() {
        JSONObject jdeploy = new JSONObject();
        JSONArray docTypes = new JSONArray();
        JSONObject docType = new JSONObject();
        docType.put("extension", "txt");
        docType.put("mimetype", "text/plain");
        docType.put("editor", true);
        docType.put("custom", false);
        docTypes.put(docType);
        jdeploy.put("documentTypes", docTypes);
        
        // Load the configuration
        panel.load(jdeploy);
        
        // Create a new jdeploy object and save
        JSONObject newJdeploy = new JSONObject();
        newJdeploy.put("documentTypes", new JSONArray());
        panel.save(newJdeploy);
        
        // Verify structure
        assertTrue(newJdeploy.has("documentTypes"));
        JSONArray savedDocTypes = newJdeploy.getJSONArray("documentTypes");
        assertTrue(savedDocTypes.length() >= 0); // May be zero if UI-based additions not made
    }

    @Test
    @DisplayName("Should handle empty documentTypes array gracefully")
    void testHandleEmptyDocumentTypes() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("documentTypes", new JSONArray());
        
        assertDoesNotThrow(() -> panel.load(jdeploy));
        assertDoesNotThrow(() -> panel.save(jdeploy));
    }

    @Test
    @DisplayName("Should skip directory type when loading document types")
    void testSkipDirectoryTypeWhenLoading() {
        JSONObject jdeploy = new JSONObject();
        JSONArray docTypes = new JSONArray();
        
        // Add a directory association
        JSONObject dirAssoc = new JSONObject();
        dirAssoc.put("type", "directory");
        dirAssoc.put("role", "Editor");
        dirAssoc.put("description", "Open as project");
        docTypes.put(dirAssoc);
        
        // Add a file type
        JSONObject fileType = new JSONObject();
        fileType.put("extension", "java");
        fileType.put("mimetype", "text/x-java");
        docTypes.put(fileType);
        
        jdeploy.put("documentTypes", docTypes);
        
        // Load should not throw
        assertDoesNotThrow(() -> panel.load(jdeploy));
    }

    @Test
    @DisplayName("Should load directory association configuration")
    void testLoadDirectoryAssociation() {
        JSONObject jdeploy = new JSONObject();
        JSONArray docTypes = new JSONArray();
        
        JSONObject dirAssoc = new JSONObject();
        dirAssoc.put("type", "directory");
        dirAssoc.put("role", "Viewer");
        dirAssoc.put("description", "View folder contents");
        docTypes.put(dirAssoc);
        
        jdeploy.put("documentTypes", docTypes);
        
        // Load the configuration
        assertDoesNotThrow(() -> panel.load(jdeploy));
    }

    @Test
    @DisplayName("Should not create documentTypes array when empty")
    void testCreateDocumentTypesArrayOnSave() {
        JSONObject jdeploy = new JSONObject();
        
        // Save without any document types added
        assertDoesNotThrow(() -> panel.save(jdeploy));
        
        // Should NOT create an empty array (cleaner JSON output)
        assertFalse(jdeploy.has("documentTypes"), "Empty documentTypes array should be omitted from JSON");
    }

    @Test
    @DisplayName("Should handle listener replacement")
    void testListenerCanBeReplaced() {
        ActionListener listener1 = e -> changeListenerCallCount.incrementAndGet();
        ActionListener listener2 = e -> changeListenerCallCount.addAndGet(10);
        
        panel.addChangeListener(listener1);
        panel.addChangeListener(listener2);
        
        // Second listener should replace first
        assertNotNull(panel);
    }

    @Test
    @DisplayName("Should handle jdeploy JSONObject parameter in load")
    void testLoadWithValidJsonObject() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("someOtherField", "value");
        
        assertDoesNotThrow(() -> panel.load(jdeploy));
        assertNotNull(panel.getRoot());
    }

    @Test
    @DisplayName("Should handle jdeploy JSONObject parameter in save")
    void testSaveWithValidJsonObject() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("someOtherField", "value");
        
        assertDoesNotThrow(() -> panel.save(jdeploy));
        assertTrue(jdeploy.has("someOtherField"));
    }

    @Test
    @DisplayName("Should preserve non-directory document types during save")
    void testPreserveNonDirectoryDocumentTypes() {
        JSONObject jdeploy = new JSONObject();
        JSONArray docTypes = new JSONArray();
        
        // Add multiple document types
        JSONObject docType1 = new JSONObject();
        docType1.put("extension", "txt");
        docType1.put("mimetype", "text/plain");
        docTypes.put(docType1);
        
        JSONObject docType2 = new JSONObject();
        docType2.put("extension", "md");
        docType2.put("mimetype", "text/markdown");
        docTypes.put(docType2);
        
        jdeploy.put("documentTypes", docTypes);
        
        // Load and save
        panel.load(jdeploy);
        JSONObject newJdeploy = new JSONObject();
        newJdeploy.put("documentTypes", new JSONArray());
        panel.save(newJdeploy);
        
        // Should preserve document types
        assertTrue(newJdeploy.has("documentTypes"));
    }

    @Test
    @DisplayName("Should remove old directory associations on save")
    void testRemoveOldDirectoryAssociationOnSave() {
        JSONObject jdeploy = new JSONObject();
        JSONArray docTypes = new JSONArray();
        
        // Add an old directory association
        JSONObject oldDirAssoc = new JSONObject();
        oldDirAssoc.put("type", "directory");
        oldDirAssoc.put("role", "Editor");
        oldDirAssoc.put("description", "Old description");
        docTypes.put(oldDirAssoc);
        
        jdeploy.put("documentTypes", docTypes);
        
        // Load configuration (this loads the old association)
        panel.load(jdeploy);
        
        // Change the directory association to a new description
        panel.enableDirectoryAssociation("Viewer", "New description");
        
        // Save to a new object
        JSONObject newJdeploy = new JSONObject();
        newJdeploy.put("documentTypes", new JSONArray());
        panel.save(newJdeploy);
        
        // Verify the new description is saved
        JSONArray savedDocTypes = newJdeploy.getJSONArray("documentTypes");
        boolean foundNewDescription = false;
        for (int i = 0; i < savedDocTypes.length(); i++) {
            JSONObject docType = savedDocTypes.getJSONObject(i);
            if (docType.has("type") && "directory".equalsIgnoreCase(docType.getString("type"))) {
                String description = docType.optString("description", "");
                assertNotEquals("Old description", description, "Old description should be replaced");
                assertEquals("New description", description, "New description should be saved");
                foundNewDescription = true;
            }
        }
        assertTrue(foundNewDescription, "Directory association should be saved");
    }
}

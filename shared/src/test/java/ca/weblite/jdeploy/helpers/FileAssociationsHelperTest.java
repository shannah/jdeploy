package ca.weblite.jdeploy.helpers;

import ca.weblite.jdeploy.models.DocumentTypeAssociation;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FileAssociationsHelperTest {

    @Test
    public void testParseFileAssociations() {
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONArray documentTypes = new JSONArray();

        JSONObject textFile = new JSONObject();
        textFile.put("extension", "txt");
        textFile.put("mimetype", "text/plain");
        textFile.put("editor", true);
        documentTypes.put(textFile);

        JSONObject htmlFile = new JSONObject();
        htmlFile.put("extension", "html");
        htmlFile.put("mimetype", "text/html");
        htmlFile.put("icon", "/path/to/html-icon.png");
        documentTypes.put(htmlFile);

        jdeploy.put("documentTypes", documentTypes);
        packageJSON.put("jdeploy", jdeploy);

        Iterable<DocumentTypeAssociation> associations =
            FileAssociationsHelper.getDocumentTypeAssociationsFromPackageJSON(packageJSON);

        List<DocumentTypeAssociation> list = new ArrayList<>();
        associations.forEach(list::add);

        assertEquals(2, list.size());

        // Check first association (txt)
        DocumentTypeAssociation txtAssoc = list.get(0);
        assertFalse(txtAssoc.isDirectory());
        assertEquals("txt", txtAssoc.getExtension());
        assertEquals("text/plain", txtAssoc.getMimetype());
        assertTrue(txtAssoc.isEditor());
        assertNull(txtAssoc.getIconPath());

        // Check second association (html)
        DocumentTypeAssociation htmlAssoc = list.get(1);
        assertFalse(htmlAssoc.isDirectory());
        assertEquals("html", htmlAssoc.getExtension());
        assertEquals("text/html", htmlAssoc.getMimetype());
        assertFalse(htmlAssoc.isEditor());
        assertEquals("/path/to/html-icon.png", htmlAssoc.getIconPath());
    }

    @Test
    public void testParseDirectoryAssociation() {
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONArray documentTypes = new JSONArray();

        JSONObject directory = new JSONObject();
        directory.put("type", "directory");
        directory.put("role", "Editor");
        directory.put("description", "Open folder as project");
        directory.put("icon", "/path/to/folder-icon.png");
        documentTypes.put(directory);

        jdeploy.put("documentTypes", documentTypes);
        packageJSON.put("jdeploy", jdeploy);

        Iterable<DocumentTypeAssociation> associations =
            FileAssociationsHelper.getDocumentTypeAssociationsFromPackageJSON(packageJSON);

        List<DocumentTypeAssociation> list = new ArrayList<>();
        associations.forEach(list::add);

        assertEquals(1, list.size());

        DocumentTypeAssociation dirAssoc = list.get(0);
        assertTrue(dirAssoc.isDirectory());
        assertTrue(dirAssoc.isValid());
        assertEquals("Editor", dirAssoc.getRole());
        assertEquals("Open folder as project", dirAssoc.getDescription());
        assertEquals("/path/to/folder-icon.png", dirAssoc.getIconPath());
        assertNull(dirAssoc.getExtension());
        assertNull(dirAssoc.getMimetype());
    }

    @Test
    public void testParseMixedAssociations() {
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONArray documentTypes = new JSONArray();

        // File association
        JSONObject textFile = new JSONObject();
        textFile.put("extension", "txt");
        textFile.put("mimetype", "text/plain");
        documentTypes.put(textFile);

        // Directory association
        JSONObject directory = new JSONObject();
        directory.put("type", "directory");
        directory.put("role", "Viewer");
        documentTypes.put(directory);

        jdeploy.put("documentTypes", documentTypes);
        packageJSON.put("jdeploy", jdeploy);

        Iterable<DocumentTypeAssociation> associations =
            FileAssociationsHelper.getDocumentTypeAssociationsFromPackageJSON(packageJSON);

        List<DocumentTypeAssociation> list = new ArrayList<>();
        associations.forEach(list::add);

        assertEquals(2, list.size());

        // First should be file
        assertFalse(list.get(0).isDirectory());
        assertEquals("txt", list.get(0).getExtension());

        // Second should be directory
        assertTrue(list.get(1).isDirectory());
        assertEquals("Viewer", list.get(1).getRole());
    }

    @Test
    public void testDirectoryAssociationCaseInsensitive() {
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONArray documentTypes = new JSONArray();

        // Test with different case variations
        JSONObject directory1 = new JSONObject();
        directory1.put("type", "DIRECTORY");
        directory1.put("role", "Editor");
        documentTypes.put(directory1);

        JSONObject directory2 = new JSONObject();
        directory2.put("type", "Directory");
        directory2.put("role", "Viewer");
        documentTypes.put(directory2);

        jdeploy.put("documentTypes", documentTypes);
        packageJSON.put("jdeploy", jdeploy);

        Iterable<DocumentTypeAssociation> associations =
            FileAssociationsHelper.getDocumentTypeAssociationsFromPackageJSON(packageJSON);

        List<DocumentTypeAssociation> list = new ArrayList<>();
        associations.forEach(list::add);

        assertEquals(2, list.size());
        assertTrue(list.get(0).isDirectory());
        assertTrue(list.get(1).isDirectory());
    }

    @Test
    public void testDirectoryAssociationWithDefaultRole() {
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONArray documentTypes = new JSONArray();

        // Directory without role should default to Viewer
        JSONObject directory = new JSONObject();
        directory.put("type", "directory");
        directory.put("description", "Test");
        documentTypes.put(directory);

        jdeploy.put("documentTypes", documentTypes);
        packageJSON.put("jdeploy", jdeploy);

        Iterable<DocumentTypeAssociation> associations =
            FileAssociationsHelper.getDocumentTypeAssociationsFromPackageJSON(packageJSON);

        List<DocumentTypeAssociation> list = new ArrayList<>();
        associations.forEach(list::add);

        assertEquals(1, list.size());
        assertTrue(list.get(0).isDirectory());
        assertEquals("Viewer", list.get(0).getRole());
    }

    @Test
    public void testInvalidFileAssociationsFiltered() {
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONArray documentTypes = new JSONArray();

        // Valid file association
        JSONObject valid = new JSONObject();
        valid.put("extension", "txt");
        valid.put("mimetype", "text/plain");
        documentTypes.put(valid);

        // Invalid - missing extension
        JSONObject noExtension = new JSONObject();
        noExtension.put("mimetype", "text/html");
        documentTypes.put(noExtension);

        // Invalid - missing mimetype
        JSONObject noMimetype = new JSONObject();
        noMimetype.put("extension", "pdf");
        documentTypes.put(noMimetype);

        jdeploy.put("documentTypes", documentTypes);
        packageJSON.put("jdeploy", jdeploy);

        Iterable<DocumentTypeAssociation> associations =
            FileAssociationsHelper.getDocumentTypeAssociationsFromPackageJSON(packageJSON);

        List<DocumentTypeAssociation> list = new ArrayList<>();
        associations.forEach(list::add);

        // Only the valid one should be included
        assertEquals(1, list.size());
        assertEquals("txt", list.get(0).getExtension());
    }

    @Test
    public void testInvalidDirectoryAssociationsFiltered() {
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONArray documentTypes = new JSONArray();

        // Valid directory
        JSONObject valid = new JSONObject();
        valid.put("type", "directory");
        valid.put("role", "Editor");
        documentTypes.put(valid);

        // Invalid - empty role
        JSONObject emptyRole = new JSONObject();
        emptyRole.put("type", "directory");
        emptyRole.put("role", "");
        documentTypes.put(emptyRole);

        jdeploy.put("documentTypes", documentTypes);
        packageJSON.put("jdeploy", jdeploy);

        Iterable<DocumentTypeAssociation> associations =
            FileAssociationsHelper.getDocumentTypeAssociationsFromPackageJSON(packageJSON);

        List<DocumentTypeAssociation> list = new ArrayList<>();
        associations.forEach(list::add);

        // Only the valid one should be included
        assertEquals(1, list.size());
        assertTrue(list.get(0).isDirectory());
        assertEquals("Editor", list.get(0).getRole());
    }

    @Test
    public void testEmptyDocumentTypes() {
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("documentTypes", new JSONArray());
        packageJSON.put("jdeploy", jdeploy);

        Iterable<DocumentTypeAssociation> associations =
            FileAssociationsHelper.getDocumentTypeAssociationsFromPackageJSON(packageJSON);

        List<DocumentTypeAssociation> list = new ArrayList<>();
        associations.forEach(list::add);

        assertEquals(0, list.size());
    }

    @Test
    public void testNoDocumentTypes() {
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        packageJSON.put("jdeploy", jdeploy);

        Iterable<DocumentTypeAssociation> associations =
            FileAssociationsHelper.getDocumentTypeAssociationsFromPackageJSON(packageJSON);

        List<DocumentTypeAssociation> list = new ArrayList<>();
        associations.forEach(list::add);

        assertEquals(0, list.size());
    }
}

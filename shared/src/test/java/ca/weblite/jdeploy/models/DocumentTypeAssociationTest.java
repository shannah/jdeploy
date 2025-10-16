package ca.weblite.jdeploy.models;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DocumentTypeAssociationTest {

    @Test
    public void testFileAssociationConstructor() {
        DocumentTypeAssociation assoc = new DocumentTypeAssociation(
                "txt",
                "text/plain",
                "/path/to/icon.png",
                true
        );

        assertFalse(assoc.isDirectory());
        assertEquals("txt", assoc.getExtension());
        assertEquals("text/plain", assoc.getMimetype());
        assertEquals("/path/to/icon.png", assoc.getIconPath());
        assertTrue(assoc.isEditor());
        assertEquals("Editor", assoc.getRole());
    }

    @Test
    public void testFileAssociationViewerRole() {
        DocumentTypeAssociation assoc = new DocumentTypeAssociation(
                "pdf",
                "application/pdf",
                null,
                false
        );

        assertFalse(assoc.isDirectory());
        assertFalse(assoc.isEditor());
        assertEquals("Viewer", assoc.getRole());
    }

    @Test
    public void testDirectoryAssociationConstructor() {
        DocumentTypeAssociation assoc = new DocumentTypeAssociation(
                "Editor",
                "Open folder as project",
                "/path/to/folder-icon.png"
        );

        assertTrue(assoc.isDirectory());
        assertNull(assoc.getExtension());
        assertNull(assoc.getMimetype());
        assertEquals("/path/to/folder-icon.png", assoc.getIconPath());
        assertTrue(assoc.isEditor());
        assertEquals("Editor", assoc.getRole());
        assertEquals("Open folder as project", assoc.getDescription());
    }

    @Test
    public void testDirectoryAssociationViewerRole() {
        DocumentTypeAssociation assoc = new DocumentTypeAssociation(
                "Viewer",
                "Browse folder",
                null
        );

        assertTrue(assoc.isDirectory());
        assertFalse(assoc.isEditor());
        assertEquals("Viewer", assoc.getRole());
        assertEquals("Browse folder", assoc.getDescription());
        assertNull(assoc.getIconPath());
    }

    @Test
    public void testDirectoryAssociationCaseInsensitiveRole() {
        DocumentTypeAssociation assoc = new DocumentTypeAssociation(
                "editor",  // lowercase
                "Test",
                null
        );

        assertTrue(assoc.isEditor());
        assertEquals("editor", assoc.getRole()); // Returns original case
    }

    @Test
    public void testDirectoryAssociationNullRole() {
        DocumentTypeAssociation assoc = new DocumentTypeAssociation(
                null,
                "Description",
                null
        );

        assertTrue(assoc.isDirectory());
        assertEquals("Viewer", assoc.getRole()); // Defaults to Viewer
        assertFalse(assoc.isEditor());
    }

    @Test
    public void testFileAssociationValidation() {
        // Valid file association
        DocumentTypeAssociation valid = new DocumentTypeAssociation(
                "txt",
                "text/plain",
                null,
                false
        );
        assertTrue(valid.isValid());

        // Invalid - missing extension
        DocumentTypeAssociation noExtension = new DocumentTypeAssociation(
                null,
                "text/plain",
                null,
                false
        );
        assertFalse(noExtension.isValid());

        // Invalid - missing mimetype
        DocumentTypeAssociation noMimetype = new DocumentTypeAssociation(
                "txt",
                null,
                null,
                false
        );
        assertFalse(noMimetype.isValid());
    }

    @Test
    public void testDirectoryAssociationValidation() {
        // Valid directory association
        DocumentTypeAssociation valid = new DocumentTypeAssociation(
                "Editor",
                "Description",
                null
        );
        assertTrue(valid.isValid());

        // Invalid - missing role
        DocumentTypeAssociation noRole = new DocumentTypeAssociation(
                null,
                "Description",
                null
        );
        assertFalse(noRole.isValid());

        // Invalid - empty role
        DocumentTypeAssociation emptyRole = new DocumentTypeAssociation(
                "",
                "Description",
                null
        );
        assertFalse(emptyRole.isValid());

        // Valid - missing description is OK
        DocumentTypeAssociation noDescription = new DocumentTypeAssociation(
                "Viewer",
                null,
                null
        );
        assertTrue(noDescription.isValid());
    }

    @Test
    public void testFileAssociationNullValues() {
        DocumentTypeAssociation assoc = new DocumentTypeAssociation(
                "txt",
                "text/plain",
                null,  // null icon is OK
                false
        );

        assertTrue(assoc.isValid());
        assertNull(assoc.getIconPath());
        assertNull(assoc.getDescription());
    }

    @Test
    public void testDirectoryAssociationMinimalValid() {
        // Minimal valid directory association
        DocumentTypeAssociation assoc = new DocumentTypeAssociation(
                "Editor",
                null,  // description can be null
                null   // icon can be null
        );

        assertTrue(assoc.isValid());
        assertTrue(assoc.isDirectory());
        assertEquals("Editor", assoc.getRole());
        assertNull(assoc.getDescription());
        assertNull(assoc.getIconPath());
    }

    @Test
    public void testRoleDefaultsToViewerForDirectory() {
        DocumentTypeAssociation assoc = new DocumentTypeAssociation(
                null,
                "Test",
                null
        );

        assertEquals("Viewer", assoc.getRole());
    }
}

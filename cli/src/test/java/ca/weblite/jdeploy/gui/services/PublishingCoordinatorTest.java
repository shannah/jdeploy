package ca.weblite.jdeploy.gui.services;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.publishTargets.PublishTargetServiceInterface;
import ca.weblite.jdeploy.publishing.github.GitHubPublishDriver;
import ca.weblite.jdeploy.services.ProjectBuilderService;
import ca.weblite.jdeploy.services.VersionCleaner;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PublishingCoordinator Tests")
class PublishingCoordinatorTest {

    @TempDir
    File tempDir;

    private File packageJSONFile;
    private JSONObject packageJSON;
    private PublishingCoordinator coordinator;

    @BeforeEach
    void setUp() throws IOException {
        packageJSONFile = new File(tempDir, "package.json");
        packageJSON = new JSONObject();
        packageJSON.put("name", "test-app");
        packageJSON.put("version", "1.0.0");
        packageJSON.put("author", "Test Author");
        packageJSON.put("description", "Test Application");
        packageJSON.put("jdeploy", new JSONObject());

        // Write package.json to disk so it exists for tests that need it
        java.nio.file.Files.write(packageJSONFile.toPath(), packageJSON.toString(4).getBytes());

        coordinator = new PublishingCoordinator(packageJSONFile, packageJSON);
    }

    @Test
    @DisplayName("Should return success when validation result is created successfully")
    void testValidationResultSuccess() {
        PublishingCoordinator.ValidationResult result = PublishingCoordinator.ValidationResult.success();
        
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
        assertEquals(PublishingCoordinator.ValidationResult.ERROR_TYPE_NONE, result.getErrorType());
        assertNull(result.getLogFile());
    }

    @Test
    @DisplayName("Should return failure with error message")
    void testValidationResultFailure() {
        String errorMsg = "Test error message";
        PublishingCoordinator.ValidationResult result = PublishingCoordinator.ValidationResult.failure(errorMsg);
        
        assertFalse(result.isValid());
        assertEquals(errorMsg, result.getErrorMessage());
        assertEquals(PublishingCoordinator.ValidationResult.ERROR_TYPE_NONE, result.getErrorType());
    }

    @Test
    @DisplayName("Should return failure with error type")
    void testValidationResultFailureWithType() {
        String errorMsg = "Not logged in";
        int errorType = PublishingCoordinator.ValidationResult.ERROR_TYPE_NOT_LOGGED_IN;
        PublishingCoordinator.ValidationResult result = PublishingCoordinator.ValidationResult.failure(errorMsg, errorType);
        
        assertFalse(result.isValid());
        assertEquals(errorMsg, result.getErrorMessage());
        assertEquals(errorType, result.getErrorType());
    }

    @Test
    @DisplayName("Should fail validation when name field is missing")
    void testValidationFailsWhenNameMissing() {
        packageJSON.remove("name");
        
        PublishingCoordinator.ValidationResult result = coordinator.validateForPublishing(null);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("name"));
    }

    @Test
    @DisplayName("Should fail validation when author field is missing")
    void testValidationFailsWhenAuthorMissing() {
        packageJSON.remove("author");
        
        PublishingCoordinator.ValidationResult result = coordinator.validateForPublishing(null);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("author"));
    }

    @Test
    @DisplayName("Should fail validation when description field is missing")
    void testValidationFailsWhenDescriptionMissing() {
        packageJSON.remove("description");
        
        PublishingCoordinator.ValidationResult result = coordinator.validateForPublishing(null);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("description"));
    }

    @Test
    @DisplayName("Should fail validation when version field is missing")
    void testValidationFailsWhenVersionMissing() {
        packageJSON.remove("version");
        
        PublishingCoordinator.ValidationResult result = coordinator.validateForPublishing(null);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("version"));
    }

    @Test
    @DisplayName("Should fail validation when jdeploy object is missing")
    void testValidationFailsWhenJdeployMissing() {
        packageJSON.remove("jdeploy");
        
        PublishingCoordinator.ValidationResult result = coordinator.validateForPublishing(null);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("jdeploy"));
    }

    @Test
    @DisplayName("Should fail validation when jar is not selected")
    void testValidationFailsWhenJarNotSelected() {
        PublishingCoordinator.ValidationResult result = coordinator.validateForPublishing(null);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("jar"));
    }

    @Test
    @DisplayName("Should fail validation when jar has wrong extension")
    void testValidationFailsWhenJarHasWrongExtension() {
        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
        jdeploy.put("jar", "app.zip");
        
        PublishingCoordinator.ValidationResult result = coordinator.validateForPublishing(null);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains(".jar"));
    }

    @Test
    @DisplayName("Should fail validation when jar file does not exist")
    void testValidationFailsWhenJarDoesNotExist() {
        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
        jdeploy.put("jar", "nonexistent.jar");
        
        PublishingCoordinator.ValidationResult result = coordinator.validateForPublishing(null);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("does not exist"));
    }

    @Test
    @DisplayName("Should fail jar validation when jar is not executable")
    void testJarValidationFailsWhenJarNotExecutable() throws IOException {
        File jarFile = new File(tempDir, "invalid.jar");
        // Create a jar without Main-Class manifest entry
        Manifest manifest = new Manifest();
        try (JarOutputStream jos = new JarOutputStream(new java.io.FileOutputStream(jarFile), manifest)) {
            jos.putNextEntry(new JarEntry("dummy.txt"));
            jos.write("dummy content".getBytes());
            jos.closeEntry();
        }

        PublishingCoordinator.ValidationResult result = coordinator.validateJar(jarFile);

        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("executable"));
    }

    @Test
    @DisplayName("Should succeed jar validation when jar is executable")
    void testJarValidationSucceedsWhenJarIsExecutable() throws IOException {
        File jarFile = new File(tempDir, "valid.jar");
        // Create a jar with Main-Class manifest entry
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MAIN_CLASS, "com.example.Main");
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        
        try (JarOutputStream jos = new JarOutputStream(new java.io.FileOutputStream(jarFile), manifest)) {
            jos.putNextEntry(new JarEntry("com/example/Main.class"));
            jos.write("dummy bytecode".getBytes());
            jos.closeEntry();
        }

        // Verify the jar was created correctly by reading it back
        PublishingCoordinator.ValidationResult result = coordinator.validateJar(jarFile);

        assertTrue(result.isValid(), "JAR validation should succeed when Main-Class is present in manifest: " + result.getErrorMessage());
    }

    @Test
    @DisplayName("PublishProgress should create inProgress record correctly")
    void testPublishProgressInProgress() {
        PublishingCoordinator.PublishProgress progress = 
            PublishingCoordinator.PublishProgress.inProgress("Message 1", "Message 2");

        assertEquals("Message 1", progress.message1());
        assertEquals("Message 2", progress.message2());
        assertFalse(progress.isComplete());
        assertFalse(progress.isFailed());
    }

    @Test
    @DisplayName("PublishProgress should create complete record correctly")
    void testPublishProgressComplete() {
        PublishingCoordinator.PublishProgress progress = 
            PublishingCoordinator.PublishProgress.complete();

        assertNull(progress.message1());
        assertNull(progress.message2());
        assertTrue(progress.isComplete());
        assertFalse(progress.isFailed());
    }

    @Test
    @DisplayName("PublishProgress should create failed record correctly")
    void testPublishProgressFailed() {
        PublishingCoordinator.PublishProgress progress = 
            PublishingCoordinator.PublishProgress.failed();

        assertNull(progress.message1());
        assertNull(progress.message2());
        assertFalse(progress.isComplete());
        assertTrue(progress.isFailed());
    }

    @Test
    @DisplayName("Should validate empty field values as missing")
    void testValidationFailsWhenFieldIsEmpty() {
        packageJSON.put("name", "");
        
        PublishingCoordinator.ValidationResult result = coordinator.validateForPublishing(null);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("name"));
    }

    @Test
    @DisplayName("Should return publish target names")
    void testGetPublishTargetNames() {
        String targetNames = coordinator.getPublishTargetNames();
        assertNotNull(targetNames);
        // Should not throw an exception
    }
}

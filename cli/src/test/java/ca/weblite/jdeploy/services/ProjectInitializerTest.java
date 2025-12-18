package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.claude.SetupClaudeService;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectInitializerTest {

    @Mock
    private ProjectJarFinder mockProjectJarFinder;

    @Mock
    SetupClaudeService mockSetupClaudeService;

    @Mock
    ProjectTypeDetectionService projectTypeDetectionService;
    
    @Mock
    RecommendedIgnoreRulesService recommendedIgnoreRulesService;

    private ProjectInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new ProjectInitializer(mockProjectJarFinder, mockSetupClaudeService, 
                                            projectTypeDetectionService, recommendedIgnoreRulesService);
        
        // Configure default behavior for ProjectTypeDetectionService mock with lenient stubbing
        ProjectType defaultProjectType = new ProjectType(
            ProjectType.BuildTool.UNKNOWN, 
            ProjectType.Framework.PLAIN_JAVA, 
            false
        );
        lenient().when(projectTypeDetectionService.detectProjectType(any(File.class))).thenReturn(defaultProjectType);
    }

    /**
     * Scenario: No package.json exists, no jarFilePath is provided.
     * The system should look up the best candidate jar using ProjectJarFinder.
     * Then create a new package.json with jdeploy config.
     */
    @Test
    void testDecorate_noPackageJson_noJarFilePath(@TempDir File tempDir) throws Exception {
        // GIVEN
        File projectDir = tempDir;
        File jarCandidate = new File(projectDir, "myapp.jar");
        FileUtils.writeStringToFile(jarCandidate, "dummy jar", StandardCharsets.UTF_8);

        // Mock the jar finder
        when(mockProjectJarFinder.findBestCandidate(projectDir)).thenReturn(jarCandidate);

        ProjectInitializer.Request request = new ProjectInitializer.Request(
                projectDir.getAbsolutePath(),
                null,       // jarFilePath => we rely on findBestCandidate
                false,      // dryRun
                false,      // generateGithubWorkflow
                null        // delegate
        );

        // WHEN
        ProjectInitializer.Response response = initializer.decorate(request);

        // THEN
        File packageJsonFile = new File(projectDir, "package.json");
        assertTrue(packageJsonFile.exists(), "package.json should have been created");

        String packageJsonStr = FileUtils.readFileToString(packageJsonFile, StandardCharsets.UTF_8);
        JSONObject packageJson = new JSONObject(packageJsonStr);

        // Should contain "jdeploy" property
        assertTrue(packageJson.has("jdeploy"), "Expected jdeploy in package.json");
        // The jarFilePath in response should match the candidate jar
        assertEquals(jarCandidate.getAbsolutePath(), response.jarFilePath);
    }

    /**
     * Scenario: No package.json exists, but jarFilePath IS provided in the request.
     * Should directly use the provided jarFilePath instead of calling findBestCandidate.
     */
    @Test
    void testDecorate_noPackageJson_withJarFilePath(@TempDir File tempDir) throws Exception {
        // GIVEN
        File projectDir = tempDir;
        File jarProvided = new File(projectDir, "provided.jar");
        FileUtils.writeStringToFile(jarProvided, "dummy jar", StandardCharsets.UTF_8);

        // Even if the finder returns null, we should still use jarProvided
        //when(mockProjectJarFinder.findBestCandidate(projectDir)).thenReturn(null);

        ProjectInitializer.Request request = new ProjectInitializer.Request(
                projectDir.getAbsolutePath(),
                jarProvided.getAbsolutePath(),
                false,
                false,
                null
        );

        // WHEN
        ProjectInitializer.Response response = initializer.decorate(request);

        // THEN
        File packageJsonFile = new File(projectDir, "package.json");
        assertTrue(packageJsonFile.exists(), "package.json should have been created");

        String packageJsonStr = FileUtils.readFileToString(packageJsonFile, StandardCharsets.UTF_8);
        JSONObject packageJson = new JSONObject(packageJsonStr);

        // Should contain "jdeploy" property
        assertTrue(packageJson.has("jdeploy"));
        // The jarFilePath in response should match jarProvided
        assertEquals(jarProvided.getAbsolutePath(), response.jarFilePath);
    }

    /**
     * Scenario: A package.json exists, but it has no "jdeploy" property.
     * Should update the existing file to add jdeploy.
     */
    @Test
    void testDecorate_existingPackageJson_noJdeploy(@TempDir File tempDir) throws Exception {
        // GIVEN
        File projectDir = tempDir;
        File packageJsonFile = new File(projectDir, "package.json");
        // Create a minimal package.json without jdeploy
        FileUtils.writeStringToFile(packageJsonFile, "{ \"name\": \"testProject\" }", StandardCharsets.UTF_8);

        // Mock finder => no jar found
        when(mockProjectJarFinder.findBestCandidate(projectDir)).thenReturn(null);

        ProjectInitializer.Request request = new ProjectInitializer.Request(
                projectDir.getAbsolutePath(),
                null,
                false,
                false,
                null
        );

        // WHEN
        ProjectInitializer.Response response = initializer.decorate(request);

        // THEN
        String updatedContents = FileUtils.readFileToString(packageJsonFile, StandardCharsets.UTF_8);
        JSONObject updatedJson = new JSONObject(updatedContents);

        assertTrue(updatedJson.has("jdeploy"), "Should add jdeploy to existing package.json");
        assertFalse(response.generatedGithubWorkflow, "Didn't request workflow generation");
    }

    /**
     * Scenario: A package.json exists and already has "jdeploy".
     * Should throw ValidationFailedException.
     */
    @Test
    void testDecorate_existingPackageJson_withJdeploy(@TempDir File tempDir) throws Exception {
        // GIVEN
        File projectDir = tempDir;
        File packageJsonFile = new File(projectDir, "package.json");
        // package.json that already has jdeploy
        String existingJson = "{ \"name\": \"testProject\", \"jdeploy\": { \"jar\": \"existing.jar\" } }";
        FileUtils.writeStringToFile(packageJsonFile, existingJson, StandardCharsets.UTF_8);

        ProjectInitializer.Request request = new ProjectInitializer.Request(
                projectDir.getAbsolutePath(),
                null,
                false,
                false,
                null
        );

        // WHEN & THEN
        assertThrows(
                ProjectInitializer.ValidationFailedException.class,
                () -> initializer.decorate(request),
                "Should throw if package.json already contains jdeploy"
        );
    }

    /**
     * Scenario: Dry run is enabled (dryRun = true).
     * No actual writes should be done, but the response should contain JSON as if it were written.
     */
    @Test
    void testDecorate_dryRun_noWrite(@TempDir File tempDir) throws Exception {
        // GIVEN
        File projectDir = tempDir;
        // No package.json
        File jarCandidate = new File(projectDir, "app.jar");
        FileUtils.writeStringToFile(jarCandidate, "dummy jar", StandardCharsets.UTF_8);

        when(mockProjectJarFinder.findBestCandidate(projectDir)).thenReturn(jarCandidate);

        ProjectInitializer.Request request = new ProjectInitializer.Request(
                projectDir.getAbsolutePath(),
                null,
                true,   // dryRun = true
                false,
                null
        );

        // WHEN
        ProjectInitializer.Response response = initializer.decorate(request);

        // THEN
        // 1) package.json should NOT exist on disk
        File packageJsonFile = new File(projectDir, "package.json");
        assertFalse(packageJsonFile.exists(), "package.json should not be written to disk in dryRun");

        // 2) But response should contain the would-be JSON
        assertNotNull(response.packageJsonContents, "Expected JSON in response for a dry-run");
        JSONObject responseJson = new JSONObject(response.packageJsonContents);
        assertTrue(responseJson.has("jdeploy"), "Expected 'jdeploy' in dry-run output");
    }

}

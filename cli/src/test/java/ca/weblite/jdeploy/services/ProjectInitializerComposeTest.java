package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.claude.SetupClaudeService;
import ca.weblite.jdeploy.models.Platform;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectInitializerComposeTest {
    
    @Mock
    private ProjectJarFinder mockProjectJarFinder;
    
    @Mock
    private SetupClaudeService mockSetupClaudeService;
    
    @Mock
    private ProjectTypeDetectionService mockProjectTypeDetectionService;
    
    private RecommendedIgnoreRulesService recommendedIgnoreRulesService;
    
    private ProjectInitializer initializer;
    
    @BeforeEach
    void setUp() {
        recommendedIgnoreRulesService = new RecommendedIgnoreRulesService();
        initializer = new ProjectInitializer(
            mockProjectJarFinder, 
            mockSetupClaudeService,
            mockProjectTypeDetectionService,
            recommendedIgnoreRulesService
        );
    }
    
    @Test
    void testComposeMultiplatformProjectConfiguration(@TempDir File tempDir) throws Exception {
        // GIVEN - A Compose Multiplatform project
        File projectDir = tempDir;
        File jarFile = new File(projectDir, "myapp.jar");
        FileUtils.writeStringToFile(jarFile, "dummy jar", StandardCharsets.UTF_8);
        
        // Mock detection of Compose Multiplatform project
        ProjectType composeProjectType = new ProjectType(
            ProjectType.BuildTool.GRADLE,
            ProjectType.Framework.COMPOSE_MULTIPLATFORM,
            false
        );
        when(mockProjectTypeDetectionService.detectProjectType(any(File.class)))
            .thenReturn(composeProjectType);
        when(mockProjectJarFinder.findBestCandidate(projectDir))
            .thenReturn(jarFile);
        
        ProjectInitializer.Request request = new ProjectInitializer.Request(
            projectDir.getAbsolutePath(),
            null,
            false, // not dry run
            false, // no github workflow
            null
        );
        
        // WHEN - Initialize the project
        ProjectInitializer.Response response = initializer.decorate(request);
        
        // THEN - Verify package.json configuration
        File packageJsonFile = new File(projectDir, "package.json");
        assertTrue(packageJsonFile.exists(), "package.json should be created");
        
        String packageJsonStr = FileUtils.readFileToString(packageJsonFile, StandardCharsets.UTF_8);
        JSONObject packageJson = new JSONObject(packageJsonStr);
        
        // Check jdeploy configuration
        assertTrue(packageJson.has("jdeploy"), "Should have jdeploy configuration");
        JSONObject jdeploy = packageJson.getJSONObject("jdeploy");

        assertTrue(jdeploy.getBoolean("platformBundlesEnabled"), 
                  "Should have platformBundlesEnabled for Compose projects");
        
        // Verify .jdpignore files were created
        verifyJdpIgnoreFiles(projectDir);
    }
    
    private void verifyJdpIgnoreFiles(File projectDir) throws Exception {
        // Check global .jdpignore
        File globalIgnoreFile = new File(projectDir, ".jdpignore");
        assertTrue(globalIgnoreFile.exists(), "Global .jdpignore should be created");
        
        String globalContent = FileUtils.readFileToString(globalIgnoreFile, StandardCharsets.UTF_8);
        assertTrue(globalContent.contains("# Auto-generated global .jdpignore file"),
                  "Global ignore file should have header");
        assertTrue(globalContent.contains("javafx"),
                  "Global ignore should exclude JavaFX libraries");
        
        // Check platform-specific .jdpignore files (all except Windows ARM64)
        List<Platform> expectedPlatforms = Arrays.asList(
            Platform.MAC_X64,
            Platform.MAC_ARM64,
            Platform.WIN_X64,
            Platform.LINUX_X64,
            Platform.LINUX_ARM64
        );
        
        for (Platform platform : expectedPlatforms) {
            File platformIgnoreFile = new File(projectDir, ".jdpignore." + platform.getIdentifier());
            assertTrue(platformIgnoreFile.exists(), 
                      ".jdpignore." + platform.getIdentifier() + " should be created");
            
            String content = FileUtils.readFileToString(platformIgnoreFile, StandardCharsets.UTF_8);
            assertTrue(content.contains("# Auto-generated .jdpignore file for " + platform.getIdentifier()),
                      "Platform ignore file should have correct header");
            assertTrue(content.contains("# Skiko (Compose Multiplatform) native libraries"),
                      "Should have Skiko rules for Compose projects");
        }
        
        // Verify Windows ARM64 is NOT created
        File winArm64IgnoreFile = new File(projectDir, ".jdpignore.win-arm64");
        assertFalse(winArm64IgnoreFile.exists(), 
                   "Windows ARM64 .jdpignore should NOT be created");
    }
    
    @Test
    void testNonComposeProjectDoesNotGetPlatformBundles(@TempDir File tempDir) throws Exception {
        // GIVEN - A regular JavaFX project (not Compose)
        File projectDir = tempDir;
        File jarFile = new File(projectDir, "myapp.jar");
        FileUtils.writeStringToFile(jarFile, "dummy jar", StandardCharsets.UTF_8);
        
        // Mock detection of JavaFX project (not Compose)
        ProjectType javafxProjectType = new ProjectType(
            ProjectType.BuildTool.MAVEN,
            ProjectType.Framework.JAVAFX,
            false
        );
        when(mockProjectTypeDetectionService.detectProjectType(any(File.class)))
            .thenReturn(javafxProjectType);
        when(mockProjectJarFinder.findBestCandidate(projectDir))
            .thenReturn(jarFile);
        
        ProjectInitializer.Request request = new ProjectInitializer.Request(
            projectDir.getAbsolutePath(),
            null,
            false,
            false,
            null
        );
        
        // WHEN - Initialize the project
        ProjectInitializer.Response response = initializer.decorate(request);
        
        // THEN - Verify package.json does not have platform bundles enabled
        File packageJsonFile = new File(projectDir, "package.json");
        String packageJsonStr = FileUtils.readFileToString(packageJsonFile, StandardCharsets.UTF_8);
        JSONObject packageJson = new JSONObject(packageJsonStr);
        JSONObject jdeploy = packageJson.getJSONObject("jdeploy");
        
        assertTrue(jdeploy.getBoolean("javafx"), 
                  "Should have javafx flag");
        assertFalse(jdeploy.has("platformBundlesEnabled"), 
                   "Non-Compose projects should not have platformBundlesEnabled");
        
        // Verify no .jdpignore files were created
        File globalIgnoreFile = new File(projectDir, ".jdpignore");
        assertFalse(globalIgnoreFile.exists(), 
                   "Non-Compose projects should not auto-generate .jdpignore files");
    }
}
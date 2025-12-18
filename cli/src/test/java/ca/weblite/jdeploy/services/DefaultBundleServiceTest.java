package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.factories.JDeployProjectFactory;
import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.models.Platform;
import ca.weblite.jdeploy.publishing.PublishingContext;
import ca.weblite.jdeploy.packaging.PackagingContext;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for DefaultBundleService to ensure it correctly processes default bundles
 * with global .jdpignore rules for publishing drivers.
 */
public class DefaultBundleServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private PlatformBundleGenerator platformBundleGenerator;
    
    @Mock
    private JDeployProjectFactory projectFactory;
    
    @Mock
    private PrintStream out;
    
    @Mock 
    private PrintStream err;

    @Mock
    private ca.weblite.jdeploy.npm.NPM npm;

    private DefaultBundleService defaultBundleService;
    private PublishingContext context;
    private PackagingContext packagingContext;
    private JDeployProject project;
    
    @BeforeEach
    public void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        
        defaultBundleService = new DefaultBundleService(platformBundleGenerator, projectFactory);
        
        // Create a test project
        File projectDir = tempDir.resolve("test-project").toFile();
        projectDir.mkdirs();
        
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");
        
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("platformBundlesEnabled", true);
        packageJson.put("jdeploy", jdeploy);
        
        File packageFile = new File(projectDir, "package.json");
        FileUtils.writeStringToFile(packageFile, packageJson.toString(2), "UTF-8");
        
        project = new JDeployProject(packageFile.toPath(), packageJson);
        
        // Create real PackagingContext instance
        Map<String, Object> packageJsonMap = new HashMap<>();
        packageJsonMap.put("name", "test-app");
        packageJsonMap.put("version", "1.0.0");
        
        packagingContext = new PackagingContext(
                projectDir,         // directory
                packageJsonMap,     // packageJsonMap
                packageFile,        // packageJsonFile
                false,              // alwaysClean
                false,              // doNotStripJavaFXFiles
                null,               // bundlesOverride
                null,               // installersOverride
                null,               // keyProvider
                null,               // packageSigningService
                out,                // out
                err,                // err
                System.in,          // in
                false,              // exitOnFail
                false               // verbose
        );

        // Create real PublishingContext instance
        context = new PublishingContext(
                packagingContext,
                false,              // alwaysPackageOnPublish
                npm,
                null,               // githubToken
                null,               // githubRepository
                null,               // githubRefName
                null,               // githubRefType
                null                // distTag
        );
        
        // Setup factory mock
        when(projectFactory.createProject(eq(packageFile.toPath()))).thenReturn(project);
    }
    
    @Test
    public void testProcessDefaultBundleWithGlobalRules() throws IOException {
        // Setup: indicate that default bundle should be filtered
        when(platformBundleGenerator.shouldFilterDefaultBundle(project)).thenReturn(true);
        
        // Execute
        defaultBundleService.processDefaultBundle(context);
        
        // Verify
        verify(platformBundleGenerator).shouldFilterDefaultBundle(project);
        verify(platformBundleGenerator).processJarsWithIgnoreService(
                eq(context.getPublishDir()),
                eq(project),
                eq(Platform.DEFAULT)
        );
        verify(out).println("Processing default bundle with global .jdpignore rules...");
        verify(out).println("Successfully processed default bundle with global .jdpignore rules");
    }
    
    @Test
    public void testProcessDefaultBundleSkippedWhenNoGlobalRules() throws IOException {
        // Setup: indicate that default bundle should NOT be filtered
        when(platformBundleGenerator.shouldFilterDefaultBundle(project)).thenReturn(false);
        
        // Execute
        defaultBundleService.processDefaultBundle(context);
        
        // Verify
        verify(platformBundleGenerator).shouldFilterDefaultBundle(project);
        verify(platformBundleGenerator, never()).processJarsWithIgnoreService(any(), any(), any());
        verify(out).println("No global .jdpignore rules found, skipping default bundle filtering");
    }
    
    @Test
    public void testProcessDefaultBundleHandlesException() throws IOException {
        // Setup: mock an exception during processing
        when(platformBundleGenerator.shouldFilterDefaultBundle(project)).thenReturn(true);
        IOException testException = new IOException("Test processing error");
        doThrow(testException).when(platformBundleGenerator).processJarsWithIgnoreService(any(), any(), any());
        
        // Execute - should not throw exception
        assertDoesNotThrow(() -> defaultBundleService.processDefaultBundle(context));
        
        // Verify error handling
        verify(err).println("Warning: Failed to process default bundle: Test processing error");
        verify(err).println("Default bundle will be published without .jdpignore processing");
        verify(err).println(testException);
    }
    
    @Test
    public void testProcessDefaultBundleAndCreateTarballSuccess() throws IOException {
        // Setup mocks
        when(platformBundleGenerator.shouldFilterDefaultBundle(project)).thenReturn(true);
        File outputDir = tempDir.resolve("output").toFile();
        outputDir.mkdirs();
        
        // Execute
        defaultBundleService.processDefaultBundleAndCreateTarball(context, outputDir);
        
        // Verify JAR processing was called
        verify(platformBundleGenerator).processJarsWithIgnoreService(
                eq(context.getPublishDir()),
                eq(project),
                eq(Platform.DEFAULT)
        );
        
        // Verify tarball creation was called
        verify(npm).pack(
                eq(context.getPublishDir()),
                eq(outputDir),
                eq(false)
        );
        
        verify(out).println("Creating default bundle tarball...");
        verify(out).println("Successfully created default bundle tarball");
    }
    
    @Test
    public void testProcessDefaultBundleAndCreateTarballHandlesPackingError() throws IOException {
        // Setup mocks
        when(platformBundleGenerator.shouldFilterDefaultBundle(project)).thenReturn(true);
        File outputDir = tempDir.resolve("output").toFile();
        outputDir.mkdirs();
        
        IOException packingError = new IOException("Tarball creation failed");
        doThrow(packingError).when(npm).pack(any(File.class), any(File.class), anyBoolean());
        
        // Execute - should throw IOException
        IOException thrown = assertThrows(IOException.class, () -> 
                defaultBundleService.processDefaultBundleAndCreateTarball(context, outputDir));
        
        // Verify exception details
        assertEquals("Failed to create default bundle tarball", thrown.getMessage());
        assertEquals(packingError, thrown.getCause());
        
        // Verify error message was logged
        verify(err).println("Warning: Failed to create default bundle tarball: Tarball creation failed");
    }
    
    @Test
    public void testShouldProcessDefaultBundle() throws IOException {
        // Setup
        File packageFile = project.getPackageJSONFile().toFile();
        
        // Test case 1: should process
        when(platformBundleGenerator.shouldFilterDefaultBundle(project)).thenReturn(true);
        assertTrue(defaultBundleService.shouldProcessDefaultBundle(packageFile));
        
        // Test case 2: should not process
        when(platformBundleGenerator.shouldFilterDefaultBundle(project)).thenReturn(false);
        assertFalse(defaultBundleService.shouldProcessDefaultBundle(packageFile));
        
        // Verify factory was called correctly
        verify(projectFactory, times(2)).createProject(packageFile.toPath());
    }
}
package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.models.Platform;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for processing default bundles with global .jdpignore rules (Platform.DEFAULT).
 * This ensures that the default bundle can be filtered using only global patterns
 * without needing platform-specific ignore files.
 */
public class DefaultBundleProcessingTest {

    @TempDir
    Path tempDir;
    
    private JDeployIgnoreService ignoreService;
    private JDeployIgnoreFileParser parser;
    private PlatformSpecificJarProcessor jarProcessor;
    private PlatformBundleGenerator bundleGenerator;

    @BeforeEach
    public void setUp() {
        parser = new JDeployIgnoreFileParser();
        ignoreService = new JDeployIgnoreService(parser);
        jarProcessor = new PlatformSpecificJarProcessor(ignoreService);
        bundleGenerator = new PlatformBundleGenerator(jarProcessor, null, ignoreService);
    }

    @Test
    public void testProcessJarForPlatformWithDefaultPlatform() throws IOException {
        // Create a test project
        JDeployProject project = createTestProject();
        
        // Create a JAR file with files that should be filtered by global rules
        File testJar = createTestJarWithGlobalFiles();
        
        // Create global .jdpignore file that filters out debug files
        createGlobalIgnoreFile(project);
        
        // Process the JAR with DEFAULT platform (should use only global rules)
        jarProcessor.processJarForPlatform(testJar, project, Platform.DEFAULT);
        
        // Verify that global ignore patterns were applied
        verifyGlobalFiltering(testJar);
    }

    @Test
    public void testProcessJarsWithIgnoreServiceWithDefaultPlatform() throws IOException {
        // Create a test project  
        JDeployProject project = createTestProject();
        
        // Create bundle directory with JAR files
        File bundleDir = createTestBundle();
        
        // Create global .jdpignore file
        createGlobalIgnoreFile(project);
        
        // Process bundle with DEFAULT platform (should not throw exception)
        assertDoesNotThrow(() -> {
            bundleGenerator.processJarsWithIgnoreService(bundleDir, project, Platform.DEFAULT);
        });
        
        // Verify that JARs were processed with global rules
        File jarFile = new File(bundleDir, "libs/test-app.jar");
        assertTrue(jarFile.exists());
        verifyGlobalFiltering(jarFile);
    }

    @Test
    public void testIgnoreServiceWithDefaultPlatformUsesOnlyGlobalPatterns() throws IOException {
        // Create a test project
        JDeployProject project = createTestProject();
        
        // Create both global and platform-specific ignore files
        createGlobalIgnoreFile(project);
        createPlatformIgnoreFile(project);
        
        // Test that DEFAULT platform only uses global patterns
        assertTrue(ignoreService.shouldIncludeFile(project, "regular.class", Platform.DEFAULT));
        assertFalse(ignoreService.shouldIncludeFile(project, "debug.class", Platform.DEFAULT));  // Global ignore
        assertTrue(ignoreService.shouldIncludeFile(project, "windows.dll", Platform.DEFAULT));   // Platform-specific ignore should NOT apply
    }

    // Helper methods

    private JDeployProject createTestProject() throws IOException {
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
        
        return new JDeployProject(packageFile.toPath(), packageJson);
    }

    private File createTestJarWithGlobalFiles() throws IOException {
        File jarFile = tempDir.resolve("global-test.jar").toFile();
        
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
            // Add regular files that should be kept
            addJarEntry(jos, "com/example/Application.class", "fake java class content");
            addJarEntry(jos, "config/application.properties", "app.name=test");
            
            // Add debug files that should be filtered by global rules
            addJarEntry(jos, "debug.class", "fake debug class");
            addJarEntry(jos, "com/example/Debug.class", "fake debug class");
            addJarEntry(jos, "log/debug.log", "debug log");
            
            // Add files that would be filtered by platform rules (but should NOT be filtered with null platform)
            addJarEntry(jos, "windows.dll", "fake dll");
            addJarEntry(jos, "native/windows/lib.dll", "fake windows lib");
        }
        
        return jarFile;
    }

    private File createTestBundle() throws IOException {
        File bundleDir = tempDir.resolve("test-bundle").toFile();
        bundleDir.mkdirs();
        
        File libsDir = new File(bundleDir, "libs");
        libsDir.mkdirs();
        
        // Create test JAR in the bundle
        File jarFile = new File(libsDir, "test-app.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
            addJarEntry(jos, "com/example/Application.class", "fake class");
            addJarEntry(jos, "debug.class", "debug class that should be filtered");
            addJarEntry(jos, "windows.dll", "dll that should NOT be filtered by global rules");
        }
        
        return bundleDir;
    }

    private void addJarEntry(JarOutputStream jos, String entryName, String content) throws IOException {
        JarEntry entry = new JarEntry(entryName);
        jos.putNextEntry(entry);
        jos.write(content.getBytes("UTF-8"));
        jos.closeEntry();
    }

    private void createGlobalIgnoreFile(JDeployProject project) throws IOException {
        File projectDir = project.getPackageJSONFile().toFile().getParentFile();
        File globalIgnore = new File(projectDir, ".jdpignore");
        
        String patterns = "# Global ignore patterns\n" +
                         "debug\n" +
                         "/log\n";
        
        FileUtils.writeStringToFile(globalIgnore, patterns, "UTF-8");
    }

    private void createPlatformIgnoreFile(JDeployProject project) throws IOException {
        File projectDir = project.getPackageJSONFile().toFile().getParentFile();
        File platformIgnore = new File(projectDir, ".jdpignore.mac-x64");
        
        String patterns = "# Platform-specific patterns (should NOT apply when platform is DEFAULT)\n" +
                         "windows.dll\n" +
                         "/native/windows\n";
        
        FileUtils.writeStringToFile(platformIgnore, patterns, "UTF-8");
    }

    private void verifyGlobalFiltering(File jarFile) throws IOException {
        try (JarInputStream jis = new JarInputStream(new FileInputStream(jarFile))) {
            JarEntry entry;
            boolean foundDebugFile = false;
            boolean foundRegularFile = false;
            boolean foundWindowsDll = false;
            
            while ((entry = jis.getNextJarEntry()) != null) {
                String name = entry.getName();
                
                if (name.startsWith("debug.") || name.contains("Debug.class") || name.startsWith("log/")) {
                    foundDebugFile = true;
                    fail("Found debug file that should have been filtered by global rules: " + name);
                }
                
                if (name.equals("com/example/Application.class") || name.equals("config/application.properties")) {
                    foundRegularFile = true;
                }
                
                if (name.endsWith(".dll")) {
                    foundWindowsDll = true; // Should be present since platform-specific rules don't apply
                }
            }
            
            // Verify expected results
            assertFalse(foundDebugFile, "Debug files should have been filtered by global rules");
            assertTrue(foundRegularFile, "Regular files should be preserved");
            assertTrue(foundWindowsDll, "DLL files should NOT be filtered when using only global rules");
        }
    }
}
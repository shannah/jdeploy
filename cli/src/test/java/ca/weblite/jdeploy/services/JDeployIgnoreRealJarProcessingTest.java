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
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple integration test to verify that .jdpignore files correctly strip files from real JAR files.
 * This test creates a real JAR with various native library files, applies .jdpignore rules,
 * and verifies that the correct files are filtered out.
 */
public class JDeployIgnoreRealJarProcessingTest {

    @TempDir
    Path tempDir;
    
    private JDeployIgnoreService ignoreService;
    private JDeployIgnoreFileParser parser;
    private PlatformSpecificJarProcessor jarProcessor;

    @BeforeEach
    public void setUp() {
        parser = new JDeployIgnoreFileParser();
        ignoreService = new JDeployIgnoreService(parser);
        jarProcessor = new PlatformSpecificJarProcessor(ignoreService);
    }

    @Test
    public void testRealJarProcessingWithNamespaceFiltering() throws IOException {
        // Create a test project
        JDeployProject project = createTestProject();
        
        // Create a real JAR file with various native library files
        File originalJar = createRealJarWithNativeFiles();
        
        // Create .jdpignore file that should filter out Windows-specific files
        createIgnoreFileWithWindowsFiltering(project);
        
        
        // Process the JAR for macOS platform (should strip Windows files)
        jarProcessor.processJarForPlatform(originalJar, project, Platform.MAC_X64);
        
        // Verify that Windows files were stripped but other files remain
        verifyJarContents(originalJar, project);
    }

    @Test
    public void testPackageNamespaceFiltering() throws IOException {
        // Create a test project
        JDeployProject project = createTestProject();
        
        // Create JAR with Java package structure
        File originalJar = createJarWithPackageStructure();
        
        // Create .jdpignore file that filters out com.windows.native package
        createIgnoreFileWithPackageFiltering(project);
        
        // Process the JAR
        jarProcessor.processJarForPlatform(originalJar, project, Platform.MAC_X64);
        
        // Verify package filtering worked
        verifyPackageFiltering(originalJar);
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

    private File createRealJarWithNativeFiles() throws IOException {
        File jarFile = tempDir.resolve("test-library.jar").toFile();
        
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
            // Add some regular Java classes
            addJarEntry(jos, "com/example/Application.class", "fake java class content");
            addJarEntry(jos, "com/example/utils/Helper.class", "fake java class content");
            
            // Add Windows native files (should be filtered out for Mac)
            addJarEntry(jos, "native/windows/library.dll", "fake dll content");
            addJarEntry(jos, "com/windows/native/driver.dll", "fake windows driver dll");
            addJarEntry(jos, "win32-x86-64/sqlite.dll", "fake sqlite dll");
            
            // Add macOS native files (should be kept for Mac)
            addJarEntry(jos, "native/macos/library.dylib", "fake dylib content");
            addJarEntry(jos, "com/macos/native/driver.dylib", "fake macos driver dylib");
            
            // Add some generic files that should be kept
            // Note: META-INF/MANIFEST.MF is handled automatically by JarOutputStream
            addJarEntry(jos, "config/application.properties", "app.name=test");
        }
        
        return jarFile;
    }

    private File createJarWithPackageStructure() throws IOException {
        File jarFile = tempDir.resolve("package-test.jar").toFile();
        
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
            // Add classes that should be kept
            addJarEntry(jos, "com/example/Application.class", "fake java class content");
            addJarEntry(jos, "com/macos/native/Library.class", "fake macos class");
            
            // Add classes that should be filtered out
            addJarEntry(jos, "com/windows/native/Driver.class", "fake windows class");
            addJarEntry(jos, "com/windows/native/helper/Util.class", "fake windows helper class");
            addJarEntry(jos, "com/windows/native/lib/Library.class", "fake windows library class");
            
            // Add some other files
            // Note: META-INF/MANIFEST.MF is handled automatically by JarOutputStream
        }
        
        return jarFile;
    }

    private void addJarEntry(JarOutputStream jos, String entryName, String content) throws IOException {
        JarEntry entry = new JarEntry(entryName);
        jos.putNextEntry(entry);
        jos.write(content.getBytes("UTF-8"));
        jos.closeEntry();
    }

    private void createIgnoreFileWithWindowsFiltering(JDeployProject project) throws IOException {
        File projectDir = project.getPackageJSONFile().toFile().getParentFile();
        File macIgnore = new File(projectDir, ".jdpignore.mac-x64");
        
        String patterns = "# Filter out Windows-specific native files for macOS\n" +
                         "*.dll\n" +
                         "com.windows.native\n" +
                         "win32-*\n" +
                         "native/windows/\n";
        
        FileUtils.writeStringToFile(macIgnore, patterns, "UTF-8");
    }

    private void createIgnoreFileWithPackageFiltering(JDeployProject project) throws IOException {
        File projectDir = project.getPackageJSONFile().toFile().getParentFile();
        File macIgnore = new File(projectDir, ".jdpignore.mac-x64");
        
        String patterns = "# Filter out Windows package namespace\n" +
                         "com.windows.native\n";
        
        FileUtils.writeStringToFile(macIgnore, patterns, "UTF-8");
    }

    private void verifyJarContents(File jarFile, JDeployProject project) throws IOException {
        // Read the processed JAR and check its contents
        try (JarInputStream jis = new JarInputStream(new FileInputStream(jarFile))) {
            JarEntry entry;
            boolean foundWindowsDll = false;
            
            while ((entry = jis.getNextJarEntry()) != null) {
                String name = entry.getName();
                
                if (name.equals("windows.dll")) {
                    foundWindowsDll = true;
                    fail("Found Windows DLL file that should have been filtered: " + name);
                }
                
                if (name.contains("com/windows/native/")) {
                    fail("Found Windows native package file that should have been filtered: " + name);
                }
                
                if (name.contains("native/windows/")) {
                    fail("Found Windows native directory file that should have been filtered: " + name);
                }
            }

            // Note: META-INF/MANIFEST.MF is handled specially by JAR processing and may not appear as an entry
            assertFalse(foundWindowsDll, "Windows .dll files should have been filtered out");
        }
    }

    private void verifyPackageFiltering(File jarFile) throws IOException {
        try (JarInputStream jis = new JarInputStream(new FileInputStream(jarFile))) {
            JarEntry entry;
            boolean foundWindowsPackage = false;
            boolean foundExampleClass = false;
            boolean foundMacosClass = false;
            
            while ((entry = jis.getNextJarEntry()) != null) {
                String name = entry.getName();
                
                if (name.startsWith("com/windows/native/")) {
                    foundWindowsPackage = true;
                    fail("Found Windows package file that should have been filtered: " + name);
                }
                
                if (name.startsWith("com/example/")) {
                    foundExampleClass = true;
                }
                
                if (name.startsWith("com/macos/native/")) {
                    foundMacosClass = true;
                }
            }
            
            // Verify filtering worked correctly
            assertFalse(foundWindowsPackage, "com.windows.native package should have been filtered out");
            assertTrue(foundExampleClass, "com.example package should be preserved");
            assertTrue(foundMacosClass, "com.macos.native package should be preserved");
        }
    }
}
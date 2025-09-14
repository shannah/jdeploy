package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.JDeployIgnorePattern;
import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.models.Platform;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JDeployIgnoreServiceTest {

    @Mock
    private JDeployIgnoreFileParser mockParser;

    private JDeployIgnoreService service;
    private JDeployProject project;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() {
        service = new JDeployIgnoreService(mockParser);
        
        // Create a mock project with package.json in temp directory
        Path packageJsonPath = tempDir.resolve("package.json");
        try {
            Files.write(packageJsonPath, "{}".getBytes());
        } catch (IOException e) {
            fail("Failed to create test package.json: " + e.getMessage());
        }
        
        JSONObject packageJson = new JSONObject();
        project = new JDeployProject(packageJsonPath, packageJson);
    }

    @Test
    public void testGetGlobalIgnoreFile() {
        File globalIgnoreFile = service.getGlobalIgnoreFile(project);
        
        assertNotNull(globalIgnoreFile);
        assertEquals(".jdpignore", globalIgnoreFile.getName());
        assertEquals(tempDir.toFile(), globalIgnoreFile.getParentFile());
    }

    @Test
    public void testGetGlobalIgnoreFileNullProject() {
        assertNull(service.getGlobalIgnoreFile(null));
    }

    @Test
    public void testGetPlatformIgnoreFile() {
        File platformIgnoreFile = service.getPlatformIgnoreFile(project, Platform.MAC_X64);
        
        assertNotNull(platformIgnoreFile);
        assertEquals(".jdpignore.mac-x64", platformIgnoreFile.getName());
        assertEquals(tempDir.toFile(), platformIgnoreFile.getParentFile());
    }

    @Test
    public void testGetPlatformIgnoreFileAllPlatforms() {
        for (Platform platform : Platform.values()) {
            File platformIgnoreFile = service.getPlatformIgnoreFile(project, platform);
            
            assertNotNull(platformIgnoreFile);
            assertEquals(".jdpignore." + platform.getIdentifier(), platformIgnoreFile.getName());
        }
    }

    @Test
    public void testGetPlatformIgnoreFileNullProject() {
        assertNull(service.getPlatformIgnoreFile(null, Platform.MAC_X64));
    }

    @Test
    public void testGetGlobalIgnorePatternsFileNotExists() throws IOException {
        // No file exists, service should return empty list without calling parser
        List<JDeployIgnorePattern> patterns = service.getGlobalIgnorePatterns(project);
        
        assertTrue(patterns.isEmpty());
        verify(mockParser, never()).parseFile(any(File.class)); // Should not be called for non-existent file
    }

    @Test
    public void testGetGlobalIgnorePatternsFileExists() throws IOException {
        // Create a global ignore file
        File globalIgnoreFile = tempDir.resolve(".jdpignore").toFile();
        Files.write(globalIgnoreFile.toPath(), "com.example.native".getBytes());
        
        List<JDeployIgnorePattern> mockPatterns = Arrays.asList(
            new JDeployIgnorePattern("com.example.native", false, "com/example/native/**")
        );
        when(mockParser.parseFile(globalIgnoreFile)).thenReturn(mockPatterns);
        
        List<JDeployIgnorePattern> patterns = service.getGlobalIgnorePatterns(project);
        
        assertEquals(1, patterns.size());
        assertEquals("com.example.native", patterns.get(0).getOriginalPattern());
        verify(mockParser).parseFile(globalIgnoreFile);
    }

    @Test
    public void testGetGlobalIgnorePatternsCaching() throws IOException {
        // Create a global ignore file
        File globalIgnoreFile = tempDir.resolve(".jdpignore").toFile();
        Files.write(globalIgnoreFile.toPath(), "com.example.native".getBytes());
        
        List<JDeployIgnorePattern> mockPatterns = Arrays.asList(
            new JDeployIgnorePattern("com.example.native", false, "com/example/native/**")
        );
        when(mockParser.parseFile(globalIgnoreFile)).thenReturn(mockPatterns);
        
        // Call twice
        List<JDeployIgnorePattern> patterns1 = service.getGlobalIgnorePatterns(project);
        List<JDeployIgnorePattern> patterns2 = service.getGlobalIgnorePatterns(project);
        
        assertEquals(patterns1, patterns2);
        // Parser should only be called once due to caching
        verify(mockParser, times(1)).parseFile(globalIgnoreFile);
    }

    @Test
    public void testGetPlatformIgnorePatternsFileExists() throws IOException {
        // Create a platform ignore file
        File platformIgnoreFile = tempDir.resolve(".jdpignore.mac-x64").toFile();
        Files.write(platformIgnoreFile.toPath(), "!ca.weblite.native.mac.x64".getBytes());
        
        List<JDeployIgnorePattern> mockPatterns = Arrays.asList(
            new JDeployIgnorePattern("!ca.weblite.native.mac.x64", true, "ca/weblite/native/mac/x64/**")
        );
        when(mockParser.parseFile(platformIgnoreFile)).thenReturn(mockPatterns);
        
        List<JDeployIgnorePattern> patterns = service.getPlatformIgnorePatterns(project, Platform.MAC_X64);
        
        assertEquals(1, patterns.size());
        assertTrue(patterns.get(0).isKeepPattern());
        verify(mockParser).parseFile(platformIgnoreFile);
    }

    @Test
    public void testShouldIncludeFileWithKeepPattern() throws IOException {
        // Setup global ignore file with keep pattern
        File globalIgnoreFile = tempDir.resolve(".jdpignore").toFile();
        Files.write(globalIgnoreFile.toPath(), "!com.example.keep".getBytes());
        
        List<JDeployIgnorePattern> keepPattern = Arrays.asList(
            new JDeployIgnorePattern("!com.example.keep", true, "com/example/keep/**")
        );
        when(mockParser.parseFile(globalIgnoreFile)).thenReturn(keepPattern);
        
        // File matches keep pattern - should be included
        assertTrue(service.shouldIncludeFile(project, "com/example/keep/lib.so", Platform.MAC_X64));
    }

    @Test
    public void testShouldIncludeFileWithIgnorePattern() throws IOException {
        // Setup global ignore file with ignore pattern
        File globalIgnoreFile = tempDir.resolve(".jdpignore").toFile();
        Files.write(globalIgnoreFile.toPath(), "com.example.ignore".getBytes());
        
        List<JDeployIgnorePattern> ignorePattern = Arrays.asList(
            new JDeployIgnorePattern("com.example.ignore", false, "com/example/ignore")
        );
        when(mockParser.parseFile(globalIgnoreFile)).thenReturn(ignorePattern);
        
        // File matches ignore pattern - should be excluded
        assertFalse(service.shouldIncludeFile(project, "com/example/ignore/lib.so", Platform.MAC_X64));
    }

    @Test
    public void testShouldIncludeFileKeepOverridesIgnore() throws IOException {
        // Setup files with both global ignore and platform keep patterns
        File globalIgnoreFile = tempDir.resolve(".jdpignore").toFile();
        Files.write(globalIgnoreFile.toPath(), "com.example.test".getBytes());
        
        File platformIgnoreFile = tempDir.resolve(".jdpignore.mac-x64").toFile();
        Files.write(platformIgnoreFile.toPath(), "!com.example.test.keep".getBytes());
        
        List<JDeployIgnorePattern> globalPatterns = Arrays.asList(
            new JDeployIgnorePattern("com.example.test", false, "com/example/test")
        );
        List<JDeployIgnorePattern> platformPatterns = Arrays.asList(
            new JDeployIgnorePattern("!com.example.test.keep", true, "com/example/test/keep")
        );
        
        when(mockParser.parseFile(globalIgnoreFile)).thenReturn(globalPatterns);
        when(mockParser.parseFile(platformIgnoreFile)).thenReturn(platformPatterns);
        
        // File matches both ignore and keep patterns - keep should win
        assertTrue(service.shouldIncludeFile(project, "com/example/test/keep/lib.so", Platform.MAC_X64));
        
        // File matches only ignore pattern - should be excluded  
        assertFalse(service.shouldIncludeFile(project, "com/example/test/other/lib.so", Platform.MAC_X64));
    }

    @Test
    public void testShouldIncludeFileDefaultInclude() {
        // No ignore files exist - should default to include
        assertTrue(service.shouldIncludeFile(project, "com/example/any/lib.so", Platform.MAC_X64));
    }

    @Test
    public void testShouldIncludeFileNullPath() {
        // Null file path should default to include
        assertTrue(service.shouldIncludeFile(project, null, Platform.MAC_X64));
    }

    @Test
    public void testClearCache() throws IOException {
        // Create ignore file and populate cache
        File globalIgnoreFile = tempDir.resolve(".jdpignore").toFile();
        Files.write(globalIgnoreFile.toPath(), "com.example.native".getBytes());
        
        List<JDeployIgnorePattern> mockPatterns = Arrays.asList(
            new JDeployIgnorePattern("com.example.native", false, "com/example/native/**")
        );
        when(mockParser.parseFile(globalIgnoreFile)).thenReturn(mockPatterns);
        
        // Populate cache
        service.getGlobalIgnorePatterns(project);
        verify(mockParser, times(1)).parseFile(globalIgnoreFile);
        
        // Clear cache
        service.clearCache();
        
        // Should call parser again after cache clear
        service.getGlobalIgnorePatterns(project);
        verify(mockParser, times(2)).parseFile(globalIgnoreFile);
    }

    @Test
    public void testHasIgnoreFilesGlobalOnly() throws IOException {
        File globalIgnoreFile = tempDir.resolve(".jdpignore").toFile();
        Files.write(globalIgnoreFile.toPath(), "com.example.native".getBytes());
        
        assertTrue(service.hasIgnoreFiles(project));
    }

    @Test
    public void testHasIgnoreFilesPlatformOnly() throws IOException {
        File platformIgnoreFile = tempDir.resolve(".jdpignore.mac-x64").toFile();
        Files.write(platformIgnoreFile.toPath(), "!ca.weblite.native.mac.x64".getBytes());
        
        assertTrue(service.hasIgnoreFiles(project));
    }

    @Test
    public void testHasIgnoreFilesNone() {
        assertFalse(service.hasIgnoreFiles(project));
    }

    @Test
    public void testGetPatternStatistics() throws IOException {
        // Setup files
        File globalIgnoreFile = tempDir.resolve(".jdpignore").toFile();
        Files.write(globalIgnoreFile.toPath(), "com.example.ignore\n!com.example.keep".getBytes());
        
        File platformIgnoreFile = tempDir.resolve(".jdpignore.mac-x64").toFile();
        Files.write(platformIgnoreFile.toPath(), "platform.ignore\n!platform.keep".getBytes());
        
        List<JDeployIgnorePattern> globalPatterns = Arrays.asList(
            new JDeployIgnorePattern("com.example.ignore", false, "com/example/ignore/**"),
            new JDeployIgnorePattern("!com.example.keep", true, "com/example/keep/**")
        );
        List<JDeployIgnorePattern> platformPatterns = Arrays.asList(
            new JDeployIgnorePattern("platform.ignore", false, "platform/ignore/**"),
            new JDeployIgnorePattern("!platform.keep", true, "platform/keep/**")
        );
        
        when(mockParser.parseFile(globalIgnoreFile)).thenReturn(globalPatterns);
        when(mockParser.parseFile(platformIgnoreFile)).thenReturn(platformPatterns);
        
        Map<String, Object> stats = service.getPatternStatistics(project, Platform.MAC_X64);
        
        assertEquals(2, stats.get("globalPatternCount"));
        assertEquals(1, stats.get("globalKeepPatternCount"));
        assertEquals(1, stats.get("globalIgnorePatternCount"));
        
        assertEquals(2, stats.get("platformPatternCount"));
        assertEquals(1, stats.get("platformKeepPatternCount"));
        assertEquals(1, stats.get("platformIgnorePatternCount"));
        assertEquals("mac-x64", stats.get("platform"));
    }

    @Test
    public void testGetPatternStatisticsGlobalOnly() throws IOException {
        File globalIgnoreFile = tempDir.resolve(".jdpignore").toFile();
        Files.write(globalIgnoreFile.toPath(), "com.example.ignore".getBytes());
        
        List<JDeployIgnorePattern> globalPatterns = Arrays.asList(
            new JDeployIgnorePattern("com.example.ignore", false, "com/example/ignore/**")
        );
        when(mockParser.parseFile(globalIgnoreFile)).thenReturn(globalPatterns);
        
        Map<String, Object> stats = service.getPatternStatistics(project, null);
        
        assertEquals(1, stats.get("globalPatternCount"));
        assertEquals(0, stats.get("globalKeepPatternCount"));
        assertEquals(1, stats.get("globalIgnorePatternCount"));
        
        assertFalse(stats.containsKey("platformPatternCount"));
        assertFalse(stats.containsKey("platform"));
    }

    @Test
    public void testParserIOException() throws IOException {
        // Create ignore file
        File globalIgnoreFile = tempDir.resolve(".jdpignore").toFile();
        Files.write(globalIgnoreFile.toPath(), "com.example.native".getBytes());
        
        // Mock parser to throw IOException
        when(mockParser.parseFile(globalIgnoreFile)).thenThrow(new IOException("Parse error"));
        
        // Should return empty list and not throw exception
        List<JDeployIgnorePattern> patterns = service.getGlobalIgnorePatterns(project);
        assertTrue(patterns.isEmpty());
    }
}
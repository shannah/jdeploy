package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.JDeployIgnorePattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JDeployIgnoreFileParserTest {

    private JDeployIgnoreFileParser parser;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() {
        parser = new JDeployIgnoreFileParser();
    }

    @Test
    public void testParseFileNotExists() throws IOException {
        File nonExistentFile = tempDir.resolve("nonexistent.jdpignore").toFile();
        List<JDeployIgnorePattern> patterns = parser.parseFile(nonExistentFile);
        
        assertTrue(patterns.isEmpty());
    }

    @Test
    public void testParseEmptyFile() throws IOException {
        File ignoreFile = tempDir.resolve(".jdpignore").toFile();
        Files.write(ignoreFile.toPath(), "".getBytes());
        
        List<JDeployIgnorePattern> patterns = parser.parseFile(ignoreFile);
        assertTrue(patterns.isEmpty());
    }

    @Test
    public void testParseFileWithComments() throws IOException {
        File ignoreFile = tempDir.resolve(".jdpignore").toFile();
        String content = "# This is a comment\n" +
                        "com.example.native\n" +
                        "# Another comment\n" +
                        "!com.example.keep\n";
        Files.write(ignoreFile.toPath(), content.getBytes());
        
        List<JDeployIgnorePattern> patterns = parser.parseFile(ignoreFile);
        assertEquals(2, patterns.size());
        
        assertEquals("com.example.native", patterns.get(0).getOriginalPattern());
        assertTrue(patterns.get(0).isIgnorePattern());
        
        assertEquals("!com.example.keep", patterns.get(1).getOriginalPattern());
        assertTrue(patterns.get(1).isKeepPattern());
    }

    @Test
    public void testParseFileWithEmptyLines() throws IOException {
        File ignoreFile = tempDir.resolve(".jdpignore").toFile();
        String content = "\n" +
                        "com.example.native\n" +
                        "\n" +
                        "   \n" +
                        "!com.example.keep\n" +
                        "\n";
        Files.write(ignoreFile.toPath(), content.getBytes());
        
        List<JDeployIgnorePattern> patterns = parser.parseFile(ignoreFile);
        assertEquals(2, patterns.size());
    }

    @Test
    public void testParseIgnorePatterns() throws IOException {
        File ignoreFile = tempDir.resolve(".jdpignore").toFile();
        String content = "com.example.ignore1\n" +
                        "!com.example.keep\n" +
                        "com.example.ignore2\n";
        Files.write(ignoreFile.toPath(), content.getBytes());
        
        List<JDeployIgnorePattern> ignorePatterns = parser.parseIgnorePatterns(ignoreFile);
        assertEquals(2, ignorePatterns.size());
        
        assertEquals("com.example.ignore1", ignorePatterns.get(0).getOriginalPattern());
        assertEquals("com.example.ignore2", ignorePatterns.get(1).getOriginalPattern());
    }

    @Test
    public void testParseKeepPatterns() throws IOException {
        File ignoreFile = tempDir.resolve(".jdpignore").toFile();
        String content = "com.example.ignore\n" +
                        "!com.example.keep1\n" +
                        "!com.example.keep2\n";
        Files.write(ignoreFile.toPath(), content.getBytes());
        
        List<JDeployIgnorePattern> keepPatterns = parser.parseKeepPatterns(ignoreFile);
        assertEquals(2, keepPatterns.size());
        
        assertEquals("!com.example.keep1", keepPatterns.get(0).getOriginalPattern());
        assertEquals("!com.example.keep2", keepPatterns.get(1).getOriginalPattern());
    }

    @Test
    public void testConvertPatternToJarPathPackageNotation() {
        assertEquals("com/example/native", parser.convertPatternToJarPath("com.example.native"));
    }

    @Test
    public void testConvertPatternToJarPathAbsolutePath() {
        assertEquals("path/to/file", parser.convertPatternToJarPath("/path/to/file"));
        assertEquals("native/lib.so", parser.convertPatternToJarPath("/native/lib.so"));
    }

    @Test
    public void testConvertPatternToJarPathRelativePath() {
        assertEquals("native/lib.so", parser.convertPatternToJarPath("native/lib.so"));
        assertEquals("path/to/file", parser.convertPatternToJarPath("path/to/file"));
    }

    @Test
    public void testConvertPatternToJarPathNullOrEmpty() {
        assertNull(parser.convertPatternToJarPath(null));
        assertNull(parser.convertPatternToJarPath(""));
        assertNull(parser.convertPatternToJarPath("   "));
    }

    @Test
    public void testMatchesAnyPattern() {
        List<JDeployIgnorePattern> patterns = Arrays.asList(
            new JDeployIgnorePattern("com.example.native", false, "com/example/native"),
            new JDeployIgnorePattern("!com.example.keep", true, "com/example/keep")
        );
        
        assertTrue(parser.matchesAnyPattern("com/example/native/lib.so", patterns));
        assertTrue(parser.matchesAnyPattern("com/example/keep/lib.so", patterns));
        assertFalse(parser.matchesAnyPattern("com/other/lib.so", patterns));
    }

    @Test
    public void testMatchesAnyPatternEmptyList() {
        assertFalse(parser.matchesAnyPattern("any/path", Collections.emptyList()));
        assertFalse(parser.matchesAnyPattern("any/path", null));
    }

    @Test
    public void testMatchesPattern() {
        assertTrue(parser.matchesPattern("com/example/native/lib.so", "com.example.native"));
        assertFalse(parser.matchesPattern("com/other/lib.so", "com.example.native"));
        assertFalse(parser.matchesPattern("com/example/native/lib.so", "invalid_pattern"));
    }

    @Test
    public void testParsePatternWithWhitespace() throws IOException {
        File ignoreFile = tempDir.resolve(".jdpignore").toFile();
        String content = "  com.example.native  \n" +
                        "   !com.example.keep   \n";
        Files.write(ignoreFile.toPath(), content.getBytes());
        
        List<JDeployIgnorePattern> patterns = parser.parseFile(ignoreFile);
        assertEquals(2, patterns.size());
        
        // Original pattern should preserve whitespace, but processing should handle trimming
        assertEquals("  com.example.native  ", patterns.get(0).getOriginalPattern());
        assertEquals("   !com.example.keep   ", patterns.get(1).getOriginalPattern());
    }

    @Test
    public void testParseKeepPatternWithExtraWhitespace() throws IOException {
        File ignoreFile = tempDir.resolve(".jdpignore").toFile();
        String content = "!   com.example.keep   \n";
        Files.write(ignoreFile.toPath(), content.getBytes());
        
        List<JDeployIgnorePattern> patterns = parser.parseFile(ignoreFile);
        assertEquals(1, patterns.size());
        
        JDeployIgnorePattern pattern = patterns.get(0);
        assertTrue(pattern.isKeepPattern());
        assertEquals("!   com.example.keep   ", pattern.getOriginalPattern());
    }

    @Test
    public void testParseRealWorldExample() throws IOException {
        File ignoreFile = tempDir.resolve(".jdpignore").toFile();
        String content = "# Ignore test and debug libraries\n" +
                        "com.testing.mocklibs.native\n" +
                        "com.development.debugging.native\n" +
                        "\n" +
                        "# Ignore obsolete libraries\n" +
                        "com.obsolete.legacy.native\n" +
                        "\n" +
                        "# But keep required test utilities\n" +
                        "!com.testing.mocklibs.native.required\n" +
                        "\n" +
                        "# Platform-specific paths\n" +
                        "/native/windows/\n" +
                        "*.dll\n";
        Files.write(ignoreFile.toPath(), content.getBytes());
        
        List<JDeployIgnorePattern> patterns = parser.parseFile(ignoreFile);
        assertEquals(6, patterns.size());
        
        // Check ignore patterns
        List<JDeployIgnorePattern> ignorePatterns = parser.parseIgnorePatterns(ignoreFile);
        assertEquals(5, ignorePatterns.size());
        
        // Check keep patterns
        List<JDeployIgnorePattern> keepPatterns = parser.parseKeepPatterns(ignoreFile);
        assertEquals(1, keepPatterns.size());
        assertEquals("!com.testing.mocklibs.native.required", keepPatterns.get(0).getOriginalPattern());
    }
}
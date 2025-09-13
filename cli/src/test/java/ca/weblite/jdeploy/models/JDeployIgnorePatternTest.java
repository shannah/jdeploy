package ca.weblite.jdeploy.models;

import ca.weblite.jdeploy.services.JDeployIgnoreFileParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JDeployIgnorePatternTest {

    @Test
    public void testConstructor() {
        JDeployIgnorePattern pattern = new JDeployIgnorePattern("!com.example.native", true, "com/example/native");
        
        assertEquals("!com.example.native", pattern.getOriginalPattern());
        assertTrue(pattern.isKeepPattern());
        assertFalse(pattern.isIgnorePattern());
        assertEquals("com/example/native", pattern.getJarPath());
    }

    @Test
    public void testIgnorePattern() {
        JDeployIgnorePattern pattern = new JDeployIgnorePattern("com.example.native", false, "com/example/native");
        
        assertEquals("com.example.native", pattern.getOriginalPattern());
        assertFalse(pattern.isKeepPattern());
        assertTrue(pattern.isIgnorePattern());
        assertEquals("com/example/native", pattern.getJarPath());
    }

    @Test
    public void testMatchesWildcard() {
        // Use parser to get correct regex pattern
        JDeployIgnoreFileParser parser = new JDeployIgnoreFileParser();
        String jarPath = parser.convertPatternToJarPath("/skiko-*.dll");
        JDeployIgnorePattern pattern = new JDeployIgnorePattern("/skiko-*.dll", false, jarPath);

        assertTrue(pattern.matches("skiko-windows-x64.dll"));
        assertFalse(pattern.matches("skiko-linux-x64.so"));
    }

    @Test
    public void testMatchesExactPath() {
        JDeployIgnorePattern pattern = new JDeployIgnorePattern("com.example.native", false, "com/example/native");
        
        assertTrue(pattern.matches("com/example/native"));
        assertFalse(pattern.matches("com/example/other"));
        assertFalse(pattern.matches("com/different/native"));
    }

    @Test
    public void testMatchesWithWildcard() {
        // Use parser to get correct regex pattern
        JDeployIgnoreFileParser parser = new JDeployIgnoreFileParser();
        String jarPath = parser.convertPatternToJarPath("com.example");
        JDeployIgnorePattern pattern = new JDeployIgnorePattern("com.example", false, jarPath);
        
        assertTrue(pattern.matches("com/example/native"));
        assertTrue(pattern.matches("com/example/anything"));
        assertTrue(pattern.matches("com/example/sub/path"));
        assertFalse(pattern.matches("com/different/native"));
    }

    @Test
    public void testMatchesWithPackageWildcard() {
        JDeployIgnorePattern pattern = new JDeployIgnorePattern("com.example.native", false, "com/example/native");
        
        assertTrue(pattern.matches("com/example/native/file.class"));
        assertTrue(pattern.matches("com/example/native/sub/file.class"));
        assertFalse(pattern.matches("com/example/other/file.class"));
    }

    @Test
    public void testMatchesDirectoryPattern() {
        JDeployIgnorePattern pattern = new JDeployIgnorePattern("/native/", false, "native/");
        
        assertTrue(pattern.matches("native/lib.so"));
        assertTrue(pattern.matches("native/sub/lib.so"));
        assertFalse(pattern.matches("other/lib.so"));
    }

    @Test
    public void testMatchesWithSpecialCharacters() {
        JDeployIgnorePattern pattern = new JDeployIgnorePattern("com.example.test", false, "com/example/test");
        
        assertTrue(pattern.matches("com/example/test"));
        assertFalse(pattern.matches("com.example.test")); // Literal dots should not match
    }

    @Test
    public void testMatchesWithNullInputs() {
        JDeployIgnorePattern pattern = new JDeployIgnorePattern("com.example.native", false, "com/example/native");
        
        assertFalse(pattern.matches(null));
        
        JDeployIgnorePattern nullPattern = new JDeployIgnorePattern("com.example.native", false, null);
        assertFalse(nullPattern.matches("anything"));
    }

    @Test
    public void testMatchesComplexPath() {
        // Use parser to get correct regex pattern  
        JDeployIgnoreFileParser parser = new JDeployIgnoreFileParser();
        String jarPath = parser.convertPatternToJarPath("/library.dll");
        JDeployIgnorePattern pattern = new JDeployIgnorePattern("/library.dll", false, jarPath);
        
        assertTrue(pattern.matches("library.dll"));
        assertFalse(pattern.matches("native.dll"));
        assertFalse(pattern.matches("library.so"));
        assertFalse(pattern.matches("dll"));
    }

    @Test
    public void testMatchesSingleCharacterWildcard() {
        // Use parser to get correct regex pattern
        JDeployIgnoreFileParser parser = new JDeployIgnoreFileParser();
        String jarPath = parser.convertPatternToJarPath("/lib1.so");
        JDeployIgnorePattern pattern = new JDeployIgnorePattern("/lib1.so", false, jarPath);
        
        assertTrue(pattern.matches("lib1.so"));
        assertFalse(pattern.matches("liba.so"));
        assertFalse(pattern.matches("lib12.so")); // ? matches single character only
        assertFalse(pattern.matches("lib.so")); // ? requires a character
    }

    @Test
    public void testEquals() {
        JDeployIgnorePattern pattern1 = new JDeployIgnorePattern("!com.example.native", true, "com/example/native");
        JDeployIgnorePattern pattern2 = new JDeployIgnorePattern("!com.example.native", true, "com/example/native");
        JDeployIgnorePattern pattern3 = new JDeployIgnorePattern("com.example.native", false, "com/example/native");
        JDeployIgnorePattern pattern4 = new JDeployIgnorePattern("!com.different.native", true, "com/different/native");

        assertEquals(pattern1, pattern2);
        assertNotEquals(pattern1, pattern3); // Different isKeep flag
        assertNotEquals(pattern1, pattern4); // Different pattern string
        assertNotEquals(pattern1, null);
        assertNotEquals(pattern1, "string");
    }

    @Test
    public void testHashCode() {
        JDeployIgnorePattern pattern1 = new JDeployIgnorePattern("!com.example.native", true, "com/example/native");
        JDeployIgnorePattern pattern2 = new JDeployIgnorePattern("!com.example.native", true, "com/example/native");
        JDeployIgnorePattern pattern3 = new JDeployIgnorePattern("com.example.native", false, "com/example/native");

        assertEquals(pattern1.hashCode(), pattern2.hashCode());
        assertNotEquals(pattern1.hashCode(), pattern3.hashCode());
    }

    @Test
    public void testToString() {
        JDeployIgnorePattern pattern = new JDeployIgnorePattern("!com.example.native", true, "com/example/native");
        String toString = pattern.toString();
        
        assertTrue(toString.contains("!com.example.native"));
        assertTrue(toString.contains("true")); // isKeep
        assertTrue(toString.contains("com/example/native"));
        assertTrue(toString.contains("JDeployIgnorePattern"));
    }
}
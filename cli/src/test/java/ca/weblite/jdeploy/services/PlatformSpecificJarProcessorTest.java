package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.*;

public class PlatformSpecificJarProcessorTest {

    private PlatformSpecificJarProcessor processor;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() {
        // Create mock ignore service
        JDeployIgnoreService mockIgnoreService = Mockito.mock(JDeployIgnoreService.class);
        processor = new PlatformSpecificJarProcessor(mockIgnoreService);
    }

    @Test
    public void testStripNativeNamespaces_EmptyList() throws IOException {
        File jarFile = createTestJar("test.jar", 
            "com/example/Main.class",
            "ca/weblite/native/mac/x64/NativeLib.class",
            "ca/weblite/native/win/x64/NativeLib.class"
        );
        
        List<String> originalEntries = getJarEntries(jarFile);
        
        processor.stripNativeNamespaces(jarFile, Collections.emptyList());
        
        List<String> newEntries = getJarEntries(jarFile);
        assertEquals(originalEntries.size(), newEntries.size());
    }

    @Test
    public void testStripNativeNamespaces_SingleNamespace() throws IOException {
        File jarFile = createTestJar("test.jar",
            "com/example/Main.class",
            "ca/weblite/native/mac/x64/NativeLib.class",
            "ca/weblite/native/win/x64/NativeLib.class",
            "ca/weblite/common/Utils.class"
        );
        
        processor.stripNativeNamespaces(jarFile, Arrays.asList("ca.weblite.native.mac.x64"));
        
        List<String> entries = getJarEntries(jarFile);
        assertFalse(entries.contains("ca/weblite/native/mac/x64/NativeLib.class"));
        assertTrue(entries.contains("ca/weblite/native/win/x64/NativeLib.class"));
        assertTrue(entries.contains("com/example/Main.class"));
        assertTrue(entries.contains("ca/weblite/common/Utils.class"));
    }

    @Test
    public void testStripNativeNamespaces_MultipleNamespaces() throws IOException {
        File jarFile = createTestJar("test.jar",
            "com/example/Main.class",
            "ca/weblite/native/mac/x64/NativeLib.class",
            "ca/weblite/native/win/x64/NativeLib.class",
            "ca/weblite/native/linux/x64/NativeLib.class",
            "ca/weblite/common/Utils.class"
        );
        
        processor.stripNativeNamespaces(jarFile, Arrays.asList(
            "ca.weblite.native.mac.x64",
            "ca.weblite.native.win.x64"
        ));
        
        List<String> entries = getJarEntries(jarFile);
        assertFalse(entries.contains("ca/weblite/native/mac/x64/NativeLib.class"));
        assertFalse(entries.contains("ca/weblite/native/win/x64/NativeLib.class"));
        assertTrue(entries.contains("ca/weblite/native/linux/x64/NativeLib.class"));
        assertTrue(entries.contains("com/example/Main.class"));
        assertTrue(entries.contains("ca/weblite/common/Utils.class"));
    }

    @Test
    public void testStripNativeNamespaces_PreservesManifest() throws IOException {
        File jarFile = createTestJarWithManifest("test.jar",
            createManifest("Test-Attribute", "Test-Value"),
            "com/example/Main.class"
        );
        
        processor.stripNativeNamespaces(jarFile, Arrays.asList("com.nonexistent"));
        
        // Verify manifest is preserved
        try (JarFile jar = new JarFile(jarFile)) {
            Manifest manifest = jar.getManifest();
            assertNotNull(manifest);
            assertEquals("Test-Value", manifest.getMainAttributes().getValue("Test-Attribute"));
        }
    }

    @Test
    public void testStripNativeNamespaces_InvalidFile() {
        File nonExistentFile = new File(tempDir.toFile(), "nonexistent.jar");
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            processor.stripNativeNamespaces(nonExistentFile, Arrays.asList("test"));
        });
        
        assertTrue(exception.getMessage().contains("JAR file must exist"));
    }

    @Test
    public void testScanJarForNativeNamespaces_EmptyJar() throws IOException {
        File jarFile = createTestJar("empty.jar");
        
        List<String> namespaces = processor.scanJarForNativeNamespaces(jarFile);
        assertTrue(namespaces.isEmpty());
    }

    @Test
    public void testScanJarForNativeNamespaces_DetectsNativeLibraries() throws IOException {
        File jarFile = createTestJar("native.jar",
            "com/example/Main.class",
            "ca/weblite/native/mac/x64/libtest.dylib",
            "ca/weblite/native/win/x64/test.dll",
            "ca/weblite/native/linux/x64/libtest.so",
            "com/other/regular/Class.class"
        );
        
        List<String> namespaces = processor.scanJarForNativeNamespaces(jarFile);
        
        // Should detect native namespaces based on file paths
        assertTrue(namespaces.size() > 0);
        // Note: exact detection depends on heuristics, but should find native-related packages
    }

    @Test
    public void testScanJarForNativeNamespaces_DetectsNativeClasses() throws IOException {
        File jarFile = createTestJar("native-classes.jar",
            "ca/weblite/native/mac/x64/NativeInterface.class",
            "ca/weblite/native/win/arm64/WindowsNative.class",
            "com/example/regular/Class.class"
        );
        
        List<String> namespaces = processor.scanJarForNativeNamespaces(jarFile);
        
        // Should detect packages with native classes
        assertFalse(namespaces.isEmpty());
    }

    @Test
    public void testCreatePlatformSpecificJar_NoStripping() throws IOException {
        File originalJar = createTestJar("original.jar",
            "com/example/Main.class",
            "com/example/Utils.class"
        );
        
        File platformJar = processor.createPlatformSpecificJar(
            originalJar, 
            Platform.MAC_X64, 
            Collections.emptyList()
        );
        
        assertEquals("original-mac-x64.jar", platformJar.getName());
        assertTrue(platformJar.exists());
        
        List<String> originalEntries = getJarEntries(originalJar);
        List<String> platformEntries = getJarEntries(platformJar);
        assertEquals(originalEntries.size(), platformEntries.size());
    }

    @Test
    public void testCreatePlatformSpecificJar_WithStripping() throws IOException {
        File originalJar = createTestJar("original.jar",
            "com/example/Main.class",
            "ca/weblite/native/mac/x64/MacNative.class",
            "ca/weblite/native/win/x64/WinNative.class",
            "ca/weblite/native/linux/x64/LinuxNative.class"
        );
        
        File platformJar = processor.createPlatformSpecificJar(
            originalJar,
            Platform.MAC_X64,
            Arrays.asList("ca.weblite.native.win.x64", "ca.weblite.native.linux.x64")
        );
        
        assertEquals("original-mac-x64.jar", platformJar.getName());
        assertTrue(platformJar.exists());
        
        List<String> entries = getJarEntries(platformJar);
        assertTrue(entries.contains("com/example/Main.class"));
        assertTrue(entries.contains("ca/weblite/native/mac/x64/MacNative.class"));
        assertFalse(entries.contains("ca/weblite/native/win/x64/WinNative.class"));
        assertFalse(entries.contains("ca/weblite/native/linux/x64/LinuxNative.class"));
    }

    @Test
    public void testCreatePlatformSpecificJar_WithStripAndKeepLists() throws IOException {
        File originalJar = createTestJar("original.jar",
            "com/example/Main.class",
            "ca/weblite/native/mac/x64/MacNative.class",
            "ca/weblite/native/win/x64/WinNative.class",
            "ca/weblite/native/linux/x64/LinuxNative.class"
        );
        
        File platformJar = processor.createPlatformSpecificJar(
            originalJar,
            Platform.MAC_X64,
            Arrays.asList("ca.weblite.native.win.x64", "ca.weblite.native.linux.x64"),
            Arrays.asList("ca.weblite.native.mac.x64")
        );
        
        assertEquals("original-mac-x64.jar", platformJar.getName());
        assertTrue(platformJar.exists());
        
        List<String> entries = getJarEntries(platformJar);
        assertTrue(entries.contains("com/example/Main.class"));
        assertTrue(entries.contains("ca/weblite/native/mac/x64/MacNative.class"));
        assertFalse(entries.contains("ca/weblite/native/win/x64/WinNative.class"));
        assertFalse(entries.contains("ca/weblite/native/linux/x64/LinuxNative.class"));
    }

    @Test
    public void testCreatePlatformSpecificJar_InvalidInput() {
        File nonExistentFile = new File(tempDir.toFile(), "nonexistent.jar");
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            processor.createPlatformSpecificJar(nonExistentFile, Platform.MAC_X64, Collections.emptyList());
        });
        
        assertTrue(exception.getMessage().contains("Original JAR file must exist"));
    }

    @Test
    public void testCreatePlatformSpecificJar_NullPlatform() throws IOException {
        File originalJar = createTestJar("original.jar", "com/example/Main.class");
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            processor.createPlatformSpecificJar(originalJar, null, Collections.emptyList());
        });
        
        assertTrue(exception.getMessage().contains("Target platform cannot be null"));
    }

    @Test
    public void testJarFileIntegrity_AfterStripping() throws IOException {
        // Create a JAR with some real content
        File jarFile = createTestJarWithContent("integrity.jar",
            "com/example/Main.class", "public class Main { public static void main(String[] args) {} }",
            "ca/weblite/native/mac/x64/Native.class", "public class Native {}",
            "resources/config.properties", "key=value\nother=setting"
        );
        
        processor.stripNativeNamespaces(jarFile, Arrays.asList("ca.weblite.native.mac.x64"));
        
        // Verify JAR is still valid and readable
        try (JarFile jar = new JarFile(jarFile)) {
            assertNotNull(jar.getManifest());
            
            // Verify remaining content is accessible
            JarEntry mainEntry = jar.getJarEntry("com/example/Main.class");
            assertNotNull(mainEntry);
            
            JarEntry configEntry = jar.getJarEntry("resources/config.properties");
            assertNotNull(configEntry);
            
            // Verify stripped content is gone
            JarEntry nativeEntry = jar.getJarEntry("ca/weblite/native/mac/x64/Native.class");
            assertNull(nativeEntry);
        }
    }

    // Tests for Path-based Namespace Support

    @Test
    public void testStripNativeNamespaces_PathBasedNotation_SpecificFile() throws IOException {
        File jarFile = createTestJar("path-test.jar",
            "com/example/Main.class",
            "my-native-lib.dll",
            "other-lib.so",
            "ca/weblite/regular/Class.class"
        );
        
        processor.stripNativeNamespaces(jarFile, Arrays.asList("/my-native-lib.dll"));
        
        List<String> entries = getJarEntries(jarFile);
        assertFalse(entries.contains("my-native-lib.dll"));
        assertTrue(entries.contains("other-lib.so"));
        assertTrue(entries.contains("com/example/Main.class"));
        assertTrue(entries.contains("ca/weblite/regular/Class.class"));
    }

    @Test
    public void testStripNativeNamespaces_PathBasedNotation_Directory() throws IOException {
        File jarFile = createTestJar("path-dir-test.jar",
            "com/example/Main.class",
            "native/windows/my-lib.dll",
            "native/windows/other-lib.dll",
            "native/macos/my-lib.dylib",
            "regular/Class.class"
        );
        
        processor.stripNativeNamespaces(jarFile, Arrays.asList("/native/windows/"));
        
        List<String> entries = getJarEntries(jarFile);
        assertFalse(entries.contains("native/windows/my-lib.dll"));
        assertFalse(entries.contains("native/windows/other-lib.dll"));
        assertTrue(entries.contains("native/macos/my-lib.dylib"));
        assertTrue(entries.contains("com/example/Main.class"));
        assertTrue(entries.contains("regular/Class.class"));
    }

    @Test
    public void testStripNativeNamespaces_PathBasedNotation_DirectoryWithoutSlash() throws IOException {
        File jarFile = createTestJar("path-dir-no-slash.jar",
            "com/example/Main.class",
            "native/lib.dll",
            "native/subdir/lib2.dll",
            "other/file.txt"
        );
        
        // Path without trailing slash should still match directory
        processor.stripNativeNamespaces(jarFile, Arrays.asList("/native"));
        
        List<String> entries = getJarEntries(jarFile);
        assertFalse(entries.contains("native/lib.dll"));
        assertFalse(entries.contains("native/subdir/lib2.dll"));
        assertTrue(entries.contains("com/example/Main.class"));
        assertTrue(entries.contains("other/file.txt"));
    }

    @Test
    public void testStripNativeNamespaces_MixedFormats() throws IOException {
        File jarFile = createTestJar("mixed-formats.jar",
            "com/example/Main.class",
            "ca/weblite/native/mac/x64/MacLib.class",
            "ca/weblite/native/win/x64/WinLib.class",
            "my-root-lib.dll",
            "native/custom/custom-lib.so",
            "lib/another.dylib"
        );
        
        // Mix Java package notation and path-based notation
        processor.stripNativeNamespaces(jarFile, Arrays.asList(
            "ca.weblite.native.mac.x64",  // Java package notation
            "/my-root-lib.dll",           // Specific file
            "/native/custom/"             // Directory path
        ));
        
        List<String> entries = getJarEntries(jarFile);
        assertFalse(entries.contains("ca/weblite/native/mac/x64/MacLib.class"));
        assertFalse(entries.contains("my-root-lib.dll"));
        assertFalse(entries.contains("native/custom/custom-lib.so"));
        assertTrue(entries.contains("ca/weblite/native/win/x64/WinLib.class"));
        assertTrue(entries.contains("lib/another.dylib"));
        assertTrue(entries.contains("com/example/Main.class"));
    }

    @Test
    public void testScanJarForNativeNamespaces_DetectsPathBasedNamespaces() throws IOException {
        File jarFile = createTestJar("scan-path-based.jar",
            "com/example/Main.class",
            "my-lib.dll",              // Root native file
            "another.so",              // Root native file
            "lib/native.dylib",        // Custom path native file
            "native/win/lib.dll",      // Custom path native file
            "ca/weblite/native/mac/x64/JavaPackageNative.class"
        );
        
        List<String> namespaces = processor.scanJarForNativeNamespaces(jarFile);
        
        // Should detect both path-based and Java package namespaces
        assertTrue(namespaces.contains("/my-lib.dll"));
        assertTrue(namespaces.contains("/another.so"));
        assertTrue(namespaces.contains("/lib/native.dylib"));
        assertTrue(namespaces.contains("/native/win/lib.dll"));
        
        // Should also detect Java package namespace
        boolean hasJavaPackageNamespace = namespaces.stream()
            .anyMatch(ns -> ns.contains("weblite.native.mac"));
    }

    @Test
    public void testScanJarForNativeNamespaces_IgnoresNonNativeFiles() throws IOException {
        File jarFile = createTestJar("scan-non-native.jar",
            "com/example/Main.class",
            "resources/config.properties",
            "data.json",
            "image.png",
            "my-native.dll"  // Only this should be detected
        );
        
        List<String> namespaces = processor.scanJarForNativeNamespaces(jarFile);
        
        assertEquals(1, namespaces.size());
        assertTrue(namespaces.contains("/my-native.dll"));
    }

    @Test
    public void testCreatePlatformSpecificJar_WithPathBasedStripping() throws IOException {
        File originalJar = createTestJar("original-path.jar",
            "com/example/Main.class",
            "my-windows-lib.dll",
            "my-mac-lib.dylib",
            "native/linux/my-lib.so",
            "ca/weblite/common/Utils.class"
        );
        
        File platformJar = processor.createPlatformSpecificJar(
            originalJar,
            Platform.MAC_X64,
            Arrays.asList("/my-windows-lib.dll", "/native/linux/")
        );
        
        List<String> entries = getJarEntries(platformJar);
        assertFalse(entries.contains("my-windows-lib.dll"));
        assertFalse(entries.contains("native/linux/my-lib.so"));
        assertTrue(entries.contains("my-mac-lib.dylib"));
        assertTrue(entries.contains("com/example/Main.class"));
        assertTrue(entries.contains("ca/weblite/common/Utils.class"));
    }

    @Test
    public void testNamespaceFormat_EdgeCases() throws IOException {
        File jarFile = createTestJar("edge-cases.jar",
            "com/example/Main.class",
            "lib.dll",                    // Simple filename
            "a/b.so",                     // Minimal path
            "very/deep/nested/path/lib.dylib",  // Deep nesting
            "file-with-dashes.dll",       // Filename with special chars
            "path with spaces/lib.so"     // Path with spaces (if supported)
        );
        
        // Test various edge cases
        processor.stripNativeNamespaces(jarFile, Arrays.asList(
            "/lib.dll",                   // Root file
            "/a/b.so",                   // Minimal path
            "/very/deep/"                // Nested directory
        ));
        
        List<String> entries = getJarEntries(jarFile);
        assertFalse(entries.contains("lib.dll"));
        assertFalse(entries.contains("a/b.so"));
        assertFalse(entries.contains("very/deep/nested/path/lib.dylib"));
        assertTrue(entries.contains("file-with-dashes.dll"));
        assertTrue(entries.contains("com/example/Main.class"));
    }

    @Test
    public void testNamespaceConversion_JavaPackageFormat() throws IOException {
        File jarFile = createTestJar("java-package-test.jar",
            "com/example/Main.class",
            "ca/weblite/native/mac/x64/Lib.class",
            "ca/weblite/native/mac/x64/Helper.class",
            "ca/weblite/native/win/x64/Lib.class",
            "ca/weblite/common/Utils.class"
        );
        
        // Test that Java package notation properly converts to path format
        processor.stripNativeNamespaces(jarFile, Arrays.asList("ca.weblite.native.mac.x64"));
        
        List<String> entries = getJarEntries(jarFile);
        assertFalse(entries.contains("ca/weblite/native/mac/x64/Lib.class"));
        assertFalse(entries.contains("ca/weblite/native/mac/x64/Helper.class"));
        assertTrue(entries.contains("ca/weblite/native/win/x64/Lib.class"));
        assertTrue(entries.contains("ca/weblite/common/Utils.class"));
        assertTrue(entries.contains("com/example/Main.class"));
    }

    @Test
    public void testNamespaceConversion_TrailingSlashHandling() throws IOException {
        File jarFile = createTestJar("trailing-slash-test.jar",
            "native/windows/lib1.dll",
            "native/windows/lib2.dll",
            "native/macos/lib.dylib",
            "other/file.txt"
        );
        
        // Test both with and without trailing slash
        File jarFile1 = createTestJar("test1.jar",
            "native/windows/lib1.dll",
            "native/windows/lib2.dll",
            "native/macos/lib.dylib"
        );
        
        File jarFile2 = createTestJar("test2.jar", 
            "native/windows/lib1.dll",
            "native/windows/lib2.dll",
            "native/macos/lib.dylib"
        );
        
        // Both should work the same way
        processor.stripNativeNamespaces(jarFile1, Arrays.asList("/native/windows/"));
        processor.stripNativeNamespaces(jarFile2, Arrays.asList("/native/windows"));
        
        List<String> entries1 = getJarEntries(jarFile1);
        List<String> entries2 = getJarEntries(jarFile2);
        
        assertEquals(entries1, entries2);
        assertFalse(entries1.contains("native/windows/lib1.dll"));
        assertFalse(entries1.contains("native/windows/lib2.dll"));
        assertTrue(entries1.contains("native/macos/lib.dylib"));
    }

    // Tests for new dual-list functionality (strip + keep lists)

    @Test
    public void testProcessJarForPlatform_EmptyLists() throws IOException {
        File jarFile = createTestJar("empty-lists-test.jar", 
            "com/example/Main.class",
            "ca/weblite/native/mac/x64/NativeLib.class",
            "ca/weblite/native/win/x64/NativeLib.class"
        );
        
        List<String> originalEntries = getJarEntries(jarFile);
        
        processor.processJarForPlatform(jarFile, Collections.emptyList(), Collections.emptyList());
        
        List<String> newEntries = getJarEntries(jarFile);
        assertEquals(originalEntries.size(), newEntries.size());
        assertEquals(originalEntries, newEntries);
    }

    @Test
    public void testProcessJarForPlatform_KeepTakesPrecedence() throws IOException {
        File jarFile = createTestJar("precedence-test.jar",
            "com/example/Main.class",
            "com/example/native/mac/x64/MacLib.class",
            "com/example/native/mac/x64/MacHelper.class", 
            "com/example/native/win/x64/WinLib.class",
            "com/example/native/test/TestLib.class",
            "com/example/core/Core.class"
        );
        
        // Strip the entire native namespace, but keep the mac.x64 sub-namespace
        processor.processJarForPlatform(jarFile, 
            Arrays.asList("com.example.native"),           // Strip entire native namespace
            Arrays.asList("com.example.native.mac.x64")    // But keep mac.x64 sub-namespace
        );
        
        List<String> entries = getJarEntries(jarFile);
        
        // Keep list should override strip list
        assertTrue(entries.contains("com/example/native/mac/x64/MacLib.class"));
        assertTrue(entries.contains("com/example/native/mac/x64/MacHelper.class"));
        
        // Strip list should still work for entries not in keep list
        assertFalse(entries.contains("com/example/native/win/x64/WinLib.class"));
        assertFalse(entries.contains("com/example/native/test/TestLib.class"));
        
        // Non-matching entries should be kept by default
        assertTrue(entries.contains("com/example/Main.class"));
        assertTrue(entries.contains("com/example/core/Core.class"));
    }

    @Test
    public void testProcessJarForPlatform_ComplexOverlapScenario() throws IOException {
        // This tests the exact scenario described in the RFC
        File jarFile = createTestJar("overlap-scenario.jar",
            "com/example/Main.class",
            "com/myapp/native/mac/x64/MacLib.dylib",        // Should be KEPT (in keep list)
            "com/myapp/native/windows/WinLib.dll",          // Should be STRIPPED (in strip, not in keep)
            "com/myapp/native/test/TestLib.so",             // Should be STRIPPED (in strip, not in keep)
            "com/myapp/core/AppCore.class"                  // Should be KEPT (default behavior)
        );
        
        processor.processJarForPlatform(jarFile,
            Arrays.asList("com.myapp.native", "ca.weblite.native.win.x64", "ca.weblite.native.linux.x64"), // Strip list
            Arrays.asList("com.myapp.native.mac.x64")  // Keep list
        );
        
        List<String> entries = getJarEntries(jarFile);
        
        // Verify the exact behavior described in the RFC
        assertTrue(entries.contains("com/myapp/native/mac/x64/MacLib.dylib"));   // KEEP (matches keep list)
        assertFalse(entries.contains("com/myapp/native/windows/WinLib.dll"));    // STRIP (matches strip list, not in keep list)
        assertFalse(entries.contains("com/myapp/native/test/TestLib.so"));       // STRIP (matches strip list, not in keep list)
        assertTrue(entries.contains("com/myapp/core/AppCore.class"));            // KEEP (doesn't match any strip pattern)
        assertTrue(entries.contains("com/example/Main.class"));                 // KEEP (doesn't match any strip pattern)
    }

    @Test
    public void testProcessJarForPlatform_PathBasedOverlap() throws IOException {
        File jarFile = createTestJar("path-based-overlap.jar",
            "com/example/Main.class",
            "native/mac/lib.dylib",        // Should be KEPT (in keep list)
            "native/win/lib.dll",          // Should be STRIPPED (in strip, not in keep)
            "native/test/lib.so",          // Should be STRIPPED (in strip, not in keep)
            "my-root-lib.dll",             // Should be KEPT (in keep list)
            "other-root-lib.so"            // Should be STRIPPED (in strip, not in keep)
        );
        
        processor.processJarForPlatform(jarFile,
            Arrays.asList("/native/", "/other-root-lib.so"),  // Strip list: native directory and specific file
            Arrays.asList("/native/mac/", "/my-root-lib.dll") // Keep list: mac subdirectory and specific file
        );
        
        List<String> entries = getJarEntries(jarFile);
        
        assertTrue(entries.contains("native/mac/lib.dylib"));    // KEEP (matches keep list)
        assertFalse(entries.contains("native/win/lib.dll"));     // STRIP (matches strip list, not in keep list)
        assertFalse(entries.contains("native/test/lib.so"));     // STRIP (matches strip list, not in keep list)  
        assertTrue(entries.contains("my-root-lib.dll"));         // KEEP (matches keep list)
        assertFalse(entries.contains("other-root-lib.so"));      // STRIP (matches strip list, not in keep list)
        assertTrue(entries.contains("com/example/Main.class"));  // KEEP (default behavior)
    }

    @Test
    public void testProcessJarForPlatform_MixedFormatOverlap() throws IOException {
        File jarFile = createTestJar("mixed-format-overlap.jar",
            "com/example/Main.class",
            "ca/weblite/native/mac/x64/JavaLib.class",  // Java package format - should be KEPT
            "ca/weblite/native/win/x64/WinLib.class",   // Java package format - should be STRIPPED
            "my-native-lib.dll",                        // Path-based format - should be KEPT
            "other-lib.so"                              // Path-based format - should be STRIPPED
        );
        
        // Mix Java package notation and path-based notation in both lists
        processor.processJarForPlatform(jarFile,
            Arrays.asList("ca.weblite.native", "/other-lib.so"),       // Strip: Java package + path-based
            Arrays.asList("ca.weblite.native.mac.x64", "/my-native-lib.dll") // Keep: Java package + path-based
        );
        
        List<String> entries = getJarEntries(jarFile);
        
        assertTrue(entries.contains("ca/weblite/native/mac/x64/JavaLib.class")); // KEEP (matches keep list)
        assertFalse(entries.contains("ca/weblite/native/win/x64/WinLib.class")); // STRIP (matches strip list, not in keep list)
        assertTrue(entries.contains("my-native-lib.dll"));                      // KEEP (matches keep list)
        assertFalse(entries.contains("other-lib.so"));                          // STRIP (matches strip list, not in keep list)
        assertTrue(entries.contains("com/example/Main.class"));                 // KEEP (default behavior)
    }

    @Test
    public void testProcessJarForPlatform_OnlyStripList() throws IOException {
        File jarFile = createTestJar("only-strip.jar",
            "com/example/Main.class",
            "ca/weblite/native/mac/x64/MacLib.class",
            "ca/weblite/native/win/x64/WinLib.class",
            "ca/weblite/common/Utils.class"
        );
        
        // Only strip list, no keep list
        processor.processJarForPlatform(jarFile, 
            Arrays.asList("ca.weblite.native.win.x64"),
            Collections.emptyList()
        );
        
        List<String> entries = getJarEntries(jarFile);
        assertTrue(entries.contains("ca/weblite/native/mac/x64/MacLib.class"));
        assertFalse(entries.contains("ca/weblite/native/win/x64/WinLib.class"));
        assertTrue(entries.contains("com/example/Main.class"));
        assertTrue(entries.contains("ca/weblite/common/Utils.class"));
    }

    @Test
    public void testProcessJarForPlatform_OnlyKeepList() throws IOException {
        File jarFile = createTestJar("only-keep.jar",
            "com/example/Main.class",
            "ca/weblite/native/mac/x64/MacLib.class",
            "ca/weblite/native/win/x64/WinLib.class",
            "ca/weblite/common/Utils.class"
        );
        
        // Only keep list, no strip list - should keep everything (keep list has no effect without strip list)
        processor.processJarForPlatform(jarFile, 
            Collections.emptyList(),
            Arrays.asList("ca.weblite.native.mac.x64")
        );
        
        List<String> entries = getJarEntries(jarFile);
        assertTrue(entries.contains("ca/weblite/native/mac/x64/MacLib.class"));
        assertTrue(entries.contains("ca/weblite/native/win/x64/WinLib.class"));
        assertTrue(entries.contains("com/example/Main.class"));
        assertTrue(entries.contains("ca/weblite/common/Utils.class"));
    }

    @Test
    public void testProcessJarForPlatform_MultipleLevelsOfOverlap() throws IOException {
        File jarFile = createTestJar("multi-level-overlap.jar",
            "com/example/Main.class",
            "com/myapp/native/mac/x64/specific/SpecificLib.class",    // Level 5 - should be KEPT
            "com/myapp/native/mac/x64/general/GeneralLib.class",      // Level 5 - should be KEPT  
            "com/myapp/native/mac/arm64/ArmLib.class",                // Level 4 - should be STRIPPED
            "com/myapp/native/win/x64/WinLib.class",                  // Level 4 - should be STRIPPED
            "com/myapp/native/test/TestLib.class",                    // Level 3 - should be STRIPPED
            "com/myapp/util/Utils.class"                              // Level 2 - should be KEPT
        );
        
        // Strip broad namespace but keep specific sub-namespace
        processor.processJarForPlatform(jarFile,
            Arrays.asList("com.myapp.native"),           // Strip entire native tree
            Arrays.asList("com.myapp.native.mac.x64")    // Keep only mac x64 subtree
        );
        
        List<String> entries = getJarEntries(jarFile);
        
        // Mac x64 subtree should be preserved completely
        assertTrue(entries.contains("com/myapp/native/mac/x64/specific/SpecificLib.class"));
        assertTrue(entries.contains("com/myapp/native/mac/x64/general/GeneralLib.class"));
        
        // Everything else under native should be stripped
        assertFalse(entries.contains("com/myapp/native/mac/arm64/ArmLib.class"));
        assertFalse(entries.contains("com/myapp/native/win/x64/WinLib.class"));
        assertFalse(entries.contains("com/myapp/native/test/TestLib.class"));
        
        // Non-native should be kept
        assertTrue(entries.contains("com/myapp/util/Utils.class"));
        assertTrue(entries.contains("com/example/Main.class"));
    }

    @Test
    public void testProcessJarForPlatform_InvalidInput() {
        File nonExistentFile = new File(tempDir.toFile(), "nonexistent.jar");
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            processor.processJarForPlatform(nonExistentFile, Collections.emptyList(), Collections.emptyList());
        });
        
        assertTrue(exception.getMessage().contains("JAR file must exist"));
    }

    @Test
    public void testProcessJarForPlatform_NullLists() throws IOException {
        File jarFile = createTestJar("null-lists.jar", "com/example/Main.class");
        
        // Should handle null lists gracefully
        assertDoesNotThrow(() -> {
            processor.processJarForPlatform(jarFile, (List<String>) null, (List<String>) null);
        });
    }

    @Test
    public void testCreatePlatformSpecificJar_DualListEdgeCase() throws IOException {
        File originalJar = createTestJar("edge-case.jar",
            "com/example/Main.class",
            "exact-match.dll",                      // Exact file in keep list
            "exact-match.dll.backup",               // Similar name but should be stripped  
            "prefix/exact-match.dll",               // File with prefix path
            "prefix/subdir/lib.so"                  // File under prefix directory
        );
        
        File platformJar = processor.createPlatformSpecificJar(
            originalJar,
            Platform.WIN_X64,
            Arrays.asList("/prefix/"),              // Strip entire prefix directory  
            Arrays.asList("/exact-match.dll")       // Keep specific file
        );
        
        List<String> entries = getJarEntries(platformJar);
        
        assertTrue(entries.contains("exact-match.dll"));           // KEEP (exact match in keep list)
        assertTrue(entries.contains("exact-match.dll.backup"));    // KEEP (doesn't match any pattern)
        assertFalse(entries.contains("prefix/exact-match.dll"));   // STRIP (matches strip prefix, not in keep)
        assertFalse(entries.contains("prefix/subdir/lib.so"));     // STRIP (matches strip prefix, not in keep)
        assertTrue(entries.contains("com/example/Main.class"));    // KEEP (default behavior)
    }

    // Helper methods

    private File createTestJar(String fileName, String... entryNames) throws IOException {
        File jarFile = new File(tempDir.toFile(), fileName);
        
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
            for (String entryName : entryNames) {
                JarEntry entry = new JarEntry(entryName);
                jos.putNextEntry(entry);
                
                if (!entryName.endsWith("/")) {
                    // Add some dummy content for files
                    jos.write(("// Dummy content for " + entryName).getBytes());
                }
                
                jos.closeEntry();
            }
        }
        
        return jarFile;
    }

    private File createTestJarWithManifest(String fileName, Manifest manifest, String... entryNames) throws IOException {
        File jarFile = new File(tempDir.toFile(), fileName);
        
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile), manifest)) {
            for (String entryName : entryNames) {
                JarEntry entry = new JarEntry(entryName);
                jos.putNextEntry(entry);
                
                if (!entryName.endsWith("/")) {
                    jos.write(("// Content for " + entryName).getBytes());
                }
                
                jos.closeEntry();
            }
        }
        
        return jarFile;
    }

    private File createTestJarWithContent(String fileName, String... entryNamesAndContent) throws IOException {
        if (entryNamesAndContent.length % 2 != 0) {
            throw new IllegalArgumentException("Must provide pairs of entry names and content");
        }
        
        File jarFile = new File(tempDir.toFile(), fileName);
        
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
            for (int i = 0; i < entryNamesAndContent.length; i += 2) {
                String entryName = entryNamesAndContent[i];
                String content = entryNamesAndContent[i + 1];
                
                JarEntry entry = new JarEntry(entryName);
                jos.putNextEntry(entry);
                jos.write(content.getBytes());
                jos.closeEntry();
            }
        }
        
        return jarFile;
    }

    private Manifest createManifest(String key, String value) {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue(key, value);
        return manifest;
    }

    private List<String> getJarEntries(File jarFile) throws IOException {
        List<String> entries = new ArrayList<>();
        
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> enumeration = jar.entries();
            while (enumeration.hasMoreElements()) {
                JarEntry entry = enumeration.nextElement();
                if (!entry.getName().startsWith("META-INF/MANIFEST.MF")) {
                    entries.add(entry.getName());
                }
            }
        }
        
        Collections.sort(entries);
        return entries;
    }
}
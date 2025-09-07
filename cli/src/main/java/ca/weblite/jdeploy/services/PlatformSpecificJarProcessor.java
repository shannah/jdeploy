package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.Platform;

import javax.inject.Singleton;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipEntry;

/**
 * Service for processing JAR files to create platform-specific bundles by stripping
 * native libraries and classes for other platforms.
 */
@Singleton
public class PlatformSpecificJarProcessor {

    /**
     * Processes a JAR file for a specific platform using strip and keep lists.
     * Keep list takes precedence over strip list for any given file.
     * Creates a temporary file and replaces the original.
     * 
     * @param jarFile the JAR file to process
     * @param namespacesToStrip list of namespaces to remove. Supports two formats:
     *                         - Java package notation: "ca.weblite.native.mac.x64"
     *                         - Path-based notation: "/my-native-lib.dll", "/native/windows/"
     * @param namespacesToKeep list of namespaces to preserve even if they match strip list
     * @throws IOException if processing fails
     */
    public void processJarForPlatform(File jarFile, List<String> namespacesToStrip, List<String> namespacesToKeep) throws IOException {
        if (jarFile == null || !jarFile.exists()) {
            throw new IllegalArgumentException("JAR file must exist: " + jarFile);
        }
        
        if ((namespacesToStrip == null || namespacesToStrip.isEmpty()) && 
            (namespacesToKeep == null || namespacesToKeep.isEmpty())) {
            return; // Nothing to process
        }
        
        // Convert namespaces to path prefixes
        Set<String> stripPrefixes = convertNamespacesToPaths(namespacesToStrip != null ? namespacesToStrip : new ArrayList<>());
        Set<String> keepPrefixes = convertNamespacesToPaths(namespacesToKeep != null ? namespacesToKeep : new ArrayList<>());
        
        // Create temporary file
        File tempFile = new File(jarFile.getParentFile(), jarFile.getName() + ".tmp");
        
        try {
            createProcessedJar(jarFile, tempFile, stripPrefixes, keepPrefixes);
            
            // Replace original file with stripped version
            if (!jarFile.delete()) {
                throw new IOException("Failed to delete original JAR file: " + jarFile);
            }
            
            if (!tempFile.renameTo(jarFile)) {
                throw new IOException("Failed to rename temp file to original: " + tempFile + " -> " + jarFile);
            }
            
        } catch (IOException e) {
            // Clean up temp file on failure
            if (tempFile.exists()) {
                tempFile.delete();
            }
            throw e;
        }
    }

    /**
     * Strips specified namespaces from a JAR file in-place (backward compatibility).
     * This is equivalent to calling processJarForPlatform with empty keep list.
     * 
     * @param jarFile the JAR file to process
     * @param namespacesToStrip list of namespaces to remove
     * @throws IOException if processing fails
     */
    public void stripNativeNamespaces(File jarFile, List<String> namespacesToStrip) throws IOException {
        processJarForPlatform(jarFile, namespacesToStrip, new ArrayList<>());
    }

    /**
     * Creates a platform-specific JAR by stripping namespaces (backward compatibility).
     * This is equivalent to calling createPlatformSpecificJar with empty keep list.
     * 
     * @param originalJar the source JAR file
     * @param targetPlatform the platform to create the JAR for
     * @param namespacesToStrip list of namespaces to strip from the JAR
     * @return the created platform-specific JAR file
     * @throws IOException if processing fails
     */
    public File createPlatformSpecificJar(File originalJar, Platform targetPlatform, List<String> namespacesToStrip) throws IOException {
        return createPlatformSpecificJar(originalJar, targetPlatform, namespacesToStrip, new ArrayList<>());
    }

    /**
     * Scans a JAR file to detect native namespaces based on common patterns.
     * Returns both Java package notation and path-based notation namespaces.
     * 
     * @param jarFile the JAR file to scan
     * @return list of detected native namespaces in both formats
     * @throws IOException if scanning fails
     */
    public List<String> scanJarForNativeNamespaces(File jarFile) throws IOException {
        if (jarFile == null || !jarFile.exists()) {
            throw new IllegalArgumentException("JAR file must exist: " + jarFile);
        }
        
        Set<String> detectedNamespaces = new HashSet<>();
        
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                // Skip directories
                if (entry.isDirectory()) {
                    continue;
                }
                
                // Look for patterns that suggest native libraries
                if (isNativeLibraryPath(entryName)) {
                    // Try to extract Java package namespace
                    String namespace = extractNamespaceFromPath(entryName);
                    if (namespace != null && isLikelyNativeNamespace(namespace)) {
                        detectedNamespaces.add(namespace);
                    }
                    
                    // Also add path-based namespace for native files in root or custom paths
                    if (isNativeFileInRootOrCustomPath(entryName)) {
                        detectedNamespaces.add("/" + entryName);
                    }
                }
            }
        }
        
        return new ArrayList<>(detectedNamespaces);
    }

    /**
     * Creates a platform-specific JAR using strip and keep lists.
     * Keep list takes precedence over strip list for any given file.
     * 
     * @param originalJar the source JAR file
     * @param targetPlatform the platform to create the JAR for
     * @param namespacesToStrip list of namespaces to strip from the JAR
     * @param namespacesToKeep list of namespaces to preserve even if they match strip list
     * @return the created platform-specific JAR file
     * @throws IOException if processing fails
     */
    public File createPlatformSpecificJar(File originalJar, Platform targetPlatform, 
                                         List<String> namespacesToStrip, List<String> namespacesToKeep) throws IOException {
        if (originalJar == null || !originalJar.exists()) {
            throw new IllegalArgumentException("Original JAR file must exist: " + originalJar);
        }
        
        if (targetPlatform == null) {
            throw new IllegalArgumentException("Target platform cannot be null");
        }
        
        // Generate output file name
        String baseName = originalJar.getName();
        if (baseName.endsWith(".jar")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        
        String outputFileName = baseName + "-" + targetPlatform.getIdentifier() + ".jar";
        File outputFile = new File(originalJar.getParentFile(), outputFileName);
        
        if ((namespacesToStrip == null || namespacesToStrip.isEmpty()) && 
            (namespacesToKeep == null || namespacesToKeep.isEmpty())) {
            // No processing needed, just copy the file
            copyFile(originalJar, outputFile);
            return outputFile;
        }
        
        // Convert namespaces to path prefixes
        Set<String> stripPrefixes = convertNamespacesToPaths(namespacesToStrip != null ? namespacesToStrip : new ArrayList<>());
        Set<String> keepPrefixes = convertNamespacesToPaths(namespacesToKeep != null ? namespacesToKeep : new ArrayList<>());
        
        createProcessedJar(originalJar, outputFile, stripPrefixes, keepPrefixes);
        
        return outputFile;
    }

    /**
     * Converts namespace strings to file system path prefixes.
     * Supports two formats:
     * 1. Java package notation: "ca.weblite.native.mac.x64" → "ca/weblite/native/mac/x64/"
     * 2. Path-based notation: "/my-native-lib.dll" → "my-native-lib.dll", "/native/windows/" → "native/windows/"
     * 
     * @param namespaces list of namespaces in either format
     * @return set of path prefixes for JAR entry matching
     */
    private Set<String> convertNamespacesToPaths(List<String> namespaces) {
        Set<String> pathPrefixes = new HashSet<>();
        
        for (String namespace : namespaces) {
            if (namespace == null || namespace.trim().isEmpty()) {
                continue;
            }
            
            String trimmedNamespace = namespace.trim();
            String path;
            
            if (trimmedNamespace.startsWith("/")) {
                // Path-based notation: strip leading "/" and use as literal path
                path = trimmedNamespace.substring(1);
                
                // For path-based notation:
                // - If it ends with "/" it's a directory prefix (keep as-is)
                // - If it contains a file extension, it's an exact file match (keep as-is) 
                // - If it doesn't end with "/" and has no extension, assume it's a directory
                if (!path.endsWith("/") && !path.contains(".")) {
                    // Looks like a directory without trailing slash, add one for prefix matching
                    path += "/";
                }
                // Note: exact files (e.g., "my-lib.dll") are kept as-is for exact matching
            } else {
                // Java package notation: convert dots to slashes and ensure trailing slash
                path = trimmedNamespace.replace('.', '/');
                if (!path.endsWith("/")) {
                    path += "/";
                }
            }
            
            pathPrefixes.add(path);
        }
        
        return pathPrefixes;
    }

    /**
     * Creates a new JAR file with specified processing logic.
     * Keep prefixes take precedence over strip prefixes for any given file.
     */
    private void createProcessedJar(File sourceJar, File targetJar, Set<String> pathPrefixesToStrip, Set<String> pathPrefixesToKeep) throws IOException {
        try (JarFile inputJar = new JarFile(sourceJar)) {
            
            // Get manifest, create default if none exists
            Manifest manifest = inputJar.getManifest();
            if (manifest == null) {
                manifest = new Manifest();
                manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            }
            
            try (JarOutputStream outputJar = new JarOutputStream(new FileOutputStream(targetJar), manifest)) {
            
            Enumeration<JarEntry> entries = inputJar.entries();
            Set<String> addedEntries = new HashSet<>(); // Prevent duplicate entries
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                // Skip manifest entries (already handled in constructor)
                if (entryName.startsWith("META-INF/MANIFEST.MF")) {
                    continue;
                }
                
                // Check processing decision: keep list takes precedence over strip list
                if (shouldProcessEntry(entryName, pathPrefixesToStrip, pathPrefixesToKeep)) {
                    continue; // Skip this entry
                }
                
                // Avoid duplicate entries
                if (addedEntries.contains(entryName)) {
                    continue;
                }
                
                // Create new entry (don't reuse the old one to avoid compression issues)
                JarEntry newEntry = new JarEntry(entryName);
                newEntry.setTime(entry.getTime());
                
                try {
                    outputJar.putNextEntry(newEntry);
                    
                    // Copy entry content if it's not a directory
                    if (!entry.isDirectory()) {
                        try (InputStream input = inputJar.getInputStream(entry)) {
                            copyStream(input, outputJar);
                        }
                    }
                    
                    outputJar.closeEntry();
                    addedEntries.add(entryName);
                    
                } catch (Exception e) {
                    // Log and continue - some entries might cause issues but we want to process the rest
                    System.err.println("Warning: Failed to process JAR entry " + entryName + ": " + e.getMessage());
                }
            }
            }
        }
    }

    /**
     * Determines if an entry should be stripped based on strip and keep lists.
     * Processing logic:
     * 1. If entry matches keep list: KEEP (return false)
     * 2. Else if entry matches strip list: STRIP (return true)  
     * 3. Else: KEEP by default (return false)
     * 
     * @param entryName the JAR entry name
     * @param pathPrefixesToStrip prefixes for entries to strip
     * @param pathPrefixesToKeep prefixes for entries to keep (takes precedence)
     * @return true if entry should be stripped, false if it should be kept
     */
    private boolean shouldProcessEntry(String entryName, Set<String> pathPrefixesToStrip, Set<String> pathPrefixesToKeep) {
        // Check keep list first - it takes precedence
        for (String keepPrefix : pathPrefixesToKeep) {
            if (matchesPathPattern(entryName, keepPrefix)) {
                return false; // KEEP - keep list overrides strip list
            }
        }
        
        // Check strip list
        for (String stripPrefix : pathPrefixesToStrip) {
            if (matchesPathPattern(entryName, stripPrefix)) {
                return true; // STRIP - matches strip list and not in keep list
            }
        }
        
        return false; // KEEP by default
    }

    /**
     * Checks if an entry name matches a path pattern.
     * Handles both prefix matching (for directories ending with /) 
     * and exact matching (for specific files).
     * 
     * @param entryName the JAR entry name
     * @param pattern the pattern to match against
     * @return true if the entry matches the pattern
     */
    private boolean matchesPathPattern(String entryName, String pattern) {
        if (pattern.endsWith("/")) {
            // Directory prefix matching
            return entryName.startsWith(pattern);
        } else {
            // Exact file matching or directory prefix without trailing slash
            return entryName.equals(pattern) || entryName.startsWith(pattern + "/");
        }
    }

    /**
     * Heuristic to determine if a path looks like it contains native libraries.
     */
    private boolean isNativeLibraryPath(String path) {
        String lowerPath = path.toLowerCase();
        
        // Look for native library files
        if (lowerPath.endsWith(".dll") || lowerPath.endsWith(".so") || 
            lowerPath.endsWith(".dylib") || lowerPath.endsWith(".jnilib")) {
            return true;
        }
        
        // Look for common native library package patterns
        return lowerPath.contains("/native/") || 
               lowerPath.contains("/jni/") ||
               lowerPath.contains("/lib/") ||
               (lowerPath.contains(".native.") && lowerPath.endsWith(".class"));
    }

    /**
     * Extracts a namespace from a file path by finding the common package structure.
     */
    private String extractNamespaceFromPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        
        // Remove file extension if present
        int lastDot = path.lastIndexOf('.');
        int lastSlash = path.lastIndexOf('/');
        
        String packagePath;
        if (lastDot > lastSlash && lastSlash >= 0) {
            // Has file extension, remove it and the filename
            packagePath = path.substring(0, lastSlash);
        } else if (lastSlash >= 0) {
            // No extension or extension before last slash, remove filename
            packagePath = path.substring(0, lastSlash);
        } else {
            // No slash, might be just a filename
            return null;
        }
        
        // Convert path to namespace
        return packagePath.replace('/', '.');
    }

    /**
     * Heuristic to determine if a namespace looks like it contains native code.
     */
    private boolean isLikelyNativeNamespace(String namespace) {
        if (namespace == null) {
            return false;
        }
        
        String lower = namespace.toLowerCase();
        return lower.contains(".native.") ||
               lower.contains(".jni.") ||
               lower.contains(".lib.") ||
               lower.matches(".*\\.(win|mac|linux|darwin|windows)\\.(x64|arm64|i386|amd64).*");
    }

    /**
     * Determines if a native file is located in the root of the JAR or in a custom path
     * that doesn't follow typical Java package structures.
     */
    private boolean isNativeFileInRootOrCustomPath(String path) {
        String lowerPath = path.toLowerCase();
        
        // Check if it's a native library file
        if (!lowerPath.endsWith(".dll") && !lowerPath.endsWith(".so") && 
            !lowerPath.endsWith(".dylib") && !lowerPath.endsWith(".jnilib")) {
            return false;
        }
        
        // Check if file is in root (no directories)
        if (!path.contains("/")) {
            return true;
        }
        
        // Check if file is in shallow custom directories (not deep package structures)
        String[] pathParts = path.split("/");
        if (pathParts.length <= 3) { // e.g., "native/windows/lib.dll" or "lib/mylib.so"
            return true;
        }
        
        // Check if it doesn't follow Java package naming conventions
        String directory = path.substring(0, path.lastIndexOf('/'));
        String[] dirParts = directory.split("/");
        
        // If most directory parts don't look like Java package names, it's probably a custom path
        int javaPackageNameCount = 0;
        for (String part : dirParts) {
            if (part.matches("[a-z][a-z0-9]*")) { // Typical Java package naming
                javaPackageNameCount++;
            }
        }
        
        return javaPackageNameCount < dirParts.length / 2; // Less than half are Java package-style
    }

    /**
     * Copies one file to another.
     */
    private void copyFile(File source, File target) throws IOException {
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            copyStream(input, output);
        }
    }

    /**
     * Copies data from input stream to output stream.
     */
    private void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }
}
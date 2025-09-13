package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.JDeployIgnorePattern;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for parsing .jdpignore and platform-specific .jdpignore.{platform} files.
 * These files contain patterns for filtering native libraries in platform-specific bundles.
 * 
 * File Format:
 * - Lines starting with # are comments
 * - Empty lines are ignored
 * - Lines starting with ! are keep patterns (override ignore patterns)
 * - All other non-empty lines are ignore patterns
 * - Patterns can be Java package notation (com.example.native) or path notation (/path/to/file)
 */
public class JDeployIgnoreFileParser {
    
    /**
     * Parses a .jdpignore file and returns all patterns (both keep and ignore).
     * @param ignoreFile the .jdpignore file to parse
     * @return list of all patterns found in the file
     * @throws IOException if the file cannot be read
     */
    public List<JDeployIgnorePattern> parseFile(File ignoreFile) throws IOException {
        if (!ignoreFile.exists() || !ignoreFile.isFile()) {
            return Collections.emptyList();
        }
        
        List<JDeployIgnorePattern> patterns = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(ignoreFile))) {
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String originalLine = line;
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                JDeployIgnorePattern pattern = parsePattern(originalLine, lineNumber);
                if (pattern != null) {
                    patterns.add(pattern);
                }
            }
        }
        
        return patterns;
    }
    
    /**
     * Parses a .jdpignore file and returns only the ignore patterns (non-keep patterns).
     * @param ignoreFile the .jdpignore file to parse
     * @return list of ignore patterns from the file
     * @throws IOException if the file cannot be read
     */
    public List<JDeployIgnorePattern> parseIgnorePatterns(File ignoreFile) throws IOException {
        return parseFile(ignoreFile).stream()
                .filter(JDeployIgnorePattern::isIgnorePattern)
                .collect(Collectors.toList());
    }
    
    /**
     * Parses a .jdpignore file and returns only the keep patterns (! prefixed patterns).
     * @param ignoreFile the .jdpignore file to parse
     * @return list of keep patterns from the file
     * @throws IOException if the file cannot be read
     */
    public List<JDeployIgnorePattern> parseKeepPatterns(File ignoreFile) throws IOException {
        return parseFile(ignoreFile).stream()
                .filter(JDeployIgnorePattern::isKeepPattern)
                .collect(Collectors.toList());
    }
    
    /**
     * Parses a single pattern line from a .jdpignore file.
     * @param line the line to parse
     * @param lineNumber the line number (for error reporting)
     * @return the parsed pattern, or null if the line is invalid
     */
    private JDeployIgnorePattern parsePattern(String line, int lineNumber) {
        String originalPattern = line;
        boolean isKeep = false;
        String pattern = line.trim();
        
        // Check if this is a keep pattern
        if (pattern.startsWith("!")) {
            isKeep = true;
            pattern = pattern.substring(1).trim();
        }
        
        // Convert pattern to JAR path format
        String jarPath = convertPatternToJarPath(pattern);
        
        if (jarPath == null) {
            System.err.println("Warning: Invalid pattern on line " + lineNumber + ": " + originalPattern);
            return null;
        }
        
        return new JDeployIgnorePattern(originalPattern, isKeep, jarPath);
    }
    
    /**
     * Converts a pattern from .jdpignore file format to JAR path format.
     * Handles both Java package notation (com.example.native) and path notation (/path/to/file).
     * 
     * @param pattern the pattern to convert
     * @return the pattern in JAR path format, or null if the pattern is invalid
     */
    public String convertPatternToJarPath(String pattern) {
        if (pattern == null) {
            return null;
        }
        
        pattern = pattern.trim();
        if (pattern.isEmpty()) {
            return null;
        }
        
        // If pattern starts with /, it's a path - remove the leading /
        if (pattern.startsWith("/")) {
            return pattern.substring(1);
        }
        
        // If pattern already contains /, treat as path (no dot conversion)
        if (pattern.contains("/")) {
            return pattern;
        }
        
        // Otherwise, treat as namespace - convert dots to slashes
        return pattern.replace(".", "/");
    }
    
    /**
     * Check if a pattern looks like a file extension pattern.
     * @param pattern the pattern to check
     * @return true if this looks like a file extension pattern
     */
    private boolean isFileExtensionPattern(String pattern) {
        // Patterns like *.dll, lib?.so, com.example.native.class etc.
        return pattern.startsWith("*") && pattern.contains(".") ||
               pattern.contains("?") && pattern.contains(".") ||
               pattern.endsWith(".class") || 
               pattern.endsWith(".dll") ||
               pattern.endsWith(".so") ||
               pattern.endsWith(".dylib") ||
               pattern.endsWith(".jar");
    }
    
    /**
     * Converts a path pattern with wildcards to a regex pattern.
     * @param pathPattern the path pattern (may contain * and ? wildcards)
     * @return the equivalent regex pattern
     */
    private String convertPathToRegex(String pathPattern) {
        if (pathPattern == null) {
            return null;
        }
        
        // First handle ** patterns (which should match anything including path separators)
        String regex = pathPattern.replace("**", "DOUBLE_STAR_PLACEHOLDER");
        
        // Escape regex special characters except * and ?
        regex = regex.replace("\\", "\\\\")
                    .replace(".", "\\.")
                    .replace("+", "\\+")
                    .replace("^", "\\^")
                    .replace("$", "\\$")
                    .replace("(", "\\(")
                    .replace(")", "\\)")
                    .replace("[", "\\[")
                    .replace("]", "\\]")
                    .replace("{", "\\{")
                    .replace("}", "\\}")
                    .replace("|", "\\|");
        
        // Convert single wildcards to regex
        regex = regex.replace("*", "[^/]*")  // Single * matches anything except path separator
                    .replace("?", "[^/]");   // ? matches single character except path separator
        
        // Convert ** back to match everything including path separators
        regex = regex.replace("DOUBLE_STAR_PLACEHOLDER", ".*");
        
        return regex;
    }
    
    /**
     * Checks if a file path matches any of the given patterns.
     * @param filePath the file path to test (in JAR path format)
     * @param patterns the patterns to test against
     * @return true if the file path matches any pattern
     */
    public boolean matchesAnyPattern(String filePath, List<JDeployIgnorePattern> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        
        return patterns.stream().anyMatch(pattern -> pattern.matches(filePath));
    }
    
    /**
     * Checks if a file path matches a specific pattern string.
     * This is a convenience method for testing individual patterns.
     * 
     * @param filePath the file path to test
     * @param pattern the pattern string to match against
     * @return true if the file path matches the pattern
     */
    public boolean matchesPattern(String filePath, String pattern) {
        String jarPath = convertPatternToJarPath(pattern);
        if (jarPath == null) {
            return false;
        }
        
        JDeployIgnorePattern ignorePattern = new JDeployIgnorePattern(pattern, false, jarPath);
        return ignorePattern.matches(filePath);
    }
}
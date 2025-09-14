package ca.weblite.jdeploy.models;

/**
 * Represents a pattern from a .jdpignore file.
 * Patterns can be either ignore patterns (default) or keep patterns (prefixed with !).
 * Keep patterns override ignore patterns and ensure files are included in platform bundles.
 */
public class JDeployIgnorePattern {
    private final String originalPattern;
    private final boolean isKeep;
    private final String jarPath;
    
    public JDeployIgnorePattern(String originalPattern, boolean isKeep, String jarPath) {
        this.originalPattern = originalPattern;
        this.isKeep = isKeep;
        this.jarPath = jarPath;
    }
    
    /**
     * Gets the original pattern string as it appeared in the .jdpignore file.
     * @return the original pattern string
     */
    public String getOriginalPattern() {
        return originalPattern;
    }
    
    /**
     * Checks if this is a keep pattern (prefixed with ! in the file).
     * Keep patterns override ignore patterns and force files to be included.
     * @return true if this is a keep pattern, false if it's an ignore pattern
     */
    public boolean isKeepPattern() {
        return isKeep;
    }
    
    /**
     * Checks if this is an ignore pattern (not prefixed with ! in the file).
     * @return true if this is an ignore pattern, false if it's a keep pattern
     */
    public boolean isIgnorePattern() {
        return !isKeep;
    }
    
    /**
     * Gets the pattern converted to JAR path format for matching.
     * @return the pattern in JAR path format
     */
    public String getJarPath() {
        return jarPath;
    }
    
    /**
     * Checks if the given file path matches this pattern using namespace-based matching.
     * 
     * Namespace rules:
     * 1. All patterns are treated as namespace prefixes
     * 2. A pattern matches if the file path starts with the pattern followed by / or is exactly the pattern
     * 3. For files, also match class names (e.g., com.example.MyClass matches com/example/MyClass.class and MyClass$1.class)
     * 4. Must avoid partial package name matches (com.example should not match com.examples)
     * 
     * @param filePath the file path to test (in JAR path format)
     * @return true if the file path matches this pattern
     */
    public boolean matches(String filePath) {
        if (filePath == null || jarPath == null) {
            return false;
        }
        
        return matchesNamespace(filePath, jarPath);
    }
    
    /**
     * Implements namespace-based matching logic.
     * 
     * @param filePath the file path to test
     * @param pattern the pattern (already converted from dots to slashes)
     * @return true if the file matches the namespace pattern
     */
    private boolean matchesNamespace(String filePath, String pattern) {
        // Exact match
        if (filePath.equals(pattern)) {
            return true;
        }
        if (pattern.contains("*")) {
            // Wildcard match - convert to regex
            String regex = pattern
                    .replace(".", "\\.")
                    .replace("**", "{DOUBLE_ASTERISK}")
                    .replace("*", "[^/]*")
                    .replace("{DOUBLE_ASTERISK}", ".*");
            return filePath.matches(regex);
        }
        if (pattern.endsWith("/")) {
            // Directory match - file is under the directory
            if (filePath.startsWith(pattern)) {
                return true;
            }
            return false;
        }
        // Namespace match - file is under the namespace
        if (filePath.startsWith(pattern + "/")) {
            return true;
        }
        
        // Handle file-level matching for class files
        // E.g., pattern "com/example/MyClass" should match:
        // - com/example/MyClass.class
        // - com/example/MyClass$1.class  
        // - com/example/MyClass$InnerClass.class
        if (isClassFilePattern(pattern, filePath)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if the pattern should match the file path for class files.
     * Handles cases where a pattern like "com.example.MyClass" should match 
     * "com/example/MyClass.class" and inner class files.
     */
    private boolean isClassFilePattern(String pattern, String filePath) {
        if (!filePath.endsWith(".class")) {
            return false;
        }
        
        // Check if filePath matches pattern.class
        if (filePath.equals(pattern + ".class")) {
            return true;
        }
        
        // Check if filePath matches pattern$*.class (inner classes)
        String innerClassPrefix = pattern + "$";
        if (filePath.startsWith(innerClassPrefix) && filePath.endsWith(".class")) {
            return true;
        }
        
        return false;
    }
    
    @Override
    public String toString() {
        return "JDeployIgnorePattern{" +
                "originalPattern='" + originalPattern + '\'' +
                ", isKeep=" + isKeep +
                ", jarPath='" + jarPath + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        JDeployIgnorePattern that = (JDeployIgnorePattern) o;
        
        if (isKeep != that.isKeep) return false;
        if (!originalPattern.equals(that.originalPattern)) return false;
        return jarPath.equals(that.jarPath);
    }
    
    @Override
    public int hashCode() {
        int result = originalPattern.hashCode();
        result = 31 * result + (isKeep ? 1 : 0);
        result = 31 * result + jarPath.hashCode();
        return result;
    }
}
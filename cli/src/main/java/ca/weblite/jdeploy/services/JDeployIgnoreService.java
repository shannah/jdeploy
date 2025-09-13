package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.models.Platform;
import ca.weblite.jdeploy.models.JDeployIgnorePattern;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for handling .jdpignore file processing and filtering logic.
 * This service bridges the gap between JDeployProject (shared module) and 
 * ignore file functionality (CLI module) while respecting module boundaries.
 * 
 * Features:
 * - Loads global .jdpignore and platform-specific .jdpignore.{platform} files
 * - Caches parsed patterns for performance
 * - Provides file filtering logic for platform-specific bundles
 * - Handles keep patterns that override ignore patterns
 */
@Singleton
public class JDeployIgnoreService {
    
    private final JDeployIgnoreFileParser parser;
    
    // Cache for parsed patterns to avoid re-parsing files
    private final Map<String, List<JDeployIgnorePattern>> patternCache = new ConcurrentHashMap<>();
    
    @Inject
    public JDeployIgnoreService(JDeployIgnoreFileParser parser) {
        this.parser = parser;
    }
    
    /**
     * Gets the global .jdpignore file for a project.
     * @param project the JDeploy project
     * @return the global ignore file, or null if the project directory cannot be determined
     */
    public File getGlobalIgnoreFile(JDeployProject project) {
        File projectDir = getProjectDirectory(project);
        if (projectDir == null) {
            return null;
        }
        return new File(projectDir, ".jdpignore");
    }
    
    /**
     * Gets the platform-specific .jdpignore file for a project and platform.
     * @param project the JDeploy project
     * @param platform the target platform
     * @return the platform-specific ignore file, or null if the project directory cannot be determined
     */
    public File getPlatformIgnoreFile(JDeployProject project, Platform platform) {
        File projectDir = getProjectDirectory(project);
        if (projectDir == null) {
            return null;
        }
        return new File(projectDir, ".jdpignore." + platform.getIdentifier());
    }
    
    /**
     * Gets all ignore patterns from the global .jdpignore file.
     * @param project the JDeploy project
     * @return list of global ignore patterns (cached)
     */
    public List<JDeployIgnorePattern> getGlobalIgnorePatterns(JDeployProject project) {
        File globalIgnoreFile = getGlobalIgnoreFile(project);
        if (globalIgnoreFile == null || !globalIgnoreFile.exists()) {
            return Collections.emptyList();
        }
        
        String cacheKey = "global:" + globalIgnoreFile.getAbsolutePath() + ":" + globalIgnoreFile.lastModified();
        return patternCache.computeIfAbsent(cacheKey, key -> {
            try {
                return parser.parseFile(globalIgnoreFile);
            } catch (IOException e) {
                System.err.println("Warning: Failed to parse global .jdpignore file: " + e.getMessage());
                return Collections.emptyList();
            }
        });
    }
    
    /**
     * Gets all ignore patterns from the platform-specific .jdpignore file.
     * @param project the JDeploy project
     * @param platform the target platform (Platform.DEFAULT returns empty list)
     * @return list of platform-specific ignore patterns (cached)
     */
    public List<JDeployIgnorePattern> getPlatformIgnorePatterns(JDeployProject project, Platform platform) {
        // For DEFAULT platform, there are no platform-specific patterns
        if (platform == Platform.DEFAULT) {
            return Collections.emptyList();
        }
        
        File platformIgnoreFile = getPlatformIgnoreFile(project, platform);
        if (platformIgnoreFile == null || !platformIgnoreFile.exists()) {
            return Collections.emptyList();
        }
        
        String cacheKey = "platform:" + platform.getIdentifier() + ":" + 
                         platformIgnoreFile.getAbsolutePath() + ":" + platformIgnoreFile.lastModified();
        return patternCache.computeIfAbsent(cacheKey, key -> {
            try {
                return parser.parseFile(platformIgnoreFile);
            } catch (IOException e) {
                System.err.println("Warning: Failed to parse platform .jdpignore file for " + 
                                 platform.getIdentifier() + ": " + e.getMessage());
                return Collections.emptyList();
            }
        });
    }
    
    /**
     * Determines whether a file should be included in a platform-specific bundle.
     * This is the main filtering logic that combines global and platform-specific patterns.
     * 
     * Resolution Logic:
     * 1. Load global and platform-specific patterns
     * 2. Check if file matches any keep pattern (global or platform) - if yes, return true
     * 3. Check if file matches any ignore pattern (global or platform) - if yes, return false  
     * 4. Default to include (return true)
     * 
     * @param project the JDeploy project
     * @param filePath the file path to test (in JAR path format)
     * @param platform the target platform
     * @return true if the file should be included, false if it should be excluded
     */
    public boolean shouldIncludeFile(JDeployProject project, String filePath, Platform platform) {
        if (filePath == null) {
            return true; // Default to include
        }
        
        // Load all patterns
        List<JDeployIgnorePattern> globalPatterns = getGlobalIgnorePatterns(project);
        List<JDeployIgnorePattern> platformPatterns = getPlatformIgnorePatterns(project, platform);

        // Apply platform patterns first, then global patterns
        
        // Step 1: Check keep patterns first (they override ignore patterns)
        for (JDeployIgnorePattern pattern : platformPatterns) {
            if (pattern.isKeepPattern() && pattern.matches(filePath)) {
                return true; // Keep pattern matched - include the file
            }
        }
        
        // Step 2: Check ignore patterns
        for (JDeployIgnorePattern pattern : platformPatterns) {
            if (pattern.isIgnorePattern() && pattern.matches(filePath)) {
                return false; // Ignore pattern matched - exclude the file
            }
        }

        for (JDeployIgnorePattern pattern : globalPatterns) {
            if (pattern.isKeepPattern() && pattern.matches(filePath)) {
                return true; // Keep pattern matched - include the file
            }
        }

        // Step 2: Check ignore patterns
        for (JDeployIgnorePattern pattern : globalPatterns) {
            if (pattern.isIgnorePattern() && pattern.matches(filePath)) {
                return false; // Ignore pattern matched - exclude the file
            }
        }
        
        // Step 3: Default to include
        return true;
    }
    
    /**
     * Clears the pattern cache. Useful for testing or when ignore files are modified.
     */
    public void clearCache() {
        patternCache.clear();
    }
    
    /**
     * Gets the project directory from a JDeployProject.
     * @param project the JDeploy project
     * @return the project directory, or null if it cannot be determined
     */
    private File getProjectDirectory(JDeployProject project) {
        if (project == null || project.getPackageJSONFile() == null) {
            return null;
        }
        
        // The package.json file should be in the project root directory
        return project.getPackageJSONFile().getParent().toFile();
    }
    
    /**
     * Checks if any .jdpignore files exist for the project.
     * @param project the JDeploy project
     * @return true if global or any platform-specific ignore files exist
     */
    public boolean hasIgnoreFiles(JDeployProject project) {
        File globalFile = getGlobalIgnoreFile(project);
        if (globalFile != null && globalFile.exists()) {
            return true;
        }
        
        // Check for any platform-specific files (excluding DEFAULT)
        for (Platform platform : Platform.values()) {
            if (platform == Platform.DEFAULT) {
                continue; // Skip DEFAULT platform - it doesn't have its own ignore file
            }
            File platformFile = getPlatformIgnoreFile(project, platform);
            if (platformFile != null && platformFile.exists()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Gets statistics about ignore patterns for debugging/logging.
     * @param project the JDeploy project
     * @param platform the target platform (optional)
     * @return a map with pattern statistics
     */
    public Map<String, Object> getPatternStatistics(JDeployProject project, Platform platform) {
        Map<String, Object> stats = new HashMap<>();
        
        List<JDeployIgnorePattern> globalPatterns = getGlobalIgnorePatterns(project);
        stats.put("globalPatternCount", globalPatterns.size());
        stats.put("globalKeepPatternCount", globalPatterns.stream().mapToInt(p -> p.isKeepPattern() ? 1 : 0).sum());
        stats.put("globalIgnorePatternCount", globalPatterns.stream().mapToInt(p -> p.isIgnorePattern() ? 1 : 0).sum());
        
        if (platform != null) {
            List<JDeployIgnorePattern> platformPatterns = getPlatformIgnorePatterns(project, platform);
            stats.put("platformPatternCount", platformPatterns.size());
            stats.put("platformKeepPatternCount", platformPatterns.stream().mapToInt(p -> p.isKeepPattern() ? 1 : 0).sum());
            stats.put("platformIgnorePatternCount", platformPatterns.stream().mapToInt(p -> p.isIgnorePattern() ? 1 : 0).sum());
            stats.put("platform", platform.getIdentifier());
        }
        
        return stats;
    }
}
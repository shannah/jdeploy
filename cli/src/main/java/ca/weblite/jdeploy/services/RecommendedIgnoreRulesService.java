package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.Platform;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for generating recommended .jdpignore rules for common scenarios.
 * Provides platform-specific and global ignore patterns for various frameworks and libraries.
 */
public class RecommendedIgnoreRulesService {
    
    /**
     * Generates recommended global ignore rules that apply to all platforms.
     * These rules typically exclude unnecessary libraries that jDeploy provides or duplicates.
     * 
     * @return list of recommended global ignore patterns
     */
    public List<String> generateGlobalIgnoreRules() {
        List<String> rules = new ArrayList<>();
        
        rules.add("# JavaFX libraries - jDeploy provides JavaFX runtime");
        rules.add("javafx");
        rules.add("com/sun/javafx");
        rules.add("com/sun/glass");
        rules.add("com/sun/prism");
        rules.add("/glass.dll");
        rules.add("/libglass.dylib");
        rules.add("/libglass.so");
        rules.add("/prism_*.dll");
        rules.add("/libprism_*.dylib");
        rules.add("/libprism_*.so");
        rules.add("/javafx_*.dll");
        rules.add("/libjavafx_*.dylib");
        rules.add("/libjavafx_*.so");
        
        return rules;
    }
    
    /**
     * Generates recommended platform-specific ignore rules for a given platform.
     * These rules keep only the native libraries relevant to the target platform
     * while ignoring libraries for other platforms.
     * 
     * @param platform the target platform
     * @return list of recommended platform-specific patterns
     */
    public List<String> generatePlatformIgnoreRules(Platform platform) {
        List<String> rules = new ArrayList<>();
        
        if (platform == Platform.DEFAULT) {
            // For global/default platform, return global rules
            return generateGlobalIgnoreRules();
        }
        
        rules.add("# Keep " + getPlatformDisplayName(platform) + " native libraries");
        rules.add("");
        
        // Add Skiko (Compose Multiplatform) rules
        addSkikoRules(rules, platform);
        
        // Add SQLite native library rules
        addSQLiteRules(rules, platform);

        // Add LibGDX specific rules
        addLibGdxRules(rules, platform);
        
        return rules;
    }

    private void addLibGdxRules(List<String> rules, Platform platform) {
        if (platform == Platform.DEFAULT || platform == Platform.WIN_ARM64) {
            // No special handling for default or Linux ARM64 (no official LWJGL support)
            return;
        }

        rules.add("# LWJGL native libraries");

        // Keep current platform's LWJGL libraries
        switch (platform) {
            case WIN_X64:
                rules.add("/linux");
                rules.add("/macos");
                rules.add("/windows/arm64");
                rules.add("/windows/x86");
                rules.add("/gdx.dll");
                rules.add("**libgdx*.so");
                rules.add("**libgdx*.dylib");
                break;
            case WIN_ARM64:
                // No Win Arm64 support in LWJGL as of now
                break;
            case MAC_X64:
                rules.add("/linux");
                rules.add("/windows");
                rules.add("/macos/arm64");
                rules.add("**gdx*.dll");
                rules.add("**libgdx*.so");
                rules.add("/libgdxarm64.dylib");
                break;
            case MAC_ARM64:
                rules.add("/linux");
                rules.add("/windows");
                rules.add("/macos/x64");
                rules.add("**gdx*.dll");
                rules.add("**libgdx*.so");
                rules.add("/libgdx64.dylib");
                break;
            case LINUX_X64:
                rules.add("/macos");
                rules.add("/windows");
                rules.add("/linux");
                rules.add("!/linux/x64");
                rules.add("**gdx*.dll");
                rules.add("**libgdx*.so");
                rules.add("/libgdx*.dylib");
                rules.add("!/libgdx64.so");
                break;
            case LINUX_ARM64:
                rules.add("/macos");
                rules.add("/windows");
                rules.add("/linux");
                rules.add("!/linux/arm64");
                rules.add("**gdx*.dll");
                rules.add("**libgdx*.so");
                rules.add("/libgdx*.dylib");
                rules.add("!/libgdxarm64.so");
                break;
        }

        rules.add("");
    }
    
    /**
     * Adds Skiko (Compose Multiplatform) specific rules for the platform.
     * Skiko includes large native rendering libraries for each platform.
     */
    private void addSkikoRules(List<String> rules, Platform platform) {
        rules.add("# Skiko (Compose Multiplatform) native libraries");
        
        // Keep current platform's Skiko libraries
        switch (platform) {
            case WIN_X64:
                rules.add("!/skiko-windows-x64.dll");
                break;
            case WIN_ARM64:
                rules.add("!/skiko-windows-arm64.dll");
                break;
            case MAC_X64:
                rules.add("!/libskiko-macos-x64.dylib");
                break;
            case MAC_ARM64:
                rules.add("!/libskiko-macos-arm64.dylib");
                break;
            case LINUX_X64:
                rules.add("!/libskiko-linux-x64.so");
                break;
            case LINUX_ARM64:
                rules.add("!/libskiko-linux-arm64.so");
                break;
        }
        
        // Ignore all other Skiko platforms
        rules.add("skiko-windows-*.dll");
        rules.add("libskiko-macos-*.dylib");
        rules.add("libskiko-linux-*.so");
        rules.add("skiko-*.dll");
        rules.add("libskiko-*.dylib");
        rules.add("libskiko-*.so");
        rules.add("");
    }
    
    /**
     * Adds SQLite native library rules for the platform.
     * SQLite libraries are organized by OS and architecture in specific paths.
     */
    private void addSQLiteRules(List<String> rules, Platform platform) {
        rules.add("# SQLite native libraries");
        
        // Keep current platform's SQLite libraries
        switch (platform) {
            case WIN_X64:
                rules.add("!/org/sqlite/native/Windows/x86_64");
                break;
            case WIN_ARM64:
                rules.add("!/org/sqlite/native/Windows/aarch64");
                break;
            case MAC_X64:
                rules.add("!/org/sqlite/native/Mac/x86_64");
                break;
            case MAC_ARM64:
                rules.add("!/org/sqlite/native/Mac/aarch64");
                break;
            case LINUX_X64:
                rules.add("!/org/sqlite/native/Linux/x86_64");
                break;
            case LINUX_ARM64:
                rules.add("!/org/sqlite/native/Linux/aarch64");
                break;
        }
        
        // Ignore all other SQLite platforms
        rules.add("org/sqlite/native");
        rules.add("");
    }
    
    /**
     * Gets the display name for a platform.
     */
    private String getPlatformDisplayName(Platform platform) {
        switch (platform) {
            case MAC_X64: return "macOS Intel";
            case MAC_ARM64: return "macOS Silicon";
            case WIN_X64: return "Windows x64";
            case WIN_ARM64: return "Windows ARM64";
            case LINUX_X64: return "Linux x64";
            case LINUX_ARM64: return "Linux ARM64";
            default: return platform.getIdentifier();
        }
    }
    
    /**
     * Gets the OS name for a platform.
     */
    private String getOSName(Platform platform) {
        switch (platform) {
            case MAC_X64:
            case MAC_ARM64:
                return "macos";
            case WIN_X64:
            case WIN_ARM64:
                return "windows";
            case LINUX_X64:
            case LINUX_ARM64:
                return "linux";
            default:
                return "unknown";
        }
    }
    
    /**
     * Gets the architecture name for a platform.
     */
    private String getArchName(Platform platform) {
        switch (platform) {
            case MAC_X64:
            case WIN_X64:
            case LINUX_X64:
                return "x64";
            case MAC_ARM64:
            case WIN_ARM64:
            case LINUX_ARM64:
                return "arm64";
            default:
                return "unknown";
        }
    }
    
    /**
     * Converts a list of rules to a formatted string suitable for .jdpignore files.
     * 
     * @param rules the list of rules
     * @return formatted string with rules separated by newlines
     */
    public String formatRulesAsText(List<String> rules) {
        if (rules.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (String rule : rules) {
            sb.append(rule).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * Generates a complete set of recommended rules for a platform, formatted as text.
     * 
     * @param platform the target platform
     * @return formatted rules as text
     */
    public String generateRecommendedRulesText(Platform platform) {
        List<String> rules = generatePlatformIgnoreRules(platform);
        return formatRulesAsText(rules);
    }
}
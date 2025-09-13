package ca.weblite.jdeploy.models;

/**
 * Enumeration of supported platform-architecture combinations for platform-specific bundles.
 */
public enum Platform {
    DEFAULT("default"),
    MAC_X64("mac-x64"),
    MAC_ARM64("mac-arm64"),
    WIN_X64("win-x64"), 
    WIN_ARM64("win-arm64"),
    LINUX_X64("linux-x64"),
    LINUX_ARM64("linux-arm64");
    
    private final String identifier;
    
    Platform(String identifier) {
        this.identifier = identifier;
    }
    
    /**
     * Gets the string identifier used in configuration (e.g., "mac-x64")
     * @return the platform identifier
     */
    public String getIdentifier() {
        return identifier;
    }
    
    /**
     * Gets the property name for this platform's package name (e.g., "packageMacX64")
     * @return the property name
     */
    public String getPackagePropertyName() {
        switch (this) {
            case DEFAULT: return "package";
            case MAC_X64: return "packageMacX64";
            case MAC_ARM64: return "packageMacArm64";
            case WIN_X64: return "packageWinX64";
            case WIN_ARM64: return "packageWinArm64";
            case LINUX_X64: return "packageLinuxX64";
            case LINUX_ARM64: return "packageLinuxArm64";
            default: throw new IllegalStateException("Unknown platform: " + this);
        }
    }
    
    /**
     * Parses a platform identifier string to a Platform enum value
     * @param identifier the platform identifier (e.g., "mac-x64")
     * @return the Platform enum value, or null if not found
     */
    public static Platform fromIdentifier(String identifier) {
        if (identifier == null) return null;
        
        for (Platform platform : Platform.values()) {
            if (platform.identifier.equals(identifier)) {
                return platform;
            }
        }
        return null;
    }
    
    /**
     * Gets all supported platform identifiers
     * @return array of platform identifier strings
     */
    public static String[] getAllIdentifiers() {
        Platform[] platforms = Platform.values();
        String[] identifiers = new String[platforms.length];
        for (int i = 0; i < platforms.length; i++) {
            identifiers[i] = platforms[i].identifier;
        }
        return identifiers;
    }
}
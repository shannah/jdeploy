package ca.weblite.jdeploy.installer.util;

/**
 * Utility class for detecting system architecture and providing architecture-specific suffixes
 * for package directory names.
 *
 * This utility supports the architecture-specific package locations feature (GitHub Issue #310)
 * which allows multiple architecture versions of the same app to coexist on systems that support
 * multiple architectures (e.g., macOS with Rosetta 2, Windows ARM64 with emulation).
 */
public class ArchitectureUtil {

    /**
     * Gets the architecture suffix to use for directory names.
     *
     * @return Architecture suffix with leading dash (e.g., "-arm64" or "-x64")
     */
    public static String getArchitectureSuffix() {
        return "-" + getArchitecture();
    }

    /**
     * Gets the current system architecture.
     *
     * @return Architecture name: "arm64" or "x64"
     */
    public static String getArchitecture() {
        String osArch = System.getProperty("os.arch");

        // ARM64 variants
        if ("aarch64".equals(osArch) || "arm64".equals(osArch)) {
            return "arm64";
        }

        // x64 variants (default)
        // Includes: x86_64, amd64, x64
        return "x64";
    }

    /**
     * Checks if the current system is running on ARM64 architecture.
     *
     * @return true if ARM64, false otherwise
     */
    public static boolean isArm64() {
        return "arm64".equals(getArchitecture());
    }

    /**
     * Checks if the current system is running on x64 architecture.
     *
     * @return true if x64, false otherwise
     */
    public static boolean isX64() {
        return "x64".equals(getArchitecture());
    }
}

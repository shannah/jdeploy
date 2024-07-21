package ca.weblite.jdeploy.jvmdownloader;

import java.io.IOException;

public interface JVMDownloader {
    /**
     * Download a JVM
     * @param basePath The base path to download the JVM to.  It will be downloaded into a subdirectory of this path
     *                 based on the platform, architecture, and bundle type.
     * @param version The Java version
     * @param bundleType The bundle type.  Either "jdk" or "jre"
     * @param javafx Whether to include JavaFX
     * @param overridePlatform The platform to download for.  If null, the current platform will be used.
     * @param overrideArch The architecture to download for.  If null, the current architecture will be used.
     * @param overrideBitness The bitness to download for.  If null, the current bitness will be used.
     * @throws IOException
     */
    void downloadJVM(
            String basePath,
            String version,
            String bundleType,
            boolean javafx,
            String overridePlatform,
            String overrideArch,
            String overrideBitness
    ) throws IOException;
}

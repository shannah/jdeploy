package ca.weblite.jdeploy.jvmdownloader;

import java.io.File;
import java.io.IOException;

public class JVMFinder {

    private JVMDownloader downloader;

    private JVMKit kit;

    private boolean download;

    public JVMFinder(
            JVMKit jvmConfig,
            JVMDownloader downloader,
            boolean download
    ){
        this.kit = jvmConfig;
        this.downloader = downloader;
        this.download = download;
    }

    public File findJVM(
            String basePath,
            String version,
            String bundleType,
            boolean javafx,
            String overridePlatform,
            String overrideArch,
            String overrideBitness
    ) throws IOException {
        String versionStr = bundleType + version + (javafx ? "fx" : "");
        File basedir = kit.getJVMParentDir(
                basePath,
                version,
                bundleType,
                javafx,
                overridePlatform,
                overrideArch,
                overrideBitness
        );

        if (!basedir.exists() && download) {
            downloader.downloadJVM(
                    basePath,
                    version,
                    bundleType,
                    javafx,
                    overridePlatform,
                    overrideArch,
                    overrideBitness
            );

        }

        if (basedir.isDirectory()) {
            for (File child : basedir.listFiles()) {
                if (child.getName().startsWith(versionStr)) {
                    return child;
                }
            }
        }

        if (download) {
            downloader.downloadJVM(
                    basePath,
                    version,
                    bundleType,
                    javafx,
                    overridePlatform,
                    overrideArch,
                    overrideBitness
            );
        }

        if (basedir.isDirectory()) {
            for (File child : basedir.listFiles()) {
                if (child.getName().startsWith(versionStr)) {
                    return child;
                }
            }
        }

        throw new IOException("Failed to find JVM");
    }
}

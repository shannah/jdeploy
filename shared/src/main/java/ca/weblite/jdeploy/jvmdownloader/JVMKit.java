package ca.weblite.jdeploy.jvmdownloader;

import java.io.File;

public class JVMKit {

    private String provider;


    public JVMKit(){
        this("zulu");
    }
    public JVMKit(String provider){
        this.provider = provider;
    }

    public JVMFinder createFinder(boolean download){
        return new JVMFinder(this, createDownloader(), download);
    }

    public JVMDownloader createDownloader() {
        return new ZuluJVMDownloader(this);
    }

    public File getJVMParentDir(
            String basePath,
            String version,
            String bundleType,
            boolean javafx,
            String overridePlatform,
            String overrideArch,
            String overrideBitness
    ) {
        String platform = overridePlatform != null ? overridePlatform : getPlatform();
        String arch = overrideArch != null ? overrideArch : getArch();
        String bitness = overrideBitness != null ? overrideBitness : "64";
        String sep = File.separator;
        return new File(
                basePath,
                "jre" + sep +
                        provider + sep +
                        platform + sep +
                        arch + bitness + sep +
                        version + sep +
                        bundleType + sep +
                        (javafx ? "fx" : "default")
        );
    }

    private String getPlatform() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "windows";
        } else if (os.contains("mac")) {
            return "macos";
        } else {
            return "linux";
        }
    }

    private String getArch() {
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("arm")) {
            return "arm";
        } else if (arch.contains("86")) {
            return "x86";
        } else {
            return "x86";
        }
    }
}

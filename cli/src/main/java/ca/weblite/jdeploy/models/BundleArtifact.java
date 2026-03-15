package ca.weblite.jdeploy.models;

import java.io.File;

/**
 * Represents a single pre-built bundle artifact (native bundle wrapped in JAR or tar.gz)
 * ready for upload during publish.
 */
public class BundleArtifact {

    private final File file;
    private final String platform;
    private final String arch;
    private final String version;
    private final boolean cli;
    private final String sha256;
    private final String filename;
    private String url;

    public BundleArtifact(
            File file,
            String platform,
            String arch,
            String version,
            boolean cli,
            String sha256,
            String filename
    ) {
        this.file = file;
        this.platform = platform;
        this.arch = arch;
        this.version = version;
        this.cli = cli;
        this.sha256 = sha256;
        this.filename = filename;
    }

    public File getFile() {
        return file;
    }

    public String getPlatform() {
        return platform;
    }

    public String getArch() {
        return arch;
    }

    public String getVersion() {
        return version;
    }

    public boolean isCli() {
        return cli;
    }

    public String getSha256() {
        return sha256;
    }

    public String getFilename() {
        return filename;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Returns the platform key used in package.json bundles section.
     * Format: {platform}-{arch}
     */
    public String getPlatformKey() {
        return platform + "-" + arch;
    }
}

package ca.weblite.jdeploy.installer.nodejs;

import ca.weblite.jdeploy.appbundler.Util;

import ca.weblite.tools.platform.Platform;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;

import java.net.URL;

public class NodeJSInstaller implements NodeConstants {
    private final String version;

    public NodeJSInstaller(String version) {
        this.version = sanitizeVersion(version);
    }

    public File getInstallDir() {
        return new File(NODE_BASE + File.separator + version);
    }

    public boolean isInstalled() {
        return getInstallDir().isDirectory();
    }

    public String getVersion() {
        return version;
    }

    public void install() throws IOException {
        if (isInstalled()) {
            throw new IOException("Node "+version+" already installed at "+getInstallDir());
        }
        extract(download(), getInstallDir());
    }

    private String sanitizeVersion(String version) {
        if (version.startsWith("v")) {
            return version;
        }
        return "v" + version;
    }


    /**
     * Gets the download URL for NodeJS
     * @return
     */
    private URL getDownloadURL() {
        StringBuilder url = new StringBuilder();
        url.append(DIST_URL).append(version).append("/node-").append(version).append("-");

        if (Platform.getSystemPlatform().isMac()) {
            url.append("darwin-x64.tar.gz");
        } else if (Platform.getSystemPlatform().isLinux()) {
            url.append("linux-x64.tar.gz");
        } else if (Platform.getSystemPlatform().isWindows()) {
            url.append("win-x64.zip");
        }
        try {
            return new URL(url.toString());
        } catch (IOException ex) {
            // We have full control over URLs here so this shouldn't happen
            throw new RuntimeException(ex);
        }
    }



    private String getArchiveExtension() {
        if (Platform.getSystemPlatform().isWindows()) {
            return ".zip";
        } else {
            return ".tar.gz";
        }
    }

    private File download() throws IOException {
        return download(File.createTempFile("node-"+version, getArchiveExtension()));
    }

    private File download(File dest) throws IOException {
        if (!dest.getParentFile().isDirectory()) {
            dest.getParentFile().mkdirs();
        }
        IOUtils.copy(getDownloadURL(), dest);
        return dest;
    }

    private File extract(File archive, File destDirectory) throws IOException {
        destDirectory.getParentFile().mkdirs();
        File tmpDir = File.createTempFile("node-"+version, "dir");
        tmpDir.delete();
        tmpDir.mkdirs();
        if (Platform.getSystemPlatform().isWindows()) {
            Util.decompressZip(archive, tmpDir);
        } else {
            Util.decompressTarGz(archive, tmpDir);
        }
        for (File child : tmpDir.listFiles()) {
            if (child.isDirectory()) {
                FileUtils.moveDirectory(child, destDirectory);
                break;
            }
        }
        FileUtils.deleteDirectory(tmpDir);

        return destDirectory;
    }


    public static void main(String[] args) {
        try {
            NodeJSInstaller installer = new NodeJSInstaller("v18.2.0");
            installer.install();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }



}

package ca.weblite.jdeploy.jvmdownloader;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class ZuluJVMDownloader implements JVMDownloader {

    private JVMKit kit;

    private static final String ZULU_BASE_URL = "https://api.azul.com/zulu/download/community/v1.0/bundles/latest/binary?";

    public ZuluJVMDownloader(JVMKit kit){
        this.kit = kit;
    }

    @Override
    public void downloadJVM(
            String basePath,
            String version,
            String bundleType,
            boolean javafx,
            String overridePlatform,
            String overrideArch,
            String overrideBitness
    ) throws IOException {
        Path basedir = kit.getJVMParentDir(
                basePath,
                version,
                bundleType,
                javafx,
                overridePlatform,
                overrideArch,
                overrideBitness
        ).toPath();

        // Ensure directory exists
        if (!Files.exists(basedir)) {
            Files.createDirectories(basedir);
        }


        String javafxStr = javafx ? "true" : "false";
        String platform = overridePlatform != null ? overridePlatform : getPlatform();
        String arch = overrideArch != null ? overrideArch : getArch();
        String bitness = overrideBitness != null ? overrideBitness : "64";
        String ext = getExt(platform, javafx, arch);

        String urlStr = ZULU_BASE_URL + "java_version=" + version +
                "&ext=" + ext + "&bundle_type=" + bundleType +
                "&javafx=" + javafxStr + "&arch=" + arch +
                "&hw_bitness=" + bitness + "&os=" + platform;
        System.out.println("Downloading: " + urlStr);
        String versionStr = bundleType + version + (javafx ? "fx" : "");
        Path fileName = basedir.resolve(versionStr + "." + ext);
        Path savePath = basedir.resolve(fileName);

        if (!Files.exists(basedir)) {
            System.out.println("File already exists: " + savePath);
            return;
        }

        // Download the file
        URL url = new URL(urlStr);
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        int responseCode = httpConn.getResponseCode();

        // Check HTTP response code
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedInputStream inputStream = new BufferedInputStream(httpConn.getInputStream());
                 FileOutputStream outputStream = new FileOutputStream(savePath.toString())) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer, 0, 4096)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                System.out.println("Download complete: " + savePath);
            }
        } else {
            System.out.println("No file to download. Server replied HTTP code: " + responseCode);
        }
        httpConn.disconnect();
    }

    private String getPlatform() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return "windows";
        } else if (osName.contains("mac")) {
            return "macos";
        } else {
            return "linux";
        }
    }

    private String getArch() {
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("amd64") || arch.contains("x86_64")) {
            return "x86";
        } else if (arch.contains("arm64") || arch.contains("aarch64")) {
            return "arm";
        } else {
            throw new UnsupportedOperationException("Unsupported architecture: " + arch);
        }
    }

    private String getExt(String platform, boolean javafx, String arch) {
        if ("linux".equals(platform) && (javafx || "arm".equals(arch))) {
            return "tar.gz";
        } else {
            return "zip";
        }
    }
}

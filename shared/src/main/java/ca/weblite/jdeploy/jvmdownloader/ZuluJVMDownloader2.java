package ca.weblite.jdeploy.jvmdownloader;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ZuluJVMDownloader2 implements JVMDownloader {

    private static final String ZULU_METADATA_API_URL = "https://api.azul.com/metadata/v1/zulu/packages?";

    @Override
    public void downloadJVM(String basePath, String version, String bundleType, boolean javafx,
                            String overridePlatform, String overrideArch, String overrideBitness) throws IOException {
        String javafxStr = javafx ? "true" : "false";
        String platform = overridePlatform != null ? overridePlatform : getPlatform();
        String arch = getCombinedArch(overrideArch != null ? overrideArch : getArch(), overrideBitness);
        String ext = getExt(platform, javafx);

        String urlStr = ZULU_METADATA_API_URL + "java_version=" + version +
                "&archive_type=" + ext + "&java_package_type=" + bundleType +
                "&javafx_bundled=" + javafxStr + "&arch=" + arch +
                "&os=" + platform + "&latest=true&availability_types=ca&page=1&page_size=1";

        String jsonResponse = fetchURL(urlStr);
        System.out.println("url=" + urlStr);
        System.out.println("response=" + jsonResponse);
        String downloadUrl = extractDownloadUrl(jsonResponse);

        String versionStr = bundleType + version + (javafx ? "fx" : "");
        Path basedir = Paths.get(basePath, "jre", "zulu", platform, arch, bundleType, javafxStr);
        String fileName = versionStr + "." + ext;
        Path savePath = basedir.resolve(fileName);

        // Ensure directory exists
        if (!Files.exists(basedir)) {
            Files.createDirectories(basedir);
        }

        // Download the file
        URL url = new URL(downloadUrl);
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
            return "x64";
        } else if (arch.contains("arm64") || arch.contains("aarch64")) {
            return "aarch64";
        } else if (arch.contains("arm")) {
            return "arm";
        } else {
            return "x86";
        }
    }

    private String getCombinedArch(String arch, String bitness) {
        if (arch.equals("arm")) {
            if (bitness.equals("64")) {
                return "aarch64";
            } else {
                return "aarch32";
            }
        } else if (arch.equals("x86")) {
            return bitness.equals("64") ? "x64" : "i686";
        } else if (arch.equals("x64") || arch.equals("amd64")) {
            return "x64";
        } else if (arch.equals("aarch64")) {
            return "aarch64";
        } else {
            throw new UnsupportedOperationException("Unsupported architecture/bitness combination: " + arch + "/" + bitness);
        }
    }

    private String getExt(String platform, boolean javafx) {
        if ("linux".equals(platform) && javafx) {
            return "tar.gz";
        } else {
            return "zip";
        }
    }

    private String fetchURL(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");

        StringBuilder result = new StringBuilder();
        try (InputStream in = conn.getInputStream();
             BufferedInputStream bufIn = new BufferedInputStream(in)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = bufIn.read(buffer)) != -1) {
                result.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
            }
        }
        return result.toString();
    }

    private String extractDownloadUrl(String jsonResponse) {
        JSONArray packagesArray = new JSONArray(new JSONTokener(jsonResponse));
        if (packagesArray.length() > 0) {
            JSONObject packageObject = packagesArray.getJSONObject(0);
            return packageObject.getString("download_url");
        } else {
            throw new RuntimeException("No packages found in the JSON response.");
        }
    }

    public static void main(String[] args) {
        ZuluJVMDownloader2 downloader = new ZuluJVMDownloader2();
        try {
            String basePath = "/your/base/path";
            String version = "11";
            String bundleType = "jdk";
            boolean javafx = false;
            String overridePlatform = null; // or "linux", "windows", "macos"
            String overrideArch = null; // or "x64", "aarch64"
            String overrideBitness = null; // or "32", "64"

            downloader.downloadJVM(basePath, version, bundleType, javafx, overridePlatform, overrideArch, overrideBitness);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

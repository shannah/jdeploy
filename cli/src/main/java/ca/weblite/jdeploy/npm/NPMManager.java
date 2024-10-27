package ca.weblite.jdeploy.npm;

import com.vdurmont.semver4j.Semver;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class NPMManager {

    private String currentVersion; // Current Node.js version, e.g., "18.18.2"

    public static final String DEFAULT_VERSION = "23.1.0";

    private static final String NODE_INSTALLATION_BASE_DIR = System.getProperty("user.home")
            + File.separator + ".jdeploy" + File.separator + "node";

    public NPMManager() {
        this(getDefaultVersion());
    }

    private static String getDefaultVersion() {
        return System.getProperty("jdeploy.node.version", DEFAULT_VERSION);
    }

    public NPMManager(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    /**
     * Checks if the specified Node.js version is installed.
     */
    public boolean isNodeInstalled() {
        File nodeDir = new File(NODE_INSTALLATION_BASE_DIR, "v" + currentVersion);
        File nodeExecutable = getNodeExecutable(nodeDir);
        return nodeExecutable.exists();
    }

    /**
     * Returns the path to the Node.js executable.
     */
    public String getNodePath() {
        File nodeDir = new File(NODE_INSTALLATION_BASE_DIR, "v" + currentVersion);
        File nodeExecutable = getNodeExecutable(nodeDir);
        return nodeExecutable.getAbsolutePath();
    }

    /**
     * Executes a Node.js command with the provided arguments.
     */
    public int nodeExec(Map<String, String> env, File directory, String[] args)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(getNodePath());
        command.addAll(Arrays.asList(args));

        ProcessBuilder builder = new ProcessBuilder(command);
        if (env != null) {
            builder.environment().putAll(env);
        }
        if (directory != null) {
            builder.directory(directory);
        }
        builder.inheritIO(); // Inherit I/O to show output in console
        Process process = builder.start();
        return process.waitFor();
    }

    /**
     * Executes an npm command with the provided arguments.
     */
    public int npmExec(Map<String, String> env, File directory, String[] args)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        String npmPath = getNpmPath();
        File npmFile = new File(npmPath);

        if (!npmFile.exists()) {
            // Fallback to 'node npm-cli.js'
            String npmCliJsPath = getNpmCliJsPath();
            command.add(getNodePath());
            command.add(npmCliJsPath);
        } else {
            command.add(npmPath);
        }

        command.addAll(Arrays.asList(args));

        ProcessBuilder builder = new ProcessBuilder(command);
        if (env != null) {
            builder.environment().putAll(env);
        }
        if (directory != null) {
            builder.directory(directory);
        }
        builder.inheritIO(); // Inherit I/O to show output in console
        Process process = builder.start();
        return process.waitFor();
    }

    /**
     * Returns the installed Node.js version.
     */
    public String getNodeVersion() throws IOException, InterruptedException {
        List<String> command = Arrays.asList(getNodePath(), "-v");
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        String version;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            version = reader.readLine();
        }
        process.waitFor();
        return version != null ? version.trim() : null;
    }

    /**
     * Returns the installed npm version.
     */
    public String getNpmVersion() throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        String npmPath = getNpmPath();
        File npmFile = new File(npmPath);

        if (!npmFile.exists()) {
            // Fallback to 'node npm-cli.js'
            String npmCliJsPath = getNpmCliJsPath();
            command.add(getNodePath());
            command.add(npmCliJsPath);
        } else {
            command.add(npmPath);
        }

        command.add("-v");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        String version;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            version = reader.readLine();
        }
        process.waitFor();
        return version != null ? version.trim() : null;
    }

    /**
     * Returns a ProcessBuilder configured to execute a Node.js command.
     */
    public ProcessBuilder nodeExecBuilder(Map<String, String> env, File directory, String[] args) {
        List<String> command = new ArrayList<>();
        command.add(getNodePath());
        command.addAll(Arrays.asList(args));

        ProcessBuilder builder = new ProcessBuilder(command);
        if (env != null) {
            builder.environment().putAll(env);
        }
        if (directory != null) {
            builder.directory(directory);
        }
        return builder;
    }

    /**
     * Returns a ProcessBuilder configured to execute an npm command.
     */
    public ProcessBuilder npmExecBuilder(Map<String, String> env, File directory, String[] args) {
        List<String> command = new ArrayList<>();
        String npmPath = getNpmPath();
        File npmFile = new File(npmPath);

        if (!npmFile.exists()) {
            // Fallback to 'node npm-cli.js'
            String npmCliJsPath = getNpmCliJsPath();
            command.add(getNodePath());
            command.add(npmCliJsPath);
        } else {
            command.add(npmPath);
        }

        command.addAll(Arrays.asList(args));

        ProcessBuilder builder = new ProcessBuilder(command);
        if (env != null) {
            builder.environment().putAll(env);
        }
        if (directory != null) {
            builder.directory(directory);
        }
        return builder;
    }


    /**
     * Updates Node.js to the latest version matching the semantic version pattern.
     */
    public void update(String semVer) throws IOException {
        // Fetch available Node.js versions
        List<String> availableVersions = getAvailableNodeVersions();

        // Filter versions matching the semantic version pattern
        List<String> matchingVersions = filterVersions(availableVersions, semVer);

        if (matchingVersions.isEmpty()) {
            throw new IOException("No Node.js versions match the pattern " + semVer);
        }

        // Get the latest version
        String latestVersion = getLatestVersion(matchingVersions);

        // Install the latest version
        install(latestVersion);
    }

    /**
     * Installs the specified Node.js version if not already installed.
     */
    public void install(String version) throws IOException {
        this.currentVersion = version;
        if (isNodeInstalled()) {
            // Node.js is already installed
            return;
        }
        // Download and install Node.js version
        downloadAndInstallNode(version);
    }

    public void install() throws IOException {
        install(currentVersion);
    }

    // Helper methods below

    private File getNodeExecutable(File nodeDir) {
        OS os = getOperatingSystem();
        if (os == OS.WINDOWS) {
            return new File(nodeDir, "node.exe");
        } else {
            return new File(nodeDir, "bin/node");
        }
    }

    private String getNpmPath() {
        File nodeDir = new File(NODE_INSTALLATION_BASE_DIR, "v" + currentVersion);
        OS os = getOperatingSystem();
        if (os == OS.WINDOWS) {
            return new File(nodeDir, "npm.cmd").getAbsolutePath();
        } else {
            return new File(nodeDir, "bin/npm").getAbsolutePath();
        }
    }

    private String getNpmCliJsPath() {
        File nodeDir = new File(NODE_INSTALLATION_BASE_DIR, "v" + currentVersion);
        OS os = getOperatingSystem();
        if (os == OS.WINDOWS) {
            return new File(nodeDir, "node_modules/npm/bin/npm-cli.js").getAbsolutePath();
        } else {
            return new File(nodeDir, "lib/node_modules/npm/bin/npm-cli.js").getAbsolutePath();
        }
    }

    private void downloadAndInstallNode(String version) throws IOException {
        OS os = getOperatingSystem();
        String arch = getArchitecture();

        String downloadUrl = getNodeDownloadUrl(version, os, arch);
        File downloadDir = new File(NODE_INSTALLATION_BASE_DIR, "downloads");
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }

        String fileName = "node-v" + version + "-" + osNameForDownload(os) + "-"
                + archForDownload(arch) + archiveExtension(os);
        File archiveFile = new File(downloadDir, fileName);

        // Download the archive if it doesn't exist
        if (!archiveFile.exists()) {
            downloadFile(downloadUrl, archiveFile);
        }

        // Extract the archive to the installation directory
        File installDir = new File(NODE_INSTALLATION_BASE_DIR, "v" + version);
        extractArchive(archiveFile, installDir);

        // Optionally, clean up the archive file
        // archiveFile.delete();
    }

    private void downloadFile(String url, File destination) throws IOException {
        try (InputStream in = new URL(url).openStream()) {
            Files.copy(in, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void extractArchive(File archiveFile, File destinationDir) throws IOException {
        OS os = getOperatingSystem();
        String extension = archiveExtension(os);
        if (extension.equals(".zip")) {
            extractZip(archiveFile, destinationDir);
        } else if (extension.equals(".tar.gz") || extension.equals(".tar.xz")) {
            extractTarArchive(archiveFile, destinationDir);
        } else {
            throw new UnsupportedOperationException("Unsupported archive extension: " + extension);
        }
    }

    private void extractZip(File zipFile, File destinationDir) throws IOException {
        String topLevelDir = null;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                if (topLevelDir == null) {
                    // Determine the top-level directory
                    int slashIndex = entryName.indexOf('/');
                    if (slashIndex > 0) {
                        topLevelDir = entryName.substring(0, slashIndex + 1);
                    }
                }

                if (topLevelDir != null) {
                    if (entryName.startsWith(topLevelDir)) {
                        entryName = entryName.substring(topLevelDir.length());
                    } else {
                        // Entry does not start with the expected top-level directory
                        // Skip this entry
                        continue;
                    }
                }

                // Skip empty entry names
                if (entryName.isEmpty()) {
                    continue;
                }

                File newFile = new File(destinationDir, entryName);

                // Prevent Zip Slip vulnerability
                String destDirPath = destinationDir.getCanonicalPath();
                String destFilePath = newFile.getCanonicalPath();
                if (!destFilePath.startsWith(destDirPath + File.separator)) {
                    throw new IOException("Entry is outside of the target dir: " + entryName);
                }

                if (entry.isDirectory()) {
                    // Create directories
                    if (!newFile.exists()) {
                        newFile.mkdirs();
                    }
                } else {
                    // Ensure parent directories exist
                    File parent = newFile.getParentFile();
                    if (!parent.exists()) {
                        parent.mkdirs();
                    }

                    // Write file content
                    try (OutputStream out = new FileOutputStream(newFile)) {
                        IOUtils.copy(zis, out);
                    }

                }
            }
        }
    }

    private void extractTarArchive(File tarFile, File destinationDir) throws IOException {
        String topLevelDir = null;
        try (FileInputStream fi = new FileInputStream(tarFile);
             BufferedInputStream bi = new BufferedInputStream(fi);
             InputStream decompressorStream = (tarFile.getName().endsWith(".tar.gz") ?
                     new GzipCompressorInputStream(bi) : new XZCompressorInputStream(bi));
             TarArchiveInputStream tarIn = new TarArchiveInputStream(decompressorStream)) {

            TarArchiveEntry entry;

            while ((entry = tarIn.getNextTarEntry()) != null) {
                String entryName = entry.getName();

                if (topLevelDir == null) {
                    // Determine the top-level directory
                    int slashIndex = entryName.indexOf('/');
                    if (slashIndex > 0) {
                        topLevelDir = entryName.substring(0, slashIndex + 1);
                    }
                }

                if (topLevelDir != null) {
                    if (entryName.startsWith(topLevelDir)) {
                        entryName = entryName.substring(topLevelDir.length());
                    } else {
                        // Entry does not start with the expected top-level directory
                        // Skip this entry
                        continue;
                    }
                }

                // Skip empty entry names
                if (entryName.isEmpty()) {
                    continue;
                }

                File newFile = new File(destinationDir, entryName);

                // Prevent Zip Slip vulnerability
                String destDirPath = destinationDir.getCanonicalPath();
                String destFilePath = newFile.getCanonicalPath();
                if (!destFilePath.startsWith(destDirPath + File.separator)) {
                    throw new IOException("Entry is outside of the target dir: " + entryName);
                }

                if (entry.isDirectory()) {
                    // Create directories
                    if (!newFile.exists()) {
                        newFile.mkdirs();
                    }
                } else {
                    // Ensure parent directories exist
                    File parent = newFile.getParentFile();
                    if (!parent.exists()) {
                        parent.mkdirs();
                    }

                    // Write file content
                    try (OutputStream out = new FileOutputStream(newFile)) {
                        IOUtils.copy(tarIn, out);
                    }

                    // Set executable permissions if necessary
                    if ((entry.getMode() & 0100) != 0) { // Owner execute bit
                        newFile.setExecutable(true, true);
                    }
                }
            }
        }
    }

    // Helper method to prevent Zip Slip vulnerability
    private File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    private String getNodeDownloadUrl(String version, OS os, String arch) {
        String osName = osNameForDownload(os);
        String archName = archForDownload(arch);
        String extension = archiveExtension(os);

        String fileName = "node-v" + version + "-" + osName + "-" + archName + extension;
        return "https://nodejs.org/dist/v" + version + "/" + fileName;
    }

    private String osNameForDownload(OS os) {
        switch (os) {
            case WINDOWS:
                return "win";
            case MAC:
                return "darwin";
            case LINUX:
                return "linux";
            default:
                throw new UnsupportedOperationException("Unsupported OS: " + os);
        }
    }

    private String archForDownload(String arch) {
        if (arch.contains("64")) {
            return "x64";
        } else if (arch.contains("86")) {
            return "x86";
        } else if (arch.startsWith("arm")) {
            return "arm64";
        } else {
            throw new UnsupportedOperationException("Unsupported architecture: " + arch);
        }
    }

    private String archiveExtension(OS os) {
        switch (os) {
            case WINDOWS:
                return ".zip";
            case MAC:
                return ".tar.gz";
            case LINUX:
                return ".tar.xz";
            default:
                throw new UnsupportedOperationException("Unsupported OS: " + os);
        }
    }

    private OS getOperatingSystem() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return OS.WINDOWS;
        } else if (osName.contains("mac")) {
            return OS.MAC;
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            return OS.LINUX;
        } else {
            return OS.OTHER;
        }
    }

    private String getArchitecture() {
        return System.getProperty("os.arch");
    }

    private List<String> getAvailableNodeVersions() throws IOException {
        URL url = new URL("https://nodejs.org/dist/index.json");
        List<String> versions = new ArrayList<>();

        try (InputStream in = url.openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {

            StringBuilder jsonText = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonText.append(line);
            }

            // Parse JSON array
            String jsonString = jsonText.toString();
            JSONArray jsonArray = new JSONArray(jsonString);

            // Iterate over the JSON array and extract versions
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject versionObject = jsonArray.getJSONObject(i);
                String version = versionObject.getString("version");
                // Remove the leading 'v' from the version string
                if (version.startsWith("v")) {
                    version = version.substring(1);
                }
                versions.add(version);
            }
        }

        return versions;
    }

    private List<String> filterVersions(List<String> versions, String semVerPattern) {
        List<String> matchingVersions = new ArrayList<>();
        Semver patternVersion = new Semver(semVerPattern, Semver.SemverType.NPM);

        for (String versionStr : versions) {
            Semver version = new Semver(versionStr, Semver.SemverType.NPM);
            if (version.satisfies(semVerPattern)) {
                matchingVersions.add(version.getValue());
            }
        }

        return matchingVersions;
    }

    private String getLatestVersion(List<String> versions) {
        versions.sort(this::compareVersions);
        return versions.get(versions.size() - 1);
    }

    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (num1 != num2) {
                return num1 - num2;
            }
        }
        return 0;
    }

    private enum OS {
        WINDOWS, MAC, LINUX, OTHER
    }
}

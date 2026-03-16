package ca.weblite.jdeploy.archive;

import org.json.JSONObject;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static ca.weblite.jdeploy.archive.JDeployArchiveGenerator.*;

/**
 * Reads and provides access to the contents of a .jdeploy archive.
 */
public class JDeployArchiveReader implements Closeable {

    private final JarFile jarFile;
    private final Manifest manifest;

    public JDeployArchiveReader(File archiveFile) throws IOException {
        this.jarFile = new JarFile(archiveFile);
        this.manifest = jarFile.getManifest();
        if (manifest == null) {
            throw new IOException("Archive is missing META-INF/MANIFEST.MF");
        }
    }

    // --- Manifest accessors ---

    public String getArchiveVersion() {
        return getManifestAttribute(ATTR_ARCHIVE_VERSION);
    }

    public String getPackageName() {
        return getManifestAttribute(ATTR_PACKAGE_NAME);
    }

    public String getPackageVersion() {
        return getManifestAttribute(ATTR_PACKAGE_VERSION);
    }

    public boolean isPrerelease() {
        return "true".equals(getManifestAttribute(ATTR_IS_PRERELEASE));
    }

    public boolean hasPlatformBundles() {
        return "true".equals(getManifestAttribute(ATTR_HAS_PLATFORM_BUNDLES));
    }

    public boolean hasPrebuiltBundles() {
        return "true".equals(getManifestAttribute(ATTR_HAS_PREBUILT_BUNDLES));
    }

    public boolean hasInstallers() {
        return "true".equals(getManifestAttribute(ATTR_HAS_INSTALLERS));
    }

    public List<String> getPlatforms() {
        String platforms = getManifestAttribute(ATTR_PLATFORMS);
        if (platforms == null || platforms.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(platforms.split(","));
    }

    public String getCreatedAt() {
        return getManifestAttribute(ATTR_CREATED_AT);
    }

    public String getCliVersion() {
        return getManifestAttribute(ATTR_CLI_VERSION);
    }

    // --- Content accessors ---

    /**
     * Returns the package.json content as a JSONObject.
     */
    public JSONObject getPackageJson() throws IOException {
        JarEntry entry = jarFile.getJarEntry("package.json");
        if (entry == null) {
            throw new IOException("Archive is missing package.json");
        }
        try (InputStream is = jarFile.getInputStream(entry)) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return new JSONObject(content);
        }
    }

    /**
     * Returns an InputStream for the universal tarball.
     * Caller is responsible for closing the stream.
     */
    public InputStream getUniversalTarball() throws IOException {
        String name = getPackageName() + "-" + getPackageVersion() + ".tgz";
        return getBundleStream("bundles/" + name);
    }

    /**
     * Returns an InputStream for a platform-specific tarball.
     * Caller is responsible for closing the stream.
     */
    public InputStream getPlatformTarball(String platformId) throws IOException {
        String name = getPackageName() + "-" + getPackageVersion() + "-" + platformId + ".tgz";
        return getBundleStream("bundles/" + name);
    }

    /**
     * Returns an InputStream for an asset file (icon.png, installsplash.png, etc.).
     * Caller is responsible for closing the stream.
     */
    public InputStream getAsset(String filename) throws IOException {
        JarEntry entry = jarFile.getJarEntry("assets/" + filename);
        if (entry == null) {
            return null;
        }
        return jarFile.getInputStream(entry);
    }

    /**
     * Lists all entries in the bundles/ directory.
     */
    public List<String> getBundleNames() {
        return listEntriesInDirectory("bundles/");
    }

    /**
     * Lists all entries in the prebuilt/ directory.
     */
    public List<String> getPrebuiltBundleNames() {
        return listEntriesInDirectory("prebuilt/");
    }

    /**
     * Returns an InputStream for a pre-built bundle file.
     * Caller is responsible for closing the stream.
     */
    public InputStream getPrebuiltBundle(String filename) throws IOException {
        JarEntry entry = jarFile.getJarEntry("prebuilt/" + filename);
        if (entry == null) {
            return null;
        }
        return jarFile.getInputStream(entry);
    }

    /**
     * Lists all entries in the installers/ directory.
     */
    public List<String> getInstallerNames() {
        return listEntriesInDirectory("installers/");
    }

    /**
     * Returns an InputStream for an installer file.
     * Caller is responsible for closing the stream.
     */
    public InputStream getInstaller(String filename) throws IOException {
        JarEntry entry = jarFile.getJarEntry("installers/" + filename);
        if (entry == null) {
            return null;
        }
        return jarFile.getInputStream(entry);
    }

    /**
     * Lists all entries in the archive with their sizes.
     * Returns entries as "path (size)" strings suitable for display.
     */
    public List<EntryInfo> listAllEntries() {
        List<EntryInfo> entries = new ArrayList<>();
        Enumeration<JarEntry> enumeration = jarFile.entries();
        while (enumeration.hasMoreElements()) {
            JarEntry entry = enumeration.nextElement();
            if (!entry.isDirectory() && !entry.getName().equals("META-INF/MANIFEST.MF")) {
                entries.add(new EntryInfo(entry.getName(), entry.getSize(), entry.getCompressedSize()));
            }
        }
        return entries;
    }

    @Override
    public void close() throws IOException {
        jarFile.close();
    }

    // --- Helpers ---

    private String getManifestAttribute(String name) {
        return manifest.getMainAttributes().getValue(name);
    }

    private InputStream getBundleStream(String entryPath) throws IOException {
        JarEntry entry = jarFile.getJarEntry(entryPath);
        if (entry == null) {
            throw new IOException("Archive is missing bundle: " + entryPath);
        }
        return jarFile.getInputStream(entry);
    }

    private List<String> listEntriesInDirectory(String prefix) {
        List<String> names = new ArrayList<>();
        Enumeration<JarEntry> enumeration = jarFile.entries();
        while (enumeration.hasMoreElements()) {
            JarEntry entry = enumeration.nextElement();
            String name = entry.getName();
            if (!entry.isDirectory() && name.startsWith(prefix)) {
                names.add(name.substring(prefix.length()));
            }
        }
        return names;
    }

    /**
     * Information about a single entry in the archive.
     */
    public static class EntryInfo {
        private final String path;
        private final long size;
        private final long compressedSize;

        public EntryInfo(String path, long size, long compressedSize) {
            this.path = path;
            this.size = size;
            this.compressedSize = compressedSize;
        }

        public String getPath() {
            return path;
        }

        public long getSize() {
            return size;
        }

        public long getCompressedSize() {
            return compressedSize;
        }
    }
}

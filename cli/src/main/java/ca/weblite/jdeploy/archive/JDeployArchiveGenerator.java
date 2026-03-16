package ca.weblite.jdeploy.archive;

import ca.weblite.jdeploy.models.BundleManifest;
import ca.weblite.jdeploy.publishing.PublishingContext;
import org.apache.commons.io.FileUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Generates a .jdeploy archive containing all artifacts needed to publish a version.
 *
 * The archive is a JAR file with a structured layout:
 * <pre>
 * META-INF/MANIFEST.MF          - Metadata about the archive
 * package.json                   - Version metadata (publish copy)
 * bundles/                       - Universal and platform-specific tarballs
 * assets/                        - Icon, splash images
 * prebuilt/                      - Pre-built native bundles (optional)
 * installers/                    - Platform installers (optional)
 * </pre>
 */
@Singleton
public class JDeployArchiveGenerator {

    static final String ARCHIVE_VERSION = "1";

    static final String ATTR_ARCHIVE_VERSION = "JDeploy-Archive-Version";
    static final String ATTR_PACKAGE_NAME = "JDeploy-Package-Name";
    static final String ATTR_PACKAGE_VERSION = "JDeploy-Package-Version";
    static final String ATTR_IS_PRERELEASE = "JDeploy-Is-Prerelease";
    static final String ATTR_HAS_PLATFORM_BUNDLES = "JDeploy-Has-Platform-Bundles";
    static final String ATTR_HAS_PREBUILT_BUNDLES = "JDeploy-Has-Prebuilt-Bundles";
    static final String ATTR_HAS_INSTALLERS = "JDeploy-Has-Installers";
    static final String ATTR_PLATFORMS = "JDeploy-Platforms";
    static final String ATTR_CREATED_AT = "JDeploy-Created-At";
    static final String ATTR_CLI_VERSION = "JDeploy-CLI-Version";

    @Inject
    public JDeployArchiveGenerator() {
    }

    /**
     * Generates a .jdeploy archive from a fully prepared publish context.
     *
     * @param context The publishing context (with prepared publish dir)
     * @param universalTarball The universal .tgz tarball from npm pack
     * @param platformTarballs Map of platformId→tarball file (nullable)
     * @param prebuiltManifest Pre-built bundle manifest (nullable)
     * @param installerFiles Array of installer files (nullable)
     * @return The generated .jdeploy archive file
     */
    public File generate(
            PublishingContext context,
            File universalTarball,
            Map<String, File> platformTarballs,
            BundleManifest prebuiltManifest,
            File[] installerFiles
    ) throws IOException {
        String packageName = context.packagingContext.getName();
        String version = context.packagingContext.getVersion();
        boolean isPrerelease = version.contains("-");

        File outputDir = new File(context.directory(), "jdeploy");
        outputDir.mkdirs();
        File archiveFile = new File(outputDir, packageName + "-" + version + ".jdeploy");

        Manifest manifest = buildManifest(
                packageName, version, isPrerelease,
                platformTarballs, prebuiltManifest, installerFiles
        );

        try (FileOutputStream fos = new FileOutputStream(archiveFile);
             JarOutputStream jos = new JarOutputStream(fos, manifest)) {

            // package.json
            File publishPackageJson = context.getPublishPackageJsonFile();
            if (publishPackageJson.exists()) {
                addFileEntry(jos, "package.json", publishPackageJson);
            }

            // bundles/ - universal tarball
            if (universalTarball != null && universalTarball.exists()) {
                addFileEntry(jos, "bundles/" + universalTarball.getName(), universalTarball);
            }

            // bundles/ - platform-specific tarballs
            if (platformTarballs != null) {
                for (Map.Entry<String, File> entry : platformTarballs.entrySet()) {
                    File tarball = entry.getValue();
                    if (tarball.exists()) {
                        addFileEntry(jos, "bundles/" + tarball.getName(), tarball);
                    }
                }
            }

            // assets/
            addOptionalAsset(jos, context.directory(), "icon.png");
            addOptionalAsset(jos, context.directory(), "installsplash.png");
            addOptionalAsset(jos, context.directory(), "launcher-splash.html");

            // prebuilt/
            if (prebuiltManifest != null && !prebuiltManifest.isEmpty()) {
                for (var artifact : prebuiltManifest.getArtifacts()) {
                    if (artifact.getFile() != null && artifact.getFile().exists()) {
                        addFileEntry(jos, "prebuilt/" + artifact.getFilename(), artifact.getFile());
                    }
                }
            }

            // installers/
            if (installerFiles != null) {
                for (File installer : installerFiles) {
                    if (installer != null && installer.exists()) {
                        addFileEntry(jos, "installers/" + installer.getName(), installer);
                    }
                }
            }
        }

        context.out().println("Generated archive: " + archiveFile.getName()
                + " (" + formatFileSize(archiveFile.length()) + ")");
        return archiveFile;
    }

    private Manifest buildManifest(
            String packageName,
            String version,
            boolean isPrerelease,
            Map<String, File> platformTarballs,
            BundleManifest prebuiltManifest,
            File[] installerFiles
    ) {
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue(ATTR_ARCHIVE_VERSION, ARCHIVE_VERSION);
        attrs.putValue(ATTR_PACKAGE_NAME, packageName);
        attrs.putValue(ATTR_PACKAGE_VERSION, version);
        attrs.putValue(ATTR_IS_PRERELEASE, String.valueOf(isPrerelease));

        boolean hasPlatformBundles = platformTarballs != null && !platformTarballs.isEmpty();
        attrs.putValue(ATTR_HAS_PLATFORM_BUNDLES, String.valueOf(hasPlatformBundles));

        boolean hasPrebuilt = prebuiltManifest != null && !prebuiltManifest.isEmpty();
        attrs.putValue(ATTR_HAS_PREBUILT_BUNDLES, String.valueOf(hasPrebuilt));

        boolean hasInstallers = installerFiles != null && installerFiles.length > 0;
        attrs.putValue(ATTR_HAS_INSTALLERS, String.valueOf(hasInstallers));

        if (hasPlatformBundles) {
            List<String> platforms = new ArrayList<>(platformTarballs.keySet());
            attrs.putValue(ATTR_PLATFORMS, String.join(",", platforms));
        }

        attrs.putValue(ATTR_CREATED_AT, Instant.now().toString());

        String cliVersion = getClass().getPackage().getImplementationVersion();
        if (cliVersion != null) {
            attrs.putValue(ATTR_CLI_VERSION, cliVersion);
        }

        return manifest;
    }

    private void addFileEntry(JarOutputStream jos, String entryPath, File file) throws IOException {
        JarEntry entry = new JarEntry(entryPath);
        entry.setTime(file.lastModified());
        jos.putNextEntry(entry);
        Files.copy(file.toPath(), jos);
        jos.closeEntry();
    }

    private void addOptionalAsset(JarOutputStream jos, File projectDir, String filename) throws IOException {
        File file = new File(projectDir, filename);
        if (file.exists()) {
            addFileEntry(jos, "assets/" + filename, file);
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}

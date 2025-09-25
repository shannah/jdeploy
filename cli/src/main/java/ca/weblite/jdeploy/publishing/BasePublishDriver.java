package ca.weblite.jdeploy.publishing;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.packaging.PackageService;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.services.PackageNameService;
import ca.weblite.jdeploy.services.VersionCleaner;
import ca.weblite.tools.io.MD5;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

@Singleton
public class BasePublishDriver implements PublishDriverInterface {

    private final PackageService packageService;

    @Inject
    public BasePublishDriver(PackageService packageService) {
        this.packageService = packageService;
    }

    @Override
    public void publish(
            PublishingContext context,
            PublishTargetInterface target,
            OneTimePasswordProviderInterface otpProvider
    ) throws IOException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void prepare(
            PublishingContext context,
            PublishTargetInterface target,
            BundlerSettings bundlerSettings
    ) throws IOException {
        // Copy all publishable artifacts to a temporary
        // directory so that we can add some information to the
        // package.json without having to modify the actual package.json
        if (context.getPublishDir().exists()) {
            FileUtils.deleteDirectory(context.getPublishDir());
        }
        if (!context.getPublishDir().exists()) {
            context.getPublishDir().mkdirs();
        }
        File publishJdeployBundleDir = context.getPublishJdeployBundleDir();
        FileUtils.copyDirectory(context.packagingContext.getJdeployBundleDir(), publishJdeployBundleDir);
        FileUtils.copyFile(context.packagingContext.packageJsonFile, context.getPublishPackageJsonFile());
        File readme = new File("README.md");
        if (readme.exists()) {
            FileUtils.copyFile(readme, new File(context.getPublishDir(), readme.getName()));
        }
        File license = new File("LICENSE");
        if (license.exists()) {
            FileUtils.copyFile(license, new File(context.getPublishDir(), license.getName()));
        }
        // Find all .jdpignore* files and copy them too
        // Use a glob pattern to find them
        File[] jdpignoreFiles = new File(".").listFiles((dir, name) -> name.startsWith(".jdpignore"));
        if (jdpignoreFiles != null) {
            for (File jdpignoreFile : jdpignoreFiles) {
                FileUtils.copyFile(jdpignoreFile, new File(context.getPublishDir(), jdpignoreFile.getName()));
            }
        }

        // Now add checksums
        JSONObject packageJSON = new JSONObject(FileUtils.readFileToString(context.packagingContext.packageJsonFile, "UTF-8"));
        if (bundlerSettings.getSource() != null && !bundlerSettings.getSource().isEmpty()) {
            packageJSON.put("source", bundlerSettings.getSource());
        }
        if (!target.getType().isDefaultSource() && target.getUrl() != null) {
            packageJSON.put("source", target.getUrl());
        }

        JSONObject jdeployObj = packageJSON.getJSONObject("jdeploy");

        File icon = new File(context.packagingContext.directory, "icon.png");
        JSONObject checksums = new JSONObject();
        jdeployObj.put("checksums", checksums);
        if (icon.exists()) {
            String md5 = MD5.getMD5Checksum(icon);
            checksums.put("icon.png", md5);
        }

        File installSplash = new File(context.packagingContext.directory, "installsplash.png");
        if (installSplash.exists()) {
            checksums.put("installsplash.png", MD5.getMD5Checksum(installSplash));
        }

        // Extract main class from JAR manifest and add to jdeploy object
        // This will be used for the Linux StartupWMClass desktop entry
        if (!jdeployObj.has("mainClass") && jdeployObj.has("jar")) {
            try {
                File mainJarFile = new File(publishJdeployBundleDir, new File(jdeployObj.getString("jar")).getName());
                if (mainJarFile.exists()) {
                    try (JarFile jarFile = new JarFile(mainJarFile)) {
                        Manifest manifest = jarFile.getManifest();
                        if (manifest != null) {
                            String mainClass = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
                            if (mainClass != null && !mainClass.isEmpty()) {
                                jdeployObj.put("mainClass", mainClass);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Log but don't fail - main class is optional for Linux desktop entry
                System.err.println("Warning: Could not extract main class from JAR manifest: " + e.getMessage());
            }
        }

        FileUtils.writeStringToFile(new File(context.getPublishDir(),"package.json"), packageJSON.toString(), "UTF-8");

        if (context.packagingContext.isPackageSigningEnabled()) {
            try {
                context.packagingContext.packageSigningService.signPackage(
                        getPackageSigningVersionString(packageJSON),
                        publishJdeployBundleDir.getAbsolutePath()
                );
                JSONArray packageSignCertificateSignatures = new JSONArray();
                packageSignCertificateSignatures.putAll(context.packagingContext.packageSigningService.calculateCertificateHashes());
                jdeployObj.put("packageSignCertificateSignatures", packageSignCertificateSignatures);
            } catch (Exception ex) {
                throw new IOException("Failed to sign package", ex);
            }
        }
    }

    @Override
    public void makePackage(PublishingContext context, PublishTargetInterface target, BundlerSettings bundlerSettings) throws IOException {
        packageService.createJdeployBundle(context.packagingContext, bundlerSettings);
    }

    @Override
    public JSONObject fetchPackageInfoFromPublicationChannel(String packageName, PublishTargetInterface target) throws IOException {
        throw new UnsupportedEncodingException("Not implemented");
    }

    @Override
    public boolean isVersionPublished(String packageName, String version, PublishTargetInterface target) {
        throw new UnsupportedOperationException("Not implemented");
    }

    private String getPackageSigningVersionString(JSONObject packageJSON) {
        String versionString = VersionCleaner.cleanVersion(packageJSON.getString("version"));
        if (packageJSON.has("commitHash")) {
            versionString += "#" + packageJSON.getString("commitHash");
        }

        return versionString;
    }
}

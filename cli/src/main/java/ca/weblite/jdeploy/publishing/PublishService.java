package ca.weblite.jdeploy.publishing;

import ca.weblite.jdeploy.JDeploy;
import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.packaging.PackageService;
import ca.weblite.tools.io.IOUtil;
import ca.weblite.tools.io.MD5;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

@Singleton
public class PublishService {

    private static final String REGISTRY_URL="https://registry.npmjs.org/";
    private static final String GITHUB_URL = "https://github.com/";

    private final PackageService packageService;

    private final ResourceUploader resourceUploader;

    @Inject
    public PublishService(PackageService packageService, ResourceUploader resourceUploader) {
        this.packageService = packageService;
        this.resourceUploader = resourceUploader;
    }

    public void publish(PublishingContext context) throws IOException {
        if (context.alwaysPackageOnPublish) {
            packageService.createJdeployBundle(context.packagingContext);
        }
        JSONObject packageJSON = prepublish(context, new BundlerSettings());
        context.npm.publish(context.getPublishDir(), context.packagingContext.exitOnFail);
        context.out().println("Package published to npm successfully.");
        context.out().println("Waiting for npm to update its registry...");

        long timeout = System.currentTimeMillis()+30000;
        while (System.currentTimeMillis() < timeout) {
            String source = packageJSON.has("source") ? packageJSON.getString("source") : "";
            if (isVersionPublished(packageJSON.getString("name"), packageJSON.getString("version"), source)) {
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (Exception ex){}
        }
        resourceUploader.uploadResources(context);
    }

    public JSONObject fetchPackageInfoFromPublicationChannel(String packageName, String source) throws IOException {
        URL u = new URL(getPackageUrl(packageName, source));
        HttpURLConnection conn = (HttpURLConnection)u.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setUseCaches(false);
        if (conn.getResponseCode() != 200) {
            throw new IOException("Failed to fetch Package info for package "+packageName+". "+conn.getResponseMessage());
        }
        return new JSONObject(IOUtil.readToString(conn.getInputStream()));
    }

    public boolean isVersionPublished(String packageName, String version, String source) {
        try {
            JSONObject jsonObject = fetchPackageInfoFromPublicationChannel(packageName, source);
            return jsonObject.has("versions") && jsonObject.getJSONObject("versions").has(version);
        } catch (Exception ex) {
            return false;
        }
    }

    private String getPackageUrl(String packageName, String source) throws UnsupportedEncodingException {
        if (source.startsWith(GITHUB_URL)) {
            return source + "/releases/download/jdeploy/package-info.json";
        } else {
            return REGISTRY_URL+ URLEncoder.encode(packageName, "UTF-8");
        }
    }


    public JSONObject prepublish(PublishingContext context, BundlerSettings bundlerSettings) throws IOException {
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

        // Now add checksums
        JSONObject packageJSON = new JSONObject(FileUtils.readFileToString(context.packagingContext.packageJsonFile, "UTF-8"));
        if (bundlerSettings.getSource() != null && !bundlerSettings.getSource().isEmpty()) {
            packageJSON.put("source", bundlerSettings.getSource());
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

        return packageJSON;
    }

    private String getPackageSigningVersionString(JSONObject packageJSON) {
        String versionString = packageJSON.getString("version");
        if (packageJSON.has("commitHash")) {
            versionString += "#" + packageJSON.getString("commitHash");
        }

        return versionString;
    }



}

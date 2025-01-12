package ca.weblite.jdeploy.publishing.npm;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishing.BasePublishDriver;
import ca.weblite.jdeploy.publishing.PublishDriverInterface;
import ca.weblite.jdeploy.publishing.PublishingContext;
import ca.weblite.tools.io.IOUtil;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

@Singleton
public class NPMPublishDriver implements PublishDriverInterface {

    private static final String REGISTRY_URL="https://registry.npmjs.org/";

    private final PublishDriverInterface basePublishDriver;

    @Inject
    public NPMPublishDriver(BasePublishDriver basePublishDriver) {
        this.basePublishDriver = basePublishDriver;
    }

    @Override
    public void publish(PublishingContext context, PublishTargetInterface target) throws IOException {
        context.npm.publish(
                context.getPublishDir(),
                context.packagingContext.exitOnFail
        );
        context.out().println("Package published to npm successfully.");
        context.out().println("Waiting for npm to update its registry...");
    }

    @Override
    public void prepare(PublishingContext context, PublishTargetInterface target, BundlerSettings bundlerSettings) throws IOException {
        basePublishDriver.prepare(context, target, bundlerSettings);
    }

    @Override
    public void makePackage(PublishingContext context, PublishTargetInterface target, BundlerSettings bundlerSettings) throws IOException {
        basePublishDriver.makePackage(context, target, bundlerSettings);
    }

    public JSONObject fetchPackageInfoFromPublicationChannel(String packageName, PublishTargetInterface target) throws IOException {
        URL u = new URL(getPackageUrl(packageName, target));
        HttpURLConnection conn = (HttpURLConnection)u.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setUseCaches(false);
        if (conn.getResponseCode() != 200) {
            throw new IOException("Failed to fetch Package info for package "+packageName+". "+conn.getResponseMessage());
        }
        return new JSONObject(IOUtil.readToString(conn.getInputStream()));
    }

    public boolean isVersionPublished(String packageName, String version, PublishTargetInterface target) {
        try {
            JSONObject jsonObject = fetchPackageInfoFromPublicationChannel(packageName, target);
            return jsonObject.has("versions") && jsonObject.getJSONObject("versions").has(version);
        } catch (Exception ex) {
            return false;
        }
    }

    private String getPackageUrl(String packageName, PublishTargetInterface target) throws UnsupportedEncodingException {
        return REGISTRY_URL+ URLEncoder.encode(packageName, "UTF-8");
    }
}

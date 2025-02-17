package ca.weblite.jdeploy.publishing;

import ca.weblite.jdeploy.JDeploy;
import ca.weblite.jdeploy.packaging.PackagingConfig;
import ca.weblite.jdeploy.services.VersionCleaner;
import org.apache.commons.io.FileUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.Base64;

@Singleton
public class ResourceUploader {

    private final PackagingConfig config;

    @Inject
    public ResourceUploader(PackagingConfig config) {
        this.config = config;
    }

    public void uploadResources(PublishingContext context) throws IOException {
        File icon = new File(context.directory(), "icon.png");
        File installSplash = new File(context.directory(),"installsplash.png");
        File publishDir = new File(context.directory(), "jdeploy" + File.separator + "publish");
        JSONObject packageJSON = new JSONObject(FileUtils.readFileToString(new File(publishDir, "package.json"), "UTF-8"));

        if (icon.exists() || installSplash.exists()) {
            // If there is a custom icon or install splash we need to upload
            // them to jdeploy.com so that they are available when generating
            // the installer.  Without this, jdeploy.com would need to download the
            // full package from npm and extract the icon and installsplash from there.
            JSONObject jdeployFiles = new JSONObject();
            byte[] iconBytes = FileUtils.readFileToByteArray(icon);
            jdeployFiles.put("icon.png", Base64.getEncoder().encodeToString(iconBytes));
            if (installSplash.exists()) {
                byte[] splashBytes = FileUtils.readFileToByteArray(installSplash);
                jdeployFiles.put("installsplash.png", Base64.getEncoder().encodeToString(splashBytes));
            }
            jdeployFiles.put("packageName", packageJSON.get("name"));
            jdeployFiles.put("version", VersionCleaner.cleanVersion(""+packageJSON.get("version")));
            try {
                context.out().println("Uploading icon to jdeploy.com...");
                JSONObject response = makeServiceCall(
                        context,
                        config.getJdeployRegistry() + "publish.php",
                        jdeployFiles.toString()
                );
                context.out().println("Upload complete");
                if (response.has("code") && response.getInt("code") == 200) {
                    context.out().println("Your package was published successfully.");
                    context.out().println("You can download native installers for your app at " + config.getJdeployRegistry() + "~" + packageJSON.getString("name"));
                } else {
                    context.err().println("There was a problem publishing the icon to " + config.getJdeployRegistry());
                    if (response.has("error")) {
                        context.err().println("Error message: " + response.getString("error"));
                    } else if (response.has("code")) {
                        context.err().println("Unexpected response code: " + response.getInt("code"));
                    } else {
                        context.err().println("Unexpected server response: " + response.toString());
                    }
                }

            } catch (Exception ex) {
                context.err().println("Failed to publish icon and splash image to jdeploy.com.  " + ex.getMessage());
                ex.printStackTrace(context.err());
                fail(context, "Failed to publish icon and splash image to jdeploy.com. "+ex.getMessage(), 1);
                return;
            }
        } else {
            context.out().println("Your package was published successfully.");
            context.out().println("You can download native installers for your app at " + config.getJdeployRegistry() + "~" + packageJSON.getString("name"));

        }
    }

    private JSONObject makeServiceCall(PublishingContext context,
                                       String url,
                                       String jsonString) {
        try{
            CloseableHttpClient client = HttpClientBuilder.create().build();
            HttpPost httpPost = new HttpPost(url);

            httpPost.setHeader("Content-Type", "application/json; charset='utf-8'");
            httpPost.setHeader("Accept-Charset", "UTF-8");
            httpPost.setEntity(new StringEntity(jsonString));


            CloseableHttpResponse response = client.execute(httpPost);
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new IOException("Failed to publish resources to jdeploy.com.  "+response.getStatusLine().getReasonPhrase());
            }
            String resultLine = EntityUtils.toString(response.getEntity());
            try {
                return new JSONObject(resultLine);
            } catch (Exception ex) {
                context.err().println("Unexpected server response.  Expected JSON but found "+resultLine+" Response code was: "+response.getStatusLine().getStatusCode()+".  Message was "+response.getStatusLine().getReasonPhrase());
                ex.printStackTrace(context.err());
                throw new Exception("Unexpected server response.  Expected JSON but found "+resultLine);
            }
        } catch (Exception ex) {
            ex.printStackTrace(context.err());
            JSONObject out = new JSONObject();
            out.put("code", 500);
            out.put("error", ex.getMessage());
            return out;
        }
    }

    private void fail(PublishingContext context, String message, int code) {
        if (context.packagingContext.exitOnFail) {
            context.err().println(message);
            System.exit(code);
        } else {
            throw new JDeploy.FailException(message, code);
        }

    }
}

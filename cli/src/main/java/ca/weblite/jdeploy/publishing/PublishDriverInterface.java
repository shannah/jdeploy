package ca.weblite.jdeploy.publishing;

import ca.weblite.jdeploy.appbundler.Bundler;
import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import org.json.JSONObject;

import java.io.IOException;

public interface PublishDriverInterface {
    void publish(
            PublishingContext context,
            PublishTargetInterface target,
            OneTimePasswordProviderInterface otpProvider
    ) throws IOException;
    void prepare(PublishingContext context, PublishTargetInterface target, BundlerSettings bundlerSettings) throws IOException;

    void makePackage(PublishingContext context, PublishTargetInterface target, BundlerSettings bundlerSettings) throws IOException;

    JSONObject fetchPackageInfoFromPublicationChannel(String packageName, PublishTargetInterface target) throws IOException;
    boolean isVersionPublished(String packageName, String version, PublishTargetInterface target);
}

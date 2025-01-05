package ca.weblite.jdeploy.publishing;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.packaging.PackageService;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetServiceInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;
import ca.weblite.jdeploy.publishing.github.GitHubPublishDriver;
import ca.weblite.jdeploy.publishing.npm.NPMPublishDriver;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class PublishService {

    private final Map<PublishTargetType, PublishDriverInterface> publishDrivers = new HashMap<>();

    private final PackageService packageService;

    private final ResourceUploader resourceUploader;

    private final PublishTargetServiceInterface publishTargetService;

    @Inject
    public PublishService(
            PackageService packageService,
            ResourceUploader resourceUploader,
            PublishTargetServiceInterface publishTargetService,
            NPMPublishDriver npmPublishDriver,
            GitHubPublishDriver gitHubPublishDriver
    ) {
        this.packageService = packageService;
        this.resourceUploader = resourceUploader;
        this.publishTargetService = publishTargetService;
        publishDrivers.put(PublishTargetType.NPM, npmPublishDriver);
        publishDrivers.put(PublishTargetType.GITHUB, gitHubPublishDriver);
    }

    public void publish(PublishingContext context) throws IOException {
        if (context.alwaysPackageOnPublish) {
            packageService.createJdeployBundle(context.packagingContext);
        }
        JSONObject packageJson = new JSONObject(context.packagingContext.packageJsonMap);
        for (PublishTargetInterface target : publishTargetService.getTargetsForPackageJson(packageJson, true)) {
            publish(context, target);
        }

        for (PublishTargetInterface target : publishTargetService.getTargetsForPackageJson(packageJson, true)) {
            wait(context, getDriverForTarget(target), target);
        }

        resourceUploader.uploadResources(context);
    }

    public void prepublish(
            PublishingContext context,
            BundlerSettings bundlerSettings,
            PublishTargetInterface target
    ) throws IOException {
        getDriverForTarget(target).prepare(context, target, bundlerSettings);
    }

    private void wait(PublishingContext context, PublishDriverInterface driver, PublishTargetInterface target) {
        long timeout = System.currentTimeMillis()+30000;
        while (System.currentTimeMillis() < timeout) {
            if (driver.isVersionPublished(
                    context.packagingContext.getString("name", ""),
                    context.packagingContext.getString("version", ""),
                    target
            )) {
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (Exception ex){}
        }
    }

    private void publish(PublishingContext context, PublishTargetInterface publishTargetInterface) throws IOException {
        PublishDriverInterface driver = getDriverForTarget(publishTargetInterface);
        driver.prepare(context, publishTargetInterface, new BundlerSettings());
        driver.publish(context, publishTargetInterface);

    }

    private PublishDriverInterface getDriverForTarget(PublishTargetInterface target) {
        PublishDriverInterface driver = publishDrivers.get(target.getType());
        if (driver == null) {
            throw new IllegalStateException("No driver found for target type "+target.getType());
        }

        return driver;
    }
}

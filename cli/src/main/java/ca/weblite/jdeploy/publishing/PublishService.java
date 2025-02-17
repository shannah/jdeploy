package ca.weblite.jdeploy.publishing;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.packaging.PackageService;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetServiceInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;
import ca.weblite.jdeploy.publishing.github.GitHubPublishDriver;
import ca.weblite.jdeploy.publishing.npm.NPMPublishDriver;
import ca.weblite.jdeploy.services.VersionCleaner;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static ca.weblite.jdeploy.BundleConstants.*;

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
        validateContext(context);
        JSONObject packageJson = new JSONObject(context.packagingContext.packageJsonMap);
        for (PublishTargetInterface target : publishTargetService.getTargetsForPackageJson(packageJson, true)) {
            publish(context, target);
        }
    }

    public void validateContext(PublishingContext context) {
        String rawVersion = context.packagingContext.getVersion();
        String cleanVersion = VersionCleaner.cleanVersion(rawVersion);
        if (!rawVersion.equals(cleanVersion)) {
            throw new IllegalArgumentException(
                    "Version "+rawVersion+" is not a valid version string.  It should be of the form "+cleanVersion
            );
        }
    }

    public void publish(PublishingContext context, PublishTargetInterface publishTargetInterface) throws IOException {
        PublishDriverInterface driver = getDriverForTarget(publishTargetInterface);
        if (alwaysPackageOnPublish(context)) {
            driver.makePackage(context, publishTargetInterface, new BundlerSettings());
        }
        driver.prepare(context, publishTargetInterface, new BundlerSettings());
        driver.publish(context, publishTargetInterface);
        wait(context, getDriverForTarget(publishTargetInterface), publishTargetInterface);
        resourceUploader.uploadResources(context);

    }

    public void prepublish(
            PublishingContext context,
            BundlerSettings bundlerSettings,
            PublishTargetInterface target
    ) throws IOException {
        validateContext(context);
        getDriverForTarget(target).prepare(context, target, bundlerSettings);
    }

    private void wait(PublishingContext context, PublishDriverInterface driver, PublishTargetInterface target) {
        long timeout = System.currentTimeMillis()+30000;
        while (System.currentTimeMillis() < timeout) {
            if (driver.isVersionPublished(
                    context.packagingContext.getName(),
                    context.packagingContext.getVersion(),
                    target
            )) {
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (Exception ex){}
        }
    }

    private PublishDriverInterface getDriverForTarget(PublishTargetInterface target) {
        PublishDriverInterface driver = publishDrivers.get(target.getType());
        if (driver == null) {
            throw new IllegalStateException("No driver found for target type "+target.getType());
        }

        return driver;
    }

    private PackagingContext getPackagingContext(PublishingContext context) {
        if (shouldGenerateInstallers(context)) {
            return context.packagingContext.withInstallers(BUNDLE_MAC_X64, BUNDLE_MAC_ARM64, BUNDLE_WIN, BUNDLE_LINUX);
        }

        return context.packagingContext;
    }

    private boolean shouldGenerateInstallers(PublishingContext context) {
        return publishTargetService
                .getTargetsForPackageJson(new JSONObject(context.packagingContext.packageJsonMap), false)
                .stream().anyMatch(target -> target.getType() == PublishTargetType.GITHUB);
    }

    private boolean alwaysPackageOnPublish(PublishingContext context) {
        return context.alwaysPackageOnPublish ||
                publishTargetService.getTargetsForPackageJson(new JSONObject(context.packagingContext.packageJsonMap), false)
                        .stream().anyMatch(target -> target.getType() == PublishTargetType.GITHUB);
    }
}

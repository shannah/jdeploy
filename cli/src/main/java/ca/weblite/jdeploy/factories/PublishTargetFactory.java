package ca.weblite.jdeploy.factories;

import ca.weblite.jdeploy.publishTargets.PublishTarget;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;

import javax.inject.Singleton;

@Singleton
public class PublishTargetFactory {
    private static final String GITHUB_URL = "https://github.com/";
    private static final String JDEPLOY_CLOUD_URL = "https://cloud.jdeploy.com/";

    public PublishTargetInterface createWithUrlAndName(String url, String name) {
        return createWithUrlAndName(url, name, false);
    }

    public PublishTargetInterface createWithUrlAndName(String url, String name, boolean isDefault) {
        if (url.startsWith(GITHUB_URL)) {
            return new PublishTarget(getName(url, name), PublishTargetType.GITHUB, url, isDefault);
        } else if (url.startsWith(JDEPLOY_CLOUD_URL)) {
            return new PublishTarget(getName(url, name), PublishTargetType.JDEPLOY_CLOUD, url, isDefault);
        } else {
            return new PublishTarget(getName(url, name), PublishTargetType.NPM, url, isDefault);
        }
    }

    private String getName(String url, String name) {
        boolean isGitHub = url.startsWith(GITHUB_URL);
        boolean isJDeployCloud = url.startsWith(JDEPLOY_CLOUD_URL);
        String prefix = isGitHub
                ? "github: "
                : isJDeployCloud
                ? "jdeploy-cloud: "
                : "npm: ";
        if (name != null) {
            return prefix + name;
        }
        if (isGitHub) {
            return prefix + url.substring(GITHUB_URL.length());
        }
        if (isJDeployCloud) {
            return prefix + url.substring(JDEPLOY_CLOUD_URL.length());
        }

        return prefix + url;
    }
}

package ca.weblite.jdeploy.factories;

import ca.weblite.jdeploy.publishTargets.PublishTarget;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;

import javax.inject.Singleton;

@Singleton
public class PublishTargetFactory {
    private static final String GITHUB_URL = "https://github.com/";
    public PublishTargetInterface createWithUrlAndName(String url, String name) {
        return createWithUrlAndName(url, name, false);
    }

    public PublishTargetInterface createWithUrlAndName(String url, String name, boolean isDefault) {
        return url.startsWith(GITHUB_URL)
                ? new PublishTarget(getName(url, name), PublishTargetType.GITHUB, url, isDefault)
                : new PublishTarget(getName(url, name), PublishTargetType.NPM, url, isDefault);
    }

    private String getName(String url, String name) {
        boolean isGitHub = url.startsWith(GITHUB_URL);
        String prefix = isGitHub
                ? "github: "
                : "npm: ";
        if (name != null) {
            return prefix + name;
        }
        if (isGitHub) {
            return prefix + url.substring(GITHUB_URL.length());
        }

        return prefix + url;
    }
}

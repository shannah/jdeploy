package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;

import javax.inject.Singleton;

@Singleton
public class PackageNameService {
    public String getFullPackageName(PublishTargetInterface target, String packageName) {
        String source = target.getType() == PublishTargetType.NPM ? null : target.getUrl();
        if (source == null || source.isEmpty()) {
            return packageName;
        }

        return source + "#" + packageName;
    }
}

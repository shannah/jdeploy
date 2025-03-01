package ca.weblite.jdeploy.publishing;

import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;

public interface OneTimePasswordProviderInterface {
    public String promptForOneTimePassword(
            PublishingContext context,
            PublishTargetInterface target
    );
}

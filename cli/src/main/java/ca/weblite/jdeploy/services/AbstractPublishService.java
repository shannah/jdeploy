package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractPublishService implements PublishServiceInterface {
    @Override
    public abstract CompletableFuture<PublishResultInterface> publish(String projectPath, PublishTargetInterface target);

    protected abstract String prepublish(String projectPath, PublishTargetInterface target);

}

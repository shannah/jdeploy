package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;

import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;

@Singleton
public interface PublishServiceInterface {
    public interface PublishResultInterface {

    }
    public CompletableFuture<PublishResultInterface> publish(String projectPath, PublishTargetInterface target);
}

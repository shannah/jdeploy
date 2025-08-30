package ca.weblite.jdeploy.publishTargets;

public interface PublishTargetInterface {
    String getName();
    PublishTargetType getType();
    String getUrl();
    boolean isDefault();
}

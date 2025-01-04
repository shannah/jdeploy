package ca.weblite.jdeploy.publishTargets;

public enum PublishTargetType {
    GITHUB,
    GITLAB,
    LOCAL,
    S3,
    NPM,
    MAVEN,
    DOCKER;

    public boolean isDefaultSource() {
        return this == NPM;
    }
}

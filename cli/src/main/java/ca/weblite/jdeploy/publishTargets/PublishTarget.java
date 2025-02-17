package ca.weblite.jdeploy.publishTargets;

public class PublishTarget implements PublishTargetInterface{
    private final String name;
    private final PublishTargetType type;
    private final String url;

    public PublishTarget(String name, PublishTargetType type, String url) {
        this.name = name;
        this.type = type;
        this.url = url;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public PublishTargetType getType() {
        return type;
    }

    @Override
    public String getUrl() {
        return url;
    }
}

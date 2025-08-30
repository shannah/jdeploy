package ca.weblite.jdeploy.publishTargets;

public class PublishTarget implements PublishTargetInterface{
    private final String name;
    private final PublishTargetType type;
    private final String url;
    private final boolean isDefault;

    public PublishTarget(String name, PublishTargetType type, String url) {
        this(name, type, url, false);
    }

    public PublishTarget(String name, PublishTargetType type, String url, boolean isDefault) {
        this.name = name;
        this.type = type;
        this.url = url;
        this.isDefault = isDefault;
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

    @Override
    public boolean isDefault() {
        return isDefault;
    }
}

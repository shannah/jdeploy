package ca.weblite.jdeploy.packaging;

public class PackagingPreferences {
    private String packageName;
    private boolean buildProjectBeforePackaging;

    public PackagingPreferences(String packageName, boolean buildProjectBeforePackaging) {
        this.packageName = packageName;
        this.buildProjectBeforePackaging = buildProjectBeforePackaging;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public boolean isBuildProjectBeforePackaging() {
        return buildProjectBeforePackaging;
    }

    public void setBuildProjectBeforePackaging(boolean buildProjectBeforePackaging) {
        this.buildProjectBeforePackaging = buildProjectBeforePackaging;
    }
}

package ca.weblite.jdeploy.appbundler;

public class BundlerSettings {
    private String source;
    private boolean compressBundles;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isCompressBundles() {
        return compressBundles;
    }

    public void setCompressBundles(boolean compressBundles) {
        this.compressBundles = compressBundles;
    }
}

package ca.weblite.jdeploy.appbundler;

public class BundlerSettings {
    private String source;
    private boolean compressBundles;

    private boolean doNotZipExeInstaller;

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

    public boolean isDoNotZipExeInstaller() {
        return doNotZipExeInstaller;
    }

    public void setDoNotZipExeInstaller(boolean doNotZipExeInstaller) {
        this.doNotZipExeInstaller = doNotZipExeInstaller;
    }
}

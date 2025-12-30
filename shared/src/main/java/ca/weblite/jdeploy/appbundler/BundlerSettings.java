package ca.weblite.jdeploy.appbundler;

public class BundlerSettings {
    private String source;
    private String bundleVersion;
    private boolean compressBundles;
    private boolean isAutoUpdateEnabled = true;
    private boolean cliCommandsEnabled;

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

    public void setBundleVersion(String bundleVersion) {
        this.bundleVersion = bundleVersion;
    }

    public String getBundleVersion() {
        return bundleVersion;
    }

    public boolean isAutoUpdateEnabled() {
        return isAutoUpdateEnabled;
    }

    public void setAutoUpdateEnabled(boolean isAutoUpdateEnabled) {
        this.isAutoUpdateEnabled = isAutoUpdateEnabled;
    }

    public boolean isCliCommandsEnabled() {
        return cliCommandsEnabled;
    }

    public void setCliCommandsEnabled(boolean cliCommandsEnabled) {
        this.cliCommandsEnabled = cliCommandsEnabled;
    }
}

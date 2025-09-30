package ca.weblite.jdeploy.installer.models;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.installer.npm.NPMPackageVersion;

import java.io.File;
import java.net.URL;

public class InstallationSettings {
    private boolean addToDesktop=true;
    private boolean addToPrograms=true;
    private boolean addToStartMenu=true;
    private boolean addToDock=true;
    private boolean alreadyAddedToDock=false;
    private boolean prerelease=false;
    private boolean overwriteApp=true;
    private AppInfo appInfo;
    private NPMPackageVersion npmPackageVersion;
    private File installFilesDir;
    private URL websiteURL;
    private boolean websiteVerified;

    private AutoUpdateSettings autoUpdate = AutoUpdateSettings.Stable;

    public boolean isAddToDesktop() {
        return addToDesktop;
    }

    public void setAddToDesktop(boolean addToDesktop) {
        this.addToDesktop = addToDesktop;
    }

    public boolean isAddToPrograms() {
        return addToPrograms;
    }

    public void setAddToPrograms(boolean addToPrograms) {
        this.addToPrograms = addToPrograms;
    }

    public boolean isAddToStartMenu() {
        return addToStartMenu;
    }

    public void setAddToStartMenu(boolean addToStartMenu) {
        this.addToStartMenu = addToStartMenu;
    }

    public boolean isAddToDock() {
        return addToDock;
    }

    public void setAddToDock(boolean addToDock) {
        this.addToDock = addToDock;
    }

    public boolean isAlreadyAddedToDock() {
        return alreadyAddedToDock;
    }

    public void setAlreadyAddedToDock(boolean alreadyAddedToDock) {
        this.alreadyAddedToDock = alreadyAddedToDock;
    }

    public boolean isPrerelease() {
        return prerelease;
    }

    public void setPrerelease(boolean prerelease) {
        this.prerelease = prerelease;
    }

    public boolean isOverwriteApp() {
        return overwriteApp;
    }

    public void setOverwriteApp(boolean overwriteApp) {
        this.overwriteApp = overwriteApp;
    }

    public AppInfo getAppInfo() {
        return appInfo;
    }

    public void setAppInfo(AppInfo appInfo) {
        this.appInfo = appInfo;
    }

    public NPMPackageVersion getNpmPackageVersion() {
        return npmPackageVersion;
    }

    public void setNpmPackageVersion(NPMPackageVersion npmPackageVersion) {
        this.npmPackageVersion = npmPackageVersion;
    }

    public File getInstallFilesDir() {
        return installFilesDir;
    }

    public void setInstallFilesDir(File installFilesDir) {
        this.installFilesDir = installFilesDir;
    }

    public AutoUpdateSettings getAutoUpdate() {
        return autoUpdate;
    }

    public void setAutoUpdate(AutoUpdateSettings autoUpdate) {
        this.autoUpdate = autoUpdate;
    }

    public File getInstallSplashImage() {
        return new File(installFilesDir, "installsplash.png");
    }

    public URL getWebsiteURL() {
        return websiteURL;
    }

    public void setWebsiteURL(URL websiteURL) {
        this.websiteURL = websiteURL;
    }

    public boolean isWebsiteVerified() {
        return websiteVerified;
    }

    public void setWebsiteVerified(boolean websiteVerified) {
        this.websiteVerified = websiteVerified;
    }
}

package ca.weblite.jdeploy.installer.models;

import ca.weblite.jdeploy.ai.models.AIToolType;
import ca.weblite.jdeploy.ai.models.AiIntegrationConfig;
import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.installer.npm.NPMPackageVersion;
import ca.weblite.jdeploy.models.HelperAction;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InstallationSettings {
    private boolean addToDesktop=true;
    private boolean addToPrograms=true;
    private boolean addToStartMenu=true;
    private boolean addToDock=true;
    private boolean installCliLauncher=true;
    private boolean installCliCommands=true;
    private boolean alreadyAddedToDock=false;
    private boolean prerelease=false;
    private boolean overwriteApp=true;
    private boolean hasDesktopEnvironment=true;
    private String commandLinePath=null;
    private boolean commandLineSymlinkCreated=false;
    private boolean addedToPath=false;
    private boolean trayMenuEnabled=true;
    private AppInfo appInfo;
    private NPMPackageVersion npmPackageVersion;
    private File installFilesDir;
    private URL websiteURL;
    private boolean websiteVerified;
    private String packageName;
    private String source;
    private List<HelperAction> helperActions = new ArrayList<>();

    private AutoUpdateSettings autoUpdate = AutoUpdateSettings.Stable;

    // AI Integration fields
    private boolean installAiIntegrations = false;
    private Set<AIToolType> selectedAiTools = new HashSet<>();
    private AiIntegrationConfig aiIntegrationConfig;

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

    public File getApplicationIcon() {
        return new File(installFilesDir, "icon.png");
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

    public boolean hasDesktopEnvironment() {
        return hasDesktopEnvironment;
    }

    public void setHasDesktopEnvironment(boolean hasDesktopEnvironment) {
        this.hasDesktopEnvironment = hasDesktopEnvironment;
    }

    public String getCommandLinePath() {
        return commandLinePath;
    }

    public void setCommandLinePath(String commandLinePath) {
        this.commandLinePath = commandLinePath;
    }

    public boolean isCommandLineSymlinkCreated() {
        return commandLineSymlinkCreated;
    }

    public void setCommandLineSymlinkCreated(boolean commandLineSymlinkCreated) {
        this.commandLineSymlinkCreated = commandLineSymlinkCreated;
    }

    public boolean isAddedToPath() {
        return addedToPath;
    }

    public void setAddedToPath(boolean addedToPath) {
        this.addedToPath = addedToPath;
    }

    public boolean isTrayMenuEnabled() {
        return trayMenuEnabled;
    }

    public void setTrayMenuEnabled(boolean trayMenuEnabled) {
        this.trayMenuEnabled = trayMenuEnabled;
    }

    public boolean isInstallCliLauncher() {
        return installCliLauncher;
    }

    public void setInstallCliLauncher(boolean installCliLauncher) {
        this.installCliLauncher = installCliLauncher;
    }

    public boolean isInstallCliCommands() {
        return installCliCommands;
    }

    public void setInstallCliCommands(boolean installCliCommands) {
        this.installCliCommands = installCliCommands;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    /**
     * Gets the helper actions for the tray menu and service management panel.
     *
     * @return unmodifiable list of helper actions
     */
    public List<HelperAction> getHelperActions() {
        return Collections.unmodifiableList(helperActions);
    }

    /**
     * Sets the helper actions for the tray menu and service management panel.
     *
     * @param helperActions the helper actions to set
     */
    public void setHelperActions(List<HelperAction> helperActions) {
        this.helperActions = helperActions != null ? new ArrayList<>(helperActions) : new ArrayList<>();
    }

    /**
     * Determines if this is a branch installation.
     * Branch installations are identified by versions starting with "0.0.0-" (e.g., "0.0.0-main", "0.0.0-staging").
     * The branch name is what comes after "0.0.0-".
     * Branch installations do not support CLI commands, CLI launchers, or services.
     *
     * @return true if this is a branch installation, false otherwise
     */
    public boolean isBranchInstallation() {
        // Check version from npmPackageVersion first
        if (npmPackageVersion != null && npmPackageVersion.getVersion() != null) {
            return npmPackageVersion.getVersion().startsWith("0.0.0-");
        }

        // Fall back to appInfo version
        if (appInfo != null && appInfo.getNpmVersion() != null) {
            return appInfo.getNpmVersion().startsWith("0.0.0-");
        }

        return false;
    }

    /**
     * Returns whether AI integrations should be installed.
     */
    public boolean isInstallAiIntegrations() {
        return installAiIntegrations;
    }

    /**
     * Sets whether AI integrations should be installed.
     */
    public void setInstallAiIntegrations(boolean installAiIntegrations) {
        this.installAiIntegrations = installAiIntegrations;
    }

    /**
     * Gets the set of selected AI tools for integration.
     */
    public Set<AIToolType> getSelectedAiTools() {
        return new HashSet<>(selectedAiTools);
    }

    /**
     * Sets the selected AI tools for integration.
     */
    public void setSelectedAiTools(Set<AIToolType> selectedAiTools) {
        this.selectedAiTools = selectedAiTools != null ? new HashSet<>(selectedAiTools) : new HashSet<>();
    }

    /**
     * Gets the AI integration configuration from the bundle.
     */
    public AiIntegrationConfig getAiIntegrationConfig() {
        return aiIntegrationConfig;
    }

    /**
     * Sets the AI integration configuration from the bundle.
     */
    public void setAiIntegrationConfig(AiIntegrationConfig aiIntegrationConfig) {
        this.aiIntegrationConfig = aiIntegrationConfig;
    }

    /**
     * Returns true if the bundle has AI integrations configured.
     */
    public boolean hasAiIntegrations() {
        return aiIntegrationConfig != null && aiIntegrationConfig.hasAnyIntegrations();
    }
}

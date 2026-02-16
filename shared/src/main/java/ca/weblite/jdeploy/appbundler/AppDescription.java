/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.weblite.jdeploy.appbundler;

import ca.weblite.jdeploy.models.CommandSpec;

import java.io.File;
import java.security.cert.Certificate;
import java.util.*;

/**
 *
 * @author joshmarinacci
 */
public class AppDescription {
    private String iconDataURI;
    private String splashDataURI;
    private String npmPackage;
    private String npmVersion;

    private String npmSource = "";
    private boolean npmPrerelease;
    private boolean fork;

    /**
     * Path to local package.json for local development mode.
     */
    private String localPackageJson;

    /**
     * Path to local jdeploy-bundle for local development mode.
     */
    private String localBundle;
    private String url;
    private List<Jar> jars;
    private String name;
    private Map<String,String> extensions;
    private Map<String,String> icons;
    private Set<String> editableExtensions;
    private final ArrayList<String> appIcons;
    private List<NativeLib> natives;
    private List<Prop> props;
    private String macBundleId;
    private boolean enableMacCodeSigning;
    private boolean enableMacNotarization;

    private String macCertificateName;
    private String macDeveloperID;
    private String macNotarizationPassword;
    private List<String> urlSchemes;

    private String macDeveloperTeamID;

    // Directory association support
    private ca.weblite.jdeploy.models.DocumentTypeAssociation directoryAssociation;

    private File bundleJre;

    private String jcefFrameworksPath;

    private String jDeployHome;

    private String jDeployHomeWindows;
    private String jDeployHomeMac;
    private String jDeployHomeLinux;
    private String jDeployRegistryUrl;

    /**
     * The version of the launcher/installer that created this bundle.
     * This is only set when running as the installer, not during CLI packaging.
     */
    private String launcherVersion;

    /**
     * The version of the app that was initially installed.
     * This is parsed from the installer filename and only set when running as the installer.
     */
    private String initialAppVersion;

    private List<CommandSpec> commands = Collections.emptyList();

    private Map<String,String> macUsageDescriptions = new HashMap<>();

    /**
     * If package signing is enabled, then this flag tells us whether to "pin" the app to the signing certificate.
     * If the app is pinned to a signing certificate, then the launcher will verify the signature of all files
     * in the jdeploy-bundle before launching the app each time.  If verification fails, then the app will not launch.
     */
    private boolean isPackageCertificatePinningEnabled;

    /**
     * This is the certificate used to sign the jdeploy bundle - NOT to be confused with the apple or windows
     * code signing certificates.
     */
    private List<Certificate> trustedCertificates;

    private String packageSigningCertificateChainPath;

    public AppDescription() {

        jars = new ArrayList<Jar>();
        extensions = new HashMap<String, String>();
        icons = new HashMap<String, String>();
        appIcons = new ArrayList<String>();
        natives = new ArrayList<NativeLib>();
        props = new ArrayList<Prop>();
    }

    public boolean hasUrlSchemes() {
        return urlSchemes != null && !urlSchemes.isEmpty();
    }

    public Iterable<String> getUrlSchemes() {
        ArrayList<String> out = new ArrayList<>();
        if (hasUrlSchemes()) {
            for (String scheme : urlSchemes) {
                out.add(scheme);
            }
        }
        return out;
    }

    public void addUrlScheme(String scheme) {
        if (urlSchemes == null) urlSchemes = new ArrayList<>();
        urlSchemes.add(scheme);
    }

    /**
     * Sets directory association for this application.
     * @param association The directory document type association
     */
    public void setDirectoryAssociation(ca.weblite.jdeploy.models.DocumentTypeAssociation association) {
        if (association != null && !association.isDirectory()) {
            throw new IllegalArgumentException("Association must be a directory type");
        }
        this.directoryAssociation = association;
    }

    /**
     * Sets directory association for this application.
     * @param role "Editor" or "Viewer"
     * @param description Human-readable description
     * @param icon Optional path to custom icon
     */
    public void setDirectoryAssociation(String role, String description, String icon) {
        this.directoryAssociation = new ca.weblite.jdeploy.models.DocumentTypeAssociation(role, description, icon);
    }

    /**
     * @return true if this application has a directory association configured
     */
    public boolean hasDirectoryAssociation() {
        return directoryAssociation != null;
    }

    /**
     * @return the directory association, or null if none configured
     */
    public ca.weblite.jdeploy.models.DocumentTypeAssociation getDirectoryAssociation() {
        return directoryAssociation;
    }

    /**
     * @return the role for directory associations ("Editor" or "Viewer")
     */
    public String getDirectoryRole() {
        return directoryAssociation != null ? directoryAssociation.getRole() : "Viewer";
    }

    /**
     * @return the description for directory associations
     */
    public String getDirectoryDescription() {
        return directoryAssociation != null ? directoryAssociation.getDescription() : null;
    }

    /**
     * @return the icon path for directory associations
     */
    public String getDirectoryIcon() {
        return directoryAssociation != null ? directoryAssociation.getIconPath() : null;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    void addJar(Jar jar) {
        this.jars.add(jar);
    }

    public Iterable<Jar> getJars() {
        return this.jars;
    }

    public void setTrustedCertificates(List<Certificate> cert) {
        this.trustedCertificates = cert;
    }

    public List<Certificate> getTrustedCertificates() {
        return this.trustedCertificates;
    }

    public void setPackageSigningCertificateChainPath(String path) {
        this.packageSigningCertificateChainPath = path;
    }

    public String getPackageSigningCertificateChainPath() {
        return this.packageSigningCertificateChainPath;
    }

    public void enablePackageCertificatePinning() {
        this.isPackageCertificatePinningEnabled = true;
    }

    public void disablePackageCertificatePinning() {
        this.isPackageCertificatePinningEnabled = false;
    }

    public boolean isPackageCertificatePinningEnabled() {
        return isPackageCertificatePinningEnabled;
    }

    public void setMacBundleId(String id) {
        this.macBundleId = id;
    }
    
    public String getMacBundleId() {
        return this.macBundleId;
    }
    
    public String getName() {
        return this.name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getMainClass() throws Exception {
        for(Jar jar : jars) {
            if(jar.isMain()) return jar.getMainClass();
        }
        throw new Exception("Error! Couldn't find a main class for the app");
    }

    public void addExtension(String fileExtension, String mimeType, String icon) {
        extensions.put(fileExtension,mimeType);
        if(icon != null) {
            icons.put(fileExtension,icon);
        }
    }

    public void addEditableExtension(String extension) {
        if (editableExtensions == null) editableExtensions = new HashSet<>();
        editableExtensions.add(extension);
    }

    public boolean isEditableExtension(String extension) {
        return editableExtensions != null && editableExtensions.contains(extension);
    }
    public Collection<String> getExtensions() {
        return extensions.keySet();
    }
    public String getExtensionMimetype(String ext) {
        return extensions.get(ext);
    }

    public String getExtensionIcon(String ext) {
        return icons.get(ext);
    }

    public Collection<String> getAppIcons() {
        return appIcons;
    }

    void addIcon(String name) {
        appIcons.add(name);
    }

    void addNative(NativeLib nativeLib) {
        natives.add(nativeLib);
    }

    public Iterable<NativeLib> getNativeLibs() {
        return natives;
    }
    
    void addProp(Prop prop) {
        props.add(prop);
    }
    
    public Iterable<Prop> getProps() {
        return props;
    }
    
    public void enableMacCodeSigning(String certificateName) {
        enableMacCodeSigning = true;
        if (certificateName != null) {
            setMacCertificateName(certificateName);
        }
    }
    
    public void disableMacCodeSigning() {
        enableMacCodeSigning = false;
    }
    
    public boolean isMacCodeSigningEnabled() {
        return enableMacCodeSigning && getMacCertificateName() != null;
    }
    
    public void enableMacNotarization(
            String developerId,
            String notarizationPassword,
            String developerTeamID
    ) {
        enableMacNotarization = true;
        if (developerId != null) {
            setMacDeveloperID(developerId);
        }
        if (notarizationPassword != null) {
            this.setMacNotarizationPassword(notarizationPassword);
        }
        if (developerTeamID != null) {
            this.setMacDeveloperTeamID(developerTeamID);
        }
        
    }
    
    public void disableMacNotarization() {
        enableMacNotarization = false;
    }
    
    public boolean isMacNotarizationEnabled() {
        return enableMacNotarization && getMacDeveloperID() != null && getMacNotarizationPassword() != null;
    }

    
    /**
     * @return the macCertificateName
     */
    public String getMacCertificateName() {
        return macCertificateName;
    }

    /**
     * @param macCertificateName the macCertificateName to set
     */
    public void setMacCertificateName(String macCertificateName) {
        this.macCertificateName = macCertificateName;
    }

    /**
     * @return the macDeveloperID
     */
    public String getMacDeveloperID() {
        return macDeveloperID;
    }

    public String getMacDeveloperTeamID() {
        return macDeveloperTeamID;
    }

    /**
     * @param macDeveloperID the macDeveloperID to set
     */
    public void setMacDeveloperID(String macDeveloperID) {
        this.macDeveloperID = macDeveloperID;
    }

    public void setMacDeveloperTeamID(String macDeveloperTeamID) {
        this.macDeveloperTeamID = macDeveloperTeamID;
    }

    /**
     * @return the macNotarizationPassword
     */
    public String getMacNotarizationPassword() {
        return macNotarizationPassword;
    }

    /**
     * @param macNotarizationPassword the macNotarizationPassword to set
     */
    public void setMacNotarizationPassword(String macNotarizationPassword) {
        this.macNotarizationPassword = macNotarizationPassword;
    }

    public String getNpmPackage() {
        return npmPackage;
    }

    public void setNpmPackage(String npmPackage) {
        this.npmPackage = npmPackage;
    }

    public String getNpmSource() {
        return npmSource;
    }

    public void setNpmSource(String npmSource) {
        this.npmSource = npmSource;
    }

    /**
     * Gets the path to local package.json for local development mode.
     * @return the local package.json path, or null if not in local mode
     */
    public String getLocalPackageJson() {
        return localPackageJson;
    }

    /**
     * Sets the path to local package.json for local development mode.
     * @param localPackageJson the absolute path to the local package.json
     */
    public void setLocalPackageJson(String localPackageJson) {
        this.localPackageJson = localPackageJson;
    }

    /**
     * Gets the path to local jdeploy-bundle for local development mode.
     * @return the local bundle path, or null if not in local mode
     */
    public String getLocalBundle() {
        return localBundle;
    }

    /**
     * Sets the path to local jdeploy-bundle for local development mode.
     * @param localBundle the absolute path to the local jdeploy-bundle
     */
    public void setLocalBundle(String localBundle) {
        this.localBundle = localBundle;
    }

    /**
     * Checks if this app is configured for local development mode.
     * @return true if both localPackageJson and localBundle are set
     */
    public boolean isLocalMode() {
        return localPackageJson != null && !localPackageJson.isEmpty()
                && localBundle != null && !localBundle.isEmpty();
    }

    public String getNpmVersion() {
        return npmVersion;
    }

    public void setNpmVersion(String npmVersion) {
        this.npmVersion = npmVersion;
    }

    public String getIconDataURI() {
        return iconDataURI;
    }

    public void setIconDataURI(String iconDataURI) {
        this.iconDataURI = iconDataURI;
    }

    public String getSplashDataURI() {
        return splashDataURI;
    }

    public void setSplashDataURI(String splashDataURI) {
        this.splashDataURI = splashDataURI;
    }

    public boolean isNpmPrerelease() {
        return npmPrerelease;
    }

    public void setNpmPrerelease(boolean npmPrerelease) {
        this.npmPrerelease = npmPrerelease;
    }

    public boolean isFork() {
        return fork;
    }

    public void setFork(boolean fork) {
        this.fork = fork;
    }

    public void setBundleJre(File bundleJre) {
        this.bundleJre = bundleJre;
    }

    public File getBundleJre() {
        return bundleJre;
    }

    public void setJcefFrameworksPath(String jcefFrameworksPath) {
        this.jcefFrameworksPath = jcefFrameworksPath;
    }

    public String getJcefFrameworksPath() {
        return jcefFrameworksPath;
    }

    public String getjDeployHome() {
        return jDeployHome;
    }

    public void setjDeployHome(String jDeployHome) {
        this.jDeployHome = jDeployHome;
    }

    public String getjDeployHomeWindows() {
        return jDeployHomeWindows;
    }

    public void setjDeployHomeWindows(String jDeployHomeWindows) {
        this.jDeployHomeWindows = jDeployHomeWindows;
    }

    public String getjDeployHomeMac() {
        return jDeployHomeMac;
    }

    public void setjDeployHomeMac(String jDeployHomeMac) {
        this.jDeployHomeMac = jDeployHomeMac;
    }

    public String getjDeployHomeLinux() {
        return jDeployHomeLinux;
    }

    public void setjDeployHomeLinux(String jDeployHomeLinux) {
        this.jDeployHomeLinux = jDeployHomeLinux;
    }

    public void setJDeployRegistryUrl(String jDeployRegistryUrl) {
        this.jDeployRegistryUrl = jDeployRegistryUrl;
    }
    public String getJDeployRegistryUrl() {
        return jDeployRegistryUrl;
    }

    public Map<String,String> getMacUsageDescriptions() {
        return macUsageDescriptions;
    }

    public void setMacUsageDescription(String key, String value) {
        macUsageDescriptions.put(key, value);
    }

    public String getLauncherVersion() {
        return launcherVersion;
    }

    public void setLauncherVersion(String launcherVersion) {
        this.launcherVersion = launcherVersion;
    }

    public String getInitialAppVersion() {
        return initialAppVersion;
    }

    public void setInitialAppVersion(String initialAppVersion) {
        this.initialAppVersion = initialAppVersion;
    }

    public List<CommandSpec> getCommands() {
        return commands;
    }

    public void setCommands(List<CommandSpec> commands) {
        this.commands = commands != null ? Collections.unmodifiableList(new ArrayList<>(commands)) : Collections.emptyList();
    }
}

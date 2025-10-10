/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.weblite.jdeploy.appbundler;

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

    private File bundleJre;

    private String jDeployHome;

    private String jDeployHomeWindows;
    private String jDeployHomeMac;
    private String jDeployHomeLinux;
    private String jDeployRegistryUrl;

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
}

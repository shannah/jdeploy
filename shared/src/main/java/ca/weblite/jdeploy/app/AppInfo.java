/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.jdeploy.app;

import ca.weblite.tools.platform.Platform;

import com.client4j.Client4J;
import com.client4j.ResourceInfo;
import com.client4j.security.RuntimeGrantedPermission;
import com.client4j.security.RuntimeGrantedPermissions;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;

/**
 *
 * @author shannah
 */
public class AppInfo  {

    private String macAppUrl;
    private String windowsAppUrl;
    private String linuxAppUrl;
    private String windowsInstallerUrl;
    private String linuxInstallerUrl;
    private String githubRepositoryUrl;

    private String macJdeployHome;

    private String windowsJdeployHome;

    private String linuxJdeployHome;

    private String jdeployHome;
    private String tagLine;
    private String title;
    private String description;
    private String vendor;
    private String version;
    private String changes;
    private String npmPackage;
    private String npmVersion;

    private String npmSource = "";
    private boolean npmAllowPrerelease;
    private String jdeployBundleCode;
    private boolean fork;

    private Map<String, String> documentMimetypes;

    private Map<String, String> documentTypeIcons;

    private Set<String> documentTypeEditor;

    private Set<String> urlSchemes;

    /**
     * Indicates that the app should use a dedicated JVM rather than the default shared JVM.
     * This is required for Java 8 on Windows (at least) since Java 8 didn't provide a way to
     * use a JRE at an arbitrary location.  This is no longer necessary in Java 9+, but we may
     * want to employ its use for other reasons.
     */
    private boolean usePrivateJVM = false;

    /**
     * Indicates that the build should include an embedded JRE as part of the app bundle.
     */
    private boolean useBundledJVM = false;

    private JVMSpecification jvmSpecification;

    public void addUrlScheme(String scheme) {
        if (urlSchemes == null) urlSchemes = new HashSet<>();
        urlSchemes.add(scheme);
    }

    public boolean hasUrlSchemes() {
        return urlSchemes != null && !urlSchemes.isEmpty();
    }

    public Iterable<String> getUrlSchemes() {
        if (urlSchemes == null) {
            urlSchemes = new HashSet<>();
        }
        return urlSchemes;
    }

    private Map<String, String> documentMimetypes() {
        if (documentMimetypes == null) documentMimetypes = new HashMap<>();
        return documentMimetypes;
    }

    private Map<String, String> documentTypeIcons() {
        if (documentTypeIcons == null) documentTypeIcons = new HashMap<>();
        return documentTypeIcons;
    }

    public void addDocumentMimetype(String extension, String mimetype) {
        documentMimetypes().put(extension, mimetype);
    }

    public void addDocumentTypeIcon(String extension, String iconPath) {
        documentTypeIcons().put(extension, iconPath);
    }

    public void setDocumentTypeEditor(String extension) {
        if (documentTypeEditor == null) documentTypeEditor = new HashSet<>();
        documentTypeEditor.add(extension);
    }

    public boolean isDocumentTypeEditor(String extension) {
        return documentTypeEditor != null && documentTypeEditor.contains(extension);
    }

    public boolean hasDocumentTypes() {
        return documentMimetypes != null && !documentMimetypes.isEmpty();
    }

    public Iterable<String> getExtensions() {
        if (documentMimetypes == null || documentMimetypes.isEmpty()) return new ArrayList<>();
        return documentMimetypes.keySet();
    }

    public String getMimetype(String extension) {
        if (documentMimetypes != null && documentMimetypes.containsKey(extension)) {
            return documentMimetypes.get(extension);
        }
        return null;
    }

    public String getDocumentTypeIconPath(String extension) {
        if (documentTypeIcons != null && documentTypeIcons.containsKey(extension)) {
            return documentTypeIcons.get(extension);
        }
        return null;
    }

    /**
     * @return the tagline
     */
    public String getTagline() {
        return tagLine;
    }

    /**
     * @param tagline the tagline to set
     */
    public void setTagline(String tagline) {
        this.tagLine = tagLine;
    }

    /**
     * @param url the githubRepositoryUrl to set
     */
    public void setGithubRepositoryUrl(String url) {
        this.githubRepositoryUrl=url;
        
    }

    public String getGithubRepositoryUrl() {
        return githubRepositoryUrl;
    }
    
    /**
     * @return the macAppBundleId
     */
    public String getMacAppBundleId() {
        return macAppBundleId;
    }

    /**
     * @param macAppBundleId the macAppBundleId to set
     */
    public void setMacAppBundleId(String macAppBundleId) {
        if (!Objects.equals(macAppBundleId, this.macAppBundleId)) {
            this.macAppBundleId = macAppBundleId;

        }
    }

    public void setMacJdeployHome(String macJdeployHome) {
        this.macJdeployHome = macJdeployHome;
    }

    public String getMacJdeployHome() {
        return macJdeployHome;
    }

    public void setWindowsJdeployHome(String windowsJdeployHome) {
        this.windowsJdeployHome = windowsJdeployHome;
    }

    public String getWindowsJdeployHome() {
        return windowsJdeployHome;
    }

    public void setLinuxJdeployHome(String linuxJdeployHome) {
        this.linuxJdeployHome = linuxJdeployHome;
    }

    public String getLinuxJdeployHome() {
        return linuxJdeployHome;
    }

    public void setJdeployHome(String jdeployHome) {
        this.jdeployHome = jdeployHome;
    }

    public String getJdeployHome() {
        return jdeployHome;
    }

    public CodeSignSettings getCodeSignSettings() {
        return codeSignSettings;
    }
    
    public void setCodeSignSettings(CodeSignSettings settings) {
        if (!Objects.equals(settings, codeSignSettings)) {
            codeSignSettings = settings;
        }
    }
    
    /**
     * @return the macAppUrl
     */
    public String getMacAppUrl() {
        return macAppUrl;
    }

    /**
     * @param url the macAppUrl to set
     */
    public void setMacAppUrl(String url) {
        this.macAppUrl = url;
    }

    /**
     * @return the windowsAppUrl
     */
    public String getWindowsAppUrl() {
        return windowsAppUrl;
    }

    /**
     * @param url the windowsAppUrl to set
     */
    public void setWindowsAppUrl(String url) {
        this.windowsAppUrl = url;
    }

    /**
     * @return the windowsInstallerUrl
     */
    public String getWindowsInstallerUrl() {
        return windowsInstallerUrl;
    }

    /**
     * @param url the windowsInstallerUrl to set
     */
    public void setWindowsInstallerUrl(String url) {
        windowsInstallerUrl = url;
    }

    /**
     * @return the linuxAppUrl
     */
    public String getLinuxAppUrl() {
        return linuxAppUrl;
    }

    /**
     * @param url the linuxAppUrl to set
     */
    public void setLinuxAppUrl(String url) {
        linuxAppUrl = url;
    }
    
    

    /**
     * @return the linuxInstallerUrl
     */
    public String getLinuxInstallerUrl() {
        return linuxInstallerUrl;
    }

    /**
     * @param url the linuxInstallerUrl to set
     */
    public void setLinuxInstallerUrl(String url) {
        linuxInstallerUrl = url;
        
    }


    /**
     * @return the updates
     */
    public Updates getUpdates() {
        return updates;
    }

    /**
     * @param updates the updates to set
     */
    public void setUpdates(Updates updates) {
        if (!Objects.equals(updates, this.updates)) {
            this.updates = updates;

        }
    }

    public String getNpmPackage() {
        return npmPackage;
    }

    public String getNpmSource() {
        return npmSource;
    }

    public void setNpmSource(String source) {
        this.npmSource = source;
    }

    public void setNpmPackage(String npmPackage) {
        this.npmPackage = npmPackage;
    }

    public String getNpmVersion() {
        return npmVersion;
    }

    public void setNpmVersion(String npmVersion) {
        this.npmVersion = npmVersion;
    }

    public boolean isNpmAllowPrerelease() {
        return npmAllowPrerelease;
    }

    public void setNpmAllowPrerelease(boolean npmAllowPrerelease) {
        this.npmAllowPrerelease = npmAllowPrerelease;
    }

    public String getJdeployBundleCode() {
        return jdeployBundleCode;
    }

    public void setJdeployBundleCode(String jdeployBundleCode) {
        this.jdeployBundleCode = jdeployBundleCode;
    }

    public boolean isFork() {
        return fork;
    }

    public void setFork(boolean fork) {
        this.fork = fork;
    }

    public boolean isUsePrivateJVM() {
        return usePrivateJVM;
    }

    public void setUsePrivateJVM(boolean usePrivateJVM) {
        this.usePrivateJVM = usePrivateJVM;
    }

    public boolean isUseBundledJVM() {
        return useBundledJVM;
    }

    public void setUseBundledJVM(boolean useBundledJVM) {
        this.useBundledJVM = useBundledJVM;
    }

    public void setJVMSpecification(JVMSpecification spec) {
        jvmSpecification = spec;
    }

    public JVMSpecification getJVMSpecification() {
        return jvmSpecification;
    }

    public static enum Updates {
        Auto,
        Manual
    }
    
    public static class PermissionsList implements Iterable<Permission>{
        private List<Permission> permissions=new ArrayList<>();

        public PermissionsList() {
            
        }
        
        public PermissionsList(Iterable<Permission> perms) {
            addAll(perms);
        }
        
        @Override
        public Iterator<Permission> iterator() {
            return permissions.iterator();
        }

        public void add(Permission perm) {

            permissions.add(perm);

        }
        
        public void sort() {
            Collections.sort(permissions);
        }
        
        public void addAll(Iterable<Permission> perms) {
            for (Permission p : perms) {
                add(p);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PermissionsList) {
                return permissions.equals(((PermissionsList)obj).permissions);
            }
            return false;
        }

        public void clear() {
            permissions.clear();
        }
    }
    
    /**
     * @return the permissions
     */
    public PermissionsList getPermissions() {
        return permissions;
    }

    /**
     * @return the dependencies
     */
    public List<Dependency> getDependencies() {
        return dependencies;
    }

    public List<JRE> getRuntimes(boolean init ) {
        if (runtimes == null && init) {
            runtimes = new ArrayList<JRE>();
        }
        return runtimes;
    }
    
    public List<Dependency> getDependencies(boolean init) {
        if (dependencies == null && init) {
            dependencies = new ArrayList<Dependency>();
        }
        return dependencies;
    }

    /**
     * @param dependencies the dependencies to set
     */
    public void setDependencies(List<Dependency> dependencies) {
        this.dependencies = dependencies;
    }
    
    public RuntimeGrantedPermissions getPermissionsAsRuntimeGrantedPermissions() {
        RuntimeGrantedPermissions out = new RuntimeGrantedPermissions();
        if (getPermissions() != null) {
            for (Permission p : getPermissions()) {
                out.add(p.asRuntimeGrantedPermission());
            }
        }
        return out;
    }

    public static class Permission implements Comparable<Permission> {
        
        public Permission() {
        }
        
        public Permission(Permission toCopy) {
            name = toCopy.name;
            target = toCopy.target;
            action = toCopy.action;
        }
        
        /**
         * Gets this permission as a RuntimeGrantedPermission.  RuntimeGrantedPermission
         * is used to encapsulate the actual permissions used by an app at runtime.  They are
         * stored in the app's permissions.xml file (in its private base directory).
         * @return The equivalent runtime granted permission.
         */
        public RuntimeGrantedPermission asRuntimeGrantedPermission() {
            RuntimeGrantedPermission out = new RuntimeGrantedPermission();
            out.setClassName(name);
            out.setName(target);
            out.setActions(action);
            out.setExpires(new Date(System.currentTimeMillis() + 30 * 24 * 60 * 60 * 1000l));
            return out;
        }
        
        public static Permission fromRuntimeGrantedPermssion(RuntimeGrantedPermission perm) {
            Permission out = new Permission();
            out.setName(perm.getClassName());
            out.setTarget(perm.getName());
            out.setAction(perm.getActions());
            return out;
        }
        
        public Permission(String name) {
            this.name = name;
        }
        
        public Permission(String name, String target) {
            this.name = name;
            this.target = target;
        }

        public Permission(String name, String target, String action) {
            this.name = name;
            this.target = target;
            this.action = action;
        }
        /**
         * @return the className
         */
        public String getName() {
            return name;
        }

        /**
         * @param className the className to set
         */
        public void setName(String className) {
            if (!Objects.equals(className, name)) {
                this.name = className;

            }
        }

        /**
         * @return the permissionName
         */
        public String getTarget() {
            return target;
        }

        /**
         * @param permissionName the permissionName to set
         */
        public void setTarget(String permissionName) {
            if (!Objects.equals(permissionName, this.target)) {
                this.target = permissionName;

            }
        }

        /**
         * @return the action
         */
        public String getAction() {
            return action;
        }

        /**
         * @param action the action to set
         */
        public void setAction(String action) {
            if (!Objects.equals(action, this.action)) {
                this.action = action;

            }
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("permission ").append(name).append(" \"").append(target).append("\"");
            if (action != null) {
                sb.append(", \"").append(action).append("\"");
            }
            sb.append(";");
            return sb.toString();
        }
        
        public Permission copy() {
            Permission out = new Permission();
            out.name = name;
            out.target = target;
            out.action = action;
            return out;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Permission) {
                Permission p = (Permission)obj;
                return Objects.equals(name, p.name) &&
                        Objects.equals(target, p.target) &&
                        Objects.equals(action, p.action);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 37 * hash + Objects.hashCode(this.name);
            hash = 37 * hash + Objects.hashCode(this.target);
            hash = 37 * hash + Objects.hashCode(this.action);
            return hash;
        }
        
        public static List<Permission> copy(List<Permission> src, List<Permission> dest) {
            for (Permission perm : src) {
                dest.add(perm.copy());
            }
            return dest;
        }

        @Override
        public int compareTo(Permission perm) {
            return (name + ":" + target + ":" + action)
                    .compareTo(perm.name+":"+perm.target+":"+perm.action);
        }
        
        private String name, target, action;

        private void trimStrings() {
            if (name != null) name = name.trim();
            if (target != null) target = target.trim();
            if (action != null) action = action.trim();
        }
    }
    
    public static class JRE extends Observable {

        /**
         * @return the fx
         */
        public boolean isFx() {
            return fx;
        }

        /**
         * @param fx the fx to set
         */
        public void setFx(boolean fx) {
            this.fx = fx;
        }

        @Override
        public String toString() {
            return "JRE{version:"+version+", os:"+os+", arch:"+arch+", url:"+url+", fx: "+fx+"}";
        }

        /**
         * @return the version
         */
        public String getVersion() {
            return version;
        }

        /**
         * @param version the version to set
         */
        public void setVersion(String version) {
            if (!Objects.equals(version, this.version)) {
                this.version = version;
                setChanged();
                
            }
        }

        /**
         * @return the platform
         */
        public String getOS() {
            return os;
        }

        /**
         * @param platform the platform to set
         */
        public void setOS(String platform) {
            if (!Objects.equals(platform, this.os)) {
                
                this.os = platform;
                setChanged();
            }
        }

        /**
         * @return the arch
         */
        public String getArch() {
            return arch;
        }

        /**
         * @param arch the arch to set
         */
        public void setArch(String arch) {
            if (!Objects.equals(arch, this.arch)) {
                this.arch = arch;
                setChanged();
            }
        }

        /**
         * @return the url
         */
        public URL getUrl() {
            return url;
        }

        /**
         * @param url the url to set
         */
        public void setUrl(URL url) {
            if (!Objects.equals(url, this.url)) {
                this.url = url;
                setChanged();
            }
        }
        
        public JRE copy() {
            JRE out = new JRE();
            out.url = url;
            out.os = os;
            out.arch = arch;
            out.version = version;
            out.fx = fx;
            return out;
            
        }
        
        public boolean isSupported() {
            return new Platform(os, arch).matchesSystem();
            
        }

        private void trimStrings() {
            if (os != null) os = os.trim();
            if (arch != null) arch = arch.trim();
            if (version != null) version = version.trim();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof JRE) {
                JRE jre = (JRE)obj;
                return Objects.equals(jre.arch, arch) && Objects.equals(jre.os, os) && Objects.equals(jre.url, url)
                        && Objects.equals(jre.version, version) && Objects.equals(jre.fx, fx);
            }
            return super.equals(obj);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 79 * hash + Objects.hashCode(this.url);
            hash = 79 * hash + Objects.hashCode(this.os);
            hash = 79 * hash + Objects.hashCode(this.arch);
            hash = 79 * hash + Objects.hashCode(this.version);
            if (fx) hash +=1;
            return hash;
        }

        private URL url;
        
        private String os, arch, version;
        private boolean fx; // includes JavaFX
    }
    
    public static class QuickLink  {

        
        public QuickLink(QuickLink toCopy) {
            this.url = toCopy.url;
            this.title = toCopy.title;
        }
        
        public QuickLink() {
            
        }
        
        /**
         * @return the url
         */
        public URL getUrl() {
            return url;
        }

        /**
         * @param url the url to set
         */
        public void setUrl(URL url) {
            if (!Objects.equals(url, this.url)) {
                this.url = url;

            }
        }

        /**
         * @return the title
         */
        public String getTitle() {
            return title;
        }

        /**
         * @param title the title to set
         */
        public void setTitle(String title) {
            if (!Objects.equals(title, this.title)) {
                this.title = title;

            }
        }
        
        public QuickLink copy() {
            return new QuickLink(this);
        }
        
        private void trimStrings() {
            if (title != null) title = title.trim();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof QuickLink) {
                QuickLink ql = (QuickLink)obj;
                return Objects.equals(ql.title, title) && Objects.equals(ql.url, url);
            }
            return super.equals(obj); 
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 47 * hash + Objects.hashCode(this.url);
            hash = 47 * hash + Objects.hashCode(this.title);
            return hash;
        }

        private URL url;
        private String title;
    }
    
    public static class Dependency extends Observable {

        /**
         * @return the commonName
         */
        public String getCommonName() {
            return commonName;
        }

        /**
         * Sets the common name of this dependency.  The common name and version
         * provides an alternative method for identifying a library vs providing
         * a URL.  
         * @param commonName the commonName to set
         */
        public void setCommonName(String commonName) {
            if (!Objects.equals(commonName, this.commonName)) {
                this.commonName = commonName;
                setChanged();
            }
        }

        /**
         * This value is only necessary when used in conjunction with the common
         * name.  If the url is set, then it won't check this value.
         * @return the version
         */
        public String getVersion() {
            return version;
        }

        /**
         * @param version the version to set
         */
        public void setVersion(String version) {
            if (!Objects.equals(version, this.version)) {
                this.version = version;
                setChanged();
            }
        }

        /**
         * @return the platform
         */
        public String getPlatform() {
            return platform;
        }

        /**
         * @param platform the platform to set
         */
        public void setPlatform(String platform) {
            if (!Objects.equals(platform, this.platform)) {
                this.platform = platform;
                setChanged();
            }
        }

        /**
         * @return the arch
         */
        public String getArch() {
            return arch;
        }

        /**
         * @param arch the arch to set
         */
        public void setArch(String arch) {
            if (!Objects.equals(arch, this.arch)) {
                this.arch = arch;
                setChanged();
            }
        }
        
        

        /**
         * @return the url
         */
        public URL getUrl() {
            return url;
        }

        /**
         * @param url the url to set
         */
        public void setUrl(URL url) {
            if (!Objects.equals(url, this.url)) {
                this.url = url;
                setChanged();
            }
        }

        /**
         * @return the trusted
         */
        public boolean isTrusted() {
            return trusted;
        }

        /**
         * @param trusted the trusted to set
         */
        public void setTrusted(boolean trusted) {
            if (!Objects.equals(trusted, this.trusted)) {
                this.trusted = trusted;
                setChanged();
            }
            
        }

        /**
         * The name of the jar that this dependency should "replace" in the classpath.
         * This is to deal with the situation that the app was distributed with one 
         * version of the library, but for trust reasons, that jar should always
         * be replaced by the jar in the dependency.
         * @return the jarName
         */
        public String getJarName() {
            return jarName;
        }

        /**
         * @param jarName the jarName to set
         */
        public void setJarName(String jarName) {
            if (!Objects.equals(jarName, this.jarName)) {
                this.jarName = jarName;
                setChanged();
            }
        }
        
        public Dependency copy() {
            Dependency out = new Dependency();
            out.url = url;
            out.trusted = trusted;
            out.jarName = jarName;
            out.platform = platform;
            out.arch = arch;
            out.commonName = commonName;
            out.version = version;
            return out;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Dependency) {
                Dependency d = (Dependency)obj;
                return Objects.equals(url, d.url)
                        && Objects.equals(trusted, d.trusted)
                        && Objects.equals(jarName, d.jarName)
                        && Objects.equals(platform, d.platform)
                        && Objects.equals(arch, d.arch)
                        && Objects.equals(commonName, d.commonName)
                        && Objects.equals(version, d.version);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 89 * hash + Objects.hashCode(this.url);
            hash = 89 * hash + (this.trusted ? 1 : 0);
            hash = 89 * hash + Objects.hashCode(this.jarName);
            hash = 89 * hash + Objects.hashCode(this.platform);
            hash = 89 * hash + Objects.hashCode(this.arch);
            hash = 89 * hash + Objects.hashCode(this.commonName);
            hash = 89 * hash + Objects.hashCode(this.version);
            return hash;
        }
        
        public boolean isSupported() {
            return new Platform(platform, arch).matchesSystem();
        }

        private void trimStrings() {
            if (jarName != null) jarName = jarName.trim();
            if (platform != null) platform = platform.trim();
            if (arch != null) arch = arch.trim();
        }
        
        private URL url;
        private boolean trusted;
        private String jarName;
        private String platform, arch, commonName, version;
    }
    
    private URL url(URL baseUrl, String url) {
        try {
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("github://")) {
                return new URL(url);
            } else {
                return getDependentURL(baseUrl, url);
            }
        } catch (MalformedURLException mex) {
            mex.printStackTrace();
            return null;
        }
    }
    
    private URL getDependentURL(URL appInfoUrl, String relativePath) {
        String url = appInfoUrl.toString();
        try {
            return new URL(url.substring(0, url.lastIndexOf("/")) + "/" + relativePath);
        } catch (MalformedURLException mex) {
            throw new RuntimeException(mex);
        }
    }

    /**
     * @return the installed
     */
    public boolean isInstalled() {
        return installed;
    }

    /**
     * @param installed the installed to set
     */
    public void setInstalled(boolean installed) {
        this.installed = installed;
    }

    /**
     * @return the appURL
     */
    public URL getAppURL() {
        return appURL;
    }
    
    public URL getAppInfoURL() {
        if (appURL == null) return null;
        String u = appURL.toString();
        u = u.substring(0, u.lastIndexOf("/")+1) + Client4J.APPINFO_NAME;
        try {
            return new URL(u);
        } catch (MalformedURLException ex) {
            // This URL should be fine... so we'll wrap in runtime exception
            // If this exception actually get's thrown we need to find out why, and guard...
            // Don't want this method to throw a MalformedURLExceptiokn
            throw new RuntimeException(ex);
        }
    }

    /**
     * @param appURL the appURL to set
     */
    public void setAppURL(URL appURL) {
        if (!Objects.equals(appURL, this.appURL)) {
            this.appURL = appURL;

        }
    }

    public int getNumScreenshots() {
        return numScreenshots;
    }
    
    public void setNumScreenshots(int numScreenshots) {
        this.numScreenshots = numScreenshots;
    }
    
    /**
     * @return the title
     */
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String t) {
        title = t;
    }

    /**
     * @return the vendor
     */
    public String getVendor() {
        return vendor;
    }
    
    public void setVendor(String v) {
        vendor = v;
    }

    /**
     * @return the version
     */
    public String getVersion() {
        return version;
    }
    
    

    /**
     * @param v the version to set
     */
    public void setVersion(String v) {
        version = v;
    }
    
    public AppInfo() {
    }

    public PermissionsList getPermissions(boolean init) {
        if (permissions == null && init) {
            permissions = new PermissionsList();

        }
        return permissions;
    }
    
    public AppInfo(Manifest mf) {
        setTitle(att(mf, Name.IMPLEMENTATION_TITLE));
        if (getTitle() == null) {
            setTitle(att(mf, Name.SPECIFICATION_TITLE));
        }
        
        setVendor(att(mf, Name.IMPLEMENTATION_VENDOR));
        if (getVendor() == null) {
            setVendor(att(mf, Name.SPECIFICATION_VENDOR));
        }
        
        setVersion(att(mf, Name.IMPLEMENTATION_VERSION));
        if (getVersion() == null) {
           setVersion(att(mf, Name.SPECIFICATION_VERSION));
        }
    }
    
    private static String att(Manifest mf, Name name) {
        return mf.getMainAttributes().getValue(name);
    }
    
    public void addScreenshot(URL screenshot) {
        if (screenshots == null) {
            screenshots = new ArrayList<URL>();
        }
        screenshots.add(screenshot);

    }
    
    public void clearScreenshots() {
        if (screenshots != null && !screenshots.isEmpty()) {
            screenshots.clear();

        }
    }
    
    public void removeScreenshot(ResourceInfo screenshot) {
        if (screenshots != null) {
            if (screenshots.remove(screenshot)) {

            }
        }
    }
    
    public List<URL> getScreenshots() {
        ArrayList<URL> out = new ArrayList<>();
        if (screenshots != null) {
            out.addAll(screenshots);
        }
        return out;
    }
    
    public URL getIcon() {
        return icon;
    }
    
    public void setIcon(URL icon) {
        if (!Objects.equals(icon, this.icon)) {
            this.icon = icon;
        }
    }

    public void setDescription(String desc) {
        description = desc;
    }
    
    public void setChanges(String ch) {
        changes = ch;
    }

    public String getDescription() {
        return description;
    }

    public String getChanges() {
        return changes;
    }
    
    public AppInfo copy() {
        AppInfo out = new AppInfo();
        out.setTitle(getTitle());
        out.setDescription(getDescription());
        out.setChanges(getChanges());
        out.setMacAppUrl(getMacAppUrl());
        out.setWindowsAppUrl(getWindowsAppUrl());
        out.setWindowsInstallerUrl(getWindowsInstallerUrl());
        out.setLinuxAppUrl(getLinuxAppUrl());
        out.setLinuxInstallerUrl(getLinuxInstallerUrl());
        out.setUsePrivateJVM(isUsePrivateJVM());
        out.setUseBundledJVM(isUseBundledJVM());
        out.codeSignSettings = codeSignSettings;
        out.macAppBundleId = macAppBundleId;
        if (permissions != null) {
            //out.permissions = new ArrayList<>();
            for (Permission p : permissions) {
                out.getPermissions(true).add(p.copy());
            }
        }
        out.setVendor(getVendor());
        out.setVersion(getVersion());
        out.icon = icon;
        if (screenshots != null) {
            out.screenshots = new ArrayList<URL>();
            for (URL u : screenshots) {
                out.screenshots.add(u);
            }
        }
        out.appURL = appURL;
        out.installed = installed;
        out.numScreenshots = numScreenshots;
        out.updates = updates;
        
        if (dependencies != null) {
            out.dependencies = new ArrayList<>();
            for (Dependency dep : dependencies) {
                out.dependencies.add(dep.copy());
            }
        }
        
        if (runtimes != null) {
            out.runtimes = new ArrayList<>();
            for (JRE r : runtimes) {
                out.runtimes.add(r.copy());
            }
        }

        out.jdeployHome = jdeployHome;
        out.macJdeployHome = macJdeployHome;
        out.windowsJdeployHome = windowsJdeployHome;
        out.linuxJdeployHome = linuxJdeployHome;

        return out;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof AppInfo) {
            return equalsImpl((AppInfo)obj);
        }
        return super.equals(obj);
    }
    
    private static boolean equalsPairs(Object... objs) {
        int len = objs.length;
        for (int i=0; i<len; i+=2) {
            Object o1 = objs[i];
            Object o2 = objs[i+1];
            
            if (!o1.getClass().equals(o2.getClass())) {
                return false;
            }
            
            if (!Objects.deepEquals(o1, o2)) {
                return false;
            }
        }
        return false;
    }
    
    public void trimStrings() {
        if (getTitle() != null) setTitle(getTitle().trim());
        if (getDescription() != null) setDescription(getDescription().trim());
        if (getChanges() != null) setChanges(getChanges().trim());
        if (getVendor() != null) setVendor(getVendor().trim());
        if (getVersion() != null) setVersion(getVersion().trim());
        if (getMacAppUrl() != null) setMacAppUrl(getMacAppUrl().trim());
        if (getWindowsAppUrl() != null) setWindowsAppUrl(getWindowsAppUrl().trim());
        if (getWindowsInstallerUrl() != null)setWindowsInstallerUrl(getWindowsInstallerUrl().trim());
        if (getLinuxAppUrl() != null) setLinuxAppUrl(getLinuxAppUrl().trim());
        if (getLinuxInstallerUrl() != null) setLinuxInstallerUrl(getLinuxInstallerUrl().trim());
        if (macAppBundleId != null) macAppBundleId = macAppBundleId.trim();
        
        if (permissions != null) {
            for (Permission p : permissions) {
                p.trimStrings();
            }
        }
        
        if (runtimes != null) {
            for (JRE r : runtimes) {
                r.trimStrings();
            }
        }

        
        if (dependencies != null) {
            for (Dependency dep : dependencies) {
                dep.trimStrings();
            }
        }
    }
    
    public void normalize() {
        trimStrings();
        if (permissions != null) {
            permissions.sort();
        }
        // TODO:  sort other collections
    }
    
    private boolean equalsImpl(AppInfo o) {
        return equalsPairs(new Object[]{
            getTitle(), o.getTitle(),
            getDescription(), o.getDescription(),
            getChanges(), o.getChanges(),
            permissions, o.permissions,
            getVendor(), o.getVendor(),
            getVersion(), o.getVersion(),
            icon, o.icon,
            screenshots, o.screenshots,
            appURL, o.appURL,
            installed, o.installed,

            numScreenshots, o.numScreenshots,
            updates, o.updates,
            dependencies, o.dependencies,
            runtimes, o.runtimes,
            macAppUrl, o.macAppUrl,
            windowsAppUrl, o.windowsAppUrl,
            windowsInstallerUrl, o.windowsInstallerUrl,
            linuxAppUrl, o.linuxAppUrl,
            linuxInstallerUrl, o.linuxInstallerUrl,
            githubRepositoryUrl, o.githubRepositoryUrl,
            tagLine, o.tagLine,
            codeSignSettings, o.codeSignSettings,
            macAppBundleId, o.macAppBundleId,
                npmPackage, o.npmPackage,
                npmVersion, o.npmVersion,
                npmAllowPrerelease, o.npmAllowPrerelease,
                jdeployHome, o.jdeployHome,
                macJdeployHome, o.macJdeployHome,
                windowsJdeployHome, o.windowsJdeployHome,
                linuxJdeployHome, o.linuxJdeployHome,
            
        });
    }
    
    
    public String[] getPropertyKeys() {
        return new String[]{
            "macAppUrl", "windowsAppUrl", "windowsInstallerUrl", "linuxAppUrl", "linuxInstallerUrl"
        };
    }
    
    public void setProperty(String key, String value) {
        switch (key) {
            case "macAppUrl" : setMacAppUrl(value); break;
            case "windowsAppUrl" : setWindowsAppUrl(value); break;
            case "windowsInstallerUrl" : setWindowsInstallerUrl(value); break;
            case "linuxAppUrl" : setLinuxAppUrl(value); break;
            case "linuxInstallerUrl" : setLinuxInstallerUrl(value); break;
            default: 
                throw new IllegalArgumentException("Attempt to set unsupported property "+key+".  Supported keys include "+Arrays.toString(getPropertyKeys()));
        }
    }
    
    public static enum CodeSignSettings {
        Default,
        None,
        CodeSign,
        CodeSignAndNotarize
    }

    private PermissionsList permissions;
    private URL icon;
    private List<URL> screenshots;
    private URL appURL;
    private boolean installed;
    private int numScreenshots;
    private Updates updates = Updates.Auto;
    private List<Dependency> dependencies;
    private List<JRE> runtimes;
    private CodeSignSettings codeSignSettings = CodeSignSettings.None;
    
    private String macAppBundleId;
}

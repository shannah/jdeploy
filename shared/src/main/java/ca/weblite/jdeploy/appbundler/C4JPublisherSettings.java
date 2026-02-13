/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.jdeploy.appbundler;

import ca.weblite.jdeploy.app.AppInfo;
//import com.client4j.publisher.client.CodeSignServerDescriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Observable;
import java.util.Properties;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 *
 * @author shannah
 */
public class C4JPublisherSettings {

    private static Properties jDeployProperties;

    private static class ObservableImpl extends Observable {

        @Override
        protected synchronized void setChanged() {
            super.setChanged(); 
        }
        
    }
    private static final ObservableImpl observable = new ObservableImpl();
    private static final String MAC_DEVELOPER_CERTIFICATE_NAME = "mac.developer-certificate-name";
    private static final String MAC_DEVELOPER_ID = "mac.developer-id";

    private static final String MAC_DEVELOPER_TEAM_ID = "mac.developer-team-id";
    private static final String MAC_NOTORIZATION_PASSWORD = "mac.notarization-password";
    private static final String MAC_SIGNING_SETTINGS = "mac.signing-settings";
    private static final String MAC_APPS_NODE = "mac.apps";

    // Windows signing constants
    private static final String WINDOWS_CERTIFICATE_PATH = "windows.certificate-path";
    private static final String WINDOWS_CERTIFICATE_PASSWORD = "windows.certificate-password";
    private static final String WINDOWS_TIMESTAMP_SERVER = "windows.timestamp-server";
    private static final String WINDOWS_PUBLISHER_NAME = "windows.publisher-name";
    private static final String DEFAULT_WINDOWS_TIMESTAMP_SERVER = "http://timestamp.digicert.com";
    
    private static Preferences root;

    static {
        if (jDeployProperties == null) {
            synchronized (observable) {
                jDeployProperties = new Properties();
                File jDeployPropertiesFile = new File(System.getProperty("user.home") + File.separator + ".jdeploy" + File.separator + "private" + File.separator + "jdeploy_settings.properties");
                if (jDeployPropertiesFile.exists()) {
                    try (FileInputStream fis = new FileInputStream(jDeployPropertiesFile)) {
                        jDeployProperties.load(fis);
                    } catch (Exception ex) {
                        System.err.println("Attempt to load properties file " + jDeployPropertiesFile + " failed.");
                        ex.printStackTrace(System.err);
                    }
                }
            }
        }
    }
    
    private C4JPublisherSettings() {

    }
    
    public static Observable getObservable() {
        return observable;
    }
    
    public static enum MacSigningSettings {
        None,
        CodeSign,
        CodeSignAndNotarize
    }
    
    private static Preferences root() {
        if (root == null) {
            root = Preferences.userNodeForPackage(C4JPublisherSettings.class);
        }
        return root;
    }
    
    public static MacSigningSettings getMacSigningSettings() {
        return MacSigningSettings.values()[root().getInt(MAC_SIGNING_SETTINGS, 0)];
    }
    
    public static void setMacSigningSettings(MacSigningSettings setting) {
        if (!Objects.equals(setting, getMacSigningSettings())) {
            root().putInt(MAC_SIGNING_SETTINGS, setting.ordinal());
            observable.setChanged();
        }
    }
    
    public static String getMacDeveloperCertificateName() {
        String certificateName = System.getenv("JDEPLOY_MAC_DEVELOPER_CERTIFICATE_NAME");
        if (certificateName != null) {
            return certificateName;
        }
        return jDeployProperties.getProperty(MAC_DEVELOPER_CERTIFICATE_NAME, root().get(MAC_DEVELOPER_CERTIFICATE_NAME, ""));
    }
    
    public static String getMacDeveloperID() {
        String developerId = System.getenv("JDEPLOY_MAC_DEVELOPER_ID");
        if (developerId != null) {
            return developerId;
        }
        return jDeployProperties.getProperty(MAC_DEVELOPER_ID, root().get(MAC_DEVELOPER_ID, ""));
    }
    
    public static void setMacDeveloperID(String developerID) {
        if (!Objects.equals(getMacDeveloperID(), developerID)) {
            root().put(MAC_DEVELOPER_ID, developerID);
            observable.setChanged();
        }
    }
    
    public static String getMacNotarizationPassword() {
        String password = System.getenv("JDEPLOY_MAC_NOTARIZATION_PASSWORD");
        if (password != null) return password;
        return jDeployProperties.getProperty(MAC_NOTORIZATION_PASSWORD, root().get(MAC_NOTORIZATION_PASSWORD, ""));
    }
   
    private static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }
    
    private static Preferences getAppsNode() {
        return root().node(MAC_APPS_NODE);
    }
    
    private static Preferences getAppNode(AppInfo appInfo) {
        if (!isEmpty(appInfo.getMacAppBundleId())) {
            return getAppsNode().node(appInfo.getMacAppBundleId());
        
        } else {
            return null;
        }
        
    }
    
    public static String getMacDeveloperID(AppInfo appInfo) {
        String developerId = System.getenv("JDEPLOY_MAC_DEVELOPER_ID");
        if (developerId != null) {
            return developerId;
        }
        Preferences node = getAppNode(appInfo);
        if (node == null) {
            return "";
        }
        return jDeployProperties.getProperty(appInfo.getNpmPackage()+"."+MAC_DEVELOPER_ID, node.get(MAC_DEVELOPER_ID, ""));
    }

    public static String getMacDeveloperTeamID(AppInfo appInfo) {
        String teamId = System.getenv("JDEPLOY_MAC_DEVELOPER_TEAM_ID");
        if (teamId != null) {
            return teamId;
        }
        Preferences node = getAppNode(appInfo);
        if (node == null) {
            return "";
        }
        return jDeployProperties.getProperty(appInfo.getNpmPackage()+"."+MAC_DEVELOPER_TEAM_ID, node.get(MAC_DEVELOPER_TEAM_ID, ""));
    }
    
    public static void setMacDeveloperID(AppInfo appInfo, String id) {
        if (!Objects.equals(id, getMacDeveloperID(appInfo))) {
            Preferences node = getAppNode(appInfo);
            if (node == null) {
                throw new IllegalStateException("Cannot set notarization password on app until it has a bundle ID");
            }
            node.put(MAC_DEVELOPER_ID, id);
            observable.setChanged();
        }
        
    }
    
    public static String getMacNotarizationPassword(AppInfo appInfo) {
        String password = System.getenv("JDEPLOY_MAC_NOTARIZATION_PASSWORD");
        if (password != null) {
            return password;
        }
        Preferences node = getAppNode(appInfo);
        if (node == null) {
            return "";
        }
        return jDeployProperties.getProperty(appInfo.getNpmPackage()+"."+MAC_NOTORIZATION_PASSWORD, node.get(MAC_NOTORIZATION_PASSWORD, ""));
    }
    
    public static void setMacNotarizationPassword(AppInfo appInfo, String password) {
        if (!Objects.equals(password, getMacNotarizationPassword(appInfo))) {
            Preferences node = getAppNode(appInfo);
            if (node == null) {
                throw new IllegalStateException("Cannot set notarization password on app until it has a bundle ID");
            }
            node.put(MAC_NOTORIZATION_PASSWORD, password);
            observable.setChanged();
        }
        
    }
    
    public static String getMacDeveloperCertificateName(AppInfo appInfo) {
        String certificateName = System.getenv("JDEPLOY_MAC_DEVELOPER_CERTIFICATE_NAME");
        if (certificateName != null) return certificateName;
        Preferences node = getAppNode(appInfo);
        if (node == null) {
            return "";
        }
        return jDeployProperties.getProperty(appInfo.getNpmPackage()+"."+MAC_DEVELOPER_CERTIFICATE_NAME, node.get(MAC_DEVELOPER_CERTIFICATE_NAME, ""));
    }
    
    public static void setMacDeveloperCertificateName(AppInfo appInfo, String name) {
        if (!Objects.equals(name, getMacDeveloperCertificateName(appInfo))) {
            Preferences node = getAppNode(appInfo);
            if (node == null) {
                throw new IllegalStateException("Cannot set developer certificate name on app until it has a bundle ID");
            }
            node.put(MAC_DEVELOPER_CERTIFICATE_NAME, name);
            observable.setChanged();
        }
        
    }
    
    
    
    public static void setMacNotarizationPassword(String password) {
        if (!Objects.equals(password, getMacNotarizationPassword())) {
            root().put(MAC_NOTORIZATION_PASSWORD, password);
            observable.setChanged();
        }
        
    }
   
    public static void setMacDeveloperCertificateName(String certName) {
        if (!Objects.equals(certName, getMacDeveloperCertificateName())) {
            root().put(MAC_DEVELOPER_CERTIFICATE_NAME, certName);
            observable.setChanged();
        }
        
    }
    //private static CodeSignServerDescriptor codesignServer;
    //public static CodeSignServerDescriptor getCodeSignServerDescriptor() {
    //    return codesignServer;
    //}
    
    //public static void setCodeSignServerDescriptor(CodeSignServerDescriptor desc) {
    //    if (!Objects.equals(desc, codesignServer)) {
    //        codesignServer = desc;
    //        observable.setChanged();
    //    }
    //}
    
    public static void flush() throws IOException {
        try {
            root().flush();

        } catch (BackingStoreException ex) {
            throw new IOException("Failed to save settings", ex);
        }
    }

    // ==================== Windows Signing Settings ====================

    /**
     * Gets the Windows certificate path from environment or properties.
     * Priority: env JDEPLOY_WINDOWS_CERTIFICATE_PATH > properties file
     *
     * @return the certificate path, or empty string if not configured
     */
    public static String getWindowsCertificatePath() {
        String path = System.getenv("JDEPLOY_WINDOWS_CERTIFICATE_PATH");
        if (path != null && !path.isEmpty()) {
            return path;
        }
        return jDeployProperties.getProperty(WINDOWS_CERTIFICATE_PATH, "");
    }

    /**
     * Gets the Windows certificate password from environment or properties.
     * Priority: env JDEPLOY_WINDOWS_CERTIFICATE_PASSWORD > properties file
     *
     * @return the certificate password, or empty string if not configured
     */
    public static String getWindowsCertificatePassword() {
        String password = System.getenv("JDEPLOY_WINDOWS_CERTIFICATE_PASSWORD");
        if (password != null) {
            return password;
        }
        return jDeployProperties.getProperty(WINDOWS_CERTIFICATE_PASSWORD, "");
    }

    /**
     * Gets the Windows timestamp server URL from environment or properties.
     * Priority: env JDEPLOY_WINDOWS_TIMESTAMP_SERVER > properties file > default
     *
     * @return the timestamp server URL
     */
    public static String getWindowsTimestampServer() {
        String server = System.getenv("JDEPLOY_WINDOWS_TIMESTAMP_SERVER");
        if (server != null && !server.isEmpty()) {
            return server;
        }
        return jDeployProperties.getProperty(WINDOWS_TIMESTAMP_SERVER, DEFAULT_WINDOWS_TIMESTAMP_SERVER);
    }

    /**
     * Gets the Windows publisher name from environment or properties.
     * Priority: env JDEPLOY_WINDOWS_PUBLISHER_NAME > properties file
     *
     * @return the publisher name, or empty string if not configured
     */
    public static String getWindowsPublisherName() {
        String name = System.getenv("JDEPLOY_WINDOWS_PUBLISHER_NAME");
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return jDeployProperties.getProperty(WINDOWS_PUBLISHER_NAME, "");
    }

    /**
     * Checks if Windows signing is configured (certificate path is set).
     *
     * @return true if Windows signing credentials are available
     */
    public static boolean isWindowsSigningConfigured() {
        String certPath = getWindowsCertificatePath();
        return certPath != null && !certPath.isEmpty();
    }
}

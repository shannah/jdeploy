/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.jdeploy.appbundler;

import ca.weblite.jdeploy.app.AppInfo;
//import com.client4j.publisher.client.CodeSignServerDescriptor;

import java.io.IOException;
import java.util.Objects;
import java.util.Observable;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 *
 * @author shannah
 */
public class C4JPublisherSettings {
    private static class ObservableImpl extends Observable {

        @Override
        protected synchronized void setChanged() {
            super.setChanged(); 
        }
        
    }
    private static final ObservableImpl observable = new ObservableImpl();
    private static final String MAC_DEVELOPER_CERTIFICATE_NAME = "mac.developer-certificate-name";
    private static final String MAC_DEVELOPER_ID = "mac.developer-id";
    private static final String MAC_NOTORIZATION_PASSWORD = "mac.notarization-password";
    private static final String MAC_SIGNING_SETTINGS = "mac.signing-settings";
    private static final String MAC_APPS_NODE = "mac.apps";
    
    private static Preferences root;
    
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
        
        return root().get(MAC_DEVELOPER_CERTIFICATE_NAME, "");
    }
    
    public static String getMacDeveloperID() {
        return root().get(MAC_DEVELOPER_ID, "");
    }
    
    public static void setMacDeveloperID(String developerID) {
        if (!Objects.equals(getMacDeveloperID(), developerID)) {
            root().put(MAC_DEVELOPER_ID, developerID);
            observable.setChanged();
        }
    }
    
    public static String getMacNotarizationPassword() {
        
        return root().get(MAC_NOTORIZATION_PASSWORD, "");
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
        
        Preferences node = getAppNode(appInfo);
        if (node == null) {
            return "";
        }
        return node.get(MAC_DEVELOPER_ID, "");
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
        Preferences node = getAppNode(appInfo);
        if (node == null) {
            return "";
        }
        return node.get(MAC_NOTORIZATION_PASSWORD, "");
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
        Preferences node = getAppNode(appInfo);
        if (node == null) {
            return "";
        }
        return node.get(MAC_DEVELOPER_CERTIFICATE_NAME, "");
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
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.jdeploy.app;

import ca.weblite.tools.io.FileUtil;
import ca.weblite.tools.io.HTTPUtil;
import com.client4j.*;
import com.client4j.AppRepository.AppLink;
import com.client4j.AppRepository.Category;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.client4j.Client4J.APPINFO_NAME;
import static com.client4j.Client4J.C4J_HOME;

/**
 *
 * @author shannah
 */
public class Workspace {
    private final File baseDir;
    
    private Settings settings;
    // To keep track of when important things were last updated essentially.

    private final Object writeLock = new Object();


    
    public Workspace(File base) {
        this.baseDir = base;
        setup();
    }
    
    public File getBaseDir() {
        return baseDir;
    }




    /**
     * Applies the given settings to this workspace
     * @param settings 
     */
    public void applySettings(Settings settings) {
        this.settings = settings;
    }
    
    /**
     * Loads settings from the file system and applies them to the workspace.
     * @throws IOException
     * @throws SAXException 
     */
    private void installSettings() throws IOException, SAXException {
        Settings userSettings = loadUserSettings();
        Settings defaultSettings = loadDefaultSettings();
        userSettings.setParent(defaultSettings);
        applySettings(userSettings);
    }

    
    /**
     * Loads user settings. These are settings that the user has set themself.
     * @return
     * @throws IOException
     * @throws SAXException 
     */
    public Settings loadUserSettings() throws IOException, SAXException {
        synchronized (writeLock) {
            if (!getUserSettingsFile().exists()) {
                return new Settings();
            }
            try (FileInputStream fis = new FileInputStream(getUserSettingsFile())) {
                JCAXMLFile f = JCAXMLFile.load(this, getUserSettingsFile().toURL(), fis);
                return f.getSettings();
            } 
        }
    }
    

    
    /**
     * Loads the default settings.  These are default settings that are synchronized
     * with the central default settings file on Github.
     * @return
     * @throws IOException
     * @throws SAXException 
     */
    public Settings loadDefaultSettings() throws IOException, SAXException {
        synchronized (writeLock) {
            if (!getDefaultSettingsFile().exists()) {
                return new Settings();
            }
            try (FileInputStream fis = new FileInputStream(getDefaultSettingsFile())) {
                JCAXMLFile f = JCAXMLFile.load(this, getDefaultSettingsFile().toURL(), fis);
                return f.getSettings();
            } 
        }
    }
    
    private static final long ONE_DAY = 24 * 60 * 60 * 1000l;

    public File getLogDir() {
        return new File(baseDir, "log");
    }
    
    
    public File getRuntimesDir() {
        return new File(baseDir, "runtimes");
    }
    
    
    public File getEtcDir() {
        return new File(baseDir, "etc");
    }
    
    /**
     * Default settings as loaded from the internet.  These shouldn't be edited by the user
     * as they will be overwritten whenever the app checks for settings updates.
     * @return 
     */
    public File getDefaultSettingsFile() {
        return new File(getEtcDir(), "default-settings" + Client4J.APPINFO_EXTENSION);
    }



    /**
     * User settings file.  This is loaded to override settings in the default settings.
     * @return 
     */
    public File getUserSettingsFile() {
        return new File(getEtcDir(), "user-settings" + Client4J.APPINFO_EXTENSION);
    }
    
    /**
     * Gets directory containing shared libraries.
     * @return 
     */
    public File getLibsDir() {
        return new File(baseDir, "libs");
    }
    
    /**
     * Gets directory containing all apps.
     * @return 
     */
    public File getAppsDir() {
        return new File(baseDir, "apps");
    }
    
    /**
     * Gets shared temp directory that can be used for temp files.
     * @return 
     */
    public File getTempDir() {
        return new File(baseDir, "tmp");
    }
    
    /**
     * Gets the private root directory that won't be accessible to any of the apps.
     * @return 
     */
    public File getPrivateDir() {
        return new File(baseDir, "private");
    }
    
    /**
     * A mirror of the Apps dir but in a separate directory tree so that the app 
     * doesn't have any access to its own directory.  This is where we will store
     * things like policy files. 
     * @return 
     */
    public File getPrivateAppsDir() {
        return new File(getPrivateDir(), "apps");
    }
    
    private void setup() {
        getPrivateAppsDir().mkdirs();
        getAppsDir().mkdirs();
        getLibsDir().mkdirs();
        getRuntimesDir().mkdirs();
        getTempDir().mkdirs();
        getLogDir().mkdirs();
        try {
            installSettings();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

    }


    
    public Settings getSettings() {
        return settings;
    }



    
}

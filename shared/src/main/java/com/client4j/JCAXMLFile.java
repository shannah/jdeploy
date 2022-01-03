/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.app.Workspace;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

/**
 *
 * @author shannah
 */
public class JCAXMLFile {

    
    
    public CommonRuntimes getRuntimes() {
        return runtimes;
    }


    /**
     * @return the type
     */
    public JCAXMLFileType getType() {
        return type;
    }

    /**
     * @return the appInfo
     */
    public AppInfo getAppInfo() {
        return appInfo;
    }
    
    public Settings getSettings() {
        return settings;
    }

    /**
     * @return the repo
     */
    public AppRepository getRepo() {
        return repo;
    }
    private JCAXMLFileType type;
    private AppInfo appInfo;
    private CommonRuntimes runtimes;
    private CommonLibraries libraries;
    private AppRepository repo;
    private Settings settings;

    private URL remoteURL;
    private Workspace workspace;
    
    public void setRemoteURL(URL url) {
        this.remoteURL = url;
    }
    
    public URL getRemoteURL() {
        return remoteURL;
    }
    
    public JCAXMLFile(Workspace workspace, CommonRuntimes runtimes) {
        type = JCAXMLFileType.Runtimes;
        this.runtimes = runtimes;
        this.workspace = workspace;
        
    }
    
    public JCAXMLFile(Workspace workspace, CommonLibraries libraries) {
        type = JCAXMLFileType.Libraries;
        this.libraries = libraries;
        this.workspace = workspace;
    }
    
    public JCAXMLFile(Workspace workspace, AppInfo appInfo) {
        type = JCAXMLFileType.App;
        this.appInfo = appInfo;
        this.workspace = workspace;
    }
    
    public JCAXMLFile(Workspace workspace, AppRepository repo) {
        type = JCAXMLFileType.Repository;
        this.repo = repo;
        this.workspace = workspace;
    }
    
    public JCAXMLFile(Workspace workspace, Settings settings) {
        type = JCAXMLFileType.Settings;
        this.settings = settings;
        this.workspace = workspace;
    }
    
    
    public static JCAXMLFile load(Workspace workspace, URL url, InputStream input) throws IOException, SAXException {
        JCAXMLParser parser = new JCAXMLParser(workspace, url);
        
        JCAXMLFile out = parser.load(input);
        out.setRemoteURL(url);
        return out;
    }
    
    public void save(OutputStream output) throws IOException {
        JCAXMLWriter writer = new JCAXMLWriter(remoteURL);
        writer.write(this, output);
    }

    public CommonLibraries getLibraries() {
        return libraries;
    }
    
}

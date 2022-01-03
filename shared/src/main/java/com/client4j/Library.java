/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j;

import ca.weblite.jdeploy.app.Workspace;
import ca.weblite.tools.io.ArchiveUtil;
import ca.weblite.tools.io.HTTPUtil;
import ca.weblite.tools.io.JarUtil;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

import static ca.weblite.tools.io.MD5.getMd5;
//import net.sourceforge.prograde.sm.ProGradeJSM;


/**
 *
 * @author shannah
 */
public class Library {

    /**
     * @return the baseURL
     */
    public URL getBaseURL() {
        return baseURL;
    }

   
    
    /**
     * The URL to the main jar file
     */
    private final  URL baseURL;
    private final Workspace workspace;
    private boolean requireHttps;

    public Library(Workspace workspace, URL baseURL) {
        this.baseURL = baseURL;
        this.workspace = workspace;
        setup();
    }
    

    public String getHost() {
        if (getBaseURL().getHost() != null && !baseURL.getHost().isEmpty()) {
            return getBaseURL().getHost();
        }
        throw new IllegalStateException("App URL has not host set.  URL: "+getBaseURL()+", host: "+getBaseURL().getHost());
    }
    
    public int getPort() {
        if (getBaseURL().getPort() > 0) {
            return getBaseURL().getPort();
        }
        if ("https".equals(getBaseURL().getProtocol())) {
            return 443;
        }
        if ("http".equals(getBaseURL().getProtocol())) {
            return 80;
        }
        
        throw new IllegalStateException("No port specified for app url");
    }
    
    
    
    private File getHostDir() {
        return new File(workspace.getLibsDir(), getHost());
    }
   
    
    private File getPortDir() {
        return new File(getHostDir(), String.valueOf(getPort()));
    }
   
    
    public File getBaseDir() {
        String path = getBaseURL().getPath();
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("No archive name");
        } else {
            return new File(getPortDir(), getArchiveName()+"-"+getMd5(getBaseURL().toString()));
        }
    }
   
 
    
    public File getLibDir() {
        return new File(getBaseDir(), "lib");
    }
    
    
    
    public String getArchiveName() {
        return new File(getBaseURL().getPath()).getName();
    }
    
    public File getInstalledArchiveFile() {
        return new File(getLibDir(), getArchiveName());
    }
    
    
    private boolean checkArchiveExtension(String... exts) {
        for (String ext : exts) {
            if(getArchiveName().endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
    

    
    private String getDependentURLPath(String classPathEntry) {
        return new File(new File(getBaseURL().getPath()).getParentFile(), classPathEntry).getPath();
    }

    

    
    public void setup() {
        getLibDir().mkdirs();
    }
    



    


   
   
    public List<File> getClassPath() throws IOException {
        List<File> files = new ArrayList<File>();
        if (checkArchiveExtension(".jar")) {
            files.add(getInstalledArchiveFile());
            String[] cp = JarUtil.getClassPath(getInstalledArchiveFile());
            for (String part : cp) {
                files.add(new File(getInstalledArchiveFile().getParentFile(), part));
            }
        } else if (checkArchiveExtension(".zip")) {
            for (File f : findAllJars()) {
                files.add(f);
            }
        }
        return files;
        
    }
    
    private List<File> findAllJars() {
        return findAllJars(new ArrayList<File>(), getInstalledArchiveFile().getParentFile());
    }
    
    private List<File> findAllJars(List<File> out, File directory) {
        for (File f : directory.listFiles()) {
            if (f.isDirectory()) {
                findAllJars(out, f);
            } else if (f.isFile() && f.getName().endsWith(".jar")) {
                out.add(f);
            }
        }
        return out;
    }

    public boolean isInstalled() {
        return getInstalledArchiveFile().exists();
    }
   
    
}

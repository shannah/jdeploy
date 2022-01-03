/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j;


import ca.weblite.jdeploy.app.Workspace;
import ca.weblite.tools.io.ArchiveUtil;
import ca.weblite.tools.io.HTTPUtil;


import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static ca.weblite.tools.io.MD5.getMd5;


/**
 *
 * @author shannah
 */
public class JavaRuntime {

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
    private boolean fx;
    private boolean trusted;
    private static final Logger logger = Logger.getLogger(JavaRuntime.class.getName());
    
    public JavaRuntime(Workspace workspace, URL baseURL) {
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
        return new File(workspace.getRuntimesDir(), getHost());
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
    

    
    
    
    private String getDependentURLPath(String classPathEntry) {
        return new File(new File(getBaseURL().getPath()).getParentFile(), classPathEntry).getPath();
    }
    
    public URL getDependentURL(String classPathEntry) throws MalformedURLException {
        return new URL(getBaseURL().getProtocol(), getBaseURL().getHost(), getBaseURL().getPort(), getDependentURLPath(classPathEntry));
    }
    

    
    public void setup() {
        getLibDir().mkdirs();
    }
    
    
    
    
   
    
    

    

   

  
    private File javaHome;
    public File getJavaHome() {
        if (javaHome == null) {
            javaHome = findJavaHome(getInstalledArchiveFile().getParentFile());
        }
        return javaHome;
    }
    
    private static final String JAVA_EXT = System.getProperty("os.name").toLowerCase().contains("win") ? ".exe" : "";
    
    private File findJavaHome(File root) {
        System.out.println("Searching for java home in "+root+" dir="+root.isDirectory());
        boolean isDir = root.isDirectory();
        if (isDir && root.getName().equals("bin") && new File(root, "java"+JAVA_EXT).exists()) {
            System.out.println("Found ajva home at "+root.getParentFile());
            return root.getParentFile();
        }
        if (isDir) {
            for (File child : root.listFiles()) {
                File res = findJavaHome(child);
                if (res != null) {
                    return res;
                }
            }
            return null;
        }
        return null;
    }
    
    public File getBinDir() {
        return new File(getJavaHome(), "bin");
    }
    
    public File getJavaExe() {
        return new File(getBinDir(), "java"+JAVA_EXT);
    }
   

    public boolean isInstalled() {
        System.out.println("Java HOME: "+getJavaHome());
        System.out.println("Java EXE: "+getJavaExe());
        return getInstalledArchiveFile().exists() && getJavaExe().exists();
    }

    public List<File> findLibs() {
        return findLibs(new ArrayList<File>(), getBaseDir());
    }
    
    private List<File> findLibs(List<File> out, File root) {
        if (root.isDirectory()) {
            for (File child : root.listFiles()) {
                findLibs(out, child);
            }
        } else {
            String name = root.getName();
            
            if (name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib")) {
                out.add(root);
            }
        }
        return out;
        
    }
   
    
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.tools.git;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 *
 * @author shannah
 */
public class GitRemote {
    private String name;
    private String url;
    private String type;

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the url
     */
    public String getUrl() {
        return url;
    }
    
    public String getUrlWithoutGitSuffix() {
        if (url.endsWith(".git")) {
            return url.substring(0, url.lastIndexOf("."));
        }
        return url;
    }

    /**
     * @param url the url to set
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = type;
    }
    
    public URL getURLForFile(File file, String branch) throws IOException {
        if (branch == null) {
            branch = "master";
        }
        
        File directory = file;
        if (!file.isDirectory()) {
            directory = file.getParentFile();
        }
        GitUtil util = new GitUtil(directory);
        File gitDir = util.getGitDir().getCanonicalFile();
        File canonicalFile = file.getCanonicalFile();
        File repoRoot = gitDir.getParentFile();
        if (canonicalFile.toURI().toString().indexOf(repoRoot.toURI().toString()) != 0) {
            throw new IOException("Cannot get URL for File "+file+" because it is not inside a git repository");
        }
        String pathPortion = canonicalFile.toURI().toString().substring(repoRoot.toURI().toString().length());
        if (pathPortion.length() > 0 && pathPortion.charAt(0) == '/') {
            pathPortion = pathPortion.substring(1);
        }
        
        String url = getUrlWithoutGitSuffix()+ "/raw/"+branch+"/"+pathPortion;
        return new URL(url);
        
    }
}

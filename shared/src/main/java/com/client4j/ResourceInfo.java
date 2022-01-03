/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j;

import java.io.File;
import java.net.URL;

/**
 *
 * @author shannah
 */
public class ResourceInfo {
    private URL remoteURL;
    private File localFile;
    
    public ResourceInfo(URL remoteURL, File localFile) {
        this.remoteURL = remoteURL;
        this.localFile = localFile;
    }

    /**
     * @return the remoteURL
     */
    public URL getRemoteURL() {
        return remoteURL;
    }

    /**
     * @param remoteURL the remoteURL to set
     */
    public void setRemoteURL(URL remoteURL) {
        this.remoteURL = remoteURL;
    }

    /**
     * @return the localFile
     */
    public File getLocalFile() {
        return localFile;
    }

    /**
     * @param localFile the localFile to set
     */
    public void setLocalFile(File localFile) {
        this.localFile = localFile;
    }
}

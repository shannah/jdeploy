/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j;

import ca.weblite.jdeploy.app.Workspace;
import ca.weblite.tools.platform.Platform;

import java.net.URL;
import java.util.Objects;

/**
 *
 * @author shannah
 */
public abstract class Artifact {

    @Override
    public String toString() {
        return getClass().getSimpleName()+"{name: "+name+", version: "+version+", url: "+url+", platform: "+platform+", arch: "+arch+"}";
    }

    
    
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
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /**
     * @param version the version to set
     */
    public void setVersion(String version) {
        this.version = version;
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
        this.url = url;
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
        this.platform = platform;
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
        this.arch = arch;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Artifact) {
            Artifact rt = (Artifact) obj;
            return Objects.equals(arch, rt.arch) && Objects.equals(platform, rt.platform)
                    && Objects.equals(version, rt.version) && Objects.equals(url, rt.url) &&
                    Objects.equals(name, rt.name);

        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 89 * hash + Objects.hashCode(this.version);
        hash = 89 * hash + Objects.hashCode(this.url);
        hash = 89 * hash + Objects.hashCode(this.platform);
        hash = 89 * hash + Objects.hashCode(this.arch);
        hash = 89 * hash + Objects.hashCode(this.name);
        return hash;
    }

    private String name;
    private String version;
    private URL url;
    private String platform;
    private String arch;

    public boolean isSupported() {
        return new Platform(getPlatform(), getArch()).matchesSystem();
    }
    
    public abstract boolean isInstalled(Workspace workspace);
}

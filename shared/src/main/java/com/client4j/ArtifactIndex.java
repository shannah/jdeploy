/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j;

import ca.weblite.jdeploy.app.Workspace;
import ca.weblite.tools.platform.Version;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author shannah
 */
public class ArtifactIndex<T extends Artifact> implements Iterable<T>{
    protected final Workspace workspace;
    private List<T> registeredArtifacts = new ArrayList<>();
    
    public static class ArtifactQuery {
        
        public ArtifactQuery(Workspace workspace, String name, String versionExpression, boolean installedOnly) {
            this.workspace = workspace;
            this.name = name;
            this.versionExpression = versionExpression;
            this.installedOnly = installedOnly;
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
         * @return the versionExpression
         */
        public String getVersionExpression() {
            return versionExpression;
        }

        /**
         * @param versionExpression the versionExpression to set
         */
        public void setVersionExpression(String versionExpression) {
            this.versionExpression = versionExpression;
        }

        /**
         * @return the installedOnly
         */
        public boolean isInstalledOnly() {
            return installedOnly;
        }

        /**
         * @param installedOnly the installedOnly to set
         */
        public void setInstalledOnly(boolean installedOnly) {
            this.installedOnly = installedOnly;
        }
        
        public boolean isMatch(Artifact rt) {
            if (name != null && !name.equals(rt.getName())) {
                return false;
            }
            if (!rt.isSupported()) {
                return false;
            }
            if (installedOnly && !rt.isInstalled(workspace)) {
                return false;
            }
            Version v = Version.parse(rt.getVersion());
            if (!v.matches(versionExpression)) {
                return false;
            }
            return true;
                
        }
        
        public void updateBestMatch(Artifact rt) {
            if (isMatch(rt)) {
                Version v = Version.parse(rt.getVersion());
                if (bestMatchVersion == null || v.compareTo(bestMatchVersion) > 0) {
                    bestMatch = rt;
                    bestMatchVersion = v;
                }
            }
        }
        
        public Artifact getBestMatch() {
            return bestMatch;
        }
        
        private String name;
        private String versionExpression;
        private boolean installedOnly;
        private Workspace workspace;
        private Version bestMatchVersion;
        private Artifact bestMatch;
        
        
        
    }
    
    
    public ArtifactIndex(Workspace workspace) {
        this.workspace = workspace;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()+"{"+registeredArtifacts+"}";
    }
    
    
    
    @Override
    public Iterator<T> iterator() {
        return registeredArtifacts.iterator();
    }
    
    
    
    /**
     * Finds the best matched runtime given the given version expression.
     * 
     * @param versionExpression Either a version number (e.g. 1.1.0), a version range (e.g. >= 1.0.1),
     * or an optimistic range (e.g. ~> 1.0.1)
     * @param installedOnly If true, then this only considers runtimes that are installed.  If false,
     * then it will return the best match whether or not it is installed.
     * @return The best matching runtime given the version expression.
     */
    public T findBestMatch(String name, String versionExpression, boolean installedOnly) {
        return findBestMatch(new ArtifactQuery(workspace, name, versionExpression, installedOnly));
    }
    
    public T findBestMatch(ArtifactQuery query) {
        if (registeredArtifacts != null) {
            for (T rt : registeredArtifacts) {
                query.updateBestMatch(rt);
            }       
        }
        return (T)query.getBestMatch();
    }
    
     /**
     * Find the best runtime matching the version expression.  This gives priority
     * to runtimes that are installed (and match the version expression).  If no 
     * installed runtimes match the expression, then it will return the best match
     * from AdoptOpenJDK.
     * @param versionExpression
     * @return Matching runtime or null if no matches.
     */
    public T findBestMatch(String name, String versionExpression) {
        
        T out = findBestMatch(name, versionExpression, true);
        if (out != null) {
            return out;
        }
        return findBestMatch(name, versionExpression, false);
    }
    
    
    public void register(T artifact) {
        registeredArtifacts.add(artifact);
    }
    
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ArtifactIndex) {
            ArtifactIndex rts = (ArtifactIndex)obj;
            return Objects.equals(registeredArtifacts, rts.registeredArtifacts) &&
                    Objects.equals(workspace.getBaseDir(), rts.workspace.getBaseDir());
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + Objects.hashCode(this.workspace);
        hash = 29 * hash + Objects.hashCode(this.registeredArtifacts);
        return hash;
    }
    
}

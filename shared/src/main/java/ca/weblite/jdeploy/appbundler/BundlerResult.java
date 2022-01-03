/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.jdeploy.appbundler;

import java.io.File;
import java.util.*;

/**
 *
 * @author shannah
 */
public class BundlerResult {
    private final String type;
    private Map<String,BundlerResult> bundles = new HashMap<>();
    private File outputFile;
    public List<File> releaseFiles = new ArrayList<>();

    public BundlerResult(String type) {
        this.type = type;
    }
    
    /**
     * @return the outputFile
     */
    public File getOutputFile() {
        return outputFile;
    }
    
    public void addReleaseFile(File f) {
        releaseFiles.add(f);
    }
    
    public void addReleaseFile(String type, File f) {
        getResultForType(type, true).addReleaseFile(f);
    }
    
    public File getReleaseFile(String ext) {
        for (File f : releaseFiles) {
            if (f.getName().endsWith(ext)) {
                return f;
            }
        }
        return null;
    }
    
    public File getReleaseFile(String type, String ext) {
        BundlerResult r = getResultForType(type, false);
        if (r == null) {
            return null;
            
        }
        return r.getReleaseFile(ext);
    }
    
    public BundlerResult getResultForType(String type, boolean init) {
        if (Objects.equals(type, this.type)) {
            return this;
        } else {
            
            BundlerResult out = bundles.get(type);
            if (out == null && init) {
                out = new BundlerResult(type);
                bundles.put(type, out);
            }
            return out;
        }
    }
    
    public void setResultForType(String type, BundlerResult res) {
        bundles.put(type, res);
    }
    
    public File getOutputFile(String type) {
        if (Objects.equals(type, this.type)) {
            return outputFile;
        } else {
            BundlerResult res = getResultForType(type, false);
            if (res == null) {
                return null;
            }
            return res.outputFile;
            
        }
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }
    
    /**
     * @param outputFile the outputFile to set
     */
    public void setOutputFile(String type, File outputFile) {
        getResultForType(type, true).setOutputFile(outputFile);
    }
    
}

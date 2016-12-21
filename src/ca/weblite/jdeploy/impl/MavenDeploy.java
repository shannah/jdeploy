/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.jdeploy.impl;

import ca.weblite.jdeploy.JDeploy;
import com.codename1.processing.Result;
import com.codename1.xml.Element;
import com.codename1.xml.XMLParser;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author shannah
 */
public class MavenDeploy extends JDeploy {
    
    final File pom;
    private Element pomRoot;
    
    public MavenDeploy(File directory, File pom) {
        super(directory);
        this.pom = pom;
    }
    
    public File getPom() {
        return pom;
    }
    
    public Element getPomRoot() {
        if (pomRoot == null) {
            try {
                String pomStr = FileUtils.readFileToString(pom, "UTF-8");
                XMLParser p = new XMLParser();
                pomRoot = p.parse(new StringReader(pomStr));
            } catch (IOException ex) {
                Logger.getLogger(MavenDeploy.class.getName()).log(Level.SEVERE, null, ex);
                throw new RuntimeException(ex);
            }
        }
        return pomRoot;
    }

    @Override
    public void initPackageJson() {
        updateConfig();
    }

    @Override
    public void updatePackageJson() {
        updateConfig();
    }
    
    
    private void updateConfig() {
        Map m = getPackageJsonMap();
        Map jm = (Map)m.get("jdeploy");
        if (jm == null) {
            m.put("jdeploy", new HashMap());
            jm = (Map)m.get("jdeploy");
        }
        Result pomr = Result.fromContent(getPomRoot());
        String packaging = pomr.getAsString("project/packaging");
        String artifactId = pomr.getAsString("project/artifactId");
        String version = pomr.getAsString("project/version");
        
        if ("war".equals(packaging)) {
            String warName = artifactId+"-"+version;
            if (pomr.get("properties/jdeploy.war") != null) {
                warName = pomr.getAsString("properties/jdeploy.war");
            } 
            setWar("target" + File.separator + warName);
            
            if (pomr.get("properties/jdeploy.port") != null) {
                setPort(pomr.getAsInteger("properties/jdeploy.port"));
            }
        } else if ("jar".equals(packaging)) {
            
        }
        
        
    }
    
    
    
}

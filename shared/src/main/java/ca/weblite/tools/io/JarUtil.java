/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.tools.io;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 *
 * @author shannah
 */
public class JarUtil {
    public static String getSecurityPolicyFileContents(File jarFile) throws IOException {
        JarFile jar = new JarFile(jarFile);
        ZipEntry policyFile = jar.getEntry("META-INF/client4j.policy");
        if (policyFile == null) {
            return null;
        }
        return IOUtil.readToString(jar.getInputStream(policyFile));
    }
    
    public static String[] getClassPath(File jarFile) throws IOException {
        JarFile jar = new JarFile(jarFile);
        String classPath = jar.getManifest().getMainAttributes().getValue("Class-Path");
        if (classPath == null) {
            return new String[0];
        }
        return classPath.split(" ");
    }
    
    
    
    
}

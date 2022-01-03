/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.tools.platform;

import java.util.Objects;

/**
 *
 * @author shannah
 */
public class Platform {
    private String os;
    private String arch;
    
    public Platform(String os, String arch) {
        this.os = os;
        this.arch = arch;
    }
    
    public static Platform getSystemPlatform() {
        return new Platform(System.getProperty("os.name"), System.getProperty("os.arch"));
    }
    
    public boolean isWindows() {
        return os.toLowerCase().contains("win");
    }
    
    public boolean isLinux() {
        return os.toLowerCase().contains("linux");
    }
    
    public boolean isMac() {
        return os.toLowerCase().contains("mac");
    }
    
    public boolean matchesSystem() {
        return matches(System.getProperty("os.name") + " "+ System.getProperty("os.arch"));
    }
    
    public boolean matches(String expression) {
        return matchesOS(expression) && matchesArch(expression);
    }
    
    public boolean matchesOS(String expression) {
        if (expression == null || expression.isEmpty() || expression.equals("*") || os == null) {
            return true;
        }
        if (isMac()) {
            return expression.toLowerCase().contains("mac");
        }
        if (isWindows()) {
            return expression.toLowerCase().contains("win");
        }
        if (isLinux()) {
            return expression.toLowerCase().contains("linux");
        }
        
        return false;
    }
    
    public boolean is64Bit() {
        return arch.contains("64");
    }
    
    public boolean matchesArch(String expression) {
        if (expression == null || expression.isEmpty() || expression.equals("*") || arch == null) {
            return true;
        }
        if (is64Bit()) {
            return expression.contains("64");
        } else {
            return !expression.contains("64");
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Platform) {
            Platform p = (Platform)obj;
            return Objects.equals(os, p.os) && Objects.equals(arch, p.arch);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 89 * hash + Objects.hashCode(this.os);
        hash = 89 * hash + Objects.hashCode(this.arch);
        return hash;
    }
    
    
}

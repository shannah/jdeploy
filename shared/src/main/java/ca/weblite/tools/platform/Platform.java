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

    private String getVersionString() {
        int lastSpacePos = os.lastIndexOf(' ');
        if (lastSpacePos > 0) {
            return os.substring(lastSpacePos+1);
        }
        return "";
    }

    /**
     * Returns a version int for the OS.
     * This takes into account major and minor version, with major multiplied by 1000.
     * So 8.1 = 8100
     *
     * @return
     */
    private int getVersionInt() {
        String versionString = getVersionString();
        StringBuilder leadingDigits = new StringBuilder();
        char[] chars = versionString.toCharArray();
        int versionInt = 0;
        int multiplier = 1000;
        for (int i=0; i<chars.length; i++) {
            char c = chars[i];
            if (Character.isDigit(c)) {
                leadingDigits.append(c);
            } else if (c == '.') {
                if (leadingDigits.length() > 0) {
                    versionInt += multiplier * Integer.parseInt(leadingDigits.toString());
                    leadingDigits.setLength(0);
                } else {
                    return versionInt;
                }
                if (multiplier >= 1000) {
                    multiplier /= 1000;
                } else {
                    return versionInt;
                }
            } else {
                break;
            }
        }
        if (leadingDigits.length() > 0) {
            versionInt += multiplier *  Integer.parseInt(leadingDigits.toString());
        }
        return versionInt;
    }

    /**
     *  Returns true if system is Windows 10 or higher.
     *
     * @return
     */
    public boolean isWindows10OrHigher() {
        if (isWindows()) {
            // Reportedly some versions of Java report 8.1 for windows 10 version.
            return getVersionInt() >= 8100;
        }
        return false;
    }

    public boolean isWindows8OrHigher() {
        if (isWindows()) {
            // Reportedly some versions of Java report 8.1 for windows 10 version.
            return getVersionInt() >= 8000;
        }
        return false;
    }
    
    
}

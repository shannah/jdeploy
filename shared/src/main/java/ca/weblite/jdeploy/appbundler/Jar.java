/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.weblite.jdeploy.appbundler;

import java.io.File;

/**
 *
 * @author joshmarinacci
 */
public class Jar {
    private final String name;
    private boolean main;
    private String mainClass;
    private File file;
    private String os;

    public Jar(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }
    void setMain(boolean main) {
        this.main = main;
    }

    void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }

    public boolean isMain() {
        return this.main;
    }

    void setFile(File file) {
        this.file = file;
    }

    public File getFile() {
        return this.file;
    }

    String getMainClass() {
        return this.mainClass;
    }

    public boolean isOSSpecified() {
        return os!=null;
    }

    public boolean matchesOS(String string) {
        if(os.equals(string.toLowerCase())) return true;
        if(os.startsWith("!")) {
            String nos = os.substring(1);
            if(!nos.equals(string.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    void setOS(String os) {
        this.os = os.trim().toLowerCase();
    }

    public String getOS() {
        return this.os;
    }

}

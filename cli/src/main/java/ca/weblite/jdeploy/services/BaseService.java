package ca.weblite.jdeploy.services;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class BaseService {
    protected JSONObject packageJSON;
    protected File packageJSONFile;
    protected Map<String,String> args = new LinkedHashMap<>();
    protected Set<String> flags = new LinkedHashSet<>();

    public BaseService(File packageJSONFile, JSONObject packageJSON) throws IOException {
        this.packageJSONFile = packageJSONFile;
        if (!packageJSONFile.exists()) throw new IOException("Non-existent package.json file: "+packageJSONFile);
        if (packageJSON == null) {

            packageJSON = new JSONObject(FileUtils.readFileToString(packageJSONFile, StandardCharsets.UTF_8));
        }
        this.packageJSON = packageJSON;
    }

    public void setArgs(String[] args) {
        this.args.clear();
        for (int i=0; i<args.length; i+=2) {
            if (args.length <= i+1) {
                this.flags.add(args[i]);
            } else if (args[i+1].startsWith("--")) {
                this.flags.add(args[i]);
                i--;
            } else {
                this.args.put(args[i], args[i + 1]);
            }
        }

    }

    protected Iterable<File> getEnvironmentPath() {
        ArrayList<File> out = new ArrayList<>();
        if (System.getenv("JAVA_HOME") != null) {
            File javaHome = new File(System.getenv("JAVA_HOME"));
            File bin = new File(javaHome, "bin");
            if (bin.exists()) {
                out.add(bin);
            }
            if (javaHome.getName().equals("jre")) {
                javaHome = javaHome.getParentFile();
                bin = new File(javaHome, "bin");
                if (bin.exists()) {
                    out.add(bin);
                }
            }
        }
        if (System.getProperty("java.home") != null) {
            File javaHome = new File(System.getProperty("java.home"));
            File bin = new File(javaHome, "bin");
            if (bin.exists()) {
                out.add(bin);
            }
            if (javaHome.getName().equals("jre")) {
                javaHome = javaHome.getParentFile();
                bin = new File(javaHome, "bin");
                if (bin.exists()) {
                    out.add(bin);
                }
            }
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String p : path.split(File.pathSeparator)) {
                File f = new File(p);
                if (f.exists()) {
                    out.add(f);
                }
            }
        }

        // Now just do guessing
        File jvmDir = new File("/Library/Java/JavaVirtualMachines");
        if (jvmDir.exists()) {
            for (File jdk : jvmDir.listFiles()) {
                File bin = new File(jdk, "Contents" + File.separator + "Home" + File.separator + "bin");
                if (bin.exists()) {
                    out.add(bin);
                }
            }
        }
        return out;

    }

    protected String getArg(String key, String defaultValue) {
        if (args.containsKey(key)) {
            return args.get(key);
        }
        return defaultValue;
    }

    protected File getProjectDirectory() throws IOException {
        return packageJSONFile.getCanonicalFile().getParentFile();
    }

    protected File getJDeployBundleDirectory() throws IOException {
        return new File(getProjectDirectory(), "jdeploy-bundle");
    }

    protected File getMainJarFile() throws IOException {
        return new File(getJDeployBundleDirectory(), new File(getJDeployObject().getString("jar")).getName());
    }

    protected File getDestDirectory() throws IOException {
        File dest = new File(getProjectDirectory(), "jdeploy");
        dest.mkdirs();
        return dest;
    }

    protected JSONObject getJDeployObject() {
        return packageJSON.getJSONObject("jdeploy");
    }

    protected String getDescription() {
        return packageJSON.has("description") ? packageJSON.getString("description") : "";
    }

    protected String getAppVersion() {
        return packageJSON.getString("version");
    }

    protected File getIconFile() throws IOException {
        return new File(getProjectDirectory(), "icon.png");
    }

    protected String getAppName() {
        return getJDeployObject().has("title") ? getJDeployObject().getString("title") :
                packageJSON.getString("name");
    }
}

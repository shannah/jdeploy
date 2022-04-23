/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.jdeploy;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.appbundler.Bundler;
import ca.weblite.jdeploy.cli.controllers.JPackageController;
import ca.weblite.jdeploy.gui.JDeployMainMenu;
import ca.weblite.jdeploy.gui.JDeployProjectEditor;
import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.services.DeveloperIdentityKeyStore;
import ca.weblite.tools.io.*;
import com.codename1.io.JSONParser;
import com.codename1.processing.Result;


import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import java.util.*;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;


import org.apache.commons.io.FileUtils;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.w3c.dom.Document;

import javax.swing.*;

import static ca.weblite.jdeploy.PathUtil.fromNativePath;
import static ca.weblite.jdeploy.PathUtil.toNativePath;


/**
 *
 * @author shannah
 */
public class JDeploy {
    static {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.eawt.quitStrategy", "CLOSE_ALL_WINDOWS");
    }
    private boolean alwaysClean = !Boolean.getBoolean("jdeploy.doNotClean");
    private boolean alwaysPackageOnPublish = !Boolean.getBoolean("jdeploy.doNotPackage");
    private boolean doNotStripJavaFXFiles;
    private final File directory;
    private File packageJsonFile;
    private Map packageJsonMap;
    private Result packageJsonResult;
    private PrintStream out = System.out;
    private PrintStream err = System.err;

    public static String JDEPLOY_REGISTRY = "https://www.jdeploy.com/";
    static {
        if (System.getenv("JDEPLOY_REGISTRY_URL") != null) {
            JDEPLOY_REGISTRY = System.getenv("JDEPLOY_REGISTRY_URL");
            if (!JDEPLOY_REGISTRY.startsWith("http://") && !JDEPLOY_REGISTRY.startsWith("https://")) {
                throw new RuntimeException("INVALID_JDEPLOY_REGISTRY_URL environment variable.  Expecting URL but found "+JDEPLOY_REGISTRY);
            }
            if (!JDEPLOY_REGISTRY.endsWith("/")) {
                JDEPLOY_REGISTRY += "/";
            }
        }
    }

    public PrintStream getOut() {
        return out;
    }

    public PrintStream getErr() {
        return err;
    }
    
    public class CopyRule {
        String dir;
        List<String> includes;
        List<String> excludes;

        private final String sanitizeDirPath(String dir) {
            if (new File(dir).isAbsolute()) {
                try {
                    //System.out.println("dir="+new File(dir).getCanonicalPath()+"; directory="+directory.getCanonicalPath());
                    dir = new File(dir).getCanonicalPath().substring(directory.getCanonicalPath().length());
                } catch (Exception ex) {
                    dir = dir.substring(directory.getAbsolutePath().length());
                }
            }
            if (dir.startsWith("/") || dir.startsWith("\\")) {
                dir = dir.substring(1);
            }
            if (dir.isEmpty()) dir = ".";

            return dir.replace("\\", "/");
        }
        
        public CopyRule(String dir, List<String> includes, List<String> excludes) {
            this.dir = sanitizeDirPath(dir);

            this.includes = includes;
            this.excludes = excludes;
        }
        
        public CopyRule(String dir, String includes, String excludes) {
            this.dir = sanitizeDirPath(dir);

            this.includes = includes == null ? null : Arrays.asList(includes.split(","));
            this.excludes = excludes == null ? null : Arrays.asList(excludes.split(","));
        }
        
        public String toString() {
            return "CopyRule{dir="+dir+", includes="+includes+", excludes="+excludes+"}";
        }
        
        public void copyTo(File destDirectory) throws IOException {
            //System.out.println("Executing copy rule "+this);
            final File srcDir = new File(dir);
            if (!srcDir.exists()) {
                throw new IOException("Source directory of copy rule does not exist: "+srcDir);
            }
            
            if (!destDirectory.exists()) {
                throw new IOException("Destination directory of copy rule does not exist: "+destDirectory);
            }
            
            if (srcDir.equals(destDirectory)) {
                err.println("Copy rule has same srcDir and destDir.  Not copying: "+srcDir);
                return;
            }
            final Set<String> includedDirectories = new HashSet<String>();
            
            FileUtils.copyDirectory(srcDir, destDirectory, new FileFilter() {

                @Override
                public boolean accept(File pathname) {
                    if (pathname.isDirectory()) {
                        for (File child : pathname.listFiles()) {
                            if (this.accept(child)) {
                                return true;
                            }
                        }
                    }
                    File parent = pathname.getParentFile();
                    if (parent != null && includedDirectories.contains(parent.getPath())) {
                        if (pathname.isDirectory()) {
                            includedDirectories.add(pathname.getPath());
                        }
                        return true;
                        
                    }
                    if (excludes != null) {
                        for (String pattern : excludes) {
                            PathMatcher matcher = srcDir.toPath().getFileSystem().getPathMatcher("glob:"+dir+"/"+pattern.replace("\\", "/"));
                            if (matcher.matches(pathname.toPath())) {
                                return false;
                            }
                        }
                    }
                    
                    if (includes != null) {
                        for (String pattern : includes) {
                            
                            PathMatcher matcher = srcDir.toPath().getFileSystem().getPathMatcher("glob:"+dir+"/"+pattern.replace("\\", "/"));
                            if (matcher.matches(pathname.toPath())) {
                                if (pathname.isDirectory()) {
                                    includedDirectories.add(pathname.getPath());
                                }
                                return true;
                            }
                        }
                        //System.out.println(pathname+" does not match any patterns.");
                        return false;
                    } else {
                        if (pathname.isDirectory()) {
                            includedDirectories.add(pathname.getPath());
                        }
                        return true;
                    }
                }
                
            });
        }
    }
    
    public JDeploy(File directory, boolean exitOnFail) {
        this.directory = directory;
        this.exitOnFail = exitOnFail;
    }

    public JDeploy(File directory) {
        this(directory, true);
    }



    public PrintStream setOut(PrintStream out) {
        PrintStream old = this.out;
        this.out = out;
        return out;
    }

    public PrintStream setErr(PrintStream err) {
        PrintStream old = this.err;
        this.err = err;
        return old;
    }

    public File getDirectory() {
        return directory;
    }
    
    public File getPackageJsonFile() {
        if (packageJsonFile == null) {
            packageJsonFile = new File(directory, "package.json");
        }
        return packageJsonFile;
    }
    
    private File f() {
        return getPackageJsonFile();
    }
    
    public Map getPackageJsonMap()  {
        if (packageJsonMap == null) {
            try {
                JSONParser p = new JSONParser();
                packageJsonMap = (Map)p.parseJSON(new StringReader(FileUtils.readFileToString(getPackageJsonFile(), "UTF-8")));
            } catch (IOException ex) {
                Logger.getLogger(JDeploy.class.getName()).log(Level.SEVERE, null, ex);
                throw new RuntimeException(ex);
            }
        }
        return packageJsonMap;
    }
    
    private Map m() {
        return getPackageJsonMap();
    }
    
    private Map mj() {
        if (!m().containsKey("jdeploy")) {
            m().put("jdeploy", new HashMap());
        }
        return (Map)m().get("jdeploy");
    }
    
    private Result rj() {
        return Result.fromContent(mj());
    }

    private Set<String> bundles() {
        Map m = mj();
        HashSet<String> out = new HashSet<String>();
        if (m.containsKey("bundles")) {
            List bundlesList = (List) m.get("bundles");
            for (Object o : bundlesList) {
                out.add((String)o);
            }
        }
        return out;
    }

    private Set<String> installers() {
        Map m = mj();
        HashSet<String> out = new HashSet<String>();
        if (m.containsKey("installers")) {
            List installersList = (List) m.get("installers");
            for (Object o : installersList) {
                out.add((String)o);
            }
        }
        return out;
    }
    
    public Result getPackageJsonResult() {
        if (packageJsonResult == null) {
            packageJsonResult = Result.fromContent(getPackageJsonMap());
        }
        return packageJsonResult;
    }
    
    private Result r() {
        return getPackageJsonResult();
    }
    
    public void savePackageJson() throws IOException {
        FileUtils.writeStringToFile(getPackageJsonFile(), getPackageJsonResult().toString(), "UTF-8");
        packageJsonMap = null;
        packageJsonResult = null;
    }
    
    public void set(String property, String value) {
        mj().put(property, value);
    }
    
    public void set(String property, int value) {
        mj().put(property, value);
    }
    
    public void set(String property, List value) {
        mj().put(property, value);
    }
    
    public String getString(String property, String defaultValue) {
        if (mj().containsKey(property)) {
            return r().getAsString("jdeploy/"+property);
        }
        return defaultValue;
    }
    
    public int getInt(String property, int defaultValue) {
        if (mj().containsKey(property)) {
            return r().getAsInteger("jdeploy/"+property);
        }
        return defaultValue;
    }
    
    public List getList(String property, boolean defaultEmptyList) {
        if (mj().containsKey(property)) {
            return r().getAsArray("jdeploy/"+property);
        }
        return defaultEmptyList ? new ArrayList() : null;
    }
    
    public int getPort(int defaultValue) {
        return getInt("port", defaultValue);
    }
    
    public int getJavaVersion(int defaultValue) {
        return getInt("javaVersion", defaultValue);
    }
    
    public void setPort(int port) {
        set("port", port);
    }
    
    
    public String getWar(String defaultVal) {
        return getString("war", defaultVal);
    }
    
    public void setWar(String war) {
        set("war", war);
    }
    
    
    public String getJar(String defaultVal) {
        return getString("jar", defaultVal);
    }
    
    public void setJar(String jar) {
        set("jar", jar);
    }
    
    public String getClassPath(String defaultVal) {
        return getString("classPath", defaultVal);
    }
    
    public void setClassPath(String cp) {
        set("classPath", cp);
    }
    
    public String getMainClass(String defaultVal) {
        return getString("mainClass", defaultVal);
    }
    
    public void setMainClass(String mainClass) {
        set("mainClass", mainClass);
    }
    
    public List<CopyRule> getFiles() {
        List files = getList("files", true);
        List<CopyRule> out = new ArrayList<CopyRule>();
        
        for (Object o : files) {
            if (o instanceof Map) {
                Map m = (Map)o;
            
                String dir = (String)m.get("dir");
                ArrayList<String> incs = null;
                if (m.containsKey("includes")) {
                    Object i = m.get("includes");
                    incs = new ArrayList<String>();
                    if (i instanceof List) {
                        incs.addAll((List<String>)i);
                    } else if (i instanceof String){
                        incs.addAll(Arrays.asList(((String)i).split(",")));
                    }
                }
                ArrayList<String> excs = null;
                if (m.containsKey("excludes")) {
                    Object i = m.get("excludes");
                    excs = new ArrayList<String>();
                    if (i instanceof List) {
                        excs.addAll((List<String>)i);
                    } else if (i instanceof String){
                        excs.addAll(Arrays.asList(((String)i).split(",")));
                    }
                }
                out.add(new CopyRule(dir, incs, excs));
            } else if (o instanceof String) {
                out.add(new CopyRule((String)o, (String)null, (String)null));
            }  
        }
        return out;
    }
    
    
    public void setPreCopyScript(String preScript) {
        set("preCopyScript", preScript);
    }
    
    public String getPreCopyScript(String defaultValue) {
        return getString("preCopyScript", defaultValue);
    }
    
    public void setPostCopyScript(String postScript) {
        set("postCopyScript", postScript);
    }
    
    public String getPostCopyScript(String defaultValue) {
        return getString("postCopyScript", defaultValue);
    }
    
    public void setPreCopyTarget(String preScript) {
        set("preCopyTarget", preScript);
    }
    
    public String getPreCopyTarget(String defaultValue) {
        return getString("preCopyTarget", defaultValue);
    }
    
    public void setPostCopyTarget(String postScript) {
        set("postCopyTarget", postScript);
    }
    
    public String getPostCopyTarget(String defaultValue) {
        return getString("postCopyTarget", defaultValue);
    }
    
    public void setAntFile(String antFile) {
        set("antFile", antFile);
    }
    
    public String getAntFile(String defaultVal) {
        return getString("antFile", defaultVal);
    }
    
    public String getBinDir() {
        return "jdeploy-bundle";
    }
    
    public String getBinName() {
        if (m().containsKey("bin")) {
            Map<String, String> bins = (Map<String,String>)m().get("bin");
            for (String binName : bins.keySet()) {
                if ((getBinDir()+"/jdeploy.js").equals(bins.get(binName))) {
                    return binName;
                }
            }
        }
        return null;
    }
    
   
    
    private int runScript(String script) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.command(script);
            Process p = pb.start();
            return p.waitFor();
        } catch (InterruptedException ex) {
            Logger.getLogger(JDeploy.class.getName()).log(Level.SEVERE, null, ex);
            return 1;
        }
    }
    
    private int runAntTask(String antFile, String target) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.command("ant", "-f", antFile, target);
            Process p = pb.start();
            return p.waitFor();
            
        } catch (InterruptedException ex) {
            Logger.getLogger(JDeploy.class.getName()).log(Level.SEVERE, null, ex);
            return 1;
        }
    }
    
    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    public File findJarFile() {
        if (getJar(null) != null) {
            File jarFile = new File(directory, toNativePath(getJar(null)));

            if (!jarFile.exists() && jarFile.getParentFile() == null) {
                return null;
            }
            if (!jarFile.exists() && jarFile.getParentFile().exists()) {
                // Jar file might be a glob
                try {
                    PathMatcher matcher = jarFile.getParentFile().toPath().getFileSystem().getPathMatcher(jarFile.getName());
                    for (File f : jarFile.getParentFile().listFiles()) {
                        if (matcher.matches(f.toPath())) {
                            jarFile = f;
                            break;
                        }
                    }
                } catch (IllegalArgumentException ex) {
                    // just eat this
                }
                
                if (!jarFile.exists()) {
                    return null;
                }
            }
            return jarFile;
        }
        return null;
    }
    
    public File findWarFile() {
        if (getWar(null) != null) {
            File warFile = new File(getWar(null));
            if (!warFile.exists() || warFile.getParentFile() == null) {
                return null;
            }
            if (!warFile.exists() && warFile.getParentFile().exists()) {
                // Jar file might be a glob
                PathMatcher matcher = warFile.getParentFile().toPath().getFileSystem().getPathMatcher(warFile.getName());
                for (File f : warFile.getParentFile().listFiles()) {
                    if (matcher.matches(f.toPath())) {
                        warFile = f;
                        break;
                    }
                }
                
                if (!warFile.exists()) {
                    return null;
                }
                
                
            }
            return warFile;
        }
        return null;
    }
    
    
    
    private String[] findClassPath(File jarFile) throws IOException {
        Manifest m = new JarFile(jarFile).getManifest();
        //System.out.println(m.getEntries());
        String cp = m.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
        //System.out.println("Class path is "+cp);
        if (cp != null) {
            return cp.split(" ");
        } else {
            return new String[0];
        }
    }
    
    public void loadDefaults() throws IOException {
       
    }
    
    private File[] findJarCandidates() throws IOException {
        File[] jars = findCandidates(directory.toPath().getFileSystem().getPathMatcher("glob:**/*.jar"));
        List<File> out = new ArrayList<File>();
        // We only want executable jars
        for (File f : jars) {
            Manifest m = new JarFile(f).getManifest();
            //System.out.println(m.getEntries());
            if (m != null) {
                Attributes atts = m.getMainAttributes();
                if (atts.containsKey(Attributes.Name.MAIN_CLASS)) {
                    //executable jar
                    out.add(f);
                }
            }
        }
        return out.toArray(new File[out.size()]);
    }
    
    private File findBestCandidate() throws IOException{
        File[] jars = findJarCandidates();
        File[] wars = findWarCandidates();
        File[] webApps = findWebAppCandidates();
        List<File> combined = new ArrayList<File>();
        combined.addAll(Arrays.asList(jars));
        combined.addAll(Arrays.asList(wars));
        combined.addAll(Arrays.asList(webApps));
        return shallowest(combined.toArray(new File[combined.size()]));
    }
    
    private File[] findWarCandidates() {
        return findCandidates(directory.toPath().getFileSystem().getPathMatcher("glob:**.war"));
    }
    
    private File[] findWebAppCandidates() {
        List<File> out = new ArrayList<File>();
        findWebAppCandidates(directory, out);
        return out.toArray(new File[out.size()]);
    }
    
    private void findWebAppCandidates(File root, List<File> matches) {
        if (".".equals(root.getName()) && root.getParentFile() != null) {
            root = root.getParentFile();
        }
        if ("WEB-INF".equals(root.getName()) && root.isDirectory() && root.getParentFile() != null) {
            matches.add(root.getParentFile());
        } else if (root.isDirectory()) {
            if (root.getName().startsWith(".") || excludedDirectoriesForJarAndWarSearches.contains(root.getName())) {
                return;
            }
            for (File f : root.listFiles()) {
                findWebAppCandidates(f, matches);
            }
        }
    }
    
    private File[] findCandidates(PathMatcher matcher) {
        List<File> out = new ArrayList<File>();
        //PathMatcher matcher = directory.toPath().getFileSystem().getPathMatcher("glob:**.jar");
        findCandidates(directory, matcher, out);
        return out.toArray(new File[out.size()]);
    }
    
    private static final List<String> excludedDirectoriesForJarAndWarSearches = new ArrayList<String>();
    static {
        final String[] l = new String[]{
            "src",
            "jdeploy-bundle",
            "node_modules"
        };
        excludedDirectoriesForJarAndWarSearches.addAll(Arrays.asList(l));
    }
    
    private File shallowest(File[] files) {
        File out = null;
        int depth = -1;
        for (File f : files) {
            int fDepth = f.getPath().split(Pattern.quote("\\") + "|" + Pattern.quote("/")).length;
            if (out == null || fDepth < depth) {
                depth = fDepth;
                out = f;
            }
        }
        return out;
    }
    
    private void findCandidates(File root, PathMatcher matcher, List<File> matches) {
        if (".".equals(root.getName()) && root.getParentFile() != null) {
            root = root.getParentFile();
        }
        //System.out.println("Checking "+root+" for "+matcher);
        if (matcher.matches(root.toPath())) {
            matches.add(root);
        }
        if (root.isDirectory()) {
            if (root.getName().startsWith(".") || excludedDirectoriesForJarAndWarSearches.contains(root.getName())) {
                return;
            }
            for (File f : root.listFiles()) {
                findCandidates(f, matcher, matches);
            }
        }
    }

    private boolean exitOnFail = true;

    public static class FailException extends RuntimeException {
        private int exitCode;
        public FailException(String message, int exitCode) {
            super(message);
            this.exitCode = exitCode;
        }

        public int getExitCode() {
            return exitCode;
        }
    }
    private void fail(String message, int code) {
        if (exitOnFail) {
            err.println(message);
            System.exit(code);
        } else {
            throw new FailException(message, code);
        }

    }
    
    public void copyToBin() throws IOException {
        
        loadDefaults();
        if (getPreCopyScript(null) != null) {
            int code = 0;
            if ((code = runScript(getPreCopyScript(null))) != 0) {
                fail("Pre-copy script failed", code);
                return;
            }
        }
        
        File antFile = new File(getAntFile("build.xml"));
        if (antFile.exists() && getPreCopyTarget(null) != null) {
            int code = runAntTask(getAntFile("build.xml"), getPreCopyTarget(null));
            if (code != 0) {
                fail("Pre-copy ant task failed", code);
                return;

            }
        }
        
        // Actually copy the files
        List<CopyRule> includes = getFiles();

        
        File bin = new File(getBinDir());
        bin.mkdir();
        
        if (getJar(null) == null && getWar(null) == null) {
            // no jar or war explicitly specified... need to scan
            out.println("No jar, war, or web app explicitly specified.  Scanning directory to find best candidate.");
            
            File best = findBestCandidate();
            out.println("Found "+best);
            out.println("To explicitly set the jar, war, or web app to build, use the \"war\" or \"jar\" property of the \"jdeploy\" section of the package.json file.");
            if (best == null) {
            } else if (best.getName().endsWith(".jar")) {
                setJar(fromNativePath(best.getPath()));
            } else {
                setWar(best.getPath());
            }
            
        }
        boolean serverProvidedJavaFX = "true".equals(getString("javafx", "false"));
        boolean stripFXFiles = serverProvidedJavaFX && "true".equals(getString("stripJavaFXFiles", "true"));
        if (doNotStripJavaFXFiles) {
            stripFXFiles = false;
        }
        File jarFile = null;
        if (getJar(null) != null) {
            // We need to include the jar at least
            
            jarFile = findJarFile();
            if (jarFile == null) {
                throw new IOException("Could not find jar file: "+getJar(null));
            }
            String parentPath = jarFile.getParentFile() != null ? jarFile.getParentFile().getPath() : ".";


            String excludes = null;

            includes.add(new CopyRule(parentPath, jarFile.getName(), null));


            for (String path : findClassPath(jarFile)) {
                File f = new File(path);
                if (stripFXFiles && f.getName().startsWith("javafx-") && f.getName().endsWith(".jar")) {
                    continue;
                }
                includes.add(new CopyRule(parentPath, path, excludes));
            }

            System.out.println("Includes: "+includes);
        } else if (getWar(null) != null) {
            File warFile = findWarFile();
            if (warFile == null) {
                throw new IOException("Could not find war file: "+getWar(null));
            }
            String parentPath = warFile.getParentFile() != null ? warFile.getParentFile().getPath() : ".";

            includes.add(new CopyRule(parentPath, warFile.getName(), null));
            //if (warFile.isDirectory()) {
            //    includes.add(new CopyRule(parentPath, warFile.getName()+"/**", null));
            //}
        } else {
            
            throw new RuntimeException("No jar, war, or web app was found to build in this directory.");
            
        }
        
        if (includes.isEmpty()) {
            throw new RuntimeException("No files were found to include in the bundle");
        }
        
        // Now actually copy the files
        for (CopyRule r : includes) {
            try {
                r.copyTo(bin);
            } catch (Exception ex) {
                err.println("Failed to copy to "+bin+" with rule "+r);
                err.println("Files: "+includes);
                throw ex;
            }
        }

        if (jarFile != null && jarFile.exists() && stripFXFiles) {
            out.println("Since JavaFX will be provided, we are stripping it from the build jar");
            out.println("If this causes problems you can disable this by setting stripJavaFXFiles to false in the jdeploy object of your package.json file.");
            try {
                File mainJarFileInBin = null;
                for (File child : bin.listFiles()) {
                    if (child.getName().equals(jarFile.getName())) {
                        mainJarFileInBin = child;
                        break;
                    }
                }
                if (mainJarFileInBin != null) {
                    ZipFile jarZipFile = new ZipFile(mainJarFileInBin);

                    String[] pathsToCheck = new String[]{
                            "javafx/",
                            "com/sun/javafx/",
                            "api-ms-win-core-console-l1-1-0.dll",
                            "api-ms-win-core-datetime-l1-1-0.dll",
                            "api-ms-win-core-debug-l1-1-0.dll",
                            "api-ms-win-core-errorhandling-l1-1-0.dll",
                            "api-ms-win-core-file-l1-1-0.dll",
                            "api-ms-win-core-file-l1-2-0.dll",
                            "api-ms-win-core-file-l2-1-0.dll",
                            "api-ms-win-core-handle-l1-1-0.dll",
                            "api-ms-win-core-heap-l1-1-0.dll",
                            "api-ms-win-core-interlocked-l1-1-0.dll",
                            "api-ms-win-core-libraryloader-l1-1-0.dll",
                            "api-ms-win-core-localization-l1-2-0.dll",
                            "api-ms-win-core-memory-l1-1-0.dll",
                            "api-ms-win-core-namedpipe-l1-1-0.dll",
                            "api-ms-win-core-processenvironment-l1-1-0.dll",
                            "api-ms-win-core-processthreads-l1-1-0.dll",
                            "api-ms-win-core-processthreads-l1-1-1.dll",
                            "api-ms-win-core-profile-l1-1-0.dll",
                            "api-ms-win-core-rtlsupport-l1-1-0.dll",
                            "api-ms-win-core-string-l1-1-0.dll",
                            "api-ms-win-core-synch-l1-1-0.dll",
                            "api-ms-win-core-synch-l1-2-0.dll",
                            "api-ms-win-core-sysinfo-l1-1-0.dll",
                            "api-ms-win-core-timezone-l1-1-0.dll",
                            "api-ms-win-core-util-l1-1-0.dll",
                            "api-ms-win-crt-conio-l1-1-0.dll",
                            "api-ms-win-crt-convert-l1-1-0.dll",
                            "api-ms-win-crt-environment-l1-1-0.dll",
                            "api-ms-win-crt-filesystem-l1-1-0.dll",
                            "api-ms-win-crt-heap-l1-1-0.dll",
                            "api-ms-win-crt-locale-l1-1-0.dll",
                            "api-ms-win-crt-math-l1-1-0.dll",
                            "api-ms-win-crt-multibyte-l1-1-0.dll",
                            "api-ms-win-crt-private-l1-1-0.dll",
                            "api-ms-win-crt-process-l1-1-0.dll",
                            "api-ms-win-crt-runtime-l1-1-0.dll",
                            "api-ms-win-crt-stdio-l1-1-0.dll",
                            "api-ms-win-crt-string-l1-1-0.dll",
                            "api-ms-win-crt-time-l1-1-0.dll",
                            "api-ms-win-crt-utility-l1-1-0.dll",
                            "concrt140.dll",
                            "decora_sse.dll",
                            "glass.dll",
                            "javafx_font.dll",
                            "javafx_iio.dll",
                            "msvcp140.dll",
                            "prism_common.dll",
                            "prism_d3d.dll",
                            "prism_sw.dll",
                            "ucrtbase.dll",
                            "vcruntime140.dll",
                            "jfxwebkit.dll",
                            "libjavafx_font_freetype.so",
                            "libglassgtk3.so",
                            "libjavafx_iio.so",
                            "libprism_sw.so",
                            "libglassgtk2.so",
                            "libprism_common.so",
                            "libglass.so",
                            "libprism_es2.so",
                            "libdecora_sse.so",
                            "libjavafx_font_pango.so",
                            "libjavafx_font.so",
                            "libjfxwebkit.so",
                            "libjavafx_iio.dylib",
                            "libglass.dylib",
                            "libjavafx_font.dylib",
                            "libprism_common.dylib",
                            "libprism_es2.dylib",
                            "libdecora_sse.dylib",
                            "libprism_sw.dylib",
                            "libjfxmedia_avf.dylib",
                            "libglib-lite.dylib",
                            "libfxplugins.dylib",
                            "libgstreamer-lite.dylib",
                            "libjfxmedia.dylib",
                            "libjfxwebkit.dylib"
                    };
                    Set<String> pathsToCheckSet = new HashSet<>(Arrays.asList(pathsToCheck));
                    List<String> pathsToRemove = new ArrayList<>();
                    for (FileHeader header : jarZipFile.getFileHeaders()) {
                        if (pathsToCheckSet.contains(header.getFileName())) {
                            pathsToRemove.add(header.getFileName());
                        }
                    }
                    jarZipFile.removeFiles(pathsToRemove);

                }
            } catch (Exception ex) {
                err.println("Attempt to strip JavaFX files from the application jar file failed.");
                ex.printStackTrace(err);
                throw ex;
            }
        }
        
        if (getWar(null) != null) {
            bundleJetty();
        }
        
        bundleJdeploy();
        bundleJarRunner();
        bundleIcon();
        bundleSplash();
        
        if (getPostCopyScript(null) != null) {
            int code = 0;
            if ((code = runScript(getPostCopyScript(null))) != 0) {
                fail("Post-copy script failed", code);
                return;
            }
        }
        
        if (antFile.exists() && getPostCopyTarget(null) != null) {
            int code = runAntTask(getAntFile("build.xml"), getPostCopyTarget(null));
            if (code != 0) {
                fail("Post-copy ant task failed", code);
                return;
            }
        }
        
    }
    
    
    public void bundleJetty() throws IOException {
        // Now we need to create the stub.
        File bin = new File(getBinDir());
        //File pkgPath = new File(bin, "ca"+File.separator+"weblite"+File.separator+"jdeploy");
        //pkgPath.mkdirs();
        //File stubFile = new File(bin, "WarRunner.jar");
        InputStream warRunnerInput = getClass().getResourceAsStream("WarRunner.jar");
        //FileUtils.copyInputStreamToFile(warRunnerInput, stubFile);

        //String stubFileSrc = FileUtils.readFileToString(stubFile, "UTF-8");
        
        //stubFileSrc = stubFileSrc.replace("{{PORT}}", String.valueOf(getPort(0)));
        //stubFileSrc = stubFileSrc.replace("{{WAR_PATH}}", new File(getWar(null)).getName());
        //FileUtils.writeStringToFile(stubFile, stubFileSrc, "UTF-8");

        InputStream jettyRunnerJarInput = getClass().getResourceAsStream("jetty-runner.jar");
        File libDir = new File(bin, "lib");
        libDir.mkdir();
        File jettyRunnerDest = new File(libDir, "jetty-runner.jar");
        File warRunnerDest = new File(libDir, "WarRunner.jar");
        FileUtils.copyInputStreamToFile(jettyRunnerJarInput, jettyRunnerDest);
        FileUtils.copyInputStreamToFile(warRunnerInput, warRunnerDest);
        /*
        ProcessBuilder javac = new ProcessBuilder();
        javac.inheritIO();
        javac.directory(bin);
        javac.command("javac", "-cp", "lib/jetty-runner.jar", "ca" + File.separator + "weblite" + File.separator + "jdeploy" + File.separator + "WarRunner.java");
        Process javacP = javac.start();
        int javacResult=0;
        try {
            javacResult = javacP.waitFor();
        } catch (InterruptedException ex) {
            Logger.getLogger(JDeploy.class.getName()).log(Level.SEVERE, null, ex);
            throw new IOException(ex);
        }
        if (javacResult != 0) {
            System.exit(javacResult);
        }
        */
        
        setMainClass("ca.weblite.jdeploy.WarRunner");
        setClassPath("."+File.pathSeparator+"lib/jetty-runner.jar"+File.pathSeparator+"lib/WarRunner.jar");
        
        
    }
    
    public void bundleJdeploy() throws IOException {
        File bin = new File(getBinDir());
        InputStream jdeployJs = this.getClass().getResourceAsStream("jdeploy.js");
        File jDeployFile = new File(bin, "jdeploy.js");
        FileUtils.copyInputStreamToFile(jdeployJs, jDeployFile);
        String jdeployContents = FileUtils.readFileToString(jDeployFile, "UTF-8");
        jdeployContents = processJdeployTemplate(jdeployContents);
        FileUtils.writeStringToFile(jDeployFile, jdeployContents, "UTF-8");
    }

    public void bundleJarRunner() throws IOException {
        File bin = new File(getBinDir());
        InputStream jarRunnerJar = this.getClass().getResourceAsStream("jar-runner.jar");
        File jarRunnerFile = new File(bin, "jar-runner.jar");
        FileUtils.copyInputStreamToFile(jarRunnerJar, jarRunnerFile);
    }

    public void bundleIcon() throws IOException {
        File jarFile = new File(directory, toNativePath(getString("jar", null)));

        File iconFile = new File(jarFile.getAbsoluteFile().getParentFile(), "icon.png");
        if (!iconFile.exists()) {
            File _iconFile = new File(directory, "icon.png");
            if (_iconFile.exists()) {
                FileUtils.copyFile(_iconFile, iconFile);
            }
        }
        if (!iconFile.exists()) {
            FileUtils.copyInputStreamToFile(JDeploy.class.getResourceAsStream("icon.png"), iconFile);
        }
        File bin = new File(getBinDir());

        File bundledIconFile = new File(bin, "icon.png");
        FileUtils.copyFile(iconFile, bundledIconFile);
    }


    public void bundleSplash() throws IOException {
        File jarFile = new File(directory, toNativePath(getString("jar", null)));
        File absoluteParent = jarFile.getAbsoluteFile().getParentFile();
        File splashFile = new File(absoluteParent, "splash.png");
        if (!splashFile.exists() && absoluteParent.isDirectory()) {
            for (File child : absoluteParent.listFiles()) {
                if (child.getName().equals("splash.jpg") || child.getName().equals("splash.gif")) {
                    splashFile = child;

                }
            }

        }
        if (!splashFile.exists() && directory.isDirectory()) {
            for (File child : directory.listFiles()) {
                if (child.getName().equals("splash.jpg") || child.getName().equals("splash.gif") || child.getName().equals("splash.png")) {
                    File _splashFile = child;
                    splashFile = new File(absoluteParent, _splashFile.getName());
                    FileUtils.copyFile(_splashFile, splashFile);
                }
            }
        }
        if (!splashFile.exists()) {

            return;
        }
        File bin = new File(getBinDir());

        File bundledSplashFile = new File(bin, splashFile.getName());
        FileUtils.copyFile(splashFile, bundledSplashFile);
    }
    
    public String processJdeployTemplate(String jdeployContents) {
        jdeployContents = jdeployContents.replace("{{JAVA_VERSION}}", String.valueOf(getJavaVersion(11)));
        jdeployContents = jdeployContents.replace("{{PORT}}", String.valueOf(getPort(0)));
        if (getWar(null) != null) {
            jdeployContents = jdeployContents.replace("{{WAR_PATH}}", new File(getWar(null)).getName());
        } else {
            jdeployContents = jdeployContents.replace("{{WAR_PATH}}", "");
        }

        if ("true".equals(getString("javafx", "false")) ) {
            jdeployContents = jdeployContents.replace("{{JAVAFX}}", "true");
        }
        if ("true".equals(getString("jdk", "false"))) {
            jdeployContents = jdeployContents.replace("{{JDK}}", "true");
        }
        if (getJar(null) != null) {
            File jarFile = findJarFile();
            if (jarFile == null) {
                throw new RuntimeException("Could not find jar file: "+getJar(null));
            }
            jdeployContents = jdeployContents.replace("{{JAR_NAME}}", findJarFile().getName());
        } else if (getMainClass(null) != null) {
            jdeployContents = jdeployContents.replace("{{CLASSPATH}}", getClassPath("."));
            jdeployContents = jdeployContents.replace("{{MAIN_CLASS}}", getMainClass(null));
        } else {
            throw new RuntimeException("No main class or jar specified.  Cannot fill template");
        }
        return jdeployContents;
    }
    
    public void initPackageJson() {
        
    }
    
    public void updatePackageJson() {
        
    }

    public static boolean isAlive(Process p) {
        try {
            p.exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
        }
    }

    private String getRelativePath(File f) {

        return directory.toURI().relativize(f.toURI()).getPath();
    }
    
    private void updatePackageJson(String commandName) throws IOException {
        File candidate = findBestCandidate();
        if (commandName == null) {
            if (candidate == null) {

            } else if (candidate.getName().endsWith(".jar") || candidate.getName().endsWith(".war")) {
                commandName = candidate.getName().substring(0, candidate.getName().lastIndexOf(".")).toLowerCase();
            } else {
                commandName = candidate.getName().toLowerCase();
            }
        }
        File packageJson = new File(directory, "package.json");
        err.println("A package.json file already exists.  Updating mandatory fields...");
        JSONParser p = new JSONParser();
        String str = FileUtils.readFileToString(packageJson, "UTF-8");
        Map pj = (Map)p.parseJSON(new StringReader(str));
        if (!pj.containsKey("bin")) {
            pj.put("bin", new HashMap());
        }
        Map bin = (Map)pj.get("bin");
        if (bin.isEmpty()) {
            bin.put(commandName, getBinDir() + "/jdeploy.js");
        }
        
        if (!pj.containsKey("dependencies")) {
            pj.put("dependencies", new HashMap());
        }
        Map deps = (Map)pj.get("dependencies");
        deps.put("shelljs", "^0.8.4");
        deps.put("njre", "^0.2.0");
        
        if (!pj.containsKey("jdeploy")) {
            pj.put("jdeploy", new HashMap());
        }
        Map jdeploy = (Map)pj.get("jdeploy");
        if (candidate != null && !jdeploy.containsKey("war") && !jdeploy.containsKey("jar")) {
            if (candidate.getName().endsWith(".jar")) {
                jdeploy.put("jar", getRelativePath(candidate));
            } else {
                jdeploy.put("war", getRelativePath(candidate));
            }
        }

        String jsonStr = Result.fromContent(pj).toString();
        out.println("Updating your package.json file as follows:\n ");
        out.println(jsonStr);
        out.println("");
        out.print("Proceed? (y/N)");
        Scanner reader = new Scanner(System.in);
        String response = reader.next();
        if ("y".equals(response.toLowerCase())) {
            out.println("Writing package.json...");
            FileUtils.writeStringToFile(packageJson, jsonStr, "UTF-8");
            out.println("Complete!");
        } else {
            out.println("Cancelled");
        }
    }

    private static String toTitleCase(String str) {
        StringBuilder out = new StringBuilder();
        char[] chars = str.toCharArray();
        boolean nextUpper = true;
        for (int i=0; i<chars.length; i++) {
            char c = chars[i];
            if (c == '_' || c == '-') {
                out.append(" ");
                nextUpper = true;
            } else if (nextUpper) {
                out.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                out.append(c);
                nextUpper = false;
            }
        }
        return out.toString();
    }
    
    private void init(String commandName, boolean prompt) throws IOException {
        commandName = directory.getAbsoluteFile().getName().toLowerCase();
        if (".".equals(commandName)) {
            commandName = directory.getAbsoluteFile().getParentFile().getName().toLowerCase();
        }
        File packageJson = new File(directory, "package.json");
        if (packageJson.exists()) {
            updatePackageJson(commandName);
        } else {
            File candidate = findBestCandidate();
            if (commandName == null) {

            }
            if (candidate != null) {
                commandName = candidate.getName();
                if (commandName.endsWith(".jar") || commandName.endsWith(".war")) {
                    commandName = commandName.substring(0, commandName.lastIndexOf("."));
                }
                commandName = commandName.replaceAll("[^a-zA-Z0-9_\\-]", "-");
            }

            Map m = new HashMap(); // for package.json
            m.put("name", commandName);
            m.put("version", "1.0.0");
            m.put("repository", "");
            m.put("description", "");
            m.put("main", "index.js");
            Map bin = new HashMap();
            bin.put(commandName, getBinDir()+"/jdeploy.js");
            m.put("bin", bin);
            m.put("preferGlobal", true);
            m.put("author", "");

            Map scripts = new HashMap();
            scripts.put("test", "echo \"Error: no test specified\" && exit 1");


            m.put("scripts", scripts);
            m.put("license", "ISC");

            Map dependencies = new HashMap();
            dependencies.put("shelljs", "^0.8.4");
            dependencies.put("njre", "^0.2.0");
            m.put("dependencies", dependencies);

            List files = new ArrayList();
            files.add("jdeploy-bundle");

            m.put("files", files);

            Map jdeploy = new HashMap();
            if (candidate == null) {
            } else if (candidate.getName().endsWith(".jar")) {
                jdeploy.put("jar", getRelativePath(candidate));
            } else {
                jdeploy.put("war", getRelativePath(candidate));
            }

            jdeploy.put("javaVersion", "11");
            jdeploy.put("javafx", false);
            jdeploy.put("jdk", false);



            String title = toTitleCase(commandName);


            jdeploy.put("title", title);

            m.put("jdeploy", jdeploy);

            Result res = Result.fromContent(m);
            String jsonStr = res.toString();
            if (prompt) {
                out.println("Creating your package.json file with following content:\n ");
                out.println(jsonStr);
                out.println("");
                out.print("Proceed? (y/N)");
                Scanner reader = new Scanner(System.in);
                String response = reader.next();
                if ("y".equals(response.toLowerCase().trim())) {
                    out.println("Writing package.json...");
                    FileUtils.writeStringToFile(packageJson, jsonStr, "UTF-8");
                    out.println("Complete!");
                } else {
                    out.println("Cancelled");
                }
            } else {
                FileUtils.writeStringToFile(packageJson, jsonStr, "UTF-8");
            }

        }
    }
    
    static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    //static String npm = NPM.npm;

    private static String getenv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null) return defaultValue;
        return value;
    }

    private Map<String,String> bundleCodeCache = new HashMap<String,String>();

    private String fetchJdeployBundleCode(AppInfo appInfo) throws IOException {
        if (appInfo.getNpmPackage() == null) {
            throw new IllegalArgumentException("Cannot fetch jdeploy bundle code without package and version");
        }

        if (bundleCodeCache.containsKey(appInfo.getNpmPackage())) {
            return bundleCodeCache.get(appInfo.getNpmPackage());
        }
        String url = JDEPLOY_REGISTRY+"register.php?package=" +
                URLEncoder.encode(appInfo.getNpmPackage(), "UTF-8");

        //System.out.println("Connecting to "+url);
        try (InputStream inputStream = URLUtil.openStream(new URL(url))) {
            JSONObject jsonResponse = new JSONObject(IOUtil.readToString(inputStream));
            String code =  jsonResponse.getString("code");
            if (code != null && !code.isEmpty()) {
                bundleCodeCache.put(appInfo.getNpmPackage(), code);
            }
            return code;
        } catch (Exception ex) {
            err.println("Failed to connect to "+url);
            throw ex;
        }
    }

    private void loadAppInfo(AppInfo appInfo) throws IOException {
        appInfo.setNpmPackage((String)m().get("name"));
        appInfo.setNpmVersion(getString("version", "latest"));
        if (appInfo.getNpmVersion() != null && appInfo.getNpmPackage() != null) {
            appInfo.setJdeployBundleCode(fetchJdeployBundleCode(appInfo));
        }
        appInfo.setMacAppBundleId(getString("macAppBundleId", null));
        appInfo.setTitle(getString("displayName", appInfo.getNpmPackage()));
        appInfo.setNpmAllowPrerelease("true".equals(getenv("JDEPLOY_BUNDLE_PRERELEASE", getString("prerelease", "false"))));
        appInfo.setFork("true".equals(getString("fork", "false")));

        if (rj().getAsBoolean("codesign") && rj().getAsBoolean("notarize")) {
            appInfo.setCodeSignSettings(AppInfo.CodeSignSettings.CodeSignAndNotarize);
        } else if (rj().getAsBoolean("codesign")) {
            appInfo.setCodeSignSettings(AppInfo.CodeSignSettings.CodeSign);
        }

        String jarPath = getString("jar", null);
        if (jarPath != null) {
            JarFile jarFile = new JarFile(new File(directory, toNativePath(jarPath)));
            String mainClass = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
            if (appInfo.getMacAppBundleId() == null) {
                appInfo.setMacAppBundleId(mainClass.toLowerCase());
            }
            appInfo.setAppURL(new File(jarPath).toURL());
        } else {
            throw new IOException("Cannot load app info because find jar file "+jarPath);
        }

        File jarFile = new File(jarPath);
        File absoluteParent = jarFile.getAbsoluteFile().getParentFile();
        File iconFile = new File(absoluteParent, "icon.png");
        if (!iconFile.exists()) {
            File projectIcon = new File("icon.png");

            if (projectIcon.exists()) {
                FileUtils.copyFile(projectIcon, iconFile);
            } else {
                FileUtils.copyInputStreamToFile(JDeploy.class.getResourceAsStream("icon.png"), iconFile);
            }

        }

    }



    public void macBundle() throws Exception {
        bundle("mac");
    }

    public void macInstaller() throws Exception {

    }

    public void windowsBundle() throws Exception {
        bundle("win");
    }
    public void linuxBundle() throws Exception {
        bundle("linux");
    }

    public void windowsInstallerBundle() throws Exception {
        bundle("win-installer");
    }

    public void linuxInstallerBundle() throws Exception {
        bundle("linux-installer");
    }



    public void allBundles() throws Exception {
        Set<String> bundles = bundles();
        if (bundles.contains("mac")) {
            macBundle();
        }
        if (bundles.contains("win")) {
            windowsBundle();
        }
        if (bundles.contains("linux")) {
            linuxBundle();
        }

    }

    public void allInstallers() throws Exception {
        Set<String> installers = installers();
        for (String target : installers) {
            String version = "latest";
            if (target.contains("@")) {
                version = target.substring(target.indexOf("@")+1);
                target = target.substring(0, target.indexOf("@"));
            }
            installer(target, version);
        }


    }

    public void bundle(String target) throws Exception {
        AppInfo appInfo = new AppInfo();
        loadAppInfo(appInfo);

        Bundler.runit(appInfo, appInfo.getAppURL().toString(), target, "jdeploy" + File.separator + "bundles", "jdeploy" + File.separator + "releases");
    }

    public void installer(String target, String version) throws Exception {
        AppInfo appInfo = new AppInfo();
        loadAppInfo(appInfo);
        String packageJSONVersion = (String)m().get("version");
        appInfo.setNpmVersion(version);
        if (packageJSONVersion != null) {
            appInfo.setNpmVersion(packageJSONVersion);
        }

        File installerDir = new File("jdeploy" + File.separator + "installers");
        installerDir.mkdirs();

        String _newName = appInfo.getTitle() + " Installer";
        if (appInfo.getJdeployBundleCode() != null) {
            _newName += "-"+appInfo.getNpmVersion()+"_"+appInfo.getJdeployBundleCode();
        }
        final String newName = _newName;


        File installerZip;
        if (target.equals("mac")) {
            installerZip = new File(installerDir, appInfo.getTitle()+" Installer-mac-amd64.tar");
            FileUtils.copyInputStreamToFile(JDeploy.class.getResourceAsStream("/jdeploy-installer-mac-amd64.tar"), installerZip);

        } else if (target.equals("win")) {
            installerZip = new File(installerDir, newName+".exe");
            FileUtils.copyInputStreamToFile(JDeploy.class.getResourceAsStream("/jdeploy-installer-win-amd64.exe"), installerZip);
            installerZip.setExecutable(true, false);
            return;

        } else if (target.equals("linux")) {
            installerZip = new File(installerDir, newName);
            FileUtils.copyInputStreamToFile(JDeploy.class.getResourceAsStream("/jdeploy-installer-linux-amd64"), installerZip);
            installerZip.setExecutable(true, false);
            return;

        } else {
            throw new IllegalArgumentException("Unsupported installer type: "+target+".  Only mac, win, and linux supported");
        }


        // We are no longer embedding jdeploy files at all because
        // Gatekeeper runs the installer in a random directory anyways, so we can't locate them.
        // Instead we now use a code in the installer app name.
        boolean embedJdeployFiles = false;
        File bundledAppXmlFile = null;
        File bundledSplashFile = null;
        File bundledIconFile = null;
        if (embedJdeployFiles) {
            byte[] appXmlBytes;

            {
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream("<?xml version='1.0'?>\n<app/>".getBytes("UTF-8"));

                Document document = XMLUtil.parse(byteArrayInputStream);
                org.w3c.dom.Element appElement = document.getDocumentElement();
                appElement.setAttribute("title", appInfo.getTitle());
                appElement.setAttribute("package", appInfo.getNpmPackage());
                appElement.setAttribute("version", appInfo.getNpmVersion());
                appElement.setAttribute("macAppBundleId", appInfo.getMacAppBundleId());


                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                XMLUtil.write(document, baos);
                appXmlBytes = baos.toByteArray();
                bundledAppXmlFile = new File(getBinDir(), "app.xml");
                FileUtils.writeByteArrayToFile(bundledAppXmlFile, appXmlBytes);
            }
            byte[] iconBytes;

            {
                File jarFile = new File(directory, toNativePath(getString("jar", null)));
                File absoluteParent = jarFile.getAbsoluteFile().getParentFile();
                File iconFile = new File(absoluteParent, "icon.png");
                if (!iconFile.exists()) {
                    FileUtils.copyInputStreamToFile(JDeploy.class.getResourceAsStream("icon.png"), iconFile);
                }
                File bin = new File(getBinDir());

                bundledIconFile = new File(bin, "icon.png");
                if (!bundledIconFile.exists()) {
                    FileUtils.copyFile(iconFile, bundledIconFile);
                }
                iconBytes = FileUtils.readFileToByteArray(bundledIconFile);

            }
            byte[] installSplashBytes;


            {
                File jarFile = new File(directory, toNativePath(getString("jar", null)));
                File absoluteParent = jarFile.getAbsoluteFile().getParentFile();
                File splashFile = new File(absoluteParent, "installsplash.png");
                if (!splashFile.exists()) {
                    FileUtils.copyInputStreamToFile(JDeploy.class.getResourceAsStream("installsplash.png"), splashFile);
                }
                File bin = new File(getBinDir());

                bundledSplashFile = new File(bin, "installsplash.png");
                if (!bundledSplashFile.exists()) {
                    FileUtils.copyFile(splashFile, bundledSplashFile);
                }
                installSplashBytes = FileUtils.readFileToByteArray(bundledSplashFile);
            }
        }

        ArchiveUtil.NameFilter filter = new ArchiveUtil.NameFilter() {


            @Override
            public String filterName(String name) {

                if ("mac".equals(target)) {
                    name = name

                            .replaceFirst("^jdeploy-installer/jdeploy-installer\\.app/(.*)", newName + "/" + newName + ".app/$1")
                            .replaceFirst("^jdeploy-installer/\\._jdeploy-installer\\.app$", newName + "/._" + newName + ".app")
                            .replaceFirst("^jdeploy-installer/(.*)", newName + "/$1");
                } else if ("win".equals(target)) {
                    name = name


                            .replaceFirst("^jdeploy-installer/jdeploy-installer\\.exe$", newName + "/"+newName+".exe")
                            .replaceFirst("^jdeploy-installer/(.*)", newName + "/$1");
                } else {
                    name = name

                            .replaceFirst("^jdeploy-installer/jdeploy-installer", newName + "/"+newName)
                            .replaceFirst("^jdeploy-installer/(.*)", newName + "/$1");
                }

                return name;
            }
        };

        ArrayList<ArchiveUtil.ArchiveFile> filesToAdd = new ArrayList<ArchiveUtil.ArchiveFile>();
        if (embedJdeployFiles) {
            filesToAdd.add(new ArchiveUtil.ArchiveFile(bundledAppXmlFile, newName + "/.jdeploy-files/app.xml"));
            filesToAdd.add(new ArchiveUtil.ArchiveFile(bundledSplashFile, newName + "/.jdeploy-files/installsplash.png"));
            filesToAdd.add(new ArchiveUtil.ArchiveFile(bundledIconFile, newName + "/.jdeploy-files/icon.png"));
        }
        if (target.equals("mac") || target.equals("linux")) {
            // Mac and linux use tar file
            ArchiveUtil.filterNamesInTarFile(installerZip, filter, filesToAdd);
        }  else {
            // Windows uses zip file
            ArchiveUtil.filterNamesInZipFile(installerZip, filter, filesToAdd);
        }

    }



    private void jpackageCLI(String[] args) {
        packageJsonFile = new File(directory, "package.json");
        final boolean origDoNotStreipJavaFXFiles = doNotStripJavaFXFiles;
        try {
            if (packageJsonFile == null) {
                throw new IllegalStateException("packageJSONFile not set yet");
            }
            JPackageController jPackageController = new JPackageController(packageJsonFile, args);
            if (!jPackageController.doesJdkIncludeJavafx()) {
                doNotStripJavaFXFiles = true;
            }
            try {
                if (alwaysClean) {
                    File jdeployBundleDirectory = new File(directory, "jdeploy-bundle");
                    if (jdeployBundleDirectory.exists()) {
                        FileUtils.deleteDirectory(jdeployBundleDirectory);
                    }
                }
                copyToBin();
            } catch (Exception ex) {
                err.println("Failed to copy files to jdeploy directory.");
                ex.printStackTrace(err);
                System.exit(1);
            }

            jPackageController.run();
        } finally {
            doNotStripJavaFXFiles = origDoNotStreipJavaFXFiles;
        }
    }

    
    private void _package() throws IOException {
        if (alwaysClean) {
            File jdeployBundle = new File(directory, "jdeploy-bundle");
            if (jdeployBundle.exists()) {
                FileUtils.deleteDirectory(jdeployBundle);
            }
        }
        copyToBin();
        try {
            allBundles();
        } catch (Exception ex) {
            if (ex instanceof IOException) {
                throw (IOException)ex;
            } else {
                throw new IOException("Failed to create bundles", ex);
            }
        }
        try {
            allInstallers();
        } catch (Exception ex) {
            if (ex instanceof IOException) {
                throw (IOException)ex;
            } else {
                throw new IOException("Failed to create installers", ex);
            }
        }
    }
    
    private void install() throws IOException {
        
        _package();
        new NPM(out, err).link(exitOnFail);
    }
    
    

    private void uploadResources() throws IOException {
        File icon = new File(directory, "icon.png");
        File installSplash = new File(directory,"installsplash.png");
        JSONObject packageJSON = new JSONObject(FileUtils.readFileToString(new File(directory, "jdeploy" + File.separator + "publish" + File.separator + "package.json"), "UTF-8"));

        if (icon.exists() || installSplash.exists()) {
            // If there is a custom icon or install splash we need to upload
            // them to jdeploy.com so that they are available when generating
            // the installer.  Without this, jdeploy.com would need to download the
            // full package from npm and extract the icon and installsplash from there.

            JSONObject jdeployFiles = new JSONObject();
            byte[] iconBytes = FileUtils.readFileToByteArray(icon);
            jdeployFiles.put("icon.png", Base64.getEncoder().encodeToString(iconBytes));
            if (installSplash.exists()) {
                byte[] splashBytes = FileUtils.readFileToByteArray(installSplash);
                jdeployFiles.put("installsplash.png", Base64.getEncoder().encodeToString(splashBytes));
            }
            jdeployFiles.put("packageName", packageJSON.get("name"));
            jdeployFiles.put("version", packageJSON.get("version"));
            try {
                out.println("Uploading icon to jdeploy.com...");
                JSONObject response = makeServiceCall(JDEPLOY_REGISTRY + "publish.php", jdeployFiles.toString());
                out.println("Upload complete");
                if (response.has("code") && response.getInt("code") == 200) {
                    out.println("Your package was published successfully.");
                    out.println("You can download native installers for your app at " + JDEPLOY_REGISTRY + "~" + packageJSON.getString("name"));
                } else {
                    err.println("There was a problem publishing the icon to " + JDEPLOY_REGISTRY);
                    if (response.has("error")) {
                        err.println("Error message: " + response.getString("error"));
                    } else if (response.has("code")) {
                        err.println("Unexpected response code: " + response.getInt("code"));
                    } else {
                        err.println("Unexpected server response: " + response.toString());
                    }
                }

            } catch (Exception ex) {
                err.println("Failed to publish icon and splash image to jdeploy.com.  " + ex.getMessage());
                ex.printStackTrace(err);
                fail("Failed to publish icon and splash image to jdeploy.com. "+ex.getMessage(), 1);
                return;
            }
        } else {
            out.println("Your package was published successfully.");
            out.println("You can download native installers for your app at " + JDEPLOY_REGISTRY + "~" + packageJSON.getString("name"));

        }
    }

    private JSONObject fetchPackageInfoFromNpm(String packageName) throws IOException {
        return new NPM(out, err).fetchPackageInfoFromNpm(packageName);

    }

    private boolean isVersionPublished(String packageName, String version) {
        return new NPM(out, err).isVersionPublished(packageName, version);

    }


    public void publish() throws IOException {


        if (alwaysPackageOnPublish) {

            _package();
        }

        // Copy all publishable artifacts to a temporary
        // directory so that we can add some information to the
        // package.json without having to modify the actual package.json
        File publishDir = new File(directory,"jdeploy" + File.separator+ "publish");
        if (publishDir.exists()) {
            FileUtils.deleteDirectory(publishDir);
        }
        if (!publishDir.exists()) {
            publishDir.mkdirs();
        }
        FileUtils.copyDirectory(new File(directory, "jdeploy-bundle"), new File(publishDir, "jdeploy-bundle"));
        FileUtils.copyFile(new File("package.json"), new File(publishDir, "package.json"));
        File readme = new File("README.md");
        if (readme.exists()) {
            FileUtils.copyFile(readme, new File(publishDir, readme.getName()));
        }
        File license = new File("LICENSE");
        if (license.exists()) {
            FileUtils.copyFile(license, new File(publishDir, license.getName()));
        }

        // Now add checksums
        JSONObject packageJSON = new JSONObject(FileUtils.readFileToString(new File(directory, "package.json"), "UTF-8"));
        JSONObject jdeployObj = packageJSON.getJSONObject("jdeploy");

        File icon = new File(directory, "icon.png");
        JSONObject checksums = new JSONObject();
        jdeployObj.put("checksums", checksums);
        if (icon.exists()) {
            String md5 = MD5.getMD5Checksum(icon);
            checksums.put("icon.png", md5);
        }

        File installSplash = new File(directory, "installsplash.png");
        if (installSplash.exists()) {
            checksums.put("installsplash.png", MD5.getMD5Checksum(installSplash));
        }

        FileUtils.writeStringToFile(new File(publishDir,"package.json"), packageJSON.toString(), "UTF-8");


        new NPM(out, err).publish(publishDir, exitOnFail);
        out.println("Package published to npm successfully.");
        out.println("Waiting for npm to update its registry...");

        long timeout = System.currentTimeMillis()+30000;
        while (System.currentTimeMillis() < timeout) {
            if (isVersionPublished(packageJSON.getString("name"), packageJSON.getString("version"))) {
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (Exception ex){}
        }

        uploadResources();


    }

    public DeveloperIdentityKeyStore getKeyStore() throws IOException {
        DeveloperIdentityKeyStore out = new DeveloperIdentityKeyStore();
        String keyString = System.getProperty("jdeploy.identity", System.getenv("JDEPLOY_IDENTITY"));
        if (keyString != null) {
            if (keyString.contains("PRIVATE KEY")) {
                out.setPemString(keyString);
            } else if (new File(keyString).exists()) {
                // it's a file
                File f = new File(keyString);
                if (f.getName().endsWith(".pem") || f.getName().endsWith(".p12")) {
                    out.setPemFile(f);
                } else {
                    try {
                        String fileContents = FileUtils.readFileToString(new File(keyString), StandardCharsets.UTF_8);
                        if (fileContents.contains("PRIVATE KEY")) {
                            out.setPemString(fileContents);
                        } else {
                            out.setKeyStoreFile(f);
                        }
                    } catch (IOException ex) {
                        out.setKeyStoreFile(f);
                    }
                }

            } else {
                if (System.getProperty("jdeploy.identity") != null) {
                    throw new IOException("The jdeploy.identity system property is set but neither points to a file, nor contains a PEM-encoded string.  This property should point to either a keystore file, a PEM-encoded file, or contain a PEM-encoded string.");
                } else {
                    throw new IOException("The JDEPLOY_IDENTITY environment variable is set but neither points to a file, nor contains a PEM-encoded string.  This variable should point to either a keystore file, a PEM-encoded file, or contain a PEM-encoded string.");
                }
            }

        }

        return out;
    }

    private JSONObject makeServiceCall(String url,
                                        String jsonString) {
        try{
            CloseableHttpClient client = HttpClientBuilder.create().build();
            HttpPost httpPost = new HttpPost(url);

            httpPost.setHeader("Content-Type", "application/json; charset='utf-8'");
            httpPost.setHeader("Accept-Charset", "UTF-8");
            httpPost.setEntity(new StringEntity(jsonString));


            CloseableHttpResponse response = client.execute(httpPost);
            if (response.getStatusLine().getStatusCode() != 200) {
                //System.err.println("Headers: "+conn.getHeaderFields());
                throw new IOException("Failed to publish resources to jdeploy.com.  "+response.getStatusLine().getReasonPhrase());
            }
            String resultLine = EntityUtils.toString(response.getEntity());
            try {
                return new JSONObject(resultLine);
            } catch (Exception ex) {
                err.println("Unexpected server response.  Expected JSON but found "+resultLine+" Response code was: "+response.getStatusLine().getStatusCode()+".  Message was "+response.getStatusLine().getReasonPhrase());
                ex.printStackTrace(err);
                throw new Exception("Unexpected server response.  Expected JSON but found "+resultLine);
            }
        } catch (Exception ex) {
            ex.printStackTrace(err);
            JSONObject out = new JSONObject();
            out.put("code", 500);
            out.put("error", ex.getMessage());
            return out;
        }


    }


    
    private void scan() throws IOException {
        File[] jars = findJarCandidates();
        out.println("Found "+jars.length+" jars: "+Arrays.toString(jars));
        out.println("Best candidate: "+shallowest(jars));
        
        File[] wars = findWarCandidates();
        out.println("Found "+wars.length+" wars: "+Arrays.toString(wars));
        out.println("Best candidate: "+shallowest(jars));
        
        File[] webApps = findWebAppCandidates();
        out.println("Found "+webApps.length+" web apps: "+Arrays.toString(webApps));
        out.println("Best candidate: "+shallowest(jars));
        
        List<File> combined = new ArrayList<File>();
        combined.addAll(Arrays.asList(jars));
        combined.addAll(Arrays.asList(wars));
        combined.addAll(Arrays.asList(webApps));
        
        out.println("If jdeploy were to run on this directory without specifying the jar or war in the package.json, it would choose " + shallowest(combined.toArray(new File[combined.size()])));
    }

    private void help(Options opts) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("jdeploy [init|package|install|publish] [commandName]\n"
                + "\n"
                + "Commands:\n"
                + "  init : Initialize the project\n"
                + "  package : Prepare for install.  This copies necessary files into bin directory.\n"
                + "  install : Installs the app locally (links to PATH)\n"
                + "  publish : Publishes to NPM\n", opts);
    }
    
    private void _run() {
        out.println("run not implemented yet");
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            JDeploy prog = new JDeploy(new File(".").getAbsoluteFile());
            Options opts = new Options();
            if (args.length > 0 && !"jpackage".equals(args[0])) {
                CommandLineParser parser = new DefaultParser();
                CommandLine line = parser.parse(opts, args);
                args = line.getArgs();
            }
            if (args.length == 0 || "gui".equals(args[0])) {
                System.out.println("Launching jdeploy gui.  Use jdeploy help for help");
                File packageJSON = new File("package.json");
                if (packageJSON.exists()) {
                    JSONObject packageJSONObject = new JSONObject(FileUtils.readFileToString(packageJSON, "UTF-8"));
                    if (packageJSON.exists()) {
                        EventQueue.invokeLater(() -> {
                            JDeployProjectEditor editor = new JDeployProjectEditor(packageJSON, packageJSONObject);
                            editor.show();
                        });
                    }
                } else {
                    prog.guiCreateNew(packageJSON);
                }

                return;

            }

            if ("gui-main".equals(args[0])) {
                EventQueue.invokeLater(()->{
                    JDeployMainMenu menu = new JDeployMainMenu();
                    menu.show();
                });
                return;

            }




            if ("upload-resources".equals(args[0])) {
                prog.uploadResources();
                System.exit(0);
            }

            if ("clean".equals(args[0])) {

                File jDeployDir = new File("jdeploy");
                if (jDeployDir.exists()) {
                    System.out.println("Deleting "+jDeployDir);
                    FileUtils.deleteDirectory(jDeployDir);
                }
                jDeployDir = new File("jdeploy-bundle");
                if (jDeployDir.exists()) {
                    System.out.println("Deleting "+jDeployDir);
                    FileUtils.deleteDirectory(jDeployDir);
                }
                if (args.length > 1) {
                    String[] args2 = new String[args.length-1];
                    System.arraycopy(args, 1, args2, 0, args2.length);
                    args = args2;
                } else {
                    System.exit(0);
                }
            }

            if ("jpackage".equals(args[0])) {
                String[] jpackageArgs = new String[args.length-1];
                System.arraycopy(args, 1, jpackageArgs, 0, jpackageArgs.length);
                prog.jpackageCLI(jpackageArgs);
            } else if ("package".equals(args[0])) {
                prog._package();
            } else if ("init".equals(args[0])) {
                String commandName = null;
                if (args.length > 1) {
                    commandName = args[1];
                }
                
                prog.init(commandName, true);
            } else if ("install".equals(args[0])) {
                prog.install();
            } else if ("publish".equals(args[0])) {
                prog.publish();
            } else if ("scan".equals(args[0])) {
                prog.scan();
            } else if ("run".equals(args[0])) {
                prog._run();
            } else {
                prog.help(opts);
                System.exit(1);
            }

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void guiCreateNew(File packageJSON) {
        EventQueue.invokeLater(()->{
            int result = JOptionPane.showConfirmDialog(null, new JLabel(
                    "<html><p style='width:400px'>No package.json file found in this directory.  Do you want to create one now?</p></html>"),
                    "Create package.json?",
                    JOptionPane.YES_NO_OPTION);


            if (result != JOptionPane.YES_OPTION) {
                System.out.println("result "+result);
                return;
            }

            exitOnFail = false;
            try {
                init(null, false);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null,  new JLabel(
                                "<html><p style='width:400px'>Failed to create package.json file. "+ex.getMessage()+"</p></html>"),
                                    "JDeploy init error",
                        JOptionPane.ERROR_MESSAGE
                        );
                return;

            }
            try {
                File packageJSONFile = new File(directory, "package.json");
                JSONObject packageJSONObject = new JSONObject(FileUtils.readFileToString(packageJSONFile, "UTF-8"));
                new JDeployProjectEditor(new File("package.json"), packageJSONObject).show();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null,  new JLabel(
                                "<html><p style='width:400px'>There was a problem reading the package.json file. "+ex.getMessage()+"</p></html>"),
                        "JDeploy init error",
                        JOptionPane.ERROR_MESSAGE
                );
            }

        });
    }

}

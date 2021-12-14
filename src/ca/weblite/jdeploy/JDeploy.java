/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.jdeploy;

import com.codename1.io.JSONParser;
import com.codename1.processing.Result;
import com.codename1.xml.Element;
import com.codename1.xml.XMLParser;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author shannah
 */
public class JDeploy {
    
    private final File directory;
    private File packageJsonFile;
    private Map packageJsonMap;
    private Result packageJsonResult;
    
    public class CopyRule {
        String dir;
        List<String> includes;
        List<String> excludes;
        
        
        public CopyRule(String dir, List<String> includes, List<String> excludes) {
            this.dir = dir;
            this.includes = includes;
            this.excludes = excludes;
        }
        
        public CopyRule(String dir, String includes, String excludes) {
            this.dir = dir;
            this.includes = includes == null ? null : Arrays.asList(includes.split(","));
            this.excludes = excludes == null ? null : Arrays.asList(excludes.split(","));
        }
        
        public String toString() {
            return "CopyRule{dir="+dir+", includes="+includes+", excludes="+excludes+"}";
        }
        
        public void copyTo(File destDirectory) throws IOException {
            System.out.println("Executing copy rule "+this);
            final File srcDir = new File(dir);
            if (!srcDir.exists()) {
                throw new IOException("Source directory of copy rule does not exist: "+srcDir);
            }
            
            if (!destDirectory.exists()) {
                throw new IOException("Destination directory of copy rule does not exist: "+destDirectory);
            }
            
            if (srcDir.equals(destDirectory)) {
                System.err.println("Copy rule has same srcDir and destDir.  Not copying: "+srcDir);
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
                        System.out.println(pathname+" does not match any patterns.");
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
    
    public JDeploy(File directory) {
        this.directory = directory;
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
            File jarFile = new File(getJar(null));
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
    
    public void copyToBin() throws IOException {
        
        loadDefaults();
        if (getPreCopyScript(null) != null) {
            int code = 0;
            if ((code = runScript(getPreCopyScript(null))) != 0) {
                System.err.println("Pre-copy script failed.");
                System.exit(code);
            }
        }
        
        File antFile = new File(getAntFile("build.xml"));
        if (antFile.exists() && getPreCopyTarget(null) != null) {
            int code = runAntTask(getAntFile("build.xml"), getPreCopyTarget(null));
            if (code != 0) {
                System.err.println("Pre-copy ant task failed");
                System.exit(code);
            }
        }
        
        // Actually copy the files
        List<CopyRule> includes = getFiles();
        /*
        if (includes.isEmpty()) {
            File distDir = new File("dist");
        
            if (distDir.exists()) {
                
                File distLib = new File(distDir, "lib");
                includes.add(new CopyRule("dist", "*.jar", null));
                if (distLib.exists()) {
                    includes.add(new CopyRule("dist", "lib/*.jar", null));
                }
            }
            File targetDir = new File("target");
            if (targetDir.exists()) {
                includes.add(new CopyRule("target", "*.jar", null));
            }
        }*/
        
        File bin = new File(getBinDir());
        bin.mkdir();
        
        if (getJar(null) == null && getWar(null) == null) {
            // no jar or war explicitly specified... need to scan
            System.out.println("No jar, war, or web app explicitly specified.  Scanning directory to find best candidate.");
            
            File best = findBestCandidate();
            System.out.println("Found "+best);
            System.out.println("To explicitly set the jar, war, or web app to build, use the \"war\" or \"jar\" property of the \"jdeploy\" section of the package.json file.");
            if (best == null) {
            } else if (best.getName().endsWith(".jar")) {
                setJar(best.getPath());
            } else {
                setWar(best.getPath());
            }
            
        }
        
        if (getJar(null) != null) {
            // We need to include the jar at least
            
            File jarFile = findJarFile();
            if (jarFile == null) {
                throw new IOException("Could not find jar file: "+getJar(null));
            }
            String parentPath = jarFile.getParentFile() != null ? jarFile.getParentFile().getPath() : ".";
            includes.add(new CopyRule(parentPath, jarFile.getName(), null));
            
            for (String path : findClassPath(jarFile)) {
                includes.add(new CopyRule(parentPath, path, null));
            }
            
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
                System.err.println("Failed to copy to "+bin+" with rule "+r);
                System.err.println("Files: "+includes);
                throw ex;
            }
        }
        
        if (getWar(null) != null) {
            bundleJetty();
        }
        
        bundleJdeploy();
        
        if (getPostCopyScript(null) != null) {
            int code = 0;
            if ((code = runScript(getPostCopyScript(null))) != 0) {
                System.err.println("Post-copy script failed.");
                System.exit(code);
            }
        }
        
        if (antFile.exists() && getPostCopyTarget(null) != null) {
            int code = runAntTask(getAntFile("build.xml"), getPostCopyTarget(null));
            if (code != 0) {
                System.err.println("Post-copy ant task failed");
                System.exit(code);
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
    
    public String processJdeployTemplate(String jdeployContents) {
        jdeployContents = jdeployContents.replace("{{JAVA_VERSION}}", String.valueOf(getJavaVersion(11)));
        jdeployContents = jdeployContents.replace("{{PORT}}", String.valueOf(getPort(0)));
        if (getWar(null) != null) {
            jdeployContents = jdeployContents.replace("{{WAR_PATH}}", new File(getWar(null)).getName());
        } else {
            jdeployContents = jdeployContents.replace("{{WAR_PATH}}", "");
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
        System.err.println("A package.json file already exists.  Updating mandatory fields...");
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
        deps.put("shelljs", "^0.7.5");
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
        System.out.println("Updating your package.json file as follows:\n ");
        System.out.println(jsonStr);
        System.out.println("");
        System.out.print("Proceed? (y/N)");
        Scanner reader = new Scanner(System.in);
        String response = reader.next();
        if ("y".equals(response.toLowerCase())) {
            System.out.println("Writing package.json...");
            FileUtils.writeStringToFile(packageJson, jsonStr, "UTF-8");
            System.out.println("Complete!");
        } else {
            System.out.println("Cancelled");
        }
    }
    
    
    private void init(String commandName) throws IOException {
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
                
                /*
                if (candidate == null) {
                    commandName = directory.getAbsoluteFile().getName().toLowerCase();
                    if (".".equals(commandName)) {
                        commandName = directory.getAbsoluteFile().getParentFile().getName().toLowerCase();
                    }
                } else if (candidate.getName().endsWith(".jar") || candidate.getName().endsWith(".war")) {
                    commandName = candidate.getName().substring(0, candidate.getName().lastIndexOf(".")).toLowerCase();
                } else {
                    commandName = candidate.getName().toLowerCase();
                }*/
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
            dependencies.put("shelljs", "^0.7.5");
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

            m.put("jdeploy", jdeploy);

            Result res = Result.fromContent(m);
            String jsonStr = res.toString();
            System.out.println("Creating your package.json file with following content:\n ");
            System.out.println(jsonStr);
            System.out.println("");
            System.out.print("Proceed? (y/N)");
            Scanner reader = new Scanner(System.in);
            String response = reader.next();
            if ("y".equals(response.toLowerCase().trim())) {
                System.out.println("Writing package.json...");
                FileUtils.writeStringToFile(packageJson, jsonStr, "UTF-8");
                System.out.println("Complete!");
            } else {
                System.out.println("Cancelled");
            }
        }
    }
    
    static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    static String npm = isWindows() ? "npm.cmd" : "npm";

    private void init_old(String commandName) throws IOException {
        try {
            File packageJson = new File(directory, "package.json");
            if (!packageJson.exists()) {
                ProcessBuilder pb = new ProcessBuilder();
                pb.command(npm, "init");
                pb.inheritIO();
                final Process p = pb.start();
                Timer t = new Timer();
                
                TimerTask tt = new TimerTask() {

                    @Override
                    public void run() {
                        if (packageJson.exists()) {
                            p.destroy();
                            cancel();
                        } 
                    }
                    
                };
                t.schedule(tt, new Date(System.currentTimeMillis()+1000), 1000);
                
                int code = p.waitFor();
                
                if (!packageJson.exists() && code != 0) {
                    System.err.println("Stopped init because npm init failed");
                    System.exit(code);
                }
            }
            
            String pkgJsonStr = FileUtils.readFileToString(packageJson, "UTF-8");
            if (!pkgJsonStr.contains("shelljs")) {
                System.out.println("Installing shelljs");
                ProcessBuilder pb = new ProcessBuilder();
                pb.inheritIO();
                pb.command(npm, "install", "shelljs", "--save");
                Process p = pb.start();
                int result = p.waitFor();
                if (result != 0) {
                    System.err.println("Failed to install shelljs");
                    System.exit(result);
                }
            }
            
            // For some reason it never sticks in the package.json file the first time
            pkgJsonStr = FileUtils.readFileToString(packageJson, "UTF-8");
            if (!pkgJsonStr.contains("shelljs")) {
                System.out.println("Installing shelljs");
                ProcessBuilder pb = new ProcessBuilder();
                pb.inheritIO();
                pb.command(npm, "install", "shelljs", "--save");
                Process p = pb.start();
                int result = p.waitFor();
                if (result != 0) {
                    System.err.println("Failed to install shelljs");
                    System.exit(result);
                }
            }
            
            JSONParser parser = new JSONParser();
            Map contents = parser.parseJSON(new StringReader(pkgJsonStr));
            if (commandName == null) {
                commandName = (String)contents.get("name");
            }
            
            if (!contents.containsKey("bin")) {
                contents.put("bin", new HashMap());
            }
            Map bins = (Map)contents.get("bin");
            
            if (!bins.values().contains(getBinDir()+"/jdeploy.js")) {
                contents.put("preferGlobal", true);
                bins.put(commandName, getBinDir()+"/jdeploy.js");
                Result res = Result.fromContent(contents);
                FileUtils.writeStringToFile(packageJson, res.toString(), "UTF-8");
            }
            
            if (!contents.containsKey("files")) {
                contents.put("files", new ArrayList());
            }
            List files = (List)contents.get("files");
            if (!files.contains(getBinDir())) {
                files.add(getBinDir());
                Result res = Result.fromContent(contents);
                FileUtils.writeStringToFile(packageJson, res.toString(), "UTF-8");
            }
            
            
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    private void _package() throws IOException {
        copyToBin();
    }
    
    private void install() throws IOException {
        
        _package();
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.inheritIO();
            pb.command(npm, "link");
            Process p = pb.start();
            int result = p.waitFor();
            if (result != 0) {
                System.exit(result);
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(JDeploy.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }
    
    

    

    private void publish() throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.inheritIO();
            pb.command(npm, "publish");
            Process p = pb.start();
            int result = p.waitFor();
            if (result != 0) {
                System.exit(result);
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(JDeploy.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }
    
    private void scan() throws IOException {
        System.out.println("Scanning directory for executable jars, wars, and webapps...");
        File[] jars = findJarCandidates();
        System.out.println("Found "+jars.length+" jars: "+Arrays.toString(jars));
        System.out.println("Best candidate: "+shallowest(jars));
        
        File[] wars = findWarCandidates();
        System.out.println("Found "+wars.length+" wars: "+Arrays.toString(wars));
        System.out.println("Best candidate: "+shallowest(jars));
        
        File[] webApps = findWebAppCandidates();
        System.out.println("Found "+webApps.length+" web apps: "+Arrays.toString(webApps));
        System.out.println("Best candidate: "+shallowest(jars));
        
        List<File> combined = new ArrayList<File>();
        combined.addAll(Arrays.asList(jars));
        combined.addAll(Arrays.asList(wars));
        combined.addAll(Arrays.asList(webApps));
        
        System.out.println("If jdeploy were to run on this directory without specifying the jar or war in the package.json, it would choose " + shallowest(combined.toArray(new File[combined.size()])));
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
        System.out.println("run not implemented yet");
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            JDeploy prog = new JDeploy(new File(".").getAbsoluteFile());
            CommandLineParser parser = new DefaultParser();
            Options opts = new Options();
            CommandLine line = parser.parse(opts, args);
            args = line.getArgs();
            if (args.length == 0) {
                prog.help(opts);
                System.exit(1);
            }
            if ("package".equals(args[0])) {
                prog._package();
            } else if ("init".equals(args[0])) {
                String commandName = null;
                if (args.length > 1) {
                    commandName = args[1];
                }
                
                prog.init(commandName);
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

}

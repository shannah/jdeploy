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
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
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
            
            FileUtils.copyDirectory(srcDir, destDirectory, new FileFilter() {

                @Override
                public boolean accept(File pathname) {
                    if (excludes != null) {
                        for (String pattern : excludes) {
                            PathMatcher matcher = srcDir.toPath().getFileSystem().getPathMatcher(dir+File.separator+pattern);
                            if (matcher.matches(pathname.toPath())) {
                                return false;
                            }
                        }
                    }
                    
                    if (includes != null) {
                        for (String pattern : includes) {
                            //System.out.println("Copying files that match "+dir+File.separator+pattern);
                            File f = new File(dir, pattern);
                            if (f.exists()) {
                                return true;
                            }
                            PathMatcher matcher = srcDir.toPath().getFileSystem().getPathMatcher("glob:"+dir+File.separator+pattern);
                            if (matcher.matches(pathname.toPath())) {
                                return true;
                            }
                        }
                        return false;
                    } else {
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
        System.out.println(m.getEntries());
        String cp = m.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
        System.out.println("Class path is "+cp);
        if (cp != null) {
            return cp.split(" ");
        } else {
            return new String[0];
        }
        /*
       // System.out.println("Classpath is "+cp);
        
        System.out.println("Finding classpath for jar "+jarFile);
        URLClassLoader cl = new URLClassLoader(new URL[]{jarFile.toURL()});
        InputStream manifestInput = cl.getResourceAsStream("META-INF/MANIFEST.MF");

        if (manifestInput != null) {
            System.out.println("Found manifest");
            //String manifestStr = convertStreamToString(manifestInput);
            Scanner s = new Scanner(manifestInput).useDelimiter("\n");
            while (s.hasNext()) {
                String line = s.next();
                System.out.println("Line: "+line);
                if (line.indexOf("Class-Path:") == 0) {
                    System.out.println("Found class path: "+line);
                    String classPath = line.substring(line.indexOf(":") + 1);
                    String[] classPaths = classPath.split(":");
                    return classPaths;

                }
            }
        }
        return new String[0];
        */
    }
    
    public void loadDefaults() throws IOException {
        boolean triedPom = false;
        boolean triedDist = false;
        boolean triedBuild = false;
        while (getJar(null) == null &&  getWar(null) == null && getMainClass(null) == null) {
            if (triedPom && triedDist && triedBuild) {
                break;
            }
            // No settings yet.  Try to figure out where things are.
            File pomFile = new File("pom.xml");
            File buildXml = new File("build.xml");
            File buildDir = new File("build");
            File distDir = new File("dist");
            
            if (!triedPom && pomFile.exists()) {
                triedPom = true;
                // This is a maven project, so let's try to load some defaults from there.
                String pomStr = FileUtils.readFileToString(pomFile, "UTF-8");
                XMLParser p = new XMLParser();
                Element pomRoot = p.parse(new StringReader(pomStr));
                Result pomRes = Result.fromContent(pomRoot);
                String artifactId = pomRes.getAsString("project/artifactId");
                String version = pomRes.getAsString("project/version");
                String packaging = pomRes.getAsString("project/packaging");
                
                if ("war".equals(packaging)) {
                    File warFile = new File("target", artifactId+"-"+version);
                    if (warFile.exists()) {
                        setWar("target"+File.separator+warFile.getName());
                    }
                } else if ("jar".equals(packaging)){
                    File jarFile = new File("target", artifactId+"-"+version+".jar");
                    if (jarFile.exists()) {
                        setJar("target"+File.separator+jarFile.getName());
                    }
                }
                continue;
            } else {
                triedPom = true;
            }
            
            if (!triedDist && distDir.exists()) {
                triedDist = true;
                File jarFile = null;
                for (File f : distDir.listFiles()) {
                    if (f.getName().endsWith(".jar")) {
                        jarFile = f;
                    }
                }
                if (jarFile != null) {
                    setJar("dist" + File.separator + jarFile.getName());
                }
                continue;
            } else {
                triedDist = true;
            }
            
            if (!triedBuild && buildDir.exists()) {
                triedBuild = true;
                
            } else {
                triedBuild = true;
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
        }
        
        File bin = new File(getBinDir());
        bin.mkdir();
        
        if (getJar(null) != null) {
            // We need to include the jar at least
            
            File jarFile = findJarFile();
            if (jarFile == null) {
                throw new IOException("Could not find jar file: "+getJar(null));
            }
            
            includes.add(new CopyRule(jarFile.getParentFile().getPath(), jarFile.getName(), null));
            
            for (String path : findClassPath(jarFile)) {
                includes.add(new CopyRule(jarFile.getParentFile().getPath(), path, null));
            }
            
        }
        
        if (getWar(null) != null) {
            File warFile = findWarFile();
            if (warFile == null) {
                throw new IOException("Could not find war file: "+getWar(null));
            }
            includes.add(new CopyRule(warFile.getParentFile().getPath(), warFile.getName(), null));
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
        File pkgPath = new File(bin, "ca"+File.separator+"weblite"+File.separator+"jdeploy");
        pkgPath.mkdirs();
        File stubFile = new File(pkgPath, "WarRunner.java");
        InputStream warRunnerInput = getClass().getResourceAsStream("WarRunner.javas");
        FileUtils.copyInputStreamToFile(warRunnerInput, stubFile);

        String stubFileSrc = FileUtils.readFileToString(stubFile, "UTF-8");
        
        stubFileSrc = stubFileSrc.replace("{{PORT}}", String.valueOf(getPort(0)));
        stubFileSrc = stubFileSrc.replace("{{WAR_PATH}}", getWar(null));
        FileUtils.writeStringToFile(stubFile, stubFileSrc, "UTF-8");

        InputStream jettyRunnerJarInput = getClass().getResourceAsStream("jetty-runner.jar");
        File libDir = new File(bin, "lib");
        libDir.mkdir();
        File jettyRunnerDest = new File(libDir, "jetty-runner.jar");
        FileUtils.copyInputStreamToFile(jettyRunnerJarInput, jettyRunnerDest);

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
        
        
        setMainClass("ca.weblite.jdeploy.WarRunner");
        setClassPath("."+File.pathSeparator+"lib/jetty-runner.jar");
        
        
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

    
    
    private void init(String commandName) throws IOException {
        try {
            File packageJson = new File(directory, "package.json");
            if (!packageJson.exists()) {
                ProcessBuilder pb = new ProcessBuilder();
                pb.command("npm", "init");
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
                pb.command("npm", "install", "shelljs", "--save");
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
                pb.command("npm", "install", "shelljs", "--save");
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
            pb.command("npm", "link");
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
        install();
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.inheritIO();
            pb.command("npm", "publish");
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
            } else {
                prog.help(opts);
                System.exit(1);
            }

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

}

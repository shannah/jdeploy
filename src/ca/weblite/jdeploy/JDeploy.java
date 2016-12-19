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
import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author shannah
 */
public class JDeploy {

    public static boolean isAlive(Process p) {
        try {
            p.exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
        }
    }

    private void init(File directory, String commandName) throws IOException {
        try {
            File packageJson = new File(directory, "package.json");
            if (!packageJson.exists()) {
                System.out.println("Please run \"npm init\" first");
                System.exit(1);
            }
            System.out.println("Installing shelljs");
            ProcessBuilder pb = new ProcessBuilder();
            pb.inheritIO();
            pb.command("npm", "install", "shelljs", "--save");
            Process p = pb.start();
            int result = p.waitFor();
            if (result != 0) {
                System.exit(result);
            }
            
            String pkgJsonStr = FileUtils.readFileToString(packageJson, "UTF-8");
            JSONParser parser = new JSONParser();
            Map contents = parser.parseJSON(new StringReader(pkgJsonStr));
            if (commandName == null) {
                commandName = (String)contents.get("name");
            }
            if (!contents.containsKey("bin")) {
                contents.put("bin", new HashMap());
            }
            Map bins = (Map)contents.get("bin");
            
            if (!bins.values().contains("bin/jdeploy.js")) {
                contents.put("preferGlobal", true);
                bins.put(commandName, "bin/jdeploy.js");
                Result res = Result.fromContent(contents);
                FileUtils.writeStringToFile(packageJson, res.toString(), "UTF-8");
            }
            
            
            
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    private void install(File directory) throws IOException {
        _package(directory);
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
    
    private void packageWar(File directory, Element pom) throws IOException {
        System.out.println("Packaging war file");
        Result pomRes = Result.fromContent(pom);
        String version = pomRes.getAsString("project/version");
        String packaging = pomRes.getAsString("project/packaging");
        if ("war".equals(packaging)) {
            try {
                String artifactId = pomRes.getAsString("project/artifactId");
                File warDir = new File(directory, "target"+File.separator+artifactId+"-"+version);
                if (!warDir.exists()) {
                    try {
                        // War dir doesn't exist.  We may need to run maven first
                        ProcessBuilder pb = new ProcessBuilder();
                        pb.inheritIO();
                        pb.command("mvn", "package", "-DskipTests=true", "-Dmaven.javadoc.skip=true");
                        Process process = pb.start();
                        int code = process.waitFor();
                        if (code != 0) {
                            System.exit(code);
                        }
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                if (!warDir.exists()) {
                    System.err.println("war directory not found.  Expecting it at "+warDir);
                    System.exit(1);
                }
                
                File bin = new File(directory, "bin");
                if (!bin.exists()) {
                    bin.mkdir();
                }
                
                File dest = new File(bin, warDir.getName());
                FileUtils.copyDirectory(warDir, dest);
                
                // Now we need to create the stub.
                File pkgPath = new File(bin, "ca"+File.separator+"weblite"+File.separator+"jdeploy");
                pkgPath.mkdirs();
                File stubFile = new File(pkgPath, "WarRunner.java");
                InputStream warRunnerInput = getClass().getResourceAsStream("WarRunner.javas");
                FileUtils.copyInputStreamToFile(warRunnerInput, stubFile);
                
                String stubFileSrc = FileUtils.readFileToString(stubFile, "UTF-8");
                int port = 0;
                
                
                File packageJson = new File(directory, "package.json");
                String packageJsonStr = FileUtils.readFileToString(packageJson, "UTF-8");
                JSONParser jparser = new JSONParser();
                Map packageJsonMap = (Map)jparser.parseJSON(new StringReader(packageJsonStr));
                Result res = Result.fromContent(packageJsonMap);
                if (packageJsonMap.containsKey("jdeployPort")) {
                    port = res.getAsInteger("jdeployPort");
                }
                
                stubFileSrc = stubFileSrc.replace("{{PORT}}", String.valueOf(port));
                stubFileSrc = stubFileSrc.replace("{{WAR_PATH}}", warDir.getName());
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
                int javacResult = javacP.waitFor();
                if (javacResult != 0) {
                    System.exit(javacResult);
                }
                
                // Now need to copy the jdeploy.js file and update it to run our stub
                InputStream jdeployJs = this.getClass().getResourceAsStream("jdeploy.js");
                File jDeployFile = new File(bin, "jdeploy.js");

                FileUtils.copyInputStreamToFile(jdeployJs, jDeployFile);
                
                String jdeployContents = FileUtils.readFileToString(jDeployFile, "UTF-8");
                jdeployContents = jdeployContents.replace("{{CLASSPATH}}", "lib/jetty-runner.jar");
                jdeployContents = jdeployContents.replace("{{MAIN_CLASS}}", "ca.weblite.jdeploy.WarRunner");
                FileUtils.writeStringToFile(jDeployFile, jdeployContents, "UTF-8");
                
                
            } catch (InterruptedException ex) {
                Logger.getLogger(JDeploy.class.getName()).log(Level.SEVERE, null, ex);
            }
                    
        } else {
            System.err.println("Attempt to package war but packaging type in pom.xml is "+packaging);
            System.exit(1);
        }
    }
    

    private void _package(File directory) throws IOException {
        
        File pom = new File(directory, "pom.xml");
        if (pom.exists()) {
            XMLParser xmlParser = new XMLParser();
            String pomSrc = FileUtils.readFileToString(pom, "UTF-8");
            Element root = xmlParser.parse(new StringReader(pomSrc));
            Result pomRes = Result.fromContent(root);
            String version = pomRes.getAsString("project/version");
            String packaging = pomRes.getAsString("project/packaging");
            if ("war".equals(packaging)) {
                packageWar(directory, root);
                return;
            } else {
                System.err.println("Only war currently supported for packaging in maven projects.  Found "+packaging);
                System.exit(1);
            }

        }
        
        File dist = new File(directory, "dist");
        if (!dist.exists()) {
            throw new RuntimeException("Directory " + dist + " doesn't exist");
        }
        File bin = new File(directory, "bin");
        File mainJar = null;
        for (File child : dist.listFiles()) {
            File dest = new File(bin, child.getName());
            if (child.isDirectory()) {
                FileUtils.copyDirectory(child, dest);
            } else {
                FileUtils.copyFile(child, dest);
            }
            if (child.getName().endsWith(".jar")) {
                mainJar = child;
            }
        }
        InputStream jdeployJs = this.getClass().getResourceAsStream("jdeploy.js");
        File jDeployFile = new File(bin, "jdeploy.js");
        
        FileUtils.copyInputStreamToFile(jdeployJs, jDeployFile);
        if (mainJar != null) {
            String jdeployContents = FileUtils.readFileToString(jDeployFile, "UTF-8");
            jdeployContents = jdeployContents.replace("{{JAR_NAME}}", mainJar.getName());
            FileUtils.writeStringToFile(jDeployFile, jdeployContents, "UTF-8");
        }

    }

    private void publish(File directory) throws IOException {
        install(directory);
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
        formatter.printHelp("jdeploy [init|package|install|publish]", opts);
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            JDeploy prog = new JDeploy();
            CommandLineParser parser = new DefaultParser();
            Options opts = new Options();
            CommandLine line = parser.parse(opts, args);
            args = line.getArgs();
            if (args.length == 0) {
                prog.help(opts);
                System.exit(1);
            }
            if ("package".equals(args[0])) {
                prog._package(new File(".").getAbsoluteFile());
            } else if ("init".equals(args[0])) {
               
                
                prog.init(new File(".").getAbsoluteFile(), null);
            } else if ("install".equals(args[0])) {
                prog.install(new File(".").getAbsoluteFile());
            } else if ("publish".equals(args[0])) {
                prog.publish(new File(".").getAbsoluteFile());
            } else {
                prog.help(opts);
                System.exit(1);
            }

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

}

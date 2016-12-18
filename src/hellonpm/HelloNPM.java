/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hellonpm;

import com.codename1.io.JSONParser;
import com.codename1.processing.Result;
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
public class HelloNPM {

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
            if (!contents.containsKey("jDeployCommandName")) {
                contents.put("jDeployCommandName", commandName);
                if (!contents.containsKey("bin")) {
                    contents.put("bin", new HashMap());
                }
                Map bins = (Map)contents.get("bin");
                if (!bins.containsKey(commandName)) {
                    bins.put(commandName, "bin/jdeploy.js");
                }
                contents.put("preferGlobal", true);
                
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
            Logger.getLogger(HelloNPM.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }

    private void _package(File directory) throws IOException {
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
            Logger.getLogger(HelloNPM.class.getName()).log(Level.SEVERE, null, ex);
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
            HelloNPM prog = new HelloNPM();
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

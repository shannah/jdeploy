/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.joshondesign.appbundler.webos;

import ca.weblite.jdeploy.appbundler.AppDescription;
import ca.weblite.jdeploy.appbundler.Jar;
import ca.weblite.jdeploy.appbundler.NativeLib;
import ca.weblite.jdeploy.appbundler.Util;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 *
 * @author josh
 */
public class WebosBundler {
   public static void start(AppDescription app, String dest_dir) throws Exception {
       File destDir = new File(dest_dir+"/webos/");
       File appDir = new File(destDir,app.getName());
       appDir.mkdirs();
       p("app dir exists = " + appDir.exists());
       
       //copy the jars over
       for(Jar jar : app.getJars()) {
           p("processing jar = " + jar.getName() + " os = "+jar.getOS());
           if(jar.isOSSpecified()) {
               if(!jar.matchesOS("webos")) {
                   p("   skipping jar");
                   continue;
               }
           }
           File jarFile = new File(appDir,jar.getName());
           Util.copyToFile(jar.getFile(), jarFile);
       }
       
       //copy the natives over
       processNatives(appDir, app, "webos");

       //generate appinfo.json
       generateAppInfo(appDir,app);
       
       //generate runit.sh
       generateRunScript(appDir,app);

   }

    private static void p(String string) {
        System.out.println(string);
    }

    private static void generateAppInfo(File appDir, AppDescription app) throws IOException, Exception {
        PrintWriter out = new PrintWriter(new FileWriter(new File(appDir,"appinfo.json")));
        out.println("{");
        kv(out,"id",app.getMainClass().toLowerCase());
        kv(out,"version","0.0.1");
        kv(out,"vendor","unknown vendor");
        kv(out,"type","pdk");
        kv(out,"main","runit.sh");
        kv(out,"title",app.getName());
        kv(out,"icon","icon.png");
        
        out.println("  \"requiredMemory\":64");
        out.println("}");
        /*
                {
	"id": "${appid}",
	"version": "0.0.1",
	"vendor": "josh",
	"type": "pdk",
	"main": "runit.sh",
	"title": "jpart",
    "icon": "icon.png",
    "requiredMemory": 64
}*/
        out.close();

    }

    private static void kv(PrintWriter out, String key, String value) {
        out.println("  \""+key+"\":\""+value+"\",");
    }

    private static void generateRunScript(File appDir, AppDescription app) throws IOException, Exception {
        PrintWriter out = new PrintWriter(new FileWriter(new File(appDir,"runit.sh")));
        out.println("#!/bin/sh");
        String filename = "/tmp/"+app.getMainClass()+".out";
        out.println("date >> "+filename);
        out.println("echo \"trying to start avian\" >> "+filename);
        out.println("echo $PWD >> "+filename);
        out.println("export LD_LIBRARY_PATH=.");
        out.print("./avian -Dcom.joshondesign.amino.impl=sdl");
        out.print(" -cp ");
        for(NativeLib lib : app.getNativeLibs()) {
            for(File os : lib.getOSDirs()) {
                if("webos".equals(os.getName())) {
                    for(File file : os.listFiles()) {
                        if(file.getName().endsWith(".jar")) {
                            out.print(file.getName()+":");
                        }
                    }
                }
            }
            for(File jar : lib.getCommonJars()) {
                out.print(jar.getName()+":");
            }
        }
        for(Jar jar : app.getJars()) {
            out.print(jar.getFile().getName()+":");
        }
        out.print(" "+app.getMainClass());
        out.print(" 1>>"+filename+" 2>>"+filename);
        out.println();
        //-cp classpath.jar:sdljava.jar:sdljava_test.jar:examples.jar:amino_sdl.jar:Amino2.jar 
        //com.joshondesign.amino.examples.Particles &amp;> /tmp/avian.out        

        out.close();
    }

    private static void processNatives(File appDir, AppDescription app, String targetOS) throws FileNotFoundException, IOException {
        //track the list of files in the appbundler_tasks.xml
        for(NativeLib lib : app.getNativeLibs()) {
            p("sucking in native lib: " + lib.getName());
            for(File os : lib.getOSDirs()) {
                if(targetOS.equals(os.getName())) {
                    p("doing natives for OS = " + os.getName());
                    for(File file : os.listFiles()) {
                        p("   file = " + file.getName());
                        File destFile = new File(appDir, file.getName());
                        p("copying to file: " + destFile);
                        Util.copyToFile(file, destFile);
                    }
                }
            }
            for(File jar : lib.getCommonJars()) {
                p("copying over native lib jar: " + jar.getName());
                Util.copyToFile(jar, new File(appDir, jar.getName()));
            }
        }
  
    }
}

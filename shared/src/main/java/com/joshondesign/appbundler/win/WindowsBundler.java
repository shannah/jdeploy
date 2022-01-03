/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.joshondesign.appbundler.win;

import ca.weblite.jdeploy.appbundler.AppDescription;
import ca.weblite.jdeploy.appbundler.Jar;
import ca.weblite.jdeploy.appbundler.NativeLib;
import ca.weblite.jdeploy.appbundler.Util;
import com.joshondesign.xml.XMLWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author joshmarinacci
 */
public class WindowsBundler {

    private static final String OSNAME = "win";

    public static void start(AppDescription app, String DEST_DIR) throws FileNotFoundException, Exception {
        //create the 'win' directory
        File destDir = new File(DEST_DIR+"/"+OSNAME+"/");
        destDir.mkdirs();

        String exeName = app.getName() +".exe";
        //create a temp dir for jsmooth to run in
        File tempdir = File.createTempFile("AppBundler", "jsmooth.dir");
        tempdir.delete();
        tempdir.mkdir();
        if(!tempdir.isDirectory()) {
            throw new Exception("temp dir isn't a directory! " + tempdir.getAbsolutePath());
        }
        if(!tempdir.canWrite()) {
            throw new Exception("can't write to tempdir! " + tempdir.getAbsolutePath());
        }
        
        //generate a project file in tempdir
        File projectFile = new File(tempdir, "app.jsmooth");
        p("generating project file: " + projectFile.getAbsolutePath());
        XMLWriter xml = new XMLWriter(projectFile);
        generateProjectFile(xml,app);
        xml.close();

        //extract the megajar for jsmooth to tempdir
        File smoothGenJar = new File(tempdir,"jsmoothgen.jar");
        Util.copyToFile(WindowsBundler.class.getResource("resources/jsmoothgen.jar"),smoothGenJar);
        File jox116Jar = new File(tempdir,"jox116.jar");
        Util.copyToFile(WindowsBundler.class.getResource("resources/jox116.jar"),jox116Jar);
        File msvcr71DLL = new File(tempdir,"msvcr71.dll");
        Util.copyToFile(WindowsBundler.class.getResource("resources/msvcr71.dll"),msvcr71DLL);

        //extract skeletons for jsmooth to tempdir
        File wrapperDir = new File(tempdir,"skeletons/autodownload-wrapper");
        wrapperDir.mkdirs();
        p("skel dir = " + wrapperDir.getAbsolutePath());
        Util.copyToFile(WindowsBundler.class.getResource("resources/skeletons/autodownload-wrapper/autodownload.exe"),
                new File(wrapperDir,"autodownload.exe"));
        Util.copyToFile(WindowsBundler.class.getResource("resources/skeletons/autodownload-wrapper/autodownload.skel"),
                new File(wrapperDir,"autodownload.skel"));
        Util.copyToFile(WindowsBundler.class.getResource("resources/skeletons/autodownload-wrapper/customdownload.skel"),
                new File(wrapperDir,"customdownload.skel"));

        //invoke jsmooth in a temp dir
        String[]command = new String[]{
            "java"
            ,"-Djsmooth.basedir="+tempdir.getAbsolutePath()
            ,"-cp"
            ,smoothGenJar.getAbsolutePath()+File.pathSeparator+jox116Jar.getAbsolutePath()
            ,"net.charabia.jsmoothgen.application.cmdline.CommandLine"
            ,projectFile.getAbsolutePath()
        };
        executeAndWait(command);

        //copy exe to output dir
        File exeFile = new File(tempdir,exeName);
        Util.copyToFile(exeFile, new File(destDir,exeName));
        Util.copyToFile(msvcr71DLL, new File(destDir,"msvcr71.dll"));
        
        //copy jars to output dir
        File libDir = new File(destDir,"lib");
        libDir.mkdir();
        for(Jar jar : app.getJars()) {
            p("processing jar = " + jar.getName() + " os = "+jar.getOS());
            if(jar.isOSSpecified()) {
                if(!jar.matchesOS(OSNAME)) {
                    p("   skipping jar");
                    continue;
                }
            }
            File jarFile = new File(libDir,jar.getName());
            Util.copyToFile(jar.getFile(), jarFile);
        }


        processNatives(libDir, app);
    }
    private static void processNatives(File javaDir, AppDescription app) throws IOException {
        //track the list of files in the appbundler_tasks.xml
        for(NativeLib lib : app.getNativeLibs()) {
            p("sucking in native lib: " + lib);
            for(File os : lib.getOSDirs()) {
                p("os = " + os.getName());
                if(OSNAME.equals(os.getName())) {
                    for(File file : os.listFiles()) {
                        File destFile = new File(javaDir, file.getName());
                        Util.copyToFile(file, destFile);
                    }
                }
            }
            for(File jar : lib.getCommonJars()) {
                p("copying over native common jar: " + jar.getName());
                Util.copyToFile(jar, new File(javaDir, jar.getName()));
            }
            for(File jar : lib.getPlatformJars(OSNAME)) {
                p("copying over native only jar: " + jar.getName());
                Util.copyToFile(jar, new File(javaDir, jar.getName()));
            }
        }
    }

    private static void generateProjectFile(XMLWriter xml, AppDescription app) throws Exception {
        xml.header();
        xml.start("jsmoothproject");
        xml.start("JVMSearchPath").text("bundled").end();
        xml.start("arguments").end();
        xml.start("bundledJVMPath").text("jre").end();
        xml.start("currentDirectory").text("${EXECUTABLEPATH}").end();//</currentDirectory>\n")

        for(Jar jar : app.getJars()) {
            xml.start("classPath").text("lib\\"+jar.getName()).end();
        }
        for(NativeLib lib : app.getNativeLibs()) {
            for(File jar : lib.getCommonJars()) {
                xml.start("classPath").text("lib\\"+jar.getName()).end();
            }
            for(File jar : lib.getPlatformJars(OSNAME)) {
                xml.start("classPath").text("lib\\"+jar.getName()).end();
            }
        }
        xml.start("embeddedJar").text("false").end();
        xml.start("executableName").text(app.getName()+".exe").end();


        xml.start("javaProperties")
                .start("name").text("java.library.path").end()
                .start("value").text("./lib").end()
                .end();

        //<initialMemoryHeap>-1</initialMemoryHeap>
        xml.start("initialMemoryHeap").text("-1").end();
        //<mainClassName>org.joshy.sketch.Main</mainClassName>
        xml.start("mainClassName").text(app.getMainClass()).end();
        //<maximumMemoryHeap>-1</maximumMemoryHeap>
        xml.start("maximumMemoryHeap").text("-1").end();
        //<maximumVersion></maximumVersion>
        //<minimumVersion>1.6</minimumVersion>
        xml.start("minimumVersion").text("1.6").end();
        //<skeletonName>Autodownload; Wrapper</skeletonName>
        xml.start("skeletonName").text("Autodownload Wrapper").end();

        Map<String,String> skeletonProperties = new HashMap<String, String>();
        skeletonProperties.put("SingleProcess","1");
        skeletonProperties.put("SingleInstance","0");
        skeletonProperties.put("JniSmooth","0");
        skeletonProperties.put("Debug","1");
        skeletonProperties.put("PressKey","1");
        for(String key : skeletonProperties.keySet()) {
            xml.start("skeletonProperties");
            xml.start("key").text(key).end();
            xml.start("value").text(skeletonProperties.get(key)).end();
            xml.end();
        }

        xml.end();
    }

    private static void p(String string) {
        System.out.println(string);
    }

    private static void executeAndWait(String[] command) throws IOException, InterruptedException {
        p("running executable:");
        for(String s : command) {
            p("    "+s);
        }
        Process proc = Runtime.getRuntime().exec(command);
        InputStream stdout = proc.getErrorStream();
        byte[] buf = new byte[1024*16];
        while(true) {
            int n = stdout.read(buf);
            if(n < 0) break;
            System.out.write(buf,0,n);
            System.out.flush();
        }
        stdout.close();
        int exit = proc.waitFor();
        p("exit value of jsmooth = " + exit);
    }

}

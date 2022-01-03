/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.joshondesign.appbundler.onejar;

import ca.weblite.jdeploy.appbundler.AppDescription;
import ca.weblite.jdeploy.appbundler.Jar;
import ca.weblite.jdeploy.appbundler.NativeLib;
import com.joshondesign.xml.XMLWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author joshmarinacci
 */
public class OneJarBundler {

    public static void start(AppDescription app, String DEST_DIR) {
        try {
            //set up the xml file
            StringWriter sout = new StringWriter();
            PrintWriter pout = new PrintWriter(sout);
            XMLWriter xml = new XMLWriter(pout,null);
            xml.header();

            xml.start("app");
            xml.attr("mainclass", app.getMainClass());

            Manifest manifest = new Manifest();
            Attributes a = manifest.getMainAttributes();
            //a.putValue("Main-Class", app.getMainClass());
            a.putValue("Main-Class", "com.joshondesign.appbundler.onejar.LaunchStub");
            a.put(Attributes.Name.MANIFEST_VERSION,"1.0");
            new File(DEST_DIR).mkdirs();
            
            Set<String> writtenNames = new HashSet<String>();
            p("doing a one jar bundler");
            JarOutputStream jarOut = new JarOutputStream(
                    new FileOutputStream(new File(DEST_DIR,app.getName()+".onejar.jar")),
                    manifest);

            for(Jar jar : app.getJars()) {
                copyJar(jarOut, jar.getFile(), writtenNames);
            }

            processNatives(xml, jarOut, app, writtenNames);

            copyLaunchStub(jarOut,"LaunchStub.class");
            copyLaunchStub(jarOut,"LaunchStub$Follower.class");
            
            //finish up the tasks xml file
            xml.end();
            xml.flush();
            pout.close();
            //write out the tasks xml file
            jarOut.putNextEntry(new JarEntry("appbundler_tasks.xml"));
            OutputStreamWriter cout = new OutputStreamWriter(jarOut);
            cout.write(sout.getBuffer().toString());
            cout.flush();

            //Flush and close all the streams
            jarOut.flush();
            jarOut.close();

        } catch (Exception ex) {
            Logger.getLogger(OneJarBundler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static void p(String string) {
        System.out.println(string);
    }

    private static void copyJar(JarOutputStream jarOut, File jarFile, Set<String> writtenNames) throws IOException {
        //Create a read buffer to transfer data from the input
        byte[] buf = new byte[4096];
        JarInputStream jarIn = new JarInputStream(new FileInputStream(jarFile));
        //Iterate the entries
        JarEntry entry;
        while ((entry = jarIn.getNextJarEntry()) != null) {
            //Exclude the manifest file from the old JAR
            if ("META-INF/MANIFEST.MF".equals(entry.getName())) continue;
            //Write the entry to the output JAR
            //p("writing entry: " + entry.getName());
            if(!writtenNames.contains(entry.getName())) {
                //TODO: compression is broken here
                //entry.setMethod(JarEntry.DEFLATED);
                jarOut.putNextEntry(entry);
                int read;
                while ((read = jarIn.read(buf)) != -1) {
                    jarOut.write(buf, 0, read);
                }
                jarOut.closeEntry();
                writtenNames.add(entry.getName());
            }
        }
        jarIn.close();
    }

    private static void processNatives(XMLWriter xml, JarOutputStream jarOut, AppDescription app, Set<String> writtenNames) throws IOException {
        //track the list of files in the appbundler_tasks.xml
        xml.start("filesToCopy");
        byte[] buf = new byte[4096];
        for(NativeLib lib : app.getNativeLibs()) {
            p("sucking in native lib: " + lib);
            for(File os : lib.getOSDirs()) {
                p("os = " + os.getName());
                for(File file : os.listFiles()) {
                    p("   file = " + file.getName());
                    jarOut.putNextEntry(new JarEntry("native/"+os.getName()+"/"+file.getName()));
                    FileInputStream fin = new FileInputStream(file);
                    while(true) {
                        int n = fin.read(buf);
                        if(n < 0) break;
                        jarOut.write(buf,0,n);
                    }
                    jarOut.closeEntry();
                    xml.start("native").attr("os",os.getName()).attr("file",file.getName());
                    xml.end();
                }
            }
            for(File jar : lib.getCommonJars()) {
                p("copying over native lib jar: " + jar.getName());
                copyJar(jarOut, jar, writtenNames);
            }
        }
        xml.end();
    }

    private static void copyLaunchStub(JarOutputStream jarOut, String resource) throws IOException {
        InputStream fin = OneJarBundler.class.getResourceAsStream(resource);
        jarOut.putNextEntry(new JarEntry("com/joshondesign/appbundler/onejar/"+resource));
        byte[] buf = new byte[4096];
        while(true) {
            int n = fin.read(buf);
            if(n < 0) break;
            jarOut.write(buf,0,n);
        }
        jarOut.closeEntry();
        fin.close();
    }

}

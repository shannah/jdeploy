package com.joshondesign.appbundler.jnlp;

import ca.weblite.jdeploy.appbundler.AppDescription;
import ca.weblite.jdeploy.appbundler.Jar;
import ca.weblite.jdeploy.appbundler.Util;

import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: joshmarinacci
 * Date: Sep 7, 2010
 * Time: 5:42:17 PM
 * To change this template use File | Settings | File Templates.
 */
public class JNLPBundler {
    
    public static void start(AppDescription app, String dest_dir, String codebase) throws Exception {
        //create the dir structure
        File destDir = new File(dest_dir+"/jnlp/");
        destDir.mkdirs();
        File libdir = new File(destDir,"lib");
        libdir.mkdirs();

        //copy over the jars
        for(Jar jar : app.getJars()) {
            File jarFile = new File(libdir,jar.getName());
            Util.copyToFile(jar.getFile(),jarFile);
        }

        buildJNLP(app,destDir, codebase);
    }

    private static void buildJNLP(AppDescription app, File dir, String codebase) throws Exception {
        PrintWriter out = new PrintWriter(new File(dir, app.getName()+".jnlp"));
        out.println("<jnlp spec='1.0+' codebase='"+codebase+"' href='Leonardo.jnlp'>");
        out.println("<information>");
        out.println("  <title>"+app.getName()+"</title>");
        out.println("  <vendor>Josh Marinacci</vendor>");
        out.println("  <homepage href='http://leonardosketch.org/'/>");
        out.println("  <description>general purpose drawing tool</description>");
        out.println("  <offline-allowed/>");
        for(String iconS : app.getAppIcons()) {
            if(iconS.toLowerCase().endsWith(".png")) {
                File icon = new File(iconS);
                out.println("  <icon href='"+icon.getName()+"' />");
                File nicon = new File(dir,icon.getName());
                p("copying icon to: "+ nicon.getAbsolutePath());
                Util.copyToFile(icon, nicon);
            }
        }
        for(String ext : app.getExtensions()) {
            p("generating extension for: " + ext);
            out.println("  <association"
                    +" mime-type=\""+app.getExtensionMimetype(ext)+"\""
                    +" extensions=\""+ext+"\""
                    +" >");
            out.println("    <description>"+ext+" file</description>");
            //out.println("<icon href=\"doc-icon.png\"/>");
            out.println("  </association>");
        }
        out.println("<shortcut online=\"false\"> <desktop/> </shortcut>");
        out.println("</information>");
        out.println("<security><all-permissions/></security>");
        out.println("<update check='always' policy='prompt-update'/>");
        out.println("<resources>");
        out.println("  <j2se version='1.6+' initial-heap-size='64m' max-heap-size='512m'/>");
//<!--                                                java-vm-args="-d32"-->

        for(Jar jar : app.getJars()) {
            out.print("  <jar href='lib/"+jar.getName()+"' ");
            out.print("main='"+jar.isMain()+"'");
            out.println("/>");
        }
        /*
                <jar href="Sketch.jar" main="true" download="eager"/>
                <jar href="Core.jar" main="false" download="eager"/>
                <jar href="MacStubs.jar"/>
                <jar href="parboiled-0.9.7.3.jar"/>
                <jar href="apache-mime4j-0.6.jar"/>
                <jar href="commons-codec-1.4.jar"/>
                <jar href="commons-logging-1.1.1.jar"/>
                <jar href="httpclient-4.0.1.jar"/>
                <jar href="httpcore-4.0.1.jar"/>
                <jar href="httpcore-nio-4.0.1.jar"/>
                <jar href="httpmime-4.0.1.jar"/>
                <jar href="XMLLib.jar"/>
                <jar href="twitter4j-core-2.1.4-SNAPSHOT.jar"/>*/
        out.println("  <property name='apple.laf.useScreenMenuBar' value='true'/>");
        out.println("</resources>");
        out.println("<application-desc main-class='"+app.getMainClass()+"'></application-desc>");
        out.println("</jnlp>");
        out.flush();
        out.close();
    }

    private static void p(String s) {
        System.out.println(s);
    }

}

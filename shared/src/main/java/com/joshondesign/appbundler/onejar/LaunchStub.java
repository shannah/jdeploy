/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.joshondesign.appbundler.onejar;

import com.joshondesign.xml.Doc;
import com.joshondesign.xml.Elem;
import com.joshondesign.xml.XMLParser;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author joshmarinacci
 */
public class LaunchStub {
    static File tmp;
    static {
        try {
            tmp = File.createTempFile("LaunchStub", "_dir");
            tmp = new File(tmp.getParentFile(),"dir_"+tmp.getName());
            tmp.mkdir();
            p("tempdir = " + tmp.getAbsolutePath());
            p("exists = " + tmp.exists() + " dir = " + tmp.isDirectory());
            String java_library_path = tmp.getAbsolutePath();
            p("setting java.library.path to: " + java_library_path);
            System.setProperty("java.library.path", java_library_path);
        } catch (IOException ex) {
            Logger.getLogger(LaunchStub.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void main(String ... args) throws Exception {
        System.out.println("I'm the launcher stub");


        Doc doc = XMLParser.parse(LaunchStub.class.getResourceAsStream("/appbundler_tasks.xml"));
        for(Elem e : doc.xpath("/app/filesToCopy/native")) {
            System.out.println("extracting file: " + e.attr("file"));
            File tmpFile = new File(tmp,e.attr("file"));
            System.out.println("to file " + tmpFile.getAbsolutePath());
            String pth = "/native/" + e.attr("os") + "/" + e.attr("file");
            p("extracting path: " + pth);
            InputStream inStream = LaunchStub.class.getResourceAsStream(pth);
            OutputStream outStream = new FileOutputStream(tmpFile);
            byte[] buf = new byte[1024];
            while(true) {
                int n = inStream.read(buf);
                if(n < 0) break;
                outStream.write(buf,0,n);
            }
            outStream.close();
            inStream.close();
        }


        printProperties();
        String mainClass =  doc.xpathString("/app/@mainclass");
        //launchApp(mainClass, args);
        spawnApp(mainClass, args);
    }

    private static void p(String string) {
        System.out.println(string);
        System.out.flush();
    }

    private static void printProperties() {
        p("System properties");
        for(Object prop : System.getProperties().keySet()) {
            String propName = (String) prop;
            String propValue = System.getProperty(propName);
            p(propName + " = " + propValue);
        }
    }

    private static void launchApp(String mainClass, String[] args) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        System.out.println("I'm going to launch: " + mainClass);
        Class clss = LaunchStub.class.forName(mainClass);
        Method method = clss.getMethod("main", String[].class);
        method.invoke(null, new Object[]{args});
    }

    private static void spawnApp(String mainClass, String[] args) throws IOException {
        String[] command = {
            System.getProperty("java.home")+"/bin/java",
            "-Djava.library.path="+tmp.getAbsolutePath(),
            "-cp",
            System.getProperty("java.class.path"),
            mainClass
        };
        p("spawning:");
        for(String s : command) {
            p("  " + s);
        }
        Process proc = Runtime.getRuntime().exec(command);
        followStream(proc.getErrorStream());
        followStream(proc.getInputStream());
    }

    private static void followStream(final InputStream in) {
        Thread th = new Thread(new Follower(in));
        th.start();
    }

    public static class Follower implements Runnable {
        private final InputStream in;

        private Follower(InputStream in) {
            this.in = in;
        }

        public void run() {
            while (true) {
                try {
                    int ch = in.read();
                    if(ch == -1) break;
                    System.err.write(ch);
                    System.err.flush();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

    }

}

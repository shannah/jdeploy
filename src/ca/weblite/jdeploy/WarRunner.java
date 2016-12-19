/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.jdeploy;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jetty.runner.Runner;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 *
 * @author shannah
 */
public class WarRunner {
    public static final int port = 0;
    public static final String warPath = "{{WAR_PATH}}";
    
    public static void main0(String[] args) throws Exception {
        Thread t = new Thread(()->{
            try {
                Runner runner = new Runner();
                runner.configure(new String[]{
                    "--port",
                    "8089",
                    "/Users/shannah/cn1_files/tests/TestMavenWebapp/bin/TestMavenWebapp-1.0-SNAPSHOT"});
                runner.run();
            } catch (Exception ex) {
                Logger.getLogger(WarRunner.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        t.start();
        System.out.println("Running...");
        Desktop.getDesktop().browse(new URI("http://localhost:8089"));
        t.join();
    }
    
    public static void main(String[] args) throws Exception {
        
        Server server = new Server(port);
        // Setup JMX
        //MBeanContainer mbContainer = new MBeanContainer(
        //        ManagementFactory.getPlatformMBeanServer());
        //server.addBean(mbContainer);

        // The WebAppContext is the entity that controls the environment in
        // which a web application lives and breathes. In this example the
        // context path is being set to "/" so it is suitable for serving root
        // context requests and then we see it setting the location of the war.
        // A whole host of other configurations are available, ranging from
        // configuring to support annotation scanning in the webapp (through
        // PlusConfiguration) to choosing where the webapp will unpack itself.
        WebAppContext webapp = new WebAppContext();
        webapp.setContextPath("/");
        //File warFile = new File(
                //"/Users/shannah/cn1_files/tests/TestMavenWebapp/bin/TestMavenWebapp-1.0-SNAPSHOT");
        File warFile = new File(warPath);
        webapp.setWar(warFile.getAbsolutePath());

        // A WebAppContext is a ContextHandler as well so it needs to be set to
        // the server so it is aware of where to send the appropriate requests.
        server.setHandler(webapp);

        // Start things up!
        server.start();

        System.out.println("Running...");
        
        int port = ((ServerConnector)server.getConnectors()[0]).getLocalPort();
        Desktop.getDesktop().browse(new URI("http://localhost:"+port));
        // The use of server.join() the will make the current thread join and
        // wait until the server is done executing.
        // See http://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html#join()
        server.join();
    }
}

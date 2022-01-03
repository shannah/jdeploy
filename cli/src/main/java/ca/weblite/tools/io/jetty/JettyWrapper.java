/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.tools.io.jetty;

import org.eclipse.jetty.util.Jetty;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author shannah
 */
public class JettyWrapper extends Observable implements Runnable {
    private File docRoot;
    private int port;
    private Process proc;
    public boolean running;
    
    private boolean stopped;
    
    public JettyWrapper(File docRoot) {
        this.docRoot = docRoot;
    }
    
    private File getJettyRunnerJar() {
        return new File(Jetty.class.getProtectionDomain().getCodeSource().getLocation().getPath());
    }
    
    private File getJava() {
        File java = new File(System.getProperty("java.home"));
        java = new File(java, "bin/java");
        return java;
    }

    public void stop() {
        stopped = true;
        try {
            proc.destroyForcibly();
        } catch (Throwable t){}
    }
    
    @Override
    public void run() {
        if (stopped) {
            return;
        }
        if (port == 0) {
            try {
                ServerSocket sock = new ServerSocket(0);
                port = sock.getLocalPort();
                sock.close();
            } catch (IOException ex) {
                Logger.getLogger(JettyWrapper.class.getName()).log(Level.SEVERE, null, ex);
            }
            
        }
        ProcessBuilder pb = new ProcessBuilder(
                getJava().getAbsolutePath(), 
                "-jar", 
                getJettyRunnerJar().getAbsolutePath(),
                "--port",
                String.valueOf(port),
                docRoot.getAbsolutePath()
        );
        pb.inheritIO();
        try {
            
            new Thread(()->{
                while (true) {
                    if (testConnection()) {
                        running = true;
                        setChanged();
                        notifyObservers();
                    }
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(JettyWrapper.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }).start();
            proc = pb.start();
        } catch (IOException ex) {
            Logger.getLogger(JettyWrapper.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        try {
            proc.waitFor();
        } catch (InterruptedException ex) {
            if (stopped) {
                stopped = false;
            } else {
                Logger.getLogger(JettyWrapper.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        stopped = false;
        running = false;
        setChanged();
        notifyObservers();
        
    }
    
    private boolean testConnection() {
        try {
            URL url = new URL("http://localhost:"+port);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (conn.getResponseCode() == 200) {
                return true;
            }
        } catch (Throwable ex) {
            
        }
        return false;
    }
    
    public int getExitCode() {
        if (proc != null) {
            return proc.exitValue();
        }
        return -1;
    }
    
    public int getPort() {
        return port;
    }
    
    
    public boolean isRunning() {
        return running;
    }
    
    public boolean waitReady(int timeout) throws InterruptedException {
        final boolean[] ready = new boolean[1];
        Observer o = new Observer() {
            @Override
            public void update(Observable o, Object arg) {
                if (isRunning()) {
                    deleteObserver(this);
                    synchronized(ready) {
                        ready[0] = true;
                        ready.notifyAll();
                    }
                }
            }
        };
        addObserver(o);
        
        if (!ready[0]) {
            synchronized(ready) {
                ready.wait(timeout);
            }
        }
        return ready[0];
    }
}

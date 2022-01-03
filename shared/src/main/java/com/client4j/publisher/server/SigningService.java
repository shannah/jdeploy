/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.publisher.server;

import ca.weblite.tools.io.ArchiveUtil;
import ca.weblite.tools.io.FileUtil;
import ca.weblite.tools.io.IOUtil;
import ca.weblite.jdeploy.appbundler.Util;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;

import static ca.weblite.tools.io.FileUtil.delTree;
import static ca.weblite.jdeploy.appbundler.Util.createDMG;

/**
 *
 * @author shannah
 */
public class SigningService {
    
    public void execute(Socket sock) throws IOException {
        execute(sock.getInputStream(), sock.getOutputStream());
    }
    
    public void execute(InputStream input, OutputStream output) throws IOException {
        ObjectInputStream ois = (input instanceof ObjectInputStream) ? (ObjectInputStream)input : 
                new ObjectInputStream(input);
        ObjectOutputStream oos = (output instanceof ObjectOutputStream) ? (ObjectOutputStream)output :
                new ObjectOutputStream(output);
        execute(ois, oos);
    }
    
    public void execute(ObjectInputStream input, ObjectOutputStream output) throws IOException {
        SigningRequest req;
        SigningResponse res = new SigningResponse();
        try {
            req = (SigningRequest)input.readObject();
            try {
                execute(req, res);
            } catch (Exception ex) {
                res.setResponseCode(500);
                res.setErrorMessage(ex.getMessage());
                StringWriter writer = new StringWriter();
                ex.printStackTrace(new PrintWriter(writer));
                res.setStackTrace(writer.toString());
            }
            output.writeObject(res);
            req.cleanup();
            res.cleanup();
            
        } catch (ClassNotFoundException cex) {
            throw new IOException(cex);
        }
    }
    
    public SigningResponse executeWrapper(SigningRequest req) {
        SigningResponse res = new SigningResponse();
        try {
            execute(req, res);
        } catch (Exception ex) {
            res.setResponseCode(500);
            res.setErrorMessage(ex.getMessage());
            StringWriter writer = new StringWriter();
            ex.printStackTrace(new PrintWriter(writer));
            res.setStackTrace(writer.toString());
        }
        
        return res;
            
        
    }
    
    
    
    public void execute(SigningRequest req, SigningResponse response) throws Exception {
        File tmpFile = File.createTempFile("SigningService", "dir");
        tmpFile.delete();
        tmpFile.mkdir();
        response.addCleanupTask(()->{
            FileUtil.delTree(tmpFile);
        });
        System.out.println("Extracting file "+req.getFile()+" to "+tmpFile);
        if (req.getFile().getName().endsWith(".tar.gz")) {
            Util.decompressTarGz(req.getFile(), tmpFile);
        } else {
            ArchiveUtil.extract(req.getFile(), tmpFile, null);
        }
        File appFile = null;
        for (File f : tmpFile.listFiles()) {
            if (f.getName().endsWith(".app")) {
                appFile = f;
                break;
            }
        }
        if (appFile == null) {
            throw new IOException("No app found in archive");
        }
        if (req.isCodesign()) {
            System.out.println("About ot codesign");
            response.setRanCodesign(true);
            //codesign --deep --verbose=4 -f -s "$CERT" "$1"
            ProcessBuilder pb = new ProcessBuilder("/usr/bin/codesign", "--deep", "--verbose=4", "-f", "-s", req.getCertificateName(), appFile.getAbsolutePath());

            Process p = pb.start();
            ByteArrayOutputStream codesignOutput = new ByteArrayOutputStream();
            Thread inputThread = new Thread(()->{
                try (InputStream input = p.getInputStream()) {
                    IOUtil.copy(input, codesignOutput);
                } catch (IOException ex){}

            });
            Thread errorThread = new Thread(()->{
                try (InputStream input = p.getErrorStream()) {
                    IOUtil.copy(input, codesignOutput);
                } catch (IOException ex){}

            });
            inputThread.start();
            errorThread.start();

            int exitCode = p.waitFor();
            response.setCodesignLog(new String(codesignOutput.toByteArray(), "UTF-8"));
            response.setCodesignExitCode(exitCode);
            if (exitCode != 0) {
                throw new RuntimeException("Codesign failed with exit code "+exitCode);
            }
            
            
        }
        
        if (req.isNotarize()) {
            System.out.println("About to notarize...");
            response.setRanAltool(true);
            File zipFile = new File(appFile.getParentFile(), appFile.getName()+".zip");
            ArchiveUtil.nativeZip(zipFile, appFile);
            ProcessBuilder pb = new ProcessBuilder("/usr/bin/xcrun", 
                    "altool", 
                    "--notarize-app", 
                    "--primary-bundle-id", 
                    req.getBundleId(),
                    "--username",
                    req.getDeveloperId(),
                    "--password",
                    req.getNotarizationPassword(),
                    "--file",
                    zipFile.getAbsolutePath()

            );
            
            Process p = pb.start();
            ByteArrayOutputStream altoolOutput = new ByteArrayOutputStream();
            Thread inputThread = new Thread(()->{
                try (InputStream input = p.getInputStream()) {
                    IOUtil.copy(input, altoolOutput);
                } catch (IOException ex){}

            });
            Thread errorThread = new Thread(()->{
                try (InputStream input = p.getErrorStream()) {
                    IOUtil.copy(input, altoolOutput);
                } catch (IOException ex){}

            });
            inputThread.start();
            errorThread.start();
            int exitCode = p.waitFor();
            response.setAltoolLog(new String(altoolOutput.toByteArray(), "UTF-8"));
            response.setAltoolExitCode(exitCode);
            if (exitCode != 0) {
                throw new RuntimeException("Notarization failed with exit code "+exitCode);
            }
        }
        /*
        if (req.getFile().getName().endsWith(".tar.gz")) {
            File targzFile = new File(appFile.getParentFile(), appFile.getName()+".tar.gz");
            Util.compressAsTarGz(targzFile, appFile);
            response.setFile(targzFile);
            
        } else {
            File zipFile = new File(appFile.getParentFile(), appFile.getName()+".zip");
            Util.compressAsZip(zipFile, appFile);
            response.setFile(zipFile);
        }*/
        File dmgFile = new File(appFile.getParentFile(), getBaseName(appFile.getName())+".dmg");
        setupDMGFolder(appFile.getParentFile());
        createDMG(getBaseName(appFile.getName()), dmgFile, appFile.getParentFile());
        response.setFile(dmgFile);
        
        response.setResponseCode(200);
        
    }

    private void setupDMGFolder(File folder) throws IOException {
        for (File child : folder.listFiles()) {
            if (child.getName().endsWith(".app")) {
                continue;
            }
            delTree(child);
            
        }
        File applications = new File("/Applications");
        File link = new File(folder, "Applications");
        Files.createSymbolicLink(link.toPath(), applications.toPath());
        
    }
    
    private static String getBaseName(String appName) {
        if (appName.endsWith(".app")) {
            return appName.substring(0, appName.lastIndexOf("."));
        }
        return appName;
        
    }
    
    
    
    
}

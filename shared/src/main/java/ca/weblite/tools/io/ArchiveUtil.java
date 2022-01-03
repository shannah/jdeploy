/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.tools.io;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.xeustechnologies.jtar.TarEntry;
import org.xeustechnologies.jtar.TarInputStream;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 *
 * @author shannah
 */
public class ArchiveUtil {
    
    public static void extract(File sourceFile, File destinationFolder, String password) throws IOException {
        if (sourceFile.getName().endsWith(".zip")) {
            unzip(sourceFile, destinationFolder, password);
        } else if (sourceFile.getName().endsWith(".tar.gz") || sourceFile.getName().endsWith(".tgz")) {
            String srcName = sourceFile.getName();
            String destName = srcName.substring(0, srcName.lastIndexOf("."));
            File destFile = new File(sourceFile.getParentFile(), destName);
            destFile.deleteOnExit();
            gunzip(sourceFile, destFile);
            untar(destFile.getAbsolutePath(), destinationFolder.getAbsolutePath(), "");
            
        } else if (sourceFile.getName().endsWith(".gz")) {
            String srcName = sourceFile.getName();
            String destName = srcName.substring(0, srcName.lastIndexOf("."));
            File destFile = new File(destinationFolder, destName);
            gunzip(sourceFile, destFile);
        } else if (sourceFile.getName().endsWith(".tar")) {
            untar(sourceFile.getAbsolutePath(), destinationFolder.getAbsolutePath(), "");
        } else {
            throw new IllegalArgumentException("ArchiveUtil.extract only supports .zip, .tar.gz, .gz, and .tar extensions as inputs");
        }
    }
    
    public static void zip(File folder, File zipFile ) throws IOException {
        //try {
            ZipFile zip = new ZipFile(zipFile);
            System.out.println("About to call createZipFile");
            //ZipParameters params = new ZipParameters();
            
            if (folder.isDirectory()) {
                zip.addFolder(folder);
            } else {
                zip.addFile(folder);
            }
            System.out.println("Done createZipFile");
        //} catch (ZipException ex) {
        //    throw new IOException("Fail to zip file "+zipFile, ex);
        //}
        
    }
    
    public static void unzip(File sourceFile, File destinationFolder, String password){
        String source = sourceFile.getAbsolutePath();
        String destination = destinationFolder.getAbsolutePath();
        

        try {
             ZipFile zipFile = new ZipFile(source);
             if (zipFile.isEncrypted()) {
                 
                zipFile.setPassword(password.toCharArray());
             }
             zipFile.extractAll(destination);
        } catch (ZipException e) {
            e.printStackTrace();
        }
    }
    
    public static void gunzip(File srcFile, File destFile) throws IOException {
        GZIPInputStream is = null;
        FileOutputStream os = null;
        try {
            is = new GZIPInputStream(new FileInputStream(srcFile));
            os = new FileOutputStream(destFile);
            
            int chunk = 1024*100;
            byte[] buf = new byte[chunk];
            int len;
            while ( (len = is.read(buf)) != -1 ){
                os.write(buf, 0, len);
            }
            os.flush();
            os.close();
            is.close();
         
            
        } finally {
            try {
                is.close();
            } catch ( Exception ex){}
            try {
                os.close();
            } catch ( Exception ex){}
        }
    }
    
    
    
    

    public static void untar(String tarPath, String destFolderPath, String prefix) throws IOException{
        File tarFile = new File(tarPath).getAbsoluteFile();
        File destFolder = new File(destFolderPath).getAbsoluteFile();
        
        // Create a TarInputStream
        TarInputStream tis = new TarInputStream(new BufferedInputStream(new FileInputStream(tarFile)));
        TarEntry entry;
        while((entry = tis.getNextEntry()) != null) {
           
            String entryName = entry.getName();
            if ( !entryName.startsWith(prefix) ){

                continue;
            }


            entryName = entryName.substring(prefix.length());
            int count;
            byte data[] = new byte[2048];
            File outputFile = new File(destFolder.getAbsolutePath() + "/" + entryName);
            if (entry.isDirectory()) {
                outputFile.mkdirs();
                continue;

            }

            outputFile.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(outputFile);
            BufferedOutputStream dest = new BufferedOutputStream(fos);

            while((count = tis.read(data)) != -1) {
               dest.write(data, 0, count);
            }

            dest.flush();
            dest.close();
            if (entry.getHeader() != null) {
                if (FileUtil.isPosix()) {
                    Set<PosixFilePermission> perms = FileUtil.getPosixFilePermissions(entry.getHeader().mode);
                    Files.setPosixFilePermissions(outputFile.toPath(), perms);
                }
                if (entry.getHeader().modTime > 0) {
                     outputFile.setLastModified(entry.getHeader().modTime);
                }
                
            }
        }

        tis.close();
    }
    
    private static void nativeUnTarGz(File tarGzFile, File destFolder) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("/usr/bin/gunzip", "-c", tarGzFile.getAbsolutePath());
        
        Process p = pb.start();
        File tempFile = File.createTempFile("tmp", ".tar");
        tempFile.deleteOnExit();
        
        try {
            try (InputStream input = p.getInputStream(); FileOutputStream fos = new FileOutputStream(tempFile)) {
                IOUtil.copy(input, fos);
            } 
            try {
                int exitValue = p.waitFor();
                if (exitValue != 0) {
                    throw new IOException("Failed to gunzip file "+tarGzFile+" exit code "+exitValue);
                }
            } catch (InterruptedException ex) {
                throw new IOException("Failed to gunzip file "+tarGzFile+" because it was interrupted", ex);
            }
            
            //pb = new ProcessBuilder("/usr/bin/tar", "xf", tempFile.getAbsolutePath(), )
            // TODO finish tar
        } finally {
            tempFile.delete();
        }
    }
    
    private static void nativeTarGz(File tarGzFile, File appFile) throws IOException {
        if (tarGzFile.getName().endsWith(".gz")) {
            tarGzFile = new File(tarGzFile.getParentFile(), tarGzFile.getName().substring(0, tarGzFile.getName().lastIndexOf('.')));
        } else {
            throw new IllegalArgumentException("nativeTarGz output file must have extension tar.gz");
        }
        ProcessBuilder pb = new ProcessBuilder("/usr/bin/tar", "-cf", tarGzFile.getAbsolutePath(), appFile.getAbsolutePath());
        pb.inheritIO();
        Process p = pb.start();
        try {
            p.waitFor();
        } catch (InterruptedException ex) {
            throw new IOException("Tarring file "+appFile+" to "+tarGzFile+" interupted", ex);
        }
        int exitCode = p.exitValue();
        if (exitCode != 0) {
            throw new IOException("Failed to tar file "+appFile+" to "+tarGzFile+".  Exit code "+exitCode);
        }
        File gzFile = new File(tarGzFile.getParentFile(), tarGzFile.getName()+".gz");
        pb = new ProcessBuilder("/usr/bin/gzip", tarGzFile.getAbsolutePath());
        p = pb.start();
        try {
            p.waitFor();
        } catch (InterruptedException ex) {
            throw new IOException("Gzipping file "+tarGzFile+" interrupted", ex);
        } finally {
            if (tarGzFile.exists()) {
                tarGzFile.delete();
            }
        }
        if (p.exitValue() != 0) {
            throw new IOException("Failed to gzip file "+tarGzFile+".  Exit code "+p.exitValue());
        }

    }
    
    public static void nativeZip(File zipFile, File appFile) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("/usr/bin/zip", "-r", zipFile.getAbsolutePath(), appFile.getAbsolutePath());
        pb.inheritIO();
        Process p = pb.start();
        try {
            p.waitFor();
        } catch (InterruptedException ex) {
            throw new IOException("Zipping file "+appFile+" to "+zipFile+" interupted", ex);
        }
        int exitCode = p.exitValue();
        if (exitCode != 0) {
            throw new IOException("Failed to zip file "+appFile+" to "+zipFile+".  Exit code "+exitCode);
        }
    }
    
    private static void nativeUnzip(File zipFile, File destFolder) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("/usr/bin/unzip", zipFile.getAbsolutePath(), "-d", destFolder.getAbsolutePath());
        pb.inheritIO();
        Process p = pb.start();
        try {
            p.waitFor();
        } catch (InterruptedException ex) {
            throw new IOException("Unzipping file "+zipFile+" to "+destFolder+" interupted", ex);
        }
        int exitCode = p.exitValue();
        if (exitCode != 0) {
            throw new IOException("Failed to unzip file "+zipFile+" to "+destFolder+".  Exit code "+exitCode);
        }
    }
    
    
}

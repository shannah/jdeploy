/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.weblite.jdeploy.appbundler;


import ca.weblite.tools.io.ArchiveUtil;
import ca.weblite.tools.io.FileUtil;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Date;
import java.util.Enumeration;
import java.util.Set;

/**
 *
 * @author joshmarinacci
 */
public class Util {

    public static void copyToFile(File srcFile, File dstFile) throws FileNotFoundException, IOException {
        byte[] buf = new byte[1024*16];
        FileInputStream fin = new FileInputStream(srcFile);
        FileOutputStream fout = new FileOutputStream(dstFile);
        while(true) {
            int n = fin.read(buf);
            if(n < 0) break;
            fout.write(buf,0,n);
        }
        fin.close();
        fout.close();
        p("copied file: " + srcFile.getName());
    }

    private static void p(String s) {
        System.out.println(s);
    }

    public static void copyToFile(URL resource, File dstFile) throws FileNotFoundException, IOException {
        byte[] buf = new byte[1024*16];
        InputStream in = resource.openStream();
        FileOutputStream fout = new FileOutputStream(dstFile);
        while(true) {
            int n = in.read(buf);
            if(n < 0) break;
            fout.write(buf,0,n);
        }
        in.close();
        fout.close();
        p("copied jar: " + resource.toString());
    }
    
    public static void compressAsTarGz(File outputFile, File... inputFiles) throws IOException {
        TAR tar = new TAR();
        tar.gzip = true;
        tar.compress(outputFile.getAbsolutePath(), inputFiles);
        
    }
    
    public static void decompressTarGz(File tarGzFile, File outputDir) throws IOException {
        TAR tar = new TAR();
        tar.gzip= true;
        tar.decompress(tarGzFile.getAbsolutePath(), outputDir);
    }
    
    /*
    public static void compressAsZip_old(File outputFile, File... inputFiles) throws IOException {
        ZIP zip = new ZIP();
        
        zip.compress(outputFile.getAbsolutePath(), inputFiles);
        
    }
    
    
    public static void compressAsZip(File outputFile, File inputFile) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("/usr/bin/zip", "-r", outputFile.getAbsolutePath(), inputFile.getAbsolutePath());
        pb.inheritIO();
        Process p = pb.start();
        try {
            p.waitFor();
        } catch (Exception ex) {
            throw new IOException("Zip failed: ", ex);
        }
        if (p.exitValue() != 0) {
            throw new IOException("ZIP failed with exit code "+p.exitValue());
        }
    }
    */
    public static void compressAsZip(File outputFile, File inputFile) throws IOException {
        ArchiveUtil.zip(inputFile, outputFile);
        
    }
    
    public static void decompressZip(File zipFile, File outputDir) throws IOException {
        ArchiveUtil.unzip(zipFile, outputDir, "");
    }
    
    public static void decompressZip_old(File zipFile, File outputDir) throws IOException {
        ZIP zip = new ZIP();
        zip.decompress(zipFile.getAbsolutePath(), outputDir);
    }
    
    public static void createDMG(String volumeName, File dmgFile, File srcFolder) throws IOException {
        //$hdiutil create /tmp/tmp.dmg -ov -volname "RecitalInstall" -fs HFS+ -srcfolder "/tmp/macosxdist/" 
        File tmp = File.createTempFile("temp", ".dmg");
        tmp.deleteOnExit();
        try {
            ProcessBuilder pb = new ProcessBuilder("/usr/bin/hdiutil", 
                    "create", 
                    tmp.getAbsolutePath(), 
                    "-ov", 
                    "-volname", 
                    volumeName,
                    "-fs", "HFS+",
                    "-srcfolder",
                    srcFolder.getAbsolutePath());
            pb.inheritIO();
            Process p = pb.start();
            try {
                if (p.waitFor() != 0) {
                    throw new IOException("Faiiled to create DMG.  Exit code of hdiutil was "+p.exitValue());
                }
            } catch (InterruptedException ex) {
                throw new IOException("Failed to create DMG.  hdiutil was interrupted.", ex);
            }
            //$hdiutil convert /tmp/tmp.dmg -format UDZO -o RecitalInstall.dmg
            pb = new ProcessBuilder("/usr/bin/hdiutil", 
                    "convert",
                    tmp.getAbsolutePath(),
                    "-format",
                    "UDZO",
                    "-o",
                    dmgFile.getAbsolutePath()
            );
            pb.inheritIO();
            p = pb.start();
            try {
                if (p.waitFor() != 0) {
                    throw new IOException("Faiiled to create DMG.  Exit code of hdiutil convert was "+p.exitValue());
                }
            } catch (InterruptedException ex) {
                throw new IOException("Failed to create DMG.  hdiutil convert was interrupted.", ex);
            }
        } finally {
            tmp.delete();
        }
               
    }
    


private static class TAR {
    private boolean gzip;
    
    private TAR() {

    }
    
    

    public  void compress(String name, File... files) throws IOException {
        try (TarArchiveOutputStream out = getTarArchiveOutputStream(name)){
            for (File file : files){
                addToArchiveCompression(out, file, ".");
            }
        }
    }

    public  void decompress(String in, File out) throws IOException {
        try (TarArchiveInputStream fin = getTarArchiveInputStream(in)){
            TarArchiveEntry entry;
            while ((entry = fin.getNextTarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                File curfile = new File(out, entry.getName());
                File parent = curfile.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                IOUtils.copy(fin, new FileOutputStream(curfile));
                if (FileUtil.isPosix()) {
                    Set<PosixFilePermission> perms = FileUtil.getPosixFilePermissions(entry.getMode());
                    Files.setPosixFilePermissions(curfile.toPath(), perms);
                }
                if (entry.getModTime() != null) {
                     curfile.setLastModified(entry.getModTime().getTime());
                }
            }
        }
    }

    private  TarArchiveOutputStream getTarArchiveOutputStream(String name) throws IOException {
        TarArchiveOutputStream taos = gzip ? 
                new TarArchiveOutputStream(new GzipCompressorOutputStream(new FileOutputStream(name))) :
                new TarArchiveOutputStream(new FileOutputStream(name));
        // TAR has an 8 gig file limit by default, this gets around that
        taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
        // TAR originally didn't support long file names, so enable the support for it
        taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        taos.setAddPaxHeadersForNonAsciiNames(true);
        
        return taos;
    }
    
    private  TarArchiveInputStream getTarArchiveInputStream(String name) throws IOException {
        TarArchiveInputStream taos = gzip ? 
                new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(name))) :
                new TarArchiveInputStream(new FileInputStream(name));
        
        
        return taos;
    }

    private  void addToArchiveCompression(TarArchiveOutputStream out, File file, String dir) throws IOException {
        String entry = dir + File.separator + file.getName();
        if (file.isFile()){
            TarArchiveEntry e = new TarArchiveEntry(file, entry);
            if (FileUtil.isPosix()) {
                e.setMode(FileUtil.getPosixFilePermissions(file));
            }
            e.setModTime(new Date(file.lastModified()));
            
            
            out.putArchiveEntry(e);
            try (FileInputStream in = new FileInputStream(file)){
                IOUtils.copy(in, out);
            }
            out.closeArchiveEntry();
        } else if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null){
                for (File child : children){
                    addToArchiveCompression(out, child, entry);
                }
            }
        } else {
            System.out.println(file.getName() + " is not supported");
        }
    }
}

private static class ZIP {
    
    
    private ZIP() {

    }
    
    

    public  void compress(String name, File... files) throws IOException {
        try (ZipArchiveOutputStream out = getZipArchiveOutputStream(name)){
            for (File file : files){
                addToArchiveCompression(out, file, ".");
            }
        }
    }

    public  void decompress(String in, File out) throws IOException {
       
        //try (ZipArchiveInputStream fin = new ZipArchiveInputStream(new FileInputStream(in))){
        try (ZipFile zipFile = new ZipFile(in)) {
            
            ZipArchiveEntry entry;
            //while ((entry = fin.getNextZipEntry()) != null) {
            Enumeration<ZipArchiveEntry> en = zipFile.getEntries();
            while (en.hasMoreElements()) {
                entry = en.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                File curfile = new File(out, entry.getName());
                File parent = curfile.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                try (InputStream is = zipFile.getInputStream(entry)) {
                    IOUtils.copy(is, new FileOutputStream(curfile));
                }
                System.out.println("Unix mode of "+curfile+": "+entry.getUnixMode());
                if ((entry.getUnixMode() & 0755) == 0755) {
                    curfile.setExecutable(true, false);
                }
            }
        }
    }

    private  ZipArchiveOutputStream getZipArchiveOutputStream(String name) throws IOException {
        
        ZipArchiveOutputStream taos = new ZipArchiveOutputStream(new FileOutputStream(new File(name)));
        
        // TAR has an 8 gig file limit by default, this gets around that
        //taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
        // TAR originally didn't support long file names, so enable the support for it
        //taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        //taos.setAddPaxHeadersForNonAsciiNames(true);
        return taos;
    }

    private  void addToArchiveCompression(ZipArchiveOutputStream out, File file, String dir) throws IOException {
        String entry = dir + File.separator + file.getName();
        if (file.isFile()){
            ZipArchiveEntry e = new ZipArchiveEntry(file, entry);
            
             if (file.canExecute()) {
                System.out.println(file+" has execute bit");
                System.out.println("Setting unix mode to "+(e.getUnixMode() | 0755));
                e.setUnixMode(e.getUnixMode() | 0755);
                System.out.println("Unix mode now "+e.getUnixMode());
            }
            out.putArchiveEntry(e);
            try (FileInputStream in = new FileInputStream(file)){
                IOUtils.copy(in, out);
            }
            out.closeArchiveEntry();
        } else if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null){
                for (File child : children){
                    addToArchiveCompression(out, child, entry);
                }
            }
        } else {
            System.out.println(file.getName() + " is not supported");
        }
    }
    }
}

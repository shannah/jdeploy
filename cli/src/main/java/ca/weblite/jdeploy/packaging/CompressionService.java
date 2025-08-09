package ca.weblite.jdeploy.packaging;

import ca.weblite.tools.io.ArchiveUtil;
import ca.weblite.tools.io.FileUtil;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;

@Singleton
public class CompressionService {
    /**
     * Compress the installer file
     * @param target The target platform.  E.g. "mac", "linux", "windows"
     * @param installerZip
     * @return
     * @throws IOException
     */
    public File compress(String target, File installerZip) throws IOException {
        if (target.startsWith("mac") || target.startsWith("linux")) {
            // Mac and linux use tar file
            String gzipFileName = installerZip.getName() + ".gz";
            if (gzipFileName.endsWith(".tar.gz")) {
                gzipFileName = gzipFileName.replaceFirst("\\.tar\\.gz$", ".tgz");
            }
            File gzipFile = new File(installerZip.getParentFile(), gzipFileName);
            ArchiveUtil.gzip(installerZip, gzipFile);
            installerZip.delete();
            installerZip = gzipFile;
        }  else {
            // Windows uses zip file
            String zipFileName = installerZip.getName();
            if (installerZip.getName().endsWith(".exe")) {
                File installerZipFolder = new File(
                        installerZip.getParentFile(),
                        installerZip.getName().substring(0, installerZip.getName().lastIndexOf("."))
                );
                if (installerZipFolder.exists()) {
                    FileUtil.delTree(installerZipFolder);
                }
                installerZipFolder.mkdirs();
                File installerZipInFolder = new File(installerZipFolder, installerZip.getName());
                installerZip.renameTo(installerZipInFolder);
                installerZip = installerZipFolder;
                zipFileName = installerZip.getName();
            }
            if (!zipFileName.endsWith(".zip")) {
                zipFileName += ".zip";
                File zipFile = new File(installerZip.getParentFile(), zipFileName);
                ArchiveUtil.zip(installerZip, zipFile);
                FileUtil.delTree(installerZip);
                installerZip = zipFile;
            }
        }
        return installerZip;
    }
}

package ca.weblite.jdeploy;

import ca.weblite.jdeploy.app.AppInfo;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.*;

public class ZipUtil {
    public static void filter(AppInfo appInfo, File src, File dest) throws Exception {
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(new FileOutputStream(dest))) {
            try (ZipArchiveInputStream input = new ZipArchiveInputStream(new FileInputStream(src))) {
                ZipArchiveEntry entry = input.getNextZipEntry();

                String newName = entry.getName();
                String prefix = "jdeploy-installer/jdeploy-installer.app/";
                String newPrefix = appInfo.getTitle() + " Installer/"+appInfo.getTitle()+" Installer/";
                if (newName.startsWith(prefix)) {
                    newName = newPrefix + newName.substring(prefix.length());
                } else {
                    prefix = "jdeploy-installer/";
                    newPrefix = appInfo.getTitle() + " Installer/";
                    if (newName.startsWith(prefix)) {
                        newName = newPrefix + newName.substring(prefix.length());
                    }
                }
                ZipArchiveEntry newEntry = new ZipArchiveEntry(newName);
                newEntry.setExtra(entry.getExtra());
                newEntry.setExtraFields(entry.getExtraFields());
                newEntry.setExternalAttributes(entry.getExternalAttributes());
                newEntry.setInternalAttributes(entry.getInternalAttributes());
                newEntry.setMethod(entry.getMethod());
                newEntry.setUnixMode(entry.getUnixMode());
                newEntry.setComment(entry.getComment());
                newEntry.setNameSource(entry.getNameSource());
                newEntry.setSize(entry.getSize());
                newEntry.setCompressedSize(entry.getCompressedSize());
                newEntry.setCrc(entry.getCrc());
                newEntry.setCentralDirectoryExtra(entry.getCentralDirectoryExtra());
                newEntry.setCommentSource(entry.getCommentSource());
                newEntry.setGeneralPurposeBit(entry.getGeneralPurposeBit());
                newEntry.setTime(entry.getTime());
                if (entry.getCreationTime() != null) {
                    newEntry.setCreationTime(entry.getCreationTime());
                }
                if (entry.getLastModifiedTime() != null) {
                    newEntry.setLastModifiedTime(entry.getLastModifiedTime());
                }
                newEntry.setDiskNumberStart(entry.getDiskNumberStart());
                newEntry.setVersionMadeBy(entry.getVersionMadeBy());
                newEntry.setVersionRequired(entry.getVersionRequired());
                newEntry.setLastAccessTime(entry.getLastAccessTime());






                output.addRawArchiveEntry(newEntry, input);
            }
        }
    }
}

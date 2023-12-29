package ca.weblite.jdeploy.helpers.filemergers;

import org.apache.commons.io.FileUtils;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;

public class DirectoryMerger {

    private FileMerger[] fileMergers;

    public DirectoryMerger(FileMerger... fileMergers) {
        this.fileMergers = fileMergers;
    }

    public void merge(File directory, File patch) throws Exception {
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("directory must be a directory");
        }
        if (!patch.isDirectory()) {
            throw new IllegalArgumentException("patch must be a directory");
        }

        for (File patchFile : patch.listFiles()) {
            File directoryFile = new File(directory, patchFile.getName());
            if (patchFile.isDirectory()) {
                if (directoryFile.exists()) {
                    merge(directoryFile, patchFile);
                } else {
                    FileUtils.copyDirectory(patchFile, directoryFile);
                }
            } else {
                if (directoryFile.exists()) {
                    boolean merged = false;
                    for (FileMerger merger : fileMergers) {
                        if (merger.isApplicableTo(directoryFile, patchFile)) {
                            merger.merge(directoryFile, patchFile);
                            merged = true;
                            break;
                        }
                    }
                    if (!merged) {
                        FileUtils.copyFile(patchFile, directoryFile);
                    }
                } else {
                    FileUtils.copyFile(patchFile, directoryFile);
                }
            }
        }
    }
}

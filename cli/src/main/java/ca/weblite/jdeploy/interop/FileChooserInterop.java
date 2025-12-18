package ca.weblite.jdeploy.interop;

import javax.inject.Singleton;
import java.awt.*;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Set;

@Singleton
public class FileChooserInterop {
    public File showFileChooser(Frame parent, String title, Set<String> extensions) {
        FileDialog fd = new FileDialog(parent, title, FileDialog.LOAD);
        fd.setFilenameFilter(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if (extensions == null) {
                    return true;
                }
                int pos = name.lastIndexOf(".");
                if (pos >= 0) {
                    String extension = name.substring(pos+1);
                    return extensions.contains(extension);
                }
                return false;
            }
        });
        fd.setVisible(true);
        File[] files = fd.getFiles();
        if (files == null || files.length == 0) return null;
        return files[0];
    }
}

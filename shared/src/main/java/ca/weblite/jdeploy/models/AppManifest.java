package ca.weblite.jdeploy.models;

import ca.weblite.tools.io.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class AppManifest {
    private File appRoot;
    private byte[] signature;
    private String identity;

    /**
     * Maps relative file paths to a file description.
     */
    private LinkedHashMap<String, FileDescription> files = new LinkedHashMap<>();

    /**
     * Gets the path of a file relative to the app root.
     *
     * @param file
     * @return
     * @throws IOException
     */
    private String getRelativePath(File file) throws IOException {
        return FileUtil.getRelativePath(appRoot, file);
    }


    /**
     * Adds a file, given an file that exists.
     * @param file
     * @param md5
     * @throws IOException
     */
    public void addFile(File file, String md5) throws IOException {
        String filePath = getRelativePath(file);
        files.put(filePath, new FileDescription(filePath, md5));
    }

    /**
     * Adds a file by relative path.   This doesn't validate the file for existence.
     * @param relativePath
     * @param md5
     */
    public void addFile(String relativePath, String md5) {
        files.put(relativePath, new FileDescription(relativePath, md5));
    }

    public File getAppRoot() {
        return appRoot;
    }

    public void setAppRoot(File appRoot) {
        this.appRoot = appRoot;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public class FileDescription {
        private String file;
        private String md5;

        FileDescription(String file, String md5) {
            this.file = file;
            this.md5 = md5;
        }

        public File toFile() {
            return new File(appRoot, file);
        }

        public String getFile() {
            return file;
        }

        public String getMD5() {
            return md5;
        }
    }

    public List<FileDescription> getFiles() {
        return new ArrayList<FileDescription>(files.values());
    }

    public File getManifestFile() {
        return new File(appRoot, "JDEPLOY.MANIFEST");
    }

}

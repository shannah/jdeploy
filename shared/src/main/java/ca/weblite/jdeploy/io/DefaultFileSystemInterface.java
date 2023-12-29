package ca.weblite.jdeploy.io;

import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;

public class DefaultFileSystemInterface implements FileSystemInterface {

    private java.nio.file.FileSystem fileSystem;

    public DefaultFileSystemInterface(){
        this(FileSystems.getDefault());
    }

    public DefaultFileSystemInterface(java.nio.file.FileSystem fileSystem){
        this.fileSystem = fileSystem;
    }

    @Override
    public Path getPath(String first, String... more) {
        return fileSystem.getPath(first, more);
    }

    @Override
    public boolean isDirectory(Path file) {
        return Files.isDirectory(file);
    }

    @Override
    public boolean exists(Path file) {

        return Files.exists(file);
    }

    @Override
    public OutputStream getOutputStream(Path path) throws IOException {
        return Files.newOutputStream(path);
    }

    @Override
    public InputStream getInputStream(Path path) throws IOException {
        // get input stream using nio APIs without converting the path to a File
        return Files.newInputStream(path);
    }

    @Override
    public String readToString(Path path, Charset charset) throws IOException {
        // read path to string using nio APIs without converting the path to a FIle
        return new String(Files.readAllBytes(path), charset);
    }

    @Override
    public void writeStringToFile(Path path, String content, Charset charset) throws IOException {
        // Write a string to a file using nio APIs without converting path to a File
        Files.write(path, content.getBytes(charset));
    }

    @Override
    public boolean makeExecutable(Path path) {
        return path.toFile().setExecutable(true, false);
    }

    @Override
    public boolean mkdirs(Path path) {
        try {
            return Files.createDirectories(path) != null;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }


    @Override
    public void copyResourceTo(Class clazz, String resource, Path target) throws IOException {
        try (InputStream in = clazz.getResourceAsStream(resource)) {
            try (OutputStream out = getOutputStream(target)) {
                // copy inputsteam to outputstream using nio APIs
                byte[] buffer = new byte[4096];
                int bytesRead = 0;
                while ((bytesRead = in.read(buffer)) > 0) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        }
    }
}

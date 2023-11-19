package ca.weblite.jdeploy.io;

import javax.inject.Singleton;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Path;

@Singleton
public interface FileSystemInterface {

    public Path getPath(String first, String... more);

    public boolean isDirectory(Path file);
    public boolean exists(Path file);
    public OutputStream getOutputStream(Path path) throws IOException;
    public InputStream getInputStream(Path path) throws IOException;
    public String readToString(Path path, Charset charset) throws IOException;
    public void writeStringToFile(Path path, String content, Charset charset) throws IOException;

    public boolean makeExecutable(Path path);

    public boolean mkdirs(Path path);

    public void copyResourceTo(Class clazz, String resource, Path path) throws IOException;
}

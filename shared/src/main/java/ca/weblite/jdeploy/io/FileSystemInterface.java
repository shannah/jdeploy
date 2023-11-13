package ca.weblite.jdeploy.io;

import javax.inject.Singleton;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Path;

@Singleton
public interface FileSystemInterface {
    public boolean isDirectory(Path file);
    public boolean exists(Path file);
    public OutputStream getOutputStream(Path path) throws FileNotFoundException;
    public InputStream getInputStream(Path path) throws FileNotFoundException;
    public String readToString(Path path, Charset charset) throws IOException;
}

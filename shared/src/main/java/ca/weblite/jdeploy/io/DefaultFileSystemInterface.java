package ca.weblite.jdeploy.io;

import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Path;

public class DefaultFileSystemInterface implements FileSystemInterface {
    @Override
    public boolean isDirectory(Path file) {
        return file.toFile().isDirectory();
    }

    @Override
    public boolean exists(Path file) {
        return file.toFile().exists();
    }

    @Override
    public OutputStream getOutputStream(Path path) throws FileNotFoundException {
        return new FileOutputStream(path.toFile());
    }

    @Override
    public InputStream getInputStream(Path path) throws FileNotFoundException {
        return new FileInputStream(path.toFile());
    }

    @Override
    public String readToString(Path path, Charset charset) throws IOException {
        return FileUtils.readFileToString(path.toFile(), charset);
    }
}

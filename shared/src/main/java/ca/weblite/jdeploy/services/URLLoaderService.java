package ca.weblite.jdeploy.services;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public interface URLLoaderService {
    public InputStream openStream(URL url) throws IOException;
}

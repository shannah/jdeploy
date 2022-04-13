package ca.weblite.jdeploy.services;

import ca.weblite.tools.io.URLUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class DefaultURLLoader implements URLLoaderService {
    @Override
    public InputStream openStream(URL url) throws IOException {
        return URLUtil.openStream(url);
    }
}

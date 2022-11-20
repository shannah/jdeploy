package ca.weblite.jdeploy.installer.npm;

import ca.weblite.tools.io.IOUtil;
import ca.weblite.tools.io.URLUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.beans.Encoder;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class NPMRegistry {
    private static final String REGISTRY_URL="https://registry.npmjs.org/";
    private static final String GITHUB_URL = "https://github.com/";

    public NPMPackage loadPackage(String packageName, String source) throws IOException {

        try (InputStream inputStream = URLUtil.openStream(
                new URL(getPackageUrl(packageName, source)))) {
            String str = IOUtil.readToString(inputStream);
            return new NPMPackage(new JSONObject(str));
        }
    }

    private String getPackageUrl(String packageName, String source) throws UnsupportedEncodingException {
        if (source.startsWith(GITHUB_URL)) {
            String[] parts = packageName.substring(GITHUB_URL.length()).split("/");
            return GITHUB_URL +
                    URLEncoder.encode(parts[1], "UTF-8") + "/" +
                    URLEncoder.encode(parts[2], "UTF-8") + "/releases/download/jdeploy/package-info.json";
        } else {
            return REGISTRY_URL+ URLEncoder.encode(packageName, "UTF-8");
        }
    }

}

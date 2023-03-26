/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.tools.io;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 *
 * @author shannah
 */
public class URLUtil {
    public static URL url(URL baseUrl, String url) {
        try {
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("github://")) {
                return new URL(url);
            } else {
                return getDependentURL(baseUrl, url);
            }
        } catch (MalformedURLException mex) {
            mex.printStackTrace();
            return null;
        }
    }
    
    public static URL getDependentURL(URL appInfoUrl, String relativePath) {
        String url = appInfoUrl.toString();
        try {
            return new URL(url.substring(0, url.lastIndexOf("/")) + "/" + relativePath);
        } catch (MalformedURLException mex) {
            throw new RuntimeException(mex);
        }
    }

    public static InputStream openStream(URL url) throws IOException {
        if (url.getProtocol().equalsIgnoreCase("file")) {
            return url.openStream();
        }
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
        conn.setInstanceFollowRedirects(true);

        return conn.getInputStream();
    }
    
    
}

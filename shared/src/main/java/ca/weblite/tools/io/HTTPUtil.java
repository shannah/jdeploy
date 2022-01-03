/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.tools.io;



import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static ca.weblite.tools.io.FileUtil.readFileToLong;
import static ca.weblite.tools.io.IOUtil.readToString;
import static ca.weblite.tools.security.CertificateUtil.getSHA1Fingerprint;

/**
 *
 * @author shannah
 */
public class HTTPUtil {
    public static interface ProgressListener {
        public void update(URL url, long bytesRead, long totalBytes);
    }
    
    private static ProgressListener progressListener;
    public static void setProgressListener(ProgressListener l) {
        progressListener = l;
    }
    private static void updateProgress(URL url, long bytesRead, long totalBytes) {
        if (progressListener != null) {
            progressListener.update(url, bytesRead, totalBytes);
        }
    }
    
    public static final Logger logger = Logger.getLogger(HTTPUtil.class.getName());
    
    public static class ETags {
        private static Map<String,String> etags;
        
        public static String sanitize(String etag) {
            return etag.replaceAll("[^a-zA-Z0-9]", "").trim();
        }
        
        public static void add(String url, String etag) {
            etag = sanitize(etag);
            if (etags == null) {
                etags = new HashMap<>();
            }
            etags.put(url, etag);
        }
        
        public static String get(String url) {
            if (etags != null) {
                return etags.get(url);
            }
            return null;
        }
        
        public static void clear() {
            etags = null;
        }
    }
    
    public static class Fingerprints {
        private static Map<String,String> fingerprints;
        
        public static void add(String url, String fingerprint) {
            if (fingerprints == null) {
                fingerprints = new HashMap<>();
            }
            fingerprints.put(url, fingerprint);
        }
        
        public static String get(String url) {
            if (fingerprints != null) {
                return fingerprints.get(url);
            }
            return null;
        }
        
        public static void clear() {
            fingerprints = null;
        }
    }
    
    
    public static boolean requiresUpdate(URL u, File destFile) throws IOException {
        return requiresUpdate(u, destFile, false);
    }
    
    
    private static void saveExpires(DownloadResponse resp, File destFile) throws IOException {
        if (resp != null && resp.getConnection() != null) {
            long expires = resp.getConnection().getHeaderFieldDate("Expires", 0);
            if (expires > 0) {
                FileUtil.writeStringToFile(String.valueOf(expires), new File(destFile.getParentFile(), destFile.getName()+".expires"));
            }
        }
    }
    
    public static long getExpiryDate(File destFile) throws IOException {
        File mtimeFile = new File(destFile.getParentFile(), destFile.getName()+".expires");
        if (mtimeFile.exists()) {
            return readFileToLong(mtimeFile);
        }
        return 0;
    }
    public static InputStream openStream(URL u) throws IOException {
        return openStream(u, true);
    }
    
    
    public static InputStream openStream(URL u, boolean cache) throws IOException {
        HttpURLConnection conn = (HttpURLConnection)u.openConnection();
        conn.setUseCaches(cache);
        conn.setDefaultUseCaches(cache);
        if (!cache) {
            conn.addRequestProperty("Cache-Control", "no-cache");
            conn.addRequestProperty("Pragma", "no-cache");
            conn.addRequestProperty("If-Modified-Since", "Sat, 1 Jan 2000 00:00:00 GMT");
        }
        
        conn.setInstanceFollowRedirects(true);
        int responseCode = conn.getResponseCode();
        String responseMessage = conn.getResponseMessage();
        return conn.getInputStream();
    }
    
    public static boolean requiresUpdate(URL u, File destFile, boolean forceCheck) throws IOException {
        //https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download
        if (destFile.exists() && "github.com".equals(u.getHost()) && u.getPath().contains("/releases/download/")) {
            logger.fine("Github release assets should not require updating.");
            return false;
        }
        if (!forceCheck && getExpiryDate(destFile) >= System.currentTimeMillis()) {
            return false;
        }
        File etagFile = new File(destFile.getParentFile(), destFile.getName()+".etag");
        if (destFile.exists() && etagFile.exists()) {
            String etag;
            try (InputStream is = new FileInputStream(etagFile)) {
                etag = readToString(is).trim();
            }
            DownloadResponse resp = new DownloadResponse();
            if (doesETagMatch(resp, u, etag)) {
                saveExpires(resp, destFile);
                return false;
            }
            saveExpires(resp, destFile);
        }
        return true;
    }


    public static class DownloadResponse {
        private HttpURLConnection connection;

        /**
         * @return the connection
         */
        public HttpURLConnection getConnection() {
            return connection;
        }

        /**
         * @param connection the connection to set
         */
        public void setConnection(HttpURLConnection connection) {
            this.connection = connection;
        }
    }
    
    
    

    public static boolean doesETagMatch(DownloadResponse resp, URL url, String etag) throws IOException {
        return doesETagMatch(resp, url, etag, true);
    }
    
    //public static boolean doesETagMatch(URL url, String etag) throws IOException {
    //    return doesETagMatch(url, etag, true);
    //}
    
    //public static boolean doesETagMatch(URL url, String etag, boolean followRedirects) throws IOException {
    //    return doesETagMatch(null, url, etag, followRedirects);
    //}
    
    public static boolean doesETagMatch(DownloadResponse resp, URL url, String etag, boolean followRedirects) throws IOException {
        if (etag == null) {
            return false;
        }
        //log("Checking etag for "+url);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        if (resp != null) {
            resp.setConnection(conn);
        }
        if (Boolean.getBoolean("client4j.disableHttpCache")) {
            conn.setUseCaches(false);
            conn.setDefaultUseCaches(false);
        }
        conn.setRequestMethod("HEAD");
        //https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download
        conn.setInstanceFollowRedirects(followRedirects);
        
        int response = conn.getResponseCode();
        logger.fine(""+conn.getHeaderFields());
        String newETag = conn.getHeaderField("ETag");
        //log("New etag is "+newETag+", old="+etag);
        
        if (newETag != null) {
            return etag.equals(ETags.sanitize(newETag));
        } else {
            return false;
        }
    }
    
    
}

package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.DeveloperIdentity;
import ca.weblite.tools.io.IOUtil;
import ca.weblite.tools.io.URLUtil;
import ca.weblite.tools.security.CertificateUtil;

import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import java.net.URLDecoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

public class DeveloperIdentityURLLoader {
    private DeveloperIdentityJSONReader reader = new DeveloperIdentityJSONReader();
    private URLLoaderService urlLoader = new DefaultURLLoader();
    public void loadIdentityFromURL(DeveloperIdentity identity, String url) throws IOException, CertificateException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, InvalidKeyException {
        if (!url.startsWith("https://")) {
            throw new IOException("Identities can only be loaded over https.  Attempted to load from "+url);
        }
        try (InputStream input = urlLoader.openStream(new URL(url))) {
            String contents = IOUtil.readToString(input);
            JSONObject json;
            try {
                json = new JSONObject(contents);
            } catch (JSONException jsonException) {
                // This might be an HTML page with embedded JSON.
                if (contents.contains("<jdeploy>")) {
                    int startTagPos = contents.indexOf("<jdeploy>");
                    int endTagPos = contents.indexOf("</jdeploy>", startTagPos);
                    if (endTagPos == -1) {
                        throw new IOException("Found opening <jdeploy> tag but no closing </jdeploy> tag in contents at "+url);
                    }
                    contents = contents.substring(startTagPos, endTagPos);
                    int startPos = contents.indexOf("{", startTagPos);
                    if (startPos < 0 || startPos > endTagPos) {
                        throw new IOException("No '{' character found inside <jdeploy> tags at "+url);
                    }

                    int endPos = contents.lastIndexOf("}", startPos);
                    if (endPos < 0 || endPos < startPos) {
                        throw new IOException("No closing '}' character found inside <jdeploy> tags at "+url);
                    }
                    contents = contents.substring(startPos, endPos+1);
                    contents = StringEscapeUtils.unescapeHtml4(contents);
                    json = new JSONObject(contents);
                } else {
                    throw new IOException("No developer identity found at "+url);
                }
            }

            reader.loadIdentityFromJSON(identity, json, url);
        }
    }


    public URLLoaderService getUrlLoader() {
        return urlLoader;
    }

    public void setUrlLoader(URLLoaderService urlLoader) {
        this.urlLoader = urlLoader;
    }
}

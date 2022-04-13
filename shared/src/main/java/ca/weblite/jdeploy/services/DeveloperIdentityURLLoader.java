package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.DeveloperIdentity;
import ca.weblite.tools.io.IOUtil;
import ca.weblite.tools.io.URLUtil;
import ca.weblite.tools.security.CertificateUtil;

import org.json.JSONArray;
import org.json.JSONObject;


import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

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
            JSONObject json = new JSONObject(contents);

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

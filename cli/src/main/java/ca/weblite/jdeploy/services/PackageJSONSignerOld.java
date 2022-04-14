package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.helpers.DeveloperIdentityHelper;
import ca.weblite.jdeploy.models.DeveloperIdentity;
import ca.weblite.jdeploy.models.NPMApplication;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.Date;

public class PackageJSONSignerOld {
    private DeveloperIdentitySigner developerIdentitySigner = new DeveloperIdentitySigner();
    private DeveloperIdentityKeyStore developerIdentityKeyStore = new DeveloperIdentityKeyStore();
    private DeveloperIdentityURLLoader identityLoader = new DeveloperIdentityURLLoader();
    private NPMApplicationSigner applicationSigner = new NPMApplicationSigner();
    private DeveloperIdentityJSONWriter identityJSONWriter = new DeveloperIdentityJSONWriter();


    public void signPackageJSON(JSONObject packageJSON, OutputStream identityFileOutputStream) throws IOException, UnrecoverableKeyException, CertificateException, KeyStoreException, NoSuchAlgorithmException, SignatureException, NoSuchProviderException, InvalidKeyException {
        NPMApplication application = new NPMApplication();
        application.setPackageName(packageJSON.getString("name"));
        application.setPackageVersion(packageJSON.getString("version"));
        application.setTimeStampString(new Date().getTime()+"");
        DeveloperIdentity masterDeveloperIdentity = DeveloperIdentityHelper.createFromPackageJSON(packageJSON);

        KeyPair keyPair = developerIdentityKeyStore.getKeyPair(true);
        if (identityFileOutputStream != null) {
            identityJSONWriter.writeIdentity(masterDeveloperIdentity, keyPair, identityFileOutputStream);
        }


        JSONObject jdeploy = packageJSON.has("jdeploy") ? packageJSON.getJSONObject("jdeploy") : new JSONObject();


        if (!jdeploy.has("identities")) {
            JSONArray identitiesJSON = new JSONArray();
            jdeploy.put("identities", identitiesJSON);
            if (masterDeveloperIdentity.getIdentityUrl() != null) {
                identitiesJSON.put(masterDeveloperIdentity.getIdentityUrl());
            }
            for (String url : masterDeveloperIdentity.getAliasUrls()) {
                identitiesJSON.put(url);
            }
        }

        JSONObject jsonSignatures = new JSONObject();
        jdeploy.put("signatures", jsonSignatures);


        if (jdeploy.has("identities")) {
            JSONArray identitiesJson = jdeploy.getJSONArray("identities");

            int len = identitiesJson.length();
            for (int i=0; i<len; i++) {
                Object identity = identitiesJson.get(i);
                if ((identity instanceof String) && ((String)identity).startsWith("https://")) {
                    DeveloperIdentity developerIdentity = new DeveloperIdentity();
                    developerIdentity.setIdentityUrl((String)identity);

                    applicationSigner.sign(keyPair, application, developerIdentity);


                    jsonSignatures.put(developerIdentity.getIdentityUrl(), Base64.getEncoder().encodeToString(application.getSignature(developerIdentity)));


                }
            }
        }
        jdeploy.put("timestamp", application.getTimeStampString());

    }

    public DeveloperIdentityKeyStore getDeveloperIdentityKeyStore() {
        return developerIdentityKeyStore;
    }

    public void setDeveloperIdentityKeyStore(DeveloperIdentityKeyStore developerIdentityKeyStore) {
        this.developerIdentityKeyStore = developerIdentityKeyStore;
    }

    public DeveloperIdentityURLLoader getIdentityLoader() {
        return identityLoader;
    }

    public void setIdentityLoader(DeveloperIdentityURLLoader identityLoader) {
        this.identityLoader = identityLoader;
    }

    public NPMApplicationSigner getApplicationSigner() {
        return applicationSigner;
    }

    public void setApplicationSigner(NPMApplicationSigner applicationSigner) {
        this.applicationSigner = applicationSigner;
    }
}

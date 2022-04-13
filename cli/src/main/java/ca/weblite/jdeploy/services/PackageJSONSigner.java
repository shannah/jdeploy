package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.DeveloperIdentity;
import ca.weblite.jdeploy.models.NPMApplication;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.Date;

public class PackageJSONSigner {
    private DeveloperIdentityKeyStore developerIdentityKeyStore = new DeveloperIdentityKeyStore();
    private DeveloperIdentityURLLoader identityLoader = new DeveloperIdentityURLLoader();
    private NPMApplicationSigner applicationSigner = new NPMApplicationSigner();

    public void signPackageJSON(JSONObject packageJSON) throws IOException, UnrecoverableKeyException, CertificateException, KeyStoreException, NoSuchAlgorithmException, SignatureException, NoSuchProviderException, InvalidKeyException {
        NPMApplication application = new NPMApplication();
        application.setPackageName(packageJSON.getString("name"));
        application.setPackageVersion(packageJSON.getString("version"));
        application.setTimeStampString(new Date().getTime()+"");

        JSONObject jdeploy = packageJSON.has("jdeploy") ? packageJSON.getJSONObject("jdeploy") : new JSONObject();



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

                    // We get a keypair from the keystore, but we don't want to generate it
                    // identities should be generated in a separate step.
                    KeyPair keyPair = developerIdentityKeyStore.getKeyPair(developerIdentity, false);
                    if (keyPair == null) {
                        throw new DeveloperIdentityNotFoundException("Key store did not contain a private key for signing the identity: "+developerIdentity.getIdentityUrl(), developerIdentity);
                    }
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

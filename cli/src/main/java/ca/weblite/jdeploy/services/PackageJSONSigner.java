package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.helpers.DeveloperIdentityHelper;
import ca.weblite.jdeploy.helpers.NPMApplicationHelper;
import ca.weblite.jdeploy.helpers.NPMApplicationSignatureHelper;
import ca.weblite.jdeploy.models.DeveloperIdentity;
import ca.weblite.jdeploy.models.NPMApplication;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Base64;

import static ca.weblite.jdeploy.helpers.NPMApplicationSignatureHelper.updateAppVersionSignature;

public class PackageJSONSigner {
    private DeveloperIdentityKeyStore developerIdentityKeyStore = new DeveloperIdentityKeyStore();
    private NPMApplicationSigner applicationSigner = new NPMApplicationSigner();
    public void signPackageJSON(JSONObject packageJSON) throws UnrecoverableKeyException, CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException, SignatureException, NoSuchProviderException, InvalidKeyException {
        KeyPair keyPair = developerIdentityKeyStore.getKeyPair(true);
        NPMApplication app = NPMApplicationHelper.createFromPackageJSON(packageJSON);
        DeveloperIdentity developerIdentity = DeveloperIdentityHelper.createFromPackageJSON(packageJSON);
        applicationSigner.sign(keyPair, app, developerIdentity);
        NPMApplicationHelper.updatePackageJSONSignatures(app, packageJSON);
    }

    public DeveloperIdentityKeyStore getDeveloperIdentityKeyStore() {
        return developerIdentityKeyStore;
    }

    public void setDeveloperIdentityKeyStore(DeveloperIdentityKeyStore developerIdentityKeyStore) {
        this.developerIdentityKeyStore = developerIdentityKeyStore;
    }
}

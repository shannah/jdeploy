package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.tests.helpers.PackageJSONHelper;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

class PackageJSONSignerTest {

    @Test
    void signPackageJSON() throws Exception {
        JSONObject packageJSON = PackageJSONHelper.createMockPackageJSON();
        PackageJSONSigner signer = new PackageJSONSigner();
        DeveloperIdentityKeyStore identityKeyStore = new DeveloperIdentityKeyStore();
        File keyStoreFile = File.createTempFile("tmpkeystore", ".ks");
        keyStoreFile.delete();
        signer.setDeveloperIdentityKeyStore(identityKeyStore);
        identityKeyStore.setKeyStoreFile(keyStoreFile);
        signer.signPackageJSON(packageJSON);

        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
        assertTrue(jdeploy.has("versionSignature"));
        assertTrue(jdeploy.has("appSignature"));
        assertTrue(jdeploy.has("developerPublicKey"));
        assertTrue(jdeploy.has("developerSignature"));
        assertTrue(jdeploy.has("timestamp"));
        //System.out.println(packageJSON.toString(2));
    }
}
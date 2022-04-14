package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.DeveloperIdentity;
import ca.weblite.jdeploy.models.NPMApplication;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.Base64;

import static ca.weblite.jdeploy.tests.helpers.DeveloperIdentifierKeyStoreHelper.createMockKeyStore;
import static ca.weblite.jdeploy.tests.helpers.DeveloperIdentityTestHelper.createMockIdentity;
import static ca.weblite.jdeploy.tests.helpers.PackageJSONHelper.createMockPackageJSON;
import static org.junit.jupiter.api.Assertions.*;

class PackageJSONSignerOldTest {

    @Test
    void signPackageJSON() throws Exception {
        DeveloperIdentityKeyStore developerIdentityKeyStore = createMockKeyStore();

        DeveloperIdentity identity = createMockIdentity();
        identity.setIdentityUrl("https://example.com/id.json");
        KeyPair keyPair = developerIdentityKeyStore.getKeyPair(true);
        identity.setPublicKey(keyPair.getPublic());

        PackageJSONSignerOld packageJSONSigner = new PackageJSONSignerOld();
        packageJSONSigner.setDeveloperIdentityKeyStore(developerIdentityKeyStore);
        JSONObject packageJSON = createMockPackageJSON();
        packageJSONSigner.signPackageJSON(packageJSON, null);

        assertEquals(1, packageJSON.getJSONObject("jdeploy").getJSONObject("signatures").length());

        NPMApplication app = new NPMApplication();
        app.setPackageName(packageJSON.getString("name"));
        app.setPackageVersion(packageJSON.getString("version"));
        app.setTimeStampString(packageJSON.getJSONObject("jdeploy").getString("timestamp"));

        NPMApplicationSigner appSigner = new NPMApplicationSigner();
        appSigner.sign(keyPair, app, identity);

        assertArrayEquals(app.getSignature(identity),
                Base64.getDecoder().decode(packageJSON.getJSONObject("jdeploy").getJSONObject("signatures").getString(identity.getIdentityUrl())));




    }

    @Test
    void signPackageJSONWithNoIdentities() throws Exception {
        DeveloperIdentityKeyStore developerIdentityKeyStore = createMockKeyStore();

        DeveloperIdentity identity = createMockIdentity();
        identity.setIdentityUrl("https://example.com/id.json");
        KeyPair keyPair = developerIdentityKeyStore.getKeyPair(true);
        identity.setPublicKey(keyPair.getPublic());

        PackageJSONSignerOld packageJSONSigner = new PackageJSONSignerOld();
        packageJSONSigner.setDeveloperIdentityKeyStore(developerIdentityKeyStore);
        JSONObject packageJSON = createMockPackageJSON();
        packageJSON.getJSONObject("jdeploy").remove("identities");
        packageJSONSigner.signPackageJSON(packageJSON, null);

        assertEquals(0, packageJSON.getJSONObject("jdeploy").getJSONObject("signatures").length());





    }
}
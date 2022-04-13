package ca.weblite.jdeploy.tests.helpers;

import ca.weblite.jdeploy.services.DeveloperIdentityKeyStore;

import java.io.File;
import java.io.IOException;

public class DeveloperIdentifierKeyStoreHelper {

    public static DeveloperIdentityKeyStore createMockKeyStore() throws IOException {
        DeveloperIdentityKeyStore out = new DeveloperIdentityKeyStore();
        out.setKeyStoreFile(File.createTempFile("testkeystore", ".ks"));
        out.getKeyStoreFile().delete();
        out.setKeyStorePassword("password".toCharArray());
        out.setKeyPassword("password".toCharArray());
        return out;

    }
}

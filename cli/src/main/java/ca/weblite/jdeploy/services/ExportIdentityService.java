package ca.weblite.jdeploy.services;

import ca.weblite.tools.security.CertificateUtil;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ExportIdentityService {
    private DeveloperIdentityKeyStore developerIdentityKeyStore = new DeveloperIdentityKeyStore();

    public void exportIdentityToFile(File outputFile) throws IOException {
        if (!outputFile.getParentFile().exists()) {
            throw new IOException("Output file "+outputFile+" cannot be created because the folder "+outputFile.getParentFile()+" does not exist.");
        }
        try {
            FileUtils.writeStringToFile(outputFile, developerIdentityKeyStore.getKeyPairAsPem(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IOException("Failed to export identity to file.", ex);
        }
    }

    public DeveloperIdentityKeyStore getDeveloperIdentityKeyStore() {
        return developerIdentityKeyStore;
    }

    public void setDeveloperIdentityKeyStore(DeveloperIdentityKeyStore developerIdentityKeyStore) {
        this.developerIdentityKeyStore = developerIdentityKeyStore;
    }
}

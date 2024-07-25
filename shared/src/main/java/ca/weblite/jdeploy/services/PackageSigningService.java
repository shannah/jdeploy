package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.factories.JDeployKeyProviderFactory;
import ca.weblite.tools.security.FileSigner;
import ca.weblite.tools.security.KeyProvider;

public class PackageSigningService {
    private final KeyProvider keyProvider;

    public PackageSigningService(
            JDeployKeyProviderFactory keyProviderFactory,
            JDeployKeyProviderFactory.KeyConfig config
    ) {
        this.keyProvider = keyProviderFactory.createKeyProvider(config);
    }

    public PackageSigningService(KeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    public void signPackage(String versionString, String packagePath) throws Exception {
        FileSigner.signDirectory(versionString, packagePath, keyProvider);
    }
}

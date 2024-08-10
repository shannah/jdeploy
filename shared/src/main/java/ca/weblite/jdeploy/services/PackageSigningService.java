package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.factories.JDeployKeyProviderFactory;
import ca.weblite.tools.security.CertificateUtil;
import ca.weblite.tools.security.FileSigner;
import ca.weblite.tools.security.KeyProvider;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

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

    public List<String> calculateCertificateHashes() throws Exception {
        List<String> hashes = new ArrayList<String>();
        for(Certificate certificate : keyProvider.getSigningCertificateChain()) {
            hashes.add(CertificateUtil.getSHA1Fingerprint(certificate));
        }

        return hashes;
    }
}

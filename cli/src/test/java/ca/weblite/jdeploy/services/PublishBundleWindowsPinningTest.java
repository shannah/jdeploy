package ca.weblite.jdeploy.services;

import static ca.weblite.jdeploy.BundleConstants.*;
import static org.junit.jupiter.api.Assertions.*;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.config.Config;
import ca.weblite.jdeploy.packaging.PackagingConfig;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.tools.security.KeyProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pre-built Windows bundles carry the certificate pins when packages are signed
 * and Windows (Authenticode) signing protects the executable that holds them.
 */
public class PublishBundleWindowsPinningTest {

    @TempDir
    File tempDir;

    private static class FakeWindowsSigning extends WindowsSigningConfigFactory {
        private final boolean configured;

        FakeWindowsSigning(boolean configured) {
            this.configured = configured;
        }

        @Override
        String getenv(String name) {
            if (!configured) {
                return null;
            }
            switch (name) {
                case "JDEPLOY_WIN_KEYSTORE_PATH": return "/tmp/signing.pfx";
                case "JDEPLOY_WIN_KEYSTORE_PASSWORD": return "password";
                default: return null;
            }
        }
    }

    private Certificate pinned;

    private PackagingContext context(boolean signPackage) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        java.security.KeyPair keys = gen.generateKeyPair();
        org.bouncycastle.asn1.x500.X500Name name = new org.bouncycastle.asn1.x500.X500Name("CN=Pinned");
        long now = System.currentTimeMillis();
        pinned = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(
                new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                        name, java.math.BigInteger.valueOf(now), new java.util.Date(now - 86400000L),
                        new java.util.Date(now + 86400000L * 365), name, keys.getPublic())
                        .build(new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA")
                                .build(keys.getPrivate())));
        KeyProvider keyProvider = new KeyProvider() {
            public PrivateKey getSigningKey() { return null; }
            public Certificate getSigningCertificate() { return pinned; }
            public List<Certificate> getSigningCertificateChain() { return Collections.singletonList(pinned); }
            public List<Certificate> getTrustedCertificates() { return Collections.singletonList(pinned); }
        };
        Map<String, Object> jdeploy = new HashMap<>();
        jdeploy.put("signPackage", signPackage);
        Map<String, Object> packageJson = new HashMap<>();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");
        packageJson.put("jdeploy", jdeploy);
        PrintStream nullOut = new PrintStream(new OutputStream() {
            public void write(int b) {}
        });
        return new PackagingContext(tempDir, packageJson, new File(tempDir, "package.json"),
                false, false, null, null, keyProvider, null, nullOut, nullOut,
                new ByteArrayInputStream(new byte[0]), false, false);
    }

    private AppInfo apply(boolean signPackage, boolean windowsSigning, String target) throws Exception {
        PublishBundleService service = new PublishBundleService(
                new PackagingConfig(new Config()), new WindowsSigningService(), new FakeWindowsSigning(windowsSigning));
        AppInfo appInfo = new AppInfo();
        service.applyWindowsCertificatePinning(context(signPackage), target, appInfo);
        return appInfo;
    }

    @Test
    public void windowsBundlesArePinnedWhenAuthenticodeSigningIsConfigured() throws Exception {
        for (String target : new String[] {BUNDLE_WIN_X64, BUNDLE_WIN_ARM64}) {
            AppInfo appInfo = apply(true, true, target);
            assertTrue(appInfo.isCertificatePinningEnabled(), target);
            assertEquals(Collections.singletonList(pinned), appInfo.getTrustedCertificates(), target);
        }
    }

    @Test
    public void windowsBundlesAreNotPinnedWithoutAuthenticodeSigning() throws Exception {
        assertFalse(apply(true, false, BUNDLE_WIN_X64).isCertificatePinningEnabled());
    }

    @Test
    public void bundlesAreNotPinnedWhenPackagesAreNotSigned() throws Exception {
        assertFalse(apply(false, true, BUNDLE_WIN_X64).isCertificatePinningEnabled());
    }

    @Test
    public void otherPlatformsAreUnchanged() throws Exception {
        assertFalse(apply(true, true, BUNDLE_MAC_ARM64).isCertificatePinningEnabled());
        assertFalse(apply(true, true, BUNDLE_LINUX_X64).isCertificatePinningEnabled());
    }
}

package ca.weblite.jdeploy.services;

import ca.weblite.tools.security.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.jar.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests that modifying JAR files after signing breaks certificate pinning verification,
 * and that re-signing after modification restores valid verification.
 *
 * This reproduces the bug where platform-specific tarballs have their JARs filtered
 * by processJarsWithIgnoreService() AFTER signing, invalidating the signatures.
 */
class PlatformBundleSigningTest {

    private static final String VERSION = "1.0.0";

    @TempDir
    File tempDir;

    private PrivateKey privateKey;
    private X509Certificate certificate;
    private KeyProvider keyProvider;
    private CertificateVerifier certificateVerifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        privateKey = keyPair.getPrivate();
        certificate = generateSelfSignedCertificate(keyPair);

        keyProvider = new KeyProvider() {
            @Override
            public PrivateKey getSigningKey() {
                return privateKey;
            }

            @Override
            public Certificate getSigningCertificate() {
                return certificate;
            }

            @Override
            public List<Certificate> getSigningCertificateChain() {
                return Collections.singletonList(certificate);
            }

            @Override
            public List<Certificate> getTrustedCertificates() {
                return Collections.singletonList(certificate);
            }
        };

        certificateVerifier = mock(CertificateVerifier.class);
        when(certificateVerifier.isTrusted(anyList())).thenReturn(true);
    }

    @Test
    @DisplayName("Modifying a JAR after signing breaks certificate pinning verification")
    void modifyingJarAfterSigningBreaksVerification() throws Exception {
        // Set up: create a jdeploy-bundle directory with a JAR
        File bundleDir = new File(tempDir, "jdeploy-bundle");
        bundleDir.mkdirs();
        File jarFile = createTestJar(bundleDir, "app.jar",
                "com/example/Main.class", "main class bytes",
                "native/windows/lib.dll", "windows native lib",
                "native/mac/lib.dylib", "mac native lib"
        );

        // Phase 1: Sign the bundle
        FileSigner.signDirectory(VERSION, bundleDir.getAbsolutePath(), keyProvider);

        // Verify: signing is valid
        VerificationResult result = FileVerifier.verifyDirectory(
                VERSION, bundleDir.getAbsolutePath(), certificateVerifier
        );
        assertEquals(VerificationResult.SIGNED_CORRECTLY, result,
                "Bundle should verify correctly immediately after signing");

        // Phase 2: Simulate platform filtering — rewrite JAR with some entries removed
        // This is what processJarsWithIgnoreService() does: strips native libs for other platforms
        rewriteJarWithoutEntries(jarFile, "native/windows/lib.dll");

        // Verify: signing is NOW BROKEN — this proves the bug
        VerificationResult brokenResult = FileVerifier.verifyDirectory(
                VERSION, bundleDir.getAbsolutePath(), certificateVerifier
        );
        assertEquals(VerificationResult.SIGNATURE_MISMATCH, brokenResult,
                "Bug: modifying a JAR after signing should break certificate pinning verification");
    }

    @Test
    @DisplayName("Re-signing after JAR modification restores valid certificate pinning verification")
    void reSigningAfterModificationRestoresVerification() throws Exception {
        // Set up: create a jdeploy-bundle directory with a JAR
        File bundleDir = new File(tempDir, "jdeploy-bundle");
        bundleDir.mkdirs();
        File jarFile = createTestJar(bundleDir, "app.jar",
                "com/example/Main.class", "main class bytes",
                "native/windows/lib.dll", "windows native lib",
                "native/mac/lib.dylib", "mac native lib"
        );

        // Phase 1: Sign
        FileSigner.signDirectory(VERSION, bundleDir.getAbsolutePath(), keyProvider);
        assertEquals(VerificationResult.SIGNED_CORRECTLY,
                FileVerifier.verifyDirectory(VERSION, bundleDir.getAbsolutePath(), certificateVerifier));

        // Phase 2: Filter (breaks signing)
        rewriteJarWithoutEntries(jarFile, "native/windows/lib.dll");
        assertEquals(VerificationResult.SIGNATURE_MISMATCH,
                FileVerifier.verifyDirectory(VERSION, bundleDir.getAbsolutePath(), certificateVerifier),
                "Filtering should break signatures");

        // Phase 3: Re-sign (the fix)
        FileSigner.signDirectory(VERSION, bundleDir.getAbsolutePath(), keyProvider);

        // Verify: signing is valid again — this proves the fix works
        VerificationResult fixedResult = FileVerifier.verifyDirectory(
                VERSION, bundleDir.getAbsolutePath(), certificateVerifier
        );
        assertEquals(VerificationResult.SIGNED_CORRECTLY, fixedResult,
                "Re-signing after filtering should restore valid certificate pinning verification");
    }

    @Test
    @DisplayName("Re-signing via PackageSigningService after JAR modification restores verification")
    void reSigningViaPackageSigningServiceRestoresVerification() throws Exception {
        // This test uses PackageSigningService (the actual class used in production code)
        // to verify the fix end-to-end through the service layer.
        PackageSigningService signingService = new PackageSigningService(keyProvider);

        File bundleDir = new File(tempDir, "jdeploy-bundle");
        bundleDir.mkdirs();
        File jarFile = createTestJar(bundleDir, "app.jar",
                "com/example/Main.class", "main class bytes",
                "native/linux/lib.so", "linux native lib",
                "native/mac/lib.dylib", "mac native lib"
        );

        // Sign via service
        signingService.signPackage(VERSION, bundleDir.getAbsolutePath());
        assertEquals(VerificationResult.SIGNED_CORRECTLY,
                FileVerifier.verifyDirectory(VERSION, bundleDir.getAbsolutePath(), certificateVerifier));

        // Filter JARs (breaks signing)
        rewriteJarWithoutEntries(jarFile, "native/linux/lib.so");
        assertEquals(VerificationResult.SIGNATURE_MISMATCH,
                FileVerifier.verifyDirectory(VERSION, bundleDir.getAbsolutePath(), certificateVerifier));

        // Re-sign via service (the fix)
        signingService.signPackage(VERSION, bundleDir.getAbsolutePath());
        assertEquals(VerificationResult.SIGNED_CORRECTLY,
                FileVerifier.verifyDirectory(VERSION, bundleDir.getAbsolutePath(), certificateVerifier),
                "Re-signing via PackageSigningService should restore valid verification");
    }

    /**
     * Creates a test JAR with the given entries.
     * @param dir directory to create the JAR in
     * @param jarName filename of the JAR
     * @param entriesAndContents alternating entry name / content pairs
     * @return the created JAR file
     */
    private File createTestJar(File dir, String jarName, String... entriesAndContents) throws IOException {
        File jarFile = new File(dir, jarName);
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
            for (int i = 0; i < entriesAndContents.length; i += 2) {
                String entryName = entriesAndContents[i];
                String content = entriesAndContents[i + 1];
                jos.putNextEntry(new JarEntry(entryName));
                jos.write(content.getBytes("UTF-8"));
                jos.closeEntry();
            }
        }
        return jarFile;
    }

    /**
     * Rewrites a JAR file with specified entries removed.
     * This simulates what PlatformSpecificJarProcessor.processJarForPlatform() does
     * when stripping native libraries for other platforms.
     */
    private void rewriteJarWithoutEntries(File jarFile, String... entriesToRemove) throws IOException {
        Set<String> removeSet = new HashSet<>(Arrays.asList(entriesToRemove));
        File tempFile = new File(jarFile.getParentFile(), jarFile.getName() + ".tmp");

        try (JarInputStream jis = new JarInputStream(new FileInputStream(jarFile));
             JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempFile))) {
            JarEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = jis.getNextJarEntry()) != null) {
                if (removeSet.contains(entry.getName())) {
                    continue; // Skip this entry
                }
                jos.putNextEntry(new JarEntry(entry.getName()));
                int bytesRead;
                while ((bytesRead = jis.read(buffer)) != -1) {
                    jos.write(buffer, 0, bytesRead);
                }
                jos.closeEntry();
            }
        }

        if (!jarFile.delete() || !tempFile.renameTo(jarFile)) {
            throw new IOException("Failed to replace JAR file");
        }
    }

    private X509Certificate generateSelfSignedCertificate(KeyPair keyPair) throws Exception {
        long now = System.currentTimeMillis();
        X500Name dnName = new X500Name("CN=Test Certificate");
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate());
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                dnName,
                new BigInteger(Long.toString(now)),
                new Date(now),
                new Date(now + 365 * 24 * 60 * 60 * 1000L),
                dnName,
                keyPair.getPublic()
        );
        return new JcaX509CertificateConverter().getCertificate(certBuilder.build(contentSigner));
    }
}

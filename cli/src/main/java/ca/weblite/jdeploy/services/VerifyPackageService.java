package ca.weblite.jdeploy.services;

import ca.weblite.tools.security.*;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VerifyPackageService {

    public static class Parameters {
        public String version;
        public String jdeployBundlePath;
        public String keyStore;
    }

    public static class Result {
        public boolean verified;
        public String errorMessage;
        public VerificationResult verificationResult;

        private Result(boolean verified, String errorMessage, VerificationResult verificationResult) {
            this.verified = verified;
            this.errorMessage = errorMessage;
            this.verificationResult = verificationResult;
        }
    }

    public Result verifyPackage(Parameters params) {
        try {
            validateParameters(params);
            KeyStore trustedCertificates = loadTrustedCertificates(params.keyStore);
            VerificationResult verificationResult = FileVerifier.verifyDirectory(
                    params.version,
                    params.jdeployBundlePath,
                    createCertificateVerifier(trustedCertificates)
            );

            switch (verificationResult) {
                case NOT_SIGNED_AT_ALL:
                    return new Result(false, "The package is not signed", verificationResult);
                case UNTRUSTED_CERTIFICATE:
                    return new Result(
                            false,
                            "The package is signed with an untrusted certificate",
                            verificationResult
                    );
                case SIGNED_CORRECTLY:
                    return new Result(true, null, verificationResult);
                case SIGNATURE_MISMATCH:
                    return new Result(
                            false, "The package signature does not match the contents",
                            verificationResult
                    );
                default:
                    return new Result(
                            false, "Unknown verification result: " + verificationResult,
                            verificationResult
                    );
            }

        } catch (Exception e) {
            return new Result(false, e.getMessage(), null);
        }
    }

    private void validateParameters(Parameters params) {
        if (params.jdeployBundlePath == null || params.jdeployBundlePath.isEmpty()) {
            throw new IllegalArgumentException("jdeployBundlePath is required");
        }
        if (params.keyStore == null || params.keyStore.isEmpty()) {
            throw new IllegalArgumentException("trustedCertificatesPemString is required");
        }
    }

    protected KeyStore loadTrustedCertificates(String keyStore) throws Exception {
        if (isPemString(keyStore)) {
            return CertificateUtil.loadCertificatesFromPEM(keyStore);
        } else if (isJksFile(keyStore)) {
            return loadJksFile(keyStore);
        } else if (isPemFile(keyStore)) {
            return loadPemFile(keyStore);
        } else if (isDerFile(keyStore)) {
            return loadDerFile(keyStore);
        } else if (isPkcs12File(keyStore)) {
            return loadPkcs12File(keyStore);
        } else if (isPkcs7File(keyStore)) {
            return loadPkcs7File(keyStore);
        } else {
            throw new IllegalArgumentException("Invalid key store format");
        }
    }

    private boolean isPemString(String keyStore) {
        return keyStore.startsWith("-----BEGIN CERTIFICATE-----");
    }

    private boolean isJksFile(String keyStore) {
        return isFile(keyStore, ".jks");
    }

    private boolean isPemFile(String keyStore) {
        return isFile(keyStore, ".pem");
    }

    private boolean isDerFile(String keyStore) {
        return isFile(keyStore, ".der") || isFile(keyStore, ".cer") || isFile(keyStore, ".crt");
    }

    private boolean isFile(String path, String extension) {
        return path.endsWith(extension) && (new File(path)).exists();
    }

    private boolean isPkcs12File(String keyStore) {
        return isFile(keyStore, ".p12") || isFile(keyStore, ".pfx");
    }

    private boolean isPkcs7File(String keyStore) {
        return isFile(keyStore, ".p7b") || isFile(keyStore, ".p7c");
    }

    private KeyStore loadDerFile(String filePath) throws Exception {
        // Load the DER file into a byte array
        FileInputStream fis = new FileInputStream(filePath);
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

        // Create the X.509 certificate from the input stream
        X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(fis);
        fis.close();

        // Extract the common name (CN) from the certificate's subject DN
        String subjectDN = certificate.getSubjectX500Principal().getName();
        String alias = getCommonName(subjectDN);

        // Create a new KeyStore instance
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null); // Initialize an empty KeyStore

        keyStore.setCertificateEntry(alias, certificate);

        return keyStore;
    }

    private KeyStore loadJksFile(String filePath) throws Exception {
        // Load the JKS file
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        FileInputStream fis = new FileInputStream(filePath);
        keyStore.load(fis, null);
        fis.close();
        return keyStore;
    }

    private KeyStore loadPkcs7File(String filePath) throws Exception {
        // Load the PKCS#7 file
        FileInputStream fis = new FileInputStream(filePath);
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(fis);
        fis.close();

        // Create a new KeyStore instance
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null); // Initialize an empty KeyStore

        // Add each certificate to the KeyStore
        int i = 1;
        for (Certificate certificate : certificates) {
            String subjectDN = ((X509Certificate)certificate).getSubjectX500Principal().getName();
            String alias = getCommonName(subjectDN);
            keyStore.setCertificateEntry(alias, certificate);
        }

        return keyStore;
    }

    private KeyStore loadPkcs12File(String filePath) throws Exception {
        // Load the PKCS#12 file
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        FileInputStream fis = new FileInputStream(filePath);
        keyStore.load(fis, null);
        fis.close();
        return keyStore;
    }

    private KeyStore loadPemFile(String filePath) throws Exception {
        // Load the PEM file
        FileInputStream fis = new FileInputStream(filePath);
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(fis);
        fis.close();

        // Create a new KeyStore instance
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null); // Initialize an empty KeyStore

        // Add each certificate to the KeyStore
        int i = 1;
        for (Certificate certificate : certificates) {
            String subjectDN = ((X509Certificate)certificate).getSubjectX500Principal().getName();
            String alias = getCommonName(subjectDN);
            keyStore.setCertificateEntry(alias, certificate);
        }

        return keyStore;
    }

    private String getCommonName(String subjectDN) {
        Pattern cnPattern = Pattern.compile("CN=([^,]+)");
        Matcher matcher = cnPattern.matcher(subjectDN);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("No CN found in subject DN: " + subjectDN);
    }

    private CertificateVerifier createCertificateVerifier(KeyStore trustedCertificates) {
        return new SimpleCertificateVerifier(trustedCertificates);
    }
}

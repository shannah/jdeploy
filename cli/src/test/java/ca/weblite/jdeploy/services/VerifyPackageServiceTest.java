package ca.weblite.jdeploy.services;

import ca.weblite.tools.security.CertificateVerifier;
import ca.weblite.tools.security.FileVerifier;
import ca.weblite.tools.security.VerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class VerifyPackageServiceTest {

    private VerifyPackageService verifyPackageService;

    @BeforeEach
    void setUp() {
        verifyPackageService = new VerifyPackageService();
    }

    @Test
    void testVerifyPackageSignedCorrectly() throws Exception {
        VerifyPackageService.Parameters params = new VerifyPackageService.Parameters();
        params.version = "1.0.0";
        params.jdeployBundlePath = "path/to/bundle";
        params.keyStore = "path/to/keystore.jks";

        KeyStore mockKeyStore = mock(KeyStore.class);

        // Mock the static methods
        try (MockedStatic<FileVerifier> fileVerifierMock = mockStatic(FileVerifier.class)) {
            fileVerifierMock.when(() -> FileVerifier.verifyDirectory(anyString(), anyString(), any(CertificateVerifier.class)))
                    .thenReturn(VerificationResult.SIGNED_CORRECTLY);

            VerifyPackageService spyService = Mockito.spy(verifyPackageService);
            doReturn(mockKeyStore).when(spyService).loadTrustedCertificates(anyString());

            VerifyPackageService.Result result = spyService.verifyPackage(params);

            assertTrue(result.verified);
            assertNull(result.errorMessage);
            assertEquals(VerificationResult.SIGNED_CORRECTLY, result.verificationResult);
        }
    }

    @Test
    void testVerifyPackageNotSignedAtAll() throws Exception {
        VerifyPackageService.Parameters params = new VerifyPackageService.Parameters();
        params.version = "1.0.0";
        params.jdeployBundlePath = "path/to/bundle";
        params.keyStore = "path/to/keystore.jks";

        KeyStore mockKeyStore = mock(KeyStore.class);

        // Mock the static methods
        try (MockedStatic<FileVerifier> fileVerifierMock = mockStatic(FileVerifier.class)) {
            fileVerifierMock.when(() -> FileVerifier.verifyDirectory(anyString(), anyString(), any(CertificateVerifier.class)))
                    .thenReturn(VerificationResult.NOT_SIGNED_AT_ALL);

            VerifyPackageService spyService = Mockito.spy(verifyPackageService);
            doReturn(mockKeyStore).when(spyService).loadTrustedCertificates(anyString());

            VerifyPackageService.Result result = spyService.verifyPackage(params);

            assertFalse(result.verified);
            assertEquals("The package is not signed", result.errorMessage);
            assertEquals(VerificationResult.NOT_SIGNED_AT_ALL, result.verificationResult);
        }
    }

    @Test
    void testVerifyPackageUntrustedCertificate() throws Exception {
        VerifyPackageService.Parameters params = new VerifyPackageService.Parameters();
        params.version = "1.0.0";
        params.jdeployBundlePath = "path/to/bundle";
        params.keyStore = "path/to/keystore.jks";

        KeyStore mockKeyStore = mock(KeyStore.class);

        // Mock the static methods
        try (MockedStatic<FileVerifier> fileVerifierMock = mockStatic(FileVerifier.class)) {
            fileVerifierMock.when(() -> FileVerifier.verifyDirectory(anyString(), anyString(), any(CertificateVerifier.class)))
                    .thenReturn(VerificationResult.UNTRUSTED_CERTIFICATE);

            VerifyPackageService spyService = Mockito.spy(verifyPackageService);
            doReturn(mockKeyStore).when(spyService).loadTrustedCertificates(anyString());

            VerifyPackageService.Result result = spyService.verifyPackage(params);

            assertFalse(result.verified);
            assertEquals("The package is signed with an untrusted certificate", result.errorMessage);
            assertEquals(VerificationResult.UNTRUSTED_CERTIFICATE, result.verificationResult);
        }
    }

    @Test
    void testVerifyPackageSignatureMismatch() throws Exception {
        VerifyPackageService.Parameters params = new VerifyPackageService.Parameters();
        params.version = "1.0.0";
        params.jdeployBundlePath = "path/to/bundle";
        params.keyStore = "path/to/keystore.jks";

        KeyStore mockKeyStore = mock(KeyStore.class);

        // Mock the static methods
        try (MockedStatic<FileVerifier> fileVerifierMock = mockStatic(FileVerifier.class)) {
            fileVerifierMock.when(() -> FileVerifier.verifyDirectory(anyString(), anyString(), any(CertificateVerifier.class)))
                    .thenReturn(VerificationResult.SIGNATURE_MISMATCH);

            VerifyPackageService spyService = Mockito.spy(verifyPackageService);
            doReturn(mockKeyStore).when(spyService).loadTrustedCertificates(anyString());

            VerifyPackageService.Result result = spyService.verifyPackage(params);

            assertFalse(result.verified);
            assertEquals("The package signature does not match the contents", result.errorMessage);
            assertEquals(VerificationResult.SIGNATURE_MISMATCH, result.verificationResult);
        }
    }

    @Test
    void testVerifyPackageException() throws Exception {
        VerifyPackageService.Parameters params = new VerifyPackageService.Parameters();
        params.version = "1.0.0";
        params.jdeployBundlePath = "path/to/bundle";
        params.keyStore = "path/to/keystore.jks";

        VerifyPackageService spyService = Mockito.spy(verifyPackageService);
        doThrow(new RuntimeException("Test Exception")).when(spyService).loadTrustedCertificates(anyString());

        VerifyPackageService.Result result = spyService.verifyPackage(params);

        assertFalse(result.verified);
        assertEquals("Test Exception", result.errorMessage);
        assertNull(result.verificationResult);
    }
}

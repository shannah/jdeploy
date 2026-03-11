package ca.weblite.jdeploy.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class WindowsSigningServiceTest {

    @TempDir
    File tempDir;

    @Test
    void sign_throwsIOException_whenExeDoesNotExist() {
        WindowsSigningService service = new WindowsSigningService();
        WindowsSigningConfig config = new WindowsSigningConfig();
        config.setKeystorePath("/nonexistent.pfx");

        File nonexistent = new File(tempDir, "nonexistent.exe");
        assertThrows(IOException.class, () -> service.sign(nonexistent, config));
    }

    @Test
    void sign_throwsIllegalStateException_whenConfigInvalid() {
        WindowsSigningService service = new WindowsSigningService();
        WindowsSigningConfig config = new WindowsSigningConfig();
        // No keystore path set

        File fakeExe = new File(tempDir, "test.exe");
        assertThrows(IllegalStateException.class, () -> service.sign(fakeExe, config));
    }
}

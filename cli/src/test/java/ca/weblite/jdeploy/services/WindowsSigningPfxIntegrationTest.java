package ca.weblite.jdeploy.services;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Windows Authenticode signing with a PFX keystore.
 *
 * <p>This test generates a self-signed code signing certificate in a PFX keystore,
 * creates a minimal valid PE executable, and signs it using {@link WindowsSigningService}.
 * No external HSM or certificate authority is required.</p>
 *
 * <p>Skipped if {@code keytool} is not available on the system.</p>
 */
class WindowsSigningPfxIntegrationTest {

    @TempDir
    static Path tempDir;

    private static File keystoreFile;
    private static File testExe;
    private static final String KEYSTORE_PASSWORD = "testpass123";
    private static final String KEY_ALIAS = "codesign";
    private static boolean keytoolAvailable;

    @BeforeAll
    static void setUp() throws Exception {
        keytoolAvailable = isKeytoolAvailable();
        if (!keytoolAvailable) {
            return;
        }

        keystoreFile = tempDir.resolve("test-codesign.pfx").toFile();
        testExe = tempDir.resolve("test-app.exe").toFile();

        generateSelfSignedCert();
        createMinimalPeExe(testExe);
    }

    @Test
    void signExe_withPfxKeystore_succeeds() throws Exception {
        Assumptions.assumeTrue(keytoolAvailable, "keytool not available");

        WindowsSigningConfig config = new WindowsSigningConfig();
        config.setKeystorePath(keystoreFile.getAbsolutePath());
        config.setKeystorePassword(KEYSTORE_PASSWORD);
        config.setAlias(KEY_ALIAS);
        config.setKeystoreType("PKCS12");
        config.setTimestampUrl(""); // skip timestamping for test speed
        config.setDescription("Test Application");
        config.setUrl("https://example.com");

        WindowsSigningService service = new WindowsSigningService();

        long sizeBefore = testExe.length();
        service.sign(testExe, config);
        long sizeAfter = testExe.length();

        // Signed EXE should be larger (Authenticode signature is appended)
        assertTrue(sizeAfter > sizeBefore,
                "Signed EXE (" + sizeAfter + " bytes) should be larger than unsigned (" + sizeBefore + " bytes)");
    }

    @Test
    void signExe_withPfxKeystore_outputToSeparateFile() throws Exception {
        Assumptions.assumeTrue(keytoolAvailable, "keytool not available");

        // Create a fresh unsigned EXE for this test
        File inputExe = tempDir.resolve("input.exe").toFile();
        File outputExe = tempDir.resolve("signed-output.exe").toFile();
        createMinimalPeExe(inputExe);

        WindowsSigningConfig config = new WindowsSigningConfig();
        config.setKeystorePath(keystoreFile.getAbsolutePath());
        config.setKeystorePassword(KEYSTORE_PASSWORD);
        config.setAlias(KEY_ALIAS);
        config.setTimestampUrl("");

        WindowsSigningService service = new WindowsSigningService();
        service.sign(inputExe, outputExe, config);

        assertTrue(outputExe.exists(), "Signed output should exist");
        assertTrue(outputExe.length() > inputExe.length(),
                "Signed output should be larger than unsigned input");
    }

    @Test
    void signExe_withAutoAlias_succeeds() throws Exception {
        Assumptions.assumeTrue(keytoolAvailable, "keytool not available");

        File exe = tempDir.resolve("auto-alias.exe").toFile();
        createMinimalPeExe(exe);

        WindowsSigningConfig config = new WindowsSigningConfig();
        config.setKeystorePath(keystoreFile.getAbsolutePath());
        config.setKeystorePassword(KEYSTORE_PASSWORD);
        // Don't set alias — should auto-detect the first entry
        config.setTimestampUrl("");

        WindowsSigningService service = new WindowsSigningService();
        assertDoesNotThrow(() -> service.sign(exe, config));
    }

    private static void generateSelfSignedCert() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "keytool",
                "-genkeypair",
                "-alias", KEY_ALIAS,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "1",
                "-dname", "CN=Test Code Signing, O=Test, L=Test, ST=Test, C=US",
                "-ext", "EKU=codeSigning",
                "-storetype", "PKCS12",
                "-keystore", keystoreFile.getAbsolutePath(),
                "-storepass", KEYSTORE_PASSWORD,
                "-keypass", KEYSTORE_PASSWORD
        );
        pb.inheritIO();
        Process p = pb.start();
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("keytool failed with exit code " + exitCode);
        }
    }

    /**
     * Creates a minimal valid PE (Portable Executable) file.
     *
     * <p>This creates the smallest valid PE that jsign can sign: a DOS header
     * pointing to a PE signature, followed by a minimal COFF header and
     * optional header. The executable doesn't need to actually run — it just
     * needs valid PE structure for Authenticode signing.</p>
     */
    static void createMinimalPeExe(File exeFile) throws IOException {
        // Minimal PE: DOS stub + PE header + optional header + one section
        byte[] pe = new byte[512];

        // DOS Header
        pe[0] = 'M';
        pe[1] = 'Z';
        // e_lfanew: offset to PE signature at byte 60-63 (little-endian)
        pe[60] = (byte) 0x80; // PE header starts at offset 128

        // PE Signature at offset 0x80
        pe[0x80] = 'P';
        pe[0x81] = 'E';
        pe[0x82] = 0;
        pe[0x83] = 0;

        // COFF Header (20 bytes starting at 0x84)
        pe[0x84] = 0x4C; // Machine: i386 (0x014C)
        pe[0x85] = 0x01;
        pe[0x86] = 0x01; // NumberOfSections: 1
        pe[0x87] = 0x00;
        // TimeDateStamp (4 bytes) - leave zero
        // PointerToSymbolTable (4 bytes) - leave zero
        // NumberOfSymbols (4 bytes) - leave zero
        pe[0x94] = (byte) 0xE0; // SizeOfOptionalHeader: 224 (0x00E0) for PE32
        pe[0x95] = 0x00;
        pe[0x96] = 0x02; // Characteristics: EXECUTABLE_IMAGE
        pe[0x97] = 0x00;

        // Optional Header (starts at 0x98)
        pe[0x98] = 0x0B; // Magic: PE32 (0x010B)
        pe[0x99] = 0x01;

        // SectionAlignment at offset 0x98+32 = 0xB8
        pe[0xB8] = 0x00;
        pe[0xB9] = 0x10; // 0x1000
        pe[0xBA] = 0x00;
        pe[0xBB] = 0x00;

        // FileAlignment at offset 0x98+36 = 0xBC
        pe[0xBC] = 0x00;
        pe[0xBD] = 0x02; // 0x200 = 512
        pe[0xBE] = 0x00;
        pe[0xBF] = 0x00;

        // SizeOfHeaders at offset 0x98+60 = 0xD4
        pe[0xD4] = 0x00;
        pe[0xD5] = 0x02; // 0x200 = 512
        pe[0xD6] = 0x00;
        pe[0xD7] = 0x00;

        // NumberOfRvaAndSizes at offset 0x98+116 = 0x10C
        pe[0x10C] = 0x10; // 16
        pe[0x10D] = 0x00;
        pe[0x10E] = 0x00;
        pe[0x10F] = 0x00;

        // Section header starts at 0x98 + 224 = 0x178
        // Name: ".text"
        pe[0x178] = '.';
        pe[0x179] = 't';
        pe[0x17A] = 'e';
        pe[0x17B] = 'x';
        pe[0x17C] = 't';

        Files.write(exeFile.toPath(), pe);
    }

    private static boolean isKeytoolAvailable() {
        try {
            Process p = new ProcessBuilder("keytool", "-help")
                    .redirectErrorStream(true)
                    .start();
            p.getInputStream().close();
            p.waitFor();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

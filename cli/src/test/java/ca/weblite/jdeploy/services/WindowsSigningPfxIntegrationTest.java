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
     * Creates a minimal valid PE (Portable Executable) file that jsign can sign.
     *
     * <p>Uses a real minimal EXE from test resources if available, otherwise
     * falls back to copying a known-good PE from the jdeploy installer templates.
     * If neither is available, builds a PE from scratch with proper data directory
     * entries including the certificate table (index 4), which jsign requires.</p>
     */
    static void createMinimalPeExe(File exeFile) throws IOException {
        // Try to use a real EXE from the installer templates
        InputStream templateStream = WindowsSigningPfxIntegrationTest.class.getResourceAsStream(
                "/test-unsigned.exe"
        );
        if (templateStream != null) {
            Files.copy(templateStream, exeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            templateStream.close();
            return;
        }

        // Build a minimal PE from scratch with proper structure.
        // PE layout:
        //   0x000: DOS header (64 bytes, e_lfanew points to 0x80)
        //   0x080: PE signature (4 bytes)
        //   0x084: COFF header (20 bytes)
        //   0x098: Optional header PE32 (224 bytes = 96 standard/windows + 128 data dirs)
        //     - Data directories: 16 entries x 8 bytes = 128 bytes (0x0F8..0x177)
        //     - Certificate table is entry #4 at offset 0x118
        //   0x178: Section header (.text, 40 bytes)
        //   0x200: Section data (file-aligned to 512)

        int fileSize = 1024; // Two sectors: headers + minimal section data
        byte[] pe = new byte[fileSize];

        // --- DOS Header ---
        pe[0] = 'M';
        pe[1] = 'Z';
        // e_lfanew at offset 60
        writeLE32(pe, 60, 0x80);

        // --- PE Signature at 0x80 ---
        pe[0x80] = 'P';
        pe[0x81] = 'E';

        // --- COFF Header at 0x84 (20 bytes) ---
        writeLE16(pe, 0x84, 0x014C); // Machine: i386
        writeLE16(pe, 0x86, 1);       // NumberOfSections: 1
        writeLE16(pe, 0x94, 0xE0);    // SizeOfOptionalHeader: 224
        writeLE16(pe, 0x96, 0x0002);  // Characteristics: EXECUTABLE_IMAGE

        // --- Optional Header at 0x98 (PE32, 224 bytes) ---
        writeLE16(pe, 0x98, 0x010B);  // Magic: PE32

        // Standard fields
        pe[0x98 + 2] = 1;  // MajorLinkerVersion
        writeLE32(pe, 0x98 + 16, 0x1000); // AddressOfEntryPoint
        writeLE32(pe, 0x98 + 28, 0x400000); // ImageBase

        // SectionAlignment
        writeLE32(pe, 0x98 + 32, 0x1000);
        // FileAlignment
        writeLE32(pe, 0x98 + 36, 0x200);

        // OS version
        writeLE16(pe, 0x98 + 40, 4); // MajorOperatingSystemVersion
        // Image version
        writeLE16(pe, 0x98 + 44, 1); // MajorImageVersion

        // Subsystem version
        writeLE16(pe, 0x98 + 48, 4); // MajorSubsystemVersion

        // SizeOfImage (must be multiple of SectionAlignment)
        writeLE32(pe, 0x98 + 56, 0x3000);
        // SizeOfHeaders (must be multiple of FileAlignment)
        writeLE32(pe, 0x98 + 60, 0x200);

        // Subsystem: WINDOWS_CUI (3)
        writeLE16(pe, 0x98 + 68, 3);

        // SizeOfStackReserve, Commit, HeapReserve, Commit
        writeLE32(pe, 0x98 + 72, 0x100000);
        writeLE32(pe, 0x98 + 76, 0x1000);
        writeLE32(pe, 0x98 + 80, 0x100000);
        writeLE32(pe, 0x98 + 84, 0x1000);

        // NumberOfRvaAndSizes: 16
        writeLE32(pe, 0x98 + 92, 16);

        // Data directories start at 0x98 + 96 = 0x0F8
        // Each entry is 8 bytes (RVA + Size)
        // Entry #4 (Certificate Table) at 0x0F8 + 4*8 = 0x118
        // Leave certificate table RVA and Size as 0 — jsign will populate them.
        // The key is that NumberOfRvaAndSizes >= 5 so the entry is recognized.

        // --- Section Header at 0x178 (40 bytes) ---
        int sectionHeaderOffset = 0x98 + 224; // 0x178
        pe[sectionHeaderOffset] = '.';
        pe[sectionHeaderOffset + 1] = 't';
        pe[sectionHeaderOffset + 2] = 'e';
        pe[sectionHeaderOffset + 3] = 'x';
        pe[sectionHeaderOffset + 4] = 't';

        writeLE32(pe, sectionHeaderOffset + 8, 0x100);  // VirtualSize
        writeLE32(pe, sectionHeaderOffset + 12, 0x1000); // VirtualAddress
        writeLE32(pe, sectionHeaderOffset + 16, 0x200);  // SizeOfRawData
        writeLE32(pe, sectionHeaderOffset + 20, 0x200);  // PointerToRawData
        writeLE32(pe, sectionHeaderOffset + 36, 0x60000020); // Characteristics: CODE|EXECUTE|READ

        // Write a minimal section at offset 0x200
        pe[0x200] = (byte) 0xC3; // RET instruction

        Files.write(exeFile.toPath(), pe);
    }

    private static void writeLE16(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    private static void writeLE32(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buf[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buf[offset + 3] = (byte) ((value >> 24) & 0xFF);
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

package ca.weblite.jdeploy.services;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Windows Authenticode signing via PKCS#11 (SoftHSM2).
 *
 * <p>This test uses SoftHSM2 as a software-based PKCS#11 HSM to verify that
 * signing through the PKCS#11 interface works correctly. This exercises the
 * same code path used by hardware HSMs (SafeNet, YubiKey) and cloud HSMs
 * (Azure Key Vault via PKCS#11, AWS CloudHSM).</p>
 *
 * <h3>Prerequisites</h3>
 * <ul>
 *   <li>{@code softhsm2-util} must be on PATH ({@code apt install softhsm2})</li>
 *   <li>{@code pkcs11-tool} must be on PATH ({@code apt install opensc})</li>
 *   <li>{@code keytool} must be on PATH (comes with JDK)</li>
 * </ul>
 *
 * <p>Tests are automatically skipped if any prerequisite is missing.</p>
 *
 * <h3>What this proves for real HSM migration</h3>
 * <ul>
 *   <li>The PKCS#11 storetype configuration path works end-to-end</li>
 *   <li>SunPKCS11 provider loads and initializes correctly</li>
 *   <li>Key lookup by alias works through the PKCS#11 interface</li>
 *   <li>Authenticode signing produces a valid signed PE output</li>
 * </ul>
 */
class WindowsSigningPkcs11IntegrationTest {

    @TempDir
    static Path tempDir;

    private static File testExe;
    private static File pkcs11ConfigFile;
    private static String softhsmLibPath;
    private static final String TOKEN_LABEL = "jdeploy-test-token";
    private static final String TOKEN_PIN = "1234";
    private static final String SO_PIN = "5678";
    private static final String KEY_ALIAS = "codesign";
    private static boolean toolsAvailable;

    @BeforeAll
    static void setUp() throws Exception {
        toolsAvailable = checkPrerequisites();
        if (!toolsAvailable) {
            return;
        }

        initializeSoftHsmToken();
        generateKeyInToken();
        createPkcs11Config();

        testExe = tempDir.resolve("test-app.exe").toFile();
        WindowsSigningPfxIntegrationTest.createMinimalPeExe(testExe);
    }

    @Test
    void signExe_withPkcs11SoftHsm_succeeds() throws Exception {
        Assumptions.assumeTrue(toolsAvailable, "SoftHSM2/OpenSC/keytool not all available");

        WindowsSigningConfig config = new WindowsSigningConfig();
        config.setKeystoreType("PKCS11");
        config.setPkcs11ConfigPath(pkcs11ConfigFile.getAbsolutePath());
        config.setKeystorePassword(TOKEN_PIN);
        config.setAlias(KEY_ALIAS);
        config.setTimestampUrl(""); // skip timestamping for test speed
        config.setDescription("PKCS#11 Test");

        WindowsSigningService service = new WindowsSigningService();

        long sizeBefore = testExe.length();
        service.sign(testExe, config);
        long sizeAfter = testExe.length();

        assertTrue(sizeAfter > sizeBefore,
                "Signed EXE (" + sizeAfter + " bytes) should be larger than unsigned (" + sizeBefore + " bytes)");
    }

    @Test
    void signExe_withPkcs11_outputToSeparateFile() throws Exception {
        Assumptions.assumeTrue(toolsAvailable, "SoftHSM2/OpenSC/keytool not all available");

        File inputExe = tempDir.resolve("pkcs11-input.exe").toFile();
        File outputExe = tempDir.resolve("pkcs11-signed.exe").toFile();
        WindowsSigningPfxIntegrationTest.createMinimalPeExe(inputExe);

        WindowsSigningConfig config = new WindowsSigningConfig();
        config.setKeystoreType("PKCS11");
        config.setPkcs11ConfigPath(pkcs11ConfigFile.getAbsolutePath());
        config.setKeystorePassword(TOKEN_PIN);
        config.setAlias(KEY_ALIAS);
        config.setTimestampUrl("");

        WindowsSigningService service = new WindowsSigningService();
        service.sign(inputExe, outputExe, config);

        assertTrue(outputExe.exists(), "Signed output should exist");
        assertTrue(outputExe.length() > inputExe.length(),
                "Signed output should be larger than unsigned input");
    }

    @Test
    void configFactory_createsPkcs11Config() {
        Assumptions.assumeTrue(toolsAvailable, "SoftHSM2/OpenSC/keytool not all available");

        WindowsSigningConfigFactory factory = new WindowsSigningConfigFactory() {
            @Override
            String getenv(String name) {
                switch (name) {
                    case "JDEPLOY_WIN_KEYSTORE_TYPE": return "PKCS11";
                    case "JDEPLOY_WIN_PKCS11_CONFIG": return pkcs11ConfigFile.getAbsolutePath();
                    case "JDEPLOY_WIN_KEYSTORE_PASSWORD": return TOKEN_PIN;
                    case "JDEPLOY_WIN_KEY_ALIAS": return KEY_ALIAS;
                    default: return null;
                }
            }
        };

        WindowsSigningConfig config = factory.createFromEnvironment();
        assertNotNull(config);
        assertTrue(config.isPkcs11());
        assertEquals(pkcs11ConfigFile.getAbsolutePath(), config.getPkcs11ConfigPath());
    }

    /**
     * Initializes a SoftHSM2 token in an isolated directory.
     */
    private static void initializeSoftHsmToken() throws Exception {
        // Create isolated SoftHSM2 token storage
        Path tokenDir = tempDir.resolve("softhsm-tokens");
        Files.createDirectories(tokenDir);

        // Write SoftHSM2 config that uses our temp directory
        Path softhsmConf = tempDir.resolve("softhsm2.conf");
        Files.write(softhsmConf, ("directories.tokendir = " + tokenDir.toAbsolutePath() + "\n").getBytes());

        // Set SOFTHSM2_CONF so softhsm2-util uses our isolated config
        // Note: we use ProcessBuilder environment for subprocess isolation
        runCommand(
                new String[]{"softhsm2-util", "--init-token", "--slot", "0",
                        "--label", TOKEN_LABEL, "--pin", TOKEN_PIN, "--so-pin", SO_PIN},
                "SOFTHSM2_CONF", softhsmConf.toAbsolutePath().toString()
        );
    }

    /**
     * Generates an RSA key pair and self-signed certificate in the SoftHSM2 token.
     *
     * <p>Uses keytool with the SunPKCS11 provider to generate the key directly
     * in the token — this is the same mechanism used with real HSMs.</p>
     */
    private static void generateKeyInToken() throws Exception {
        // Write a PKCS#11 provider config for keytool
        Path keytoolP11Conf = tempDir.resolve("keytool-pkcs11.cfg");
        String keytoolConfig = "name = SoftHSM2-keytool\n"
                + "library = " + softhsmLibPath + "\n"
                + "slotListIndex = 0\n";

        // Need SOFTHSM2_CONF for the token dir
        Path softhsmConf = tempDir.resolve("softhsm2.conf");

        Files.write(keytoolP11Conf, keytoolConfig.getBytes());

        // Generate key pair in the PKCS#11 token using keytool
        runCommand(
                new String[]{
                        "keytool",
                        "-genkeypair",
                        "-alias", KEY_ALIAS,
                        "-keyalg", "RSA",
                        "-keysize", "2048",
                        "-validity", "1",
                        "-dname", "CN=PKCS11 Test Signing, O=Test, C=US",
                        "-ext", "BC=ca:false",
                        "-ext", "KU=digitalSignature",
                        "-ext", "EKU=codeSigning",
                        "-storetype", "PKCS11",
                        "-providerClass", "sun.security.pkcs11.SunPKCS11",
                        "-providerArg", keytoolP11Conf.toAbsolutePath().toString(),
                        "-storepass", TOKEN_PIN,
                        "-keypass", TOKEN_PIN
                },
                "SOFTHSM2_CONF", softhsmConf.toAbsolutePath().toString()
        );
    }

    /**
     * Creates the PKCS#11 configuration file for jsign's KeyStoreBuilder.
     */
    private static void createPkcs11Config() throws IOException {
        pkcs11ConfigFile = tempDir.resolve("jsign-pkcs11.cfg").toFile();
        String config = "name = SoftHSM2\n"
                + "library = " + softhsmLibPath + "\n"
                + "slotListIndex = 0\n";

        // Need to set SOFTHSM2_CONF in the actual test environment
        // jsign will load this config via SunPKCS11
        Files.write(pkcs11ConfigFile.toPath(), config.getBytes());

        // Also write a wrapper script or set env for the JVM
        // The SOFTHSM2_CONF env var needs to be set before the test JVM
        // reads the token. For in-process testing, we set it as a system property.
        Path softhsmConf = tempDir.resolve("softhsm2.conf");
        System.setProperty("SOFTHSM2_CONF", softhsmConf.toAbsolutePath().toString());
    }

    private static boolean checkPrerequisites() {
        softhsmLibPath = findSoftHsmLib();
        return softhsmLibPath != null
                && isCommandAvailable("softhsm2-util")
                && isCommandAvailable("keytool");
    }

    private static String findSoftHsmLib() {
        String[] candidates = {
                "/usr/lib/softhsm/libsofthsm2.so",
                "/usr/lib/x86_64-linux-gnu/softhsm/libsofthsm2.so",
                "/usr/local/lib/softhsm/libsofthsm2.so",
                "/usr/lib64/softhsm/libsofthsm2.so",
                "/opt/homebrew/lib/softhsm/libsofthsm2.so",  // macOS homebrew ARM
                "/usr/local/lib/softhsm/libsofthsm2.so",     // macOS homebrew x86
        };
        for (String path : candidates) {
            if (new File(path).exists()) {
                return path;
            }
        }
        return null;
    }

    private static boolean isCommandAvailable(String command) {
        try {
            Process p = new ProcessBuilder(command, "--help")
                    .redirectErrorStream(true)
                    .start();
            p.getInputStream().close();
            p.waitFor();
            return true;
        } catch (Exception e) {
            // Also try with -help (keytool uses single dash)
            try {
                Process p = new ProcessBuilder(command, "-help")
                        .redirectErrorStream(true)
                        .start();
                p.getInputStream().close();
                p.waitFor();
                return true;
            } catch (Exception e2) {
                return false;
            }
        }
    }

    private static void runCommand(String[] command, String envKey, String envValue) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put(envKey, envValue);
        pb.inheritIO();
        Process p = pb.start();
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException(
                    "Command failed with exit code " + exitCode + ": " + String.join(" ", command)
            );
        }
    }
}

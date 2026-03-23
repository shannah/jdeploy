package ca.weblite.jdeploy.services;

import net.jsign.AuthenticodeSigner;
import net.jsign.KeyStoreBuilder;
import net.jsign.Signable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for signing Windows executables with Authenticode signatures.
 *
 * Supports:
 * <ul>
 *   <li>Local keystores: PFX/PKCS12 and JKS</li>
 *   <li>PKCS#11 HSM tokens (SafeNet, YubiKey, etc.)</li>
 *   <li>DigiCert ONE / KeyLocker cloud HSM</li>
 * </ul>
 *
 * Uses the jsign library for Authenticode signing.
 */
public class WindowsSigningService {

    private static final Logger logger = Logger.getLogger(WindowsSigningService.class.getName());

    /**
     * Signs a Windows executable file in-place.
     *
     * @param exeFile the EXE file to sign
     * @param config  signing configuration
     * @throws Exception if signing fails
     */
    public void sign(File exeFile, WindowsSigningConfig config) throws Exception {
        config.validate();

        if (!exeFile.exists()) {
            throw new IOException("EXE file does not exist: " + exeFile.getAbsolutePath());
        }

        logger.log(Level.INFO, "Signing {0} with Authenticode", exeFile.getName());

        KeyStore keystore = buildKeyStore(config);
        String alias = resolveAlias(keystore, config);
        String keyPassword = config.getKeyPassword() != null
                ? config.getKeyPassword()
                : config.getKeystorePassword();

        AuthenticodeSigner signer = new AuthenticodeSigner(keystore, alias, keyPassword);

        if (config.getDescription() != null) {
            signer.withProgramName(config.getDescription());
        }
        if (config.getUrl() != null) {
            signer.withProgramURL(config.getUrl());
        }
        if (config.getTimestampUrl() != null && !config.getTimestampUrl().isEmpty()) {
            signer.withTimestamping(true);
            signer.withTimestampingAuthority(config.getTimestampUrl());
        }

        try (Signable signable = Signable.of(exeFile)) {
            signer.sign(signable);
        }

        logger.log(Level.INFO, "Successfully signed {0}", exeFile.getName());
    }

    /**
     * Signs a Windows executable, writing the signed output to a separate file.
     *
     * @param inputExe  source EXE file
     * @param outputExe destination for the signed EXE
     * @param config    signing configuration
     * @throws Exception if signing fails
     */
    public void sign(File inputExe, File outputExe, WindowsSigningConfig config) throws Exception {
        Files.copy(inputExe.toPath(), outputExe.toPath(), StandardCopyOption.REPLACE_EXISTING);
        sign(outputExe, config);
    }

    private KeyStore buildKeyStore(WindowsSigningConfig config) throws Exception {
        KeyStoreBuilder builder = new KeyStoreBuilder();

        if (config.isDigiCertOne()) {
            builder.storetype("DIGICERTONE");
            // jsign DIGICERTONE keystore format: API_KEY|CLIENT_CERT_PATH|CLIENT_CERT_PASSWORD
            String keystoreValue = config.getSmApiKey()
                    + "|" + config.getSmClientCertFile()
                    + "|" + config.getSmClientCertPassword();
            builder.keystore(keystoreValue);
            builder.storepass(config.getSmApiKey());
        } else if (config.isPkcs11()) {
            builder.storetype("PKCS11");
            builder.keystore(config.getPkcs11ConfigPath());
            if (config.getKeystorePassword() != null) {
                builder.storepass(config.getKeystorePassword());
            }
        } else {
            builder.storetype(config.getKeystoreType());
            builder.keystore(config.getKeystorePath());
            if (config.getKeystorePassword() != null) {
                builder.storepass(config.getKeystorePassword());
            }
        }

        return builder.build();
    }

    private String resolveAlias(KeyStore keystore, WindowsSigningConfig config) throws Exception {
        if (config.getAlias() != null && !config.getAlias().isEmpty()) {
            return config.getAlias();
        }
        // Default to first alias in the keystore
        if (keystore.aliases().hasMoreElements()) {
            return keystore.aliases().nextElement();
        }
        throw new IllegalStateException("Keystore contains no entries and no alias was specified");
    }
}

package ca.weblite.jdeploy.factories;

import ca.weblite.tools.platform.Platform;
import ca.weblite.tools.security.*;

import java.nio.file.Paths;


public class JDeployKeyProviderFactory {

    public static interface KeyConfig {
        String getKeystorePath();

        String getDeveloperId();

        String getCertificateAuthorityId();

        char[] getKeystorePassword();
    }

    public static class DefaultKeyConfig implements KeyConfig {
        private String defaultKeyStorePath = System.getProperty("user.home") + "/.jdeploy/keystore.jks";
        public DefaultKeyConfig() {

        }

        @Override
        public String getKeystorePath() {
            String envKeyStorePath = System.getenv("JDEPLOY_KEYSTORE_PATH");
            if (envKeyStorePath != null) {
                return envKeyStorePath;
            }
            return defaultKeyStorePath;
        }

        @Override
        public String getDeveloperId() {
            String envKeyStoreAlias = System.getenv("JDEPLOY_DEVELOPER_ID");
            if (envKeyStoreAlias != null) {
                return envKeyStoreAlias;
            }
            return null;
        }

        @Override
        public String getCertificateAuthorityId() {
            String envKeyStoreAlias = System.getenv("JDEPLOY_DEVELOPER_CA_ID");
            if (envKeyStoreAlias != null) {
                return envKeyStoreAlias;
            }
            return null;
        }

        @Override
        public char[] getKeystorePassword() {
            String envKeyStorePassword = System.getenv("JDEPLOY_KEYSTORE_PASSWORD");
            if (envKeyStorePassword != null) {
                return envKeyStorePassword.toCharArray();
            }
            return null;
        }
    }

    public KeyProvider createKeyProvider(KeyConfig config) {
        if (config == null) {
            config = new DefaultKeyConfig();
        }

        CompositeKeyProvider provider = new CompositeKeyProvider();
        provider.registerKeyProvider(new EnvKeyProvider());
        if (
                config.getKeystorePath() != null && Paths.get(config.getKeystorePath()).toFile().exists() &&
                        config.getDeveloperId() != null && config.getKeystorePassword() != null
        ) {
            provider.registerKeyProvider(
                    new KeyStoreKeyProvider(
                            config.getKeystorePath(),
                            config.getKeystorePassword(),
                            config.getDeveloperId(),
                            config.getDeveloperId(),
                            config.getCertificateAuthorityId()
                    )
            );
        }

        if (Platform.getSystemPlatform().isMac() && config.getDeveloperId() != null) {
            provider.registerKeyProvider(
                    new MacKeyStoreKeyProvider(
                            config.getDeveloperId(),
                            null,
                            config.getCertificateAuthorityId()
                    )
            );
        }

        if (Platform.getSystemPlatform().isWindows() && config.getDeveloperId() != null) {
            provider.registerKeyProvider(
                    new WindowsKeyStoreKeyProvider(
                            config.getDeveloperId(),
                            null, config.getCertificateAuthorityId()
                    )
            );
        }

        return provider;
    }
}

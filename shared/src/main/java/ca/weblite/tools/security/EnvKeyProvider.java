package ca.weblite.tools.security;

import ca.weblite.tools.env.DefaultEnvVarProvider;
import ca.weblite.tools.env.EnvVarProvider;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnvKeyProvider implements KeyProvider {

    private static final Pattern PEM_PATTERN = Pattern.compile("-----BEGIN (.+?)-----");
    private final EnvVarProvider envVarProvider;

    public EnvKeyProvider() {
        this.envVarProvider = new DefaultEnvVarProvider();
    }

    public EnvKeyProvider(EnvVarProvider envVarProvider) {
        this.envVarProvider = envVarProvider != null ? envVarProvider : new DefaultEnvVarProvider();
    }

    @Override
    public PrivateKey getPrivateKey() throws Exception {
        String privateKeyEnv = envVarProvider.getEnv("JDEPLOY_PRIVATE_KEY");
        if (privateKeyEnv == null) {
            throw new Exception("Environment variable JDEPLOY_PRIVATE_KEY not set");
        }

        byte[] keyBytes = loadKey(privateKeyEnv);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    @Override
    public Certificate getCertificate() throws Exception {
        String certificateEnv = envVarProvider.getEnv("JDEPLOY_CERTIFICATE");
        if (certificateEnv == null) {
            throw new Exception("Environment variable JDEPLOY_CERTIFICATE not set");
        }

        byte[] certBytes = loadKey(certificateEnv);
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return factory.generateCertificate(new java.io.ByteArrayInputStream(certBytes));
    }

    private byte[] loadKey(String key) throws Exception {
        if (PEM_PATTERN.matcher(key).find()) {
            return decodePEM(key);
        } else {
            return Files.readAllBytes(Paths.get(key));
        }
    }

    private byte[] decodePEM(String pem) throws Exception {
        Matcher matcher = PEM_PATTERN.matcher(pem);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid PEM format");
        }

        String type = matcher.group(1);
        String base64Data = pem.replaceAll("-----BEGIN " + type + "-----", "")
                .replaceAll("-----END " + type + "-----", "")
                .replaceAll("\\s", "");

        return Base64.getDecoder().decode(base64Data);
    }
}

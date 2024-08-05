package ca.weblite.tools.security;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FileKeyProvider implements KeyProvider {

    private final String privateKeyPath;
    private final String certificatePath;

    public FileKeyProvider(String privateKeyPath, String certificatePath) {
        this.privateKeyPath = privateKeyPath;
        this.certificatePath = certificatePath;
    }

    @Override
    public PrivateKey getSigningKey() throws Exception {
        byte[] keyBytes = Files.readAllBytes(Paths.get(privateKeyPath));
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    @Override
    public Certificate getSigningCertificate() throws Exception {
        String pem = readPemFile(certificatePath);
        byte[] der = decodePem(pem);
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return factory.generateCertificate(new java.io.ByteArrayInputStream(der));
    }

    @Override
    public List<Certificate> getSigningCertificateChain() throws Exception {
        return Collections.singletonList(getSigningCertificate());
    }

    @Override
    public List<Certificate> getTrustedCertificates() throws Exception {
        return Collections.singletonList(getSigningCertificate());
    }

    private String readPemFile(String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            return reader.lines()
                    .filter(line -> !line.startsWith("-----BEGIN CERTIFICATE-----") && !line.startsWith("-----END CERTIFICATE-----"))
                    .collect(Collectors.joining());
        }
    }

    private byte[] decodePem(String pem) {
        return Base64.getDecoder().decode(pem);
    }
}

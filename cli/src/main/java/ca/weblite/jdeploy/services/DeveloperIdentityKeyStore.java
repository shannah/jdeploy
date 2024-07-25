package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.DeveloperIdentity;
import ca.weblite.tools.security.CertificateUtil;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;

import java.security.spec.InvalidKeySpecException;
import java.security.cert.Certificate;

import static ca.weblite.jdeploy.helpers.KeyPairGenerator.generateKeyPair;


public class DeveloperIdentityKeyStore {
    private File keyStoreFile;
    private char[] keyStorePassword;
    private KeyStore keyStore;
    private char[] keyPassword;
    private String alias;

    private String pemString;

    private File pemFile;

    public KeyPair getKeyPair(boolean generate) throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException, UnrecoverableKeyException, NoSuchProviderException, SignatureException, InvalidKeyException {

        if (pemString != null) {
            try {
                return CertificateUtil.getKeyPairFromPem(pemString);
            } catch (InvalidKeySpecException e) {
                throw new RuntimeException("Failed to get keypair from pemString "+pemString, e);
            }
        }

        if (pemFile != null) {
            try {
                return CertificateUtil.getKeyPairFromPem(FileUtils.readFileToString(pemFile, StandardCharsets.UTF_8));
            } catch (InvalidKeySpecException e) {
                throw new RuntimeException("Failed to get keypair from pemFile "+pemFile, e);
            }
        }

        KeyStore keyStore = getKeyStore();
        Key key = keyStore.getKey(getAlias(), getKeyPassword());
        if (key instanceof PrivateKey) {
            Certificate cert = keyStore.getCertificate(getAlias());
            PublicKey publicKey = cert.getPublicKey();
            return new KeyPair(publicKey, (PrivateKey)key);
        }
        if (generate) {
            KeyPair keyPair = generateKeyPair();
            DeveloperIdentity developerIdentity = new DeveloperIdentity();
            developerIdentity.setName(getAlias());
            developerIdentity.setIdentityUrl("https://www.jdeploy.com");

            Certificate[] certs = new CertificateGenerator().generateCertificates(developerIdentity, keyPair);
            keyStore.setKeyEntry(getAlias(), keyPair.getPrivate(), getKeyPassword(), certs);
            if (getKeyStoreFile() != null) {
                getKeyStoreFile().getParentFile().mkdirs();
                try (FileOutputStream output = new FileOutputStream(getKeyStoreFile())) {
                    keyStore.store(output, getKeyStorePassword());
                }
            }
            return keyPair;
        }


        return null;

    }

    public String getPrivateKeyAsPem() throws UnrecoverableKeyException, CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException, SignatureException, NoSuchProviderException, InvalidKeyException {
        return CertificateUtil.toPemEncodedString(getKeyPair(true).getPrivate());
    }

    public Certificate getPublicKeyAsPem() throws UnrecoverableKeyException, CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException, SignatureException, NoSuchProviderException, InvalidKeyException {
        return CertificateUtil.toPemEncodedString(getKeyPair(true).getPublic());
    }

    public String getKeyPairAsPem() throws UnrecoverableKeyException, CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException, SignatureException, NoSuchProviderException, InvalidKeyException {
        StringBuilder sb = new StringBuilder();
        sb.append(getPublicKeyAsPem()).append("\n").append(getPrivateKeyAsPem()).append("\n");
        return sb.toString();
    }



    private KeyStore getKeyStore() throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException {
        loadKeyStore();
        return keyStore;
    }

    private void loadKeyStore() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        if (keyStore != null) return;
        keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        if (getKeyStoreFile() != null && getKeyStoreFile().exists()) {
            try (FileInputStream fis = new FileInputStream(getKeyStoreFile())) {
                keyStore.load(fis, getKeyStorePassword());
            }
        } else {
            keyStore.load(null, null);

        }

    }

    public File getKeyStoreFile() {
        if (keyStoreFile != null) return keyStoreFile;
        String keyStorePath = System.getProperty("jdeploy.keystore.path", System.getProperty("user.home") + File.separator + ".jdeploy" + File.separator + ".keystore.ks");
        return new File(keyStorePath);
    }

    public void setKeyStoreFile(File keyStoreFile) {
        this.keyStoreFile = keyStoreFile;
    }



    public void setKeyStorePassword(char[] pass) {
        this.keyStorePassword = pass;
    }

    private char[] getKeyStorePassword() {
        if (keyStorePassword != null) return keyStorePassword;
        String passString = System.getProperty("jdeploy.keystore.password", "");
        return passString.toCharArray();
    }

    public void setKeyPassword(char[] pass) {
        this.keyPassword = pass;
    }

    private char[] getKeyPassword() {
        if (keyPassword != null) return keyPassword;
        String passString = System.getProperty("jdeploy.key.password", "");

        return passString.toCharArray();
    }

    public String getAlias() {
        if (alias != null) return alias;
        return System.getProperty("jdeploy.keystore.alias", "jdeploy");

    }

    /**
     * Allows us to use a pemString as the identity instead of the keystore.
     */
    public String getPemString() {
        return pemString;
    }

    /**
     * Allows us to use a pem string as the signing identity instead of the keystore.
     * @param pemString
     */
    public void setPemString(String pemString) {
        this.pemString = pemString;
    }

    /**
     * Allows us to use a pem file as the identity instead of the keystore.
     */
    public File getPemFile() {
        return pemFile;
    }

    /**
     * Allows us to use a pem file as the signing identity instead of the keystore.
     * @param pemFile
     */
    public void setPemFile(File pemFile) {
        this.pemFile = pemFile;
    }
}

package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.DeveloperIdentity;

import java.io.*;
import java.security.*;
import java.security.cert.CertificateException;

import java.util.ArrayList;
import java.security.cert.Certificate;

import static ca.weblite.jdeploy.helpers.KeyPairGenerator.generateKeyPair;


public class DeveloperIdentityKeyStore {
    private File keyStoreFile;
    private char[] keyStorePassword;
    private KeyStore keyStore;
    private char[] keyPassword;
    private String alias;



    public KeyPair getKeyPair(boolean generate) throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException, UnrecoverableKeyException, NoSuchProviderException, SignatureException, InvalidKeyException {
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
            try (FileOutputStream output = new FileOutputStream(getKeyStoreFile())) {
                keyStore.store(output, getKeyStorePassword());
            }
            return keyPair;
        }


        return null;

    }



    private KeyStore getKeyStore() throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException {
        loadKeyStore();
        return keyStore;
    }

    private void loadKeyStore() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        if (keyStore != null) return;
        keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        if (getKeyStoreFile().exists()) {
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
}

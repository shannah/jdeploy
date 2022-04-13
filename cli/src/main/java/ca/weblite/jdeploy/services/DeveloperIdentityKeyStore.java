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

    public KeyPair getKeyPair(DeveloperIdentity developerIdentity, boolean generate) throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException, UnrecoverableKeyException, NoSuchProviderException, SignatureException, InvalidKeyException {
        KeyStore keyStore = getKeyStore();
        ArrayList<String> possibleAliases = new ArrayList<String>();
        possibleAliases.add(developerIdentity.getIdentityUrl());
        for (String alias : developerIdentity.getAliasUrls()) {
            possibleAliases.add(alias);
        }
        for (String alias : possibleAliases) {
            Key key = keyStore.getKey(developerIdentity.getIdentityUrl(), getKeyPassword());
            if (key instanceof PrivateKey) {
                Certificate cert = keyStore.getCertificate(alias);
                PublicKey publicKey = cert.getPublicKey();
                return new KeyPair(publicKey, (PrivateKey)key);
            }
        }

        if (generate) {
            KeyPair keyPair = generateKeyPair();
            Certificate[] certs = new CertificateGenerator().generateCertificates(developerIdentity, keyPair);
            keyStore.setKeyEntry(developerIdentity.getIdentityUrl(), keyPair.getPrivate(), getKeyPassword(), certs);
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
        String keyStorePath = System.getProperty("jdeploy.keystore.path", System.getProperty("user.home") + File.separator + ".jdeploy-keystore.ks");
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
        String passString = System.getProperty("jdeploy.keystore.password", null);
        if (passString == null) {
            return null;
        }
        return passString.toCharArray();
    }

    public void setKeyPassword(char[] pass) {
        this.keyPassword = pass;
    }

    private char[] getKeyPassword() {
        if (keyPassword != null) return keyPassword;
        String passString = System.getProperty("jdeploy.key.password", null);
        if (passString == null) return null;
        return passString.toCharArray();
    }
}

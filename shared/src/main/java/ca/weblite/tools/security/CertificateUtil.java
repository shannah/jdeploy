/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.tools.security;

import ca.weblite.tools.io.StringUtil;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author shannah
 */
public class CertificateUtil {

    public static String getSHA1Fingerprint(Certificate cert) throws CertificateEncodingException {
        return getFingerprint(cert, "SHA1");
    }

    public static String getFingerprint(Certificate server, String type) throws CertificateEncodingException {
        try {
            byte[] encoded = server.getEncoded();
            MessageDigest sha1 = MessageDigest.getInstance(type);
            sha1.update(encoded);
            return bytesToHex(sha1.digest());
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(CertificateUtil.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static String generateCertificateSHA256Hash(Certificate certificate)
            throws NoSuchAlgorithmException, CertificateEncodingException {
        // Get the encoded form of the certificate
        byte[] encodedCertificate = certificate.getEncoded();

        // Create a SHA-256 MessageDigest instance
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        // Hash the encoded certificate
        byte[] hash = digest.digest(encodedCertificate);

        // Convert the hash to a hexadecimal string
        return bytesToHex(hash);
    }

    public static String toPemEncodedString(PrivateKey privateKey) {
        byte[] keyBytes = privateKey.getEncoded();
        String keyContent = Base64.getEncoder().encodeToString(keyBytes);
        keyContent = StringUtil.splitIntoFixedWidthLines(keyContent, 64);
        String keyFormatted = "-----BEGIN PRIVATE KEY-----\n" + keyContent;

        if (keyFormatted.charAt(keyFormatted.length()-1) != '\n') {
            keyFormatted += "\n";
        }
        keyFormatted += "-----END PRIVATE KEY-----";
        return keyFormatted;
    }

    public static String toPemEncodedString(PublicKey publicKey) {
        byte[] publicKeyBytes = publicKey.getEncoded();
        String publicKeyContent = Base64.getEncoder().encodeToString(publicKeyBytes);
        publicKeyContent = StringUtil.splitIntoFixedWidthLines(publicKeyContent, 64);
        String publicKeyFormatted = "-----BEGIN PUBLIC KEY-----\n" + publicKeyContent;

        if (publicKeyFormatted.charAt(publicKeyFormatted.length()-1) != '\n') {
            publicKeyFormatted += "\n";
        }
        publicKeyFormatted += "-----END PUBLIC KEY-----";
        return publicKeyFormatted;
    }

    public static String toPemEncodedString(Certificate certificate) throws CertificateEncodingException {
        byte[] certificateBytes = certificate.getEncoded();
        String certificateContent = Base64.getEncoder().encodeToString(certificateBytes);
        certificateContent = StringUtil.splitIntoFixedWidthLines(certificateContent, 64);
        String certificateFormatted = "-----BEGIN CERTIFICATE-----\n" + certificateContent;

        if (certificateFormatted.charAt(certificateFormatted.length() - 1) != '\n') {
            certificateFormatted += "\n";
        }
        certificateFormatted += "-----END CERTIFICATE-----";
        return certificateFormatted;
    }

    public static PrivateKey getPrivateKeyFromPem(String pem) throws CertificateException, NoSuchAlgorithmException, InvalidKeySpecException {
        String temp = pem;

        if(temp.contains("-----BEGIN PRIVATE KEY-----"))
        {
            temp = temp.substring(temp.indexOf("-----BEGIN PRIVATE KEY-----"), temp.indexOf("-----END PRIVATE KEY-----"));
            pem = temp
                    .replace("-----BEGIN PRIVATE KEY-----\n", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("\n", "")
                    .replace("\r", "")
                    .trim();
        }
        else if(temp.contains("-----BEGIN RSA PRIVATE KEY-----"))
        {
            temp = temp.substring(temp.indexOf("-----BEGIN RSA PRIVATE KEY-----"), temp.indexOf("-----END RSA PRIVATE KEY-----"));
            pem = temp
                    .replace("-----BEGIN RSA PRIVATE KEY-----\n", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replace("\n", "")
                    .replace("\r", "")
                    .trim();
        } else {
            throw new IllegalArgumentException("No priate key found in Pem string "+pem);
        }


        pem = pem.replace("\n", "")
                .replace("\r", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(decoded);

        KeyFactory keyFact = KeyFactory.getInstance("RSA");

        return keyFact.generatePrivate(privateKeySpec);

    }

    public static KeyPair getKeyPairFromPem(String pem) throws CertificateException, NoSuchAlgorithmException, InvalidKeySpecException {
        PrivateKey privateKey = getPrivateKeyFromPem(pem);
        PublicKey publicKey = getPublicKeyFromPem(pem);
        return new KeyPair(publicKey, privateKey);
    }

    public static PublicKey getPublicKeyFromPem(String publicKeyPEM) throws CertificateException, NoSuchAlgorithmException, InvalidKeySpecException {
        String temp = publicKeyPEM;

        if(temp.contains("-----BEGIN PUBLIC KEY-----"))
        {
            temp = temp.substring(temp.indexOf("-----BEGIN PUBLIC KEY-----"), temp.indexOf("-----END PUBLIC KEY-----"));
            publicKeyPEM = temp
                    .replace("-----BEGIN PUBLIC KEY-----\n", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("\n", "")
                    .replace("\r", "")
                    .trim();
        }
        else if(temp.contains("-----BEGIN RSA PUBLIC KEY-----"))
        {
            temp = temp.substring(temp.indexOf("-----BEGIN RSA PUBLIC KEY-----"), temp.indexOf("-----END RSA PUBLIC KEY-----"));
            publicKeyPEM = temp
                    .replace("-----BEGIN RSA PUBLIC KEY-----\n", "")
                    .replace("-----END RSA PUBLIC KEY-----", "")
                    .replace("\n", "")
                    .replace("\r", "")
                    .trim();
        }
        else if(temp.contains("-----BEGIN CERTIFICATE-----"))
        {
            temp = temp.substring(temp.indexOf("-----BEGIN CERTIFICATE-----"), temp.indexOf("-----END CERTIFICATE-----")+"-----END CERTIFICATE-----".length());
            CertificateFactory fact = CertificateFactory.getInstance("X.509");
            X509Certificate cer = (X509Certificate) fact.generateCertificate(new ByteArrayInputStream(temp.getBytes(StandardCharsets.UTF_8)));
            return cer.getPublicKey();
        }

        publicKeyPEM = publicKeyPEM.replace("\n", "")
                .replace("\r", "");
        byte[] decoded = Base64.getDecoder().decode(publicKeyPEM);

        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    public static String getSHA256String(byte[] input) throws NoSuchAlgorithmException {
        return toHexString(getSHA256(input));
    }

    public static byte[] getSHA256(byte[] input) throws NoSuchAlgorithmException
    {
        // Static getInstance method is called with hashing SHA
        MessageDigest md = MessageDigest.getInstance("SHA-256");

        // digest() method called
        // to calculate message digest of an input
        // and return array of byte
        return md.digest(input);
    }

    public static KeyStore loadCertificatesFromPEM(String pemEncodedCertificates) throws Exception {
        // Create an empty KeyStore
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);  // Initialize the KeyStore with null parameters

        // Split the PEM string into individual certificates
        String[] certArray = pemEncodedCertificates.split("(?m)(?=-----BEGIN CERTIFICATE-----)");

        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

        int certIndex = 0;

        // Process each certificate in the PEM string
        for (String certString : certArray) {
            // Clean up the PEM string by removing whitespace and newlines
            certString = certString.replaceAll("\\s", "").replaceAll("-----BEGINCERTIFICATE-----", "").replaceAll("-----ENDCERTIFICATE-----", "");

            // Decode the Base64 encoded certificate
            byte[] decoded = Base64.getDecoder().decode(certString);

            // Generate the X.509 certificate
            X509Certificate certificate = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(decoded));

            // Add the certificate to the KeyStore with a unique alias
            keyStore.setCertificateEntry("cert-" + certIndex, certificate);
            certIndex++;
        }

        return keyStore;
    }

    private static String toHexString(byte[] hash)
    {
        // Convert byte array into signum representation
        BigInteger number = new BigInteger(1, hash);

        // Convert message digest into hex value
        StringBuilder hexString = new StringBuilder(number.toString(16));

        // Pad with leading zeros
        while (hexString.length() < 32)
        {
            hexString.insert(0, '0');
        }

        return hexString.toString();
    }


}

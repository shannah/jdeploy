/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.tools.security;

import ca.weblite.tools.io.StringUtil;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author shannah
 */
public class CertificateUtil {

    public static String getSHA1Fingerprint(X509Certificate cert) throws CertificateEncodingException {
        return getFingerprint(cert, "SHA1");
    }

    public static String getFingerprint(X509Certificate server, String type) throws CertificateEncodingException {
        try {
            byte[] encoded = server.getEncoded();
            MessageDigest sha1 = MessageDigest.getInstance(type);
            System.out.println("  Subject " + server.getSubjectDN());
            System.out.println("   Issuer  " + server.getIssuerDN());
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

    public static PublicKey getPublicKeyFromPem(String publicKeyPEM) throws CertificateException, NoSuchAlgorithmException, InvalidKeySpecException {
        String temp = publicKeyPEM;

        if(temp.contains("-----BEGIN PUBLIC KEY-----"))
        {
            publicKeyPEM = temp
                    .replace("-----BEGIN PUBLIC KEY-----\n", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("\n", "")
                    .replace("\r", "")
                    .trim();
        }
        else if(temp.contains("-----BEGIN RSA PUBLIC KEY-----"))
        {
            publicKeyPEM = temp
                    .replace("-----BEGIN RSA PUBLIC KEY-----\n", "")
                    .replace("-----END RSA PUBLIC KEY-----", "")
                    .replace("\n", "")
                    .replace("\r", "")
                    .trim();
        }
        else if(temp.contains("-----BEGIN CERTIFICATE-----"))
        {
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


}

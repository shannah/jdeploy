package ca.weblite.jdeploy.helpers;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

public class KeyPairGenerator {
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("RSA");
        kpg.initialize(4096);
        return kpg.generateKeyPair();
    }
}

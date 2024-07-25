package ca.weblite.tools.security;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;

public interface KeyProvider {
    PrivateKey getPrivateKey() throws Exception;
    Certificate getCertificate() throws Exception;
}


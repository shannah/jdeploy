package ca.weblite.tools.security;

import java.security.PrivateKey;
import java.security.PublicKey;

public interface KeyProvider {
    PrivateKey getPrivateKey() throws Exception;
    PublicKey getPublicKey() throws Exception;
}


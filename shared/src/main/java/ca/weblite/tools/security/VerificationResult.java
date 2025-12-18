package ca.weblite.tools.security;

public enum VerificationResult {
    SIGNED_CORRECTLY,
    NOT_SIGNED_AT_ALL,
    SIGNATURE_MISMATCH,
    UNTRUSTED_CERTIFICATE
}

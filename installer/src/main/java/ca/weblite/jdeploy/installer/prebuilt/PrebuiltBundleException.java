package ca.weblite.jdeploy.installer.prebuilt;

/**
 * Thrown when a release declares a pre-built bundle for this platform but it
 * cannot be downloaded, verified or installed. The installer must then fail
 * rather than keep the bundle it generated: the pre-built one is the signed
 * bundle that carries the certificate pins.
 */
public class PrebuiltBundleException extends Exception {
    public PrebuiltBundleException(String message, Throwable cause) {
        super(message, cause);
    }
}

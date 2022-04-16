package ca.weblite.jdeploy.helpers;

import ca.weblite.jdeploy.models.NPMApplication;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.SignatureException;

public class NPMApplicationSignatureHelper {
    public static void updateAppVersionSignature(NPMApplication app, Signature sig) throws SignatureException {
        sig.update("registryUrl=".getBytes(StandardCharsets.UTF_8));
        sig.update(app.getNpmRegistryUrl().getBytes(StandardCharsets.UTF_8));
        sig.update("\npackageName=".getBytes(StandardCharsets.UTF_8));
        sig.update(app.getPackageName().getBytes(StandardCharsets.UTF_8));
        sig.update("\nversion=".getBytes(StandardCharsets.UTF_8));
        sig.update(app.getPackageVersion().getBytes(StandardCharsets.UTF_8));
        sig.update("\ntimestamp=".getBytes(StandardCharsets.UTF_8));
    }

    public static void updateAppSignature(NPMApplication app, Signature sig) throws SignatureException {
        sig.update("registryUrl=".getBytes(StandardCharsets.UTF_8));
        sig.update(app.getNpmRegistryUrl().getBytes(StandardCharsets.UTF_8));
        sig.update("\npackageName=".getBytes(StandardCharsets.UTF_8));
        sig.update(app.getPackageName().getBytes(StandardCharsets.UTF_8));
    }
}

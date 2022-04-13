package ca.weblite.jdeploy.helpers;

import ca.weblite.jdeploy.models.DeveloperIdentity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.SignatureException;

public class DeveloperIdentitySignatureHelper {
    public static void updateSignature(DeveloperIdentity identity, Signature sig) throws IOException, SignatureException {
        sig.update("name=".getBytes(StandardCharsets.UTF_8));
        sig.update(identity.getName().getBytes("UTF-8"));
        sig.update("\n".getBytes(StandardCharsets.UTF_8));
        sig.update("identityUrl=".getBytes(StandardCharsets.UTF_8));
        sig.update(identity.getIdentityUrl().getBytes("UTF-8"));
        sig.update("\n".getBytes(StandardCharsets.UTF_8));
        for (String aliasUrl : identity.getAliasUrls()) {
            sig.update("aliasUrl=".getBytes(StandardCharsets.UTF_8));
            sig.update(aliasUrl.getBytes(StandardCharsets.UTF_8));
            sig.update("\n".getBytes(StandardCharsets.UTF_8));
        }
    }
}

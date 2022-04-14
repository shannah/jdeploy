package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.data.VerificationStatus;
import ca.weblite.jdeploy.models.NPMApplication;
import ca.weblite.tools.io.IOUtil;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class WebsiteVerifier {
    private URLLoaderService urlService = new DefaultURLLoader();

    /**
     * Returns true if the given URL contains any of the app, version, or developer signatures
     * in the URL.
     * @param app
     * @param url
     * @return
     * @throws IOException
     */
    public boolean checkURLContainsSignature(NPMApplication app, String url) throws IOException {

        try (InputStream input = urlService.openStream(new URL(url))) {
            String contents = IOUtil.readToString(input);
            // Check to see if the webpage text contains any signatures for this app).
            return app.containsAnySignature(contents);
        }
    }

    public boolean verifyHomepage(NPMApplication app) throws IOException {
        if (app.getHomepageVerificationStatus() != VerificationStatus.UNKNOWN) {
            return app.isHomepageVerified();
        }
        if (app.getHomepage() != null && app.getHomepage().startsWith("https://")) {
            if (checkURLContainsSignature(app, app.getHomepage())) {
                app.setHomepageVerified(true);
                return true;
            }
        }
        app.setHomepageVerified(false);
        return false;
    }
}

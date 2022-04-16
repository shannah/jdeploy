package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.data.VerificationStatus;
import ca.weblite.jdeploy.models.NPMApplication;
import ca.weblite.tools.io.IOUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static ca.weblite.jdeploy.helpers.NPMApplicationHelper.getApplicationSha256Hash;
import static ca.weblite.jdeploy.helpers.NPMApplicationHelper.getVersionSha256Hash;

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
    public boolean checkURLContainsSha256Hash(NPMApplication app, String url) throws IOException {

        try (InputStream input = urlService.openStream(new URL(url))) {
            String contents = IOUtil.readToString(input);
            // Check to see if the webpage text contains any signatures for this app).
            try {
                if (contents.contains(getApplicationSha256Hash(app))) {
                    return true;
                }
            } catch (Exception ex) {
                System.err.println("Failed to check for application hash in app.");
                ex.printStackTrace(System.err);
            }
            try {
                if (contents.contains(getVersionSha256Hash(app))) {
                    return true;
                }
            } catch (Exception ex) {
                System.err.println("Failed to check for version hash in app.");
                ex.printStackTrace(System.err);
            }


            return false;
        }
    }

    public boolean verifyHomepage(NPMApplication app) throws IOException {
        if (app.getHomepageVerificationStatus() != VerificationStatus.UNKNOWN) {
            return app.isHomepageVerified();
        }
        if (app.getHomepage() != null && app.getHomepage().startsWith("https://")) {
            if (checkURLContainsSha256Hash(app, app.getHomepage())) {
                app.setHomepageVerified(true);
                return true;
            }
        }
        app.setHomepageVerified(false);
        return false;
    }
}

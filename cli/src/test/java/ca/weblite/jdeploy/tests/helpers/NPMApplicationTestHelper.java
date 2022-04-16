package ca.weblite.jdeploy.tests.helpers;

import ca.weblite.jdeploy.models.NPMApplication;

public class NPMApplicationTestHelper {

    public static NPMApplication createMockNPMApplication() {
        NPMApplication out = new NPMApplication();
        out.setPackageVersion("1.0.0");
        out.setPackageName("jdeploy-test-app");
        out.setNpmRegistryUrl(NPMApplication.DEFAULT_NPM_REGISTRY);
        out.setHomepage("https://www.example.com");
        return out;
    }
}

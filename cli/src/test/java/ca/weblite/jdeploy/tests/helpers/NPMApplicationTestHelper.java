package ca.weblite.jdeploy.tests.helpers;

import ca.weblite.jdeploy.JDeploy;
import ca.weblite.jdeploy.models.NPMApplication;
import ca.weblite.jdeploy.services.NPMApplicationSigner;

import java.util.Date;


public class NPMApplicationTestHelper {

    public static NPMApplication createMockNPMApplication() {
        NPMApplication out = new NPMApplication();
        out.setPackageVersion("1.0.0");
        out.setPackageName("jdeploy-test-app");
        out.setTimeStampString(""+new Date().getTime());
        out.setNpmRegistryUrl(NPMApplication.DEFAULT_NPM_REGISTRY);
        return out;
    }
}

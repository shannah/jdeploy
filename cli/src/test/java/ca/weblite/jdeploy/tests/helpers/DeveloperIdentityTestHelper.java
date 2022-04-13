package ca.weblite.jdeploy.tests.helpers;

import ca.weblite.jdeploy.models.DeveloperIdentity;

public class DeveloperIdentityTestHelper {
    public static DeveloperIdentity createMockIdentity() {
        DeveloperIdentity identity = new DeveloperIdentity();
        identity.setName("Appleseed Inc.");
        identity.setIdentityUrl("https://example.com/jdeploy-id.json");
        identity.setCity("Langley");
        identity.setCountryCode("CA");
        identity.setOrganization("Big Corp.");
        return identity;

    }
}

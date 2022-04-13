package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.DeveloperIdentity;

import java.io.IOException;

public class DeveloperIdentityNotFoundException extends IOException {
    private DeveloperIdentity identity;

    public DeveloperIdentityNotFoundException(String message, DeveloperIdentity identity) {
        super(message);
        this.identity = identity;
    }


    public DeveloperIdentity getIdentity() {
        return identity;
    }

    public void setIdentity(DeveloperIdentity identity) {
        this.identity = identity;
    }
}

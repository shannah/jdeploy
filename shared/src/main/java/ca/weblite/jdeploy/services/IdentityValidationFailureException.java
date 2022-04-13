package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.DeveloperIdentity;

import java.io.IOException;

public class IdentityValidationFailureException extends IOException {
    private final  DeveloperIdentity identity;
    public IdentityValidationFailureException(String message, DeveloperIdentity identity) {
        super(message);
        this.identity = identity;

    }

    public DeveloperIdentity getIdentity() {
        return identity;
    }
}

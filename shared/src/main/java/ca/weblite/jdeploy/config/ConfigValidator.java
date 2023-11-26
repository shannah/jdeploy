package ca.weblite.jdeploy.config;

import ca.weblite.jdeploy.exception.ConfigValidationException;

public interface ConfigValidator {
    public void validate() throws ConfigValidationException;
}

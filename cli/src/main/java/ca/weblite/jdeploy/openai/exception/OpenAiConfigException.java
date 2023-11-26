package ca.weblite.jdeploy.openai.exception;

import ca.weblite.jdeploy.exception.ConfigValidationException;

public class OpenAiConfigException extends ConfigValidationException {
    public OpenAiConfigException(String message) {
        super(message);
    }
}

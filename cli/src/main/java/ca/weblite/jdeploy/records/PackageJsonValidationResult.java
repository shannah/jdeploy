package ca.weblite.jdeploy.records;

import javax.inject.Singleton;

public class PackageJsonValidationResult {

    public enum ErrorType {
        MISSING_PROPERTY,
        INVALID_PROPERTY_VALUE,

        PARSE_ERROR
    }

    private final PackageJsonPropertyError[] errors;

    public PackageJsonValidationResult(PackageJsonPropertyError[] errors) {
        this.errors = errors;
    }

    public PackageJsonPropertyError[] getErrors() {
        return errors;
    }

    public boolean isValid(){
        return errors.length == 0;
    }

    public static class PackageJsonPropertyError {
        private final String propertyName, message;
        private final ErrorType errorType;

        public PackageJsonPropertyError(String propertyName, String message, ErrorType errorType) {
            this.propertyName = propertyName;
            this.message = message;
            this.errorType = errorType;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public String getMessage() {
            return message;
        }

        public ErrorType getErrorType() {
            return errorType;
        }
    }

    @Singleton
    public static class PackageJsonPropertyErrorFactory {
        public PackageJsonPropertyError fromException(Exception ex) {
            return new PackageJsonPropertyError("Unknown", ex.getMessage(), ErrorType.PARSE_ERROR);
        }
    }

    @Singleton
    public static class Factory {
        public PackageJsonValidationResult fromException(Exception ex) {
            return new PackageJsonValidationResult(new PackageJsonPropertyError[]{
                    new PackageJsonPropertyError("Unknown", ex.getMessage(), ErrorType.PARSE_ERROR)
            });
        }
    }
}

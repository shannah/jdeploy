package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.records.PackageJsonValidationResult;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class PackageJsonValidator {

    private static final String[] REQUIRED_FIELDS = new String[]{
        "name", "version", "description", "dependencies", "bin", "jdeploy"
    };

    private static final String MSG_MISSING_FIELD = "Missing required field: %s";

    public PackageJsonValidationResult validate(File packageJsonFile) throws IOException {
        return validate(new JSONObject(FileUtils.readFileToString(packageJsonFile, "UTF-8")));
    }

    public PackageJsonValidationResult validate(JSONObject packageJson) {
        List<PackageJsonValidationResult.PackageJsonPropertyError> errors = new ArrayList<>();
        for (String field : REQUIRED_FIELDS){
            if (!packageJson.has(field)){
                errors.add(new PackageJsonValidationResult.PackageJsonPropertyError(
                        String.format(MSG_MISSING_FIELD, field),
                        field,
                        PackageJsonValidationResult.ErrorType.MISSING_PROPERTY
                ));
            }
        }

        if (packageJson.has("name") && packageJson.getString("name").matches("[^A-Za-z0-9\\-_]")){
            errors.add(new PackageJsonValidationResult.PackageJsonPropertyError(
                    "Invalid package name.  Package names can only contain lowercase letters, numbers, hyphens, and underscores.",
                    "name",
                    PackageJsonValidationResult.ErrorType.INVALID_PROPERTY_VALUE
            ));
        }

        if (packageJson.has("version") && !packageJson.getString("version").matches("^[0-9].*")){
            errors.add(new PackageJsonValidationResult.PackageJsonPropertyError(
                    "Invalid package version.  Package versions must start with a number.",
                    "version",
                    PackageJsonValidationResult.ErrorType.INVALID_PROPERTY_VALUE
            ));
        }

        return new PackageJsonValidationResult(errors.toArray(new PackageJsonValidationResult.PackageJsonPropertyError[0]));

    }
}

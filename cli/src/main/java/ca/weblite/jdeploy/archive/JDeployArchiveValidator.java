package ca.weblite.jdeploy.archive;

import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static ca.weblite.jdeploy.archive.JDeployArchiveGenerator.*;

/**
 * Validates a .jdeploy archive for correctness and completeness.
 */
@Singleton
public class JDeployArchiveValidator {

    @Inject
    public JDeployArchiveValidator() {
    }

    /**
     * Validates the given archive file.
     *
     * @param archiveFile The .jdeploy archive to validate
     * @return Validation result with errors and warnings
     */
    public ValidationResult validate(File archiveFile) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!archiveFile.exists()) {
            errors.add("Archive file does not exist: " + archiveFile.getAbsolutePath());
            return new ValidationResult(errors, warnings);
        }

        if (!archiveFile.getName().endsWith(".jdeploy")) {
            warnings.add("Archive file does not have .jdeploy extension: " + archiveFile.getName());
        }

        try (JDeployArchiveReader reader = new JDeployArchiveReader(archiveFile)) {
            validateManifest(reader, errors, warnings);
            validatePackageJson(reader, errors, warnings);
            validateBundles(reader, errors, warnings);
            validateConsistency(reader, errors, warnings);
        } catch (IOException e) {
            errors.add("Failed to read archive: " + e.getMessage());
        }

        return new ValidationResult(errors, warnings);
    }

    private void validateManifest(JDeployArchiveReader reader, List<String> errors, List<String> warnings) {
        String archiveVersion = reader.getArchiveVersion();
        if (archiveVersion == null || archiveVersion.isEmpty()) {
            errors.add("Missing required manifest attribute: " + ATTR_ARCHIVE_VERSION);
        } else if (!ARCHIVE_VERSION.equals(archiveVersion)) {
            warnings.add("Unknown archive version: " + archiveVersion + " (expected " + ARCHIVE_VERSION + ")");
        }

        if (reader.getPackageName() == null || reader.getPackageName().isEmpty()) {
            errors.add("Missing required manifest attribute: " + ATTR_PACKAGE_NAME);
        }

        if (reader.getPackageVersion() == null || reader.getPackageVersion().isEmpty()) {
            errors.add("Missing required manifest attribute: " + ATTR_PACKAGE_VERSION);
        }

        if (reader.getCreatedAt() == null || reader.getCreatedAt().isEmpty()) {
            warnings.add("Missing manifest attribute: " + ATTR_CREATED_AT);
        }
    }

    private void validatePackageJson(JDeployArchiveReader reader, List<String> errors, List<String> warnings) {
        try {
            JSONObject packageJson = reader.getPackageJson();

            if (!packageJson.has("name")) {
                errors.add("package.json missing required field: name");
            }
            if (!packageJson.has("version")) {
                errors.add("package.json missing required field: version");
            }
            if (!packageJson.has("jdeploy")) {
                errors.add("package.json missing required field: jdeploy");
            }
        } catch (IOException e) {
            errors.add("Failed to read package.json: " + e.getMessage());
        }
    }

    private void validateBundles(JDeployArchiveReader reader, List<String> errors, List<String> warnings) {
        List<String> bundleNames = reader.getBundleNames();

        if (bundleNames.isEmpty()) {
            errors.add("Archive contains no bundles in bundles/ directory");
            return;
        }

        // Check that the universal tarball is present
        String expectedUniversal = reader.getPackageName() + "-" + reader.getPackageVersion() + ".tgz";
        if (!bundleNames.contains(expectedUniversal)) {
            errors.add("Missing universal tarball: bundles/" + expectedUniversal);
        }

        // If manifest says platform bundles exist, verify them
        if (reader.hasPlatformBundles()) {
            List<String> declaredPlatforms = reader.getPlatforms();
            for (String platform : declaredPlatforms) {
                String expected = reader.getPackageName() + "-" + reader.getPackageVersion() + "-" + platform + ".tgz";
                if (!bundleNames.contains(expected)) {
                    errors.add("Missing platform tarball declared in manifest: bundles/" + expected);
                }
            }
        }
    }

    private void validateConsistency(JDeployArchiveReader reader, List<String> errors, List<String> warnings) {
        try {
            JSONObject packageJson = reader.getPackageJson();
            String manifestName = reader.getPackageName();
            String manifestVersion = reader.getPackageVersion();

            if (manifestName != null && packageJson.has("name")) {
                String jsonName = packageJson.getString("name");
                if (!manifestName.equals(jsonName)) {
                    errors.add("Package name mismatch: manifest=" + manifestName + ", package.json=" + jsonName);
                }
            }

            if (manifestVersion != null && packageJson.has("version")) {
                String jsonVersion = packageJson.getString("version");
                if (!manifestVersion.equals(jsonVersion)) {
                    errors.add("Version mismatch: manifest=" + manifestVersion + ", package.json=" + jsonVersion);
                }
            }
        } catch (IOException e) {
            // Already reported in validatePackageJson
        }
    }

    /**
     * Result of archive validation.
     */
    public static class ValidationResult {
        private final List<String> errors;
        private final List<String> warnings;

        public ValidationResult(List<String> errors, List<String> warnings) {
            this.errors = errors;
            this.warnings = warnings;
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }
}

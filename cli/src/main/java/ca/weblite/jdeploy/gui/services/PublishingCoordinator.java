package ca.weblite.jdeploy.gui.services;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.JDeploy;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.packaging.PackagingPreferences;
import ca.weblite.jdeploy.packaging.PackagingPreferencesService;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetServiceInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;
import ca.weblite.jdeploy.publishing.OneTimePasswordProviderInterface;
import ca.weblite.jdeploy.publishing.PublishingContext;
import ca.weblite.jdeploy.publishing.github.GitHubPublishDriver;
import ca.weblite.jdeploy.services.ProjectBuilderService;
import ca.weblite.jdeploy.services.VersionCleaner;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

/**
 * Encapsulates publishing validation and execution workflow.
 * Handles business logic for publishing validation and execution while keeping
 * UI interaction (dialogs, progress display) in the caller.
 */
public class PublishingCoordinator {

    /**
     * Represents the result of a validation operation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final int errorType;
        private final File logFile;

        public static final int ERROR_TYPE_NONE = 0;
        public static final int ERROR_TYPE_NOT_LOGGED_IN = 1;
        public static final int ERROR_TYPE_JAR_VALIDATION = 2;
        public static final int ERROR_TYPE_VERSION_MISMATCH = 3;
        public static final int ERROR_TYPE_BUILD_FAILED = 4;

        private ValidationResult(boolean valid, String errorMessage, int errorType, File logFile) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.errorType = errorType;
            this.logFile = logFile;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null, ERROR_TYPE_NONE, null);
        }

        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, errorMessage, ERROR_TYPE_NONE, null);
        }

        public static ValidationResult failure(String errorMessage, int errorType) {
            return new ValidationResult(false, errorMessage, errorType, null);
        }

        public static ValidationResult failure(String errorMessage, int errorType, File logFile) {
            return new ValidationResult(false, errorMessage, errorType, logFile);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public int getErrorType() {
            return errorType;
        }

        public File getLogFile() {
            return logFile;
        }
    }

    /**
     * Immutable class for reporting publishing progress.
     */
    public static class PublishProgress {
        private final String message1;
        private final String message2;
        private final boolean isComplete;
        private final boolean isFailed;

        private PublishProgress(String message1, String message2, boolean isComplete, boolean isFailed) {
            this.message1 = message1;
            this.message2 = message2;
            this.isComplete = isComplete;
            this.isFailed = isFailed;
        }

        public String message1() {
            return message1;
        }

        public String message2() {
            return message2;
        }

        public boolean isComplete() {
            return isComplete;
        }

        public boolean isFailed() {
            return isFailed;
        }

        public static PublishProgress inProgress(String message1, String message2) {
            return new PublishProgress(message1, message2, false, false);
        }

        public static PublishProgress complete() {
            return new PublishProgress(null, null, true, false);
        }

        public static PublishProgress failed() {
            return new PublishProgress(null, null, false, true);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PublishProgress that = (PublishProgress) o;
            return isComplete == that.isComplete &&
                    isFailed == that.isFailed &&
                    java.util.Objects.equals(message1, that.message1) &&
                    java.util.Objects.equals(message2, that.message2);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(message1, message2, isComplete, isFailed);
        }

        @Override
        public String toString() {
            return "PublishProgress{" +
                    "message1='" + message1 + '\'' +
                    ", message2='" + message2 + '\'' +
                    ", isComplete=" + isComplete +
                    ", isFailed=" + isFailed +
                    '}';
        }
    }

    /**
     * Callback interface for progress reporting during publish operations.
     */
    public interface ProgressCallback {
        void onProgress(PublishProgress progress);
    }

    private final File packageJSONFile;
    private final JSONObject packageJSON;
    private final ProjectBuilderService projectBuilderService;
    private final PackagingPreferencesService packagingPreferencesService;
    private final PublishTargetServiceInterface publishTargetService;
    private final GitHubPublishDriver gitHubPublishDriver;

    public PublishingCoordinator(File packageJSONFile, JSONObject packageJSON) {
        this.packageJSONFile = packageJSONFile;
        this.packageJSON = packageJSON;
        this.projectBuilderService = DIContext.get(ProjectBuilderService.class);
        this.packagingPreferencesService = DIContext.get(PackagingPreferencesService.class);
        this.publishTargetService = DIContext.get(PublishTargetServiceInterface.class);
        this.gitHubPublishDriver = DIContext.get(GitHubPublishDriver.class);
    }

    /**
     * Validates all preconditions for publishing.
     * Performs checks in the following order:
     * 1. Required fields (name, author, description, version)
     * 2. jdeploy object existence
     * 3. JAR file selection and validity
     * 4. JAR file existence (with build if needed)
     * 5. Version publication status
     * 6. NPM login status (if NPM publishing enabled)
     *
     * @param npmToken the NPM token for authentication (may be null)
     * @return ValidationResult indicating success or the first validation error encountered
     */
    public ValidationResult validateForPublishing(String npmToken) {
        File absDirectory = packageJSONFile.getAbsoluteFile().getParentFile();

        // Validate required fields
        String[] requiredFields = {"name", "author", "description", "version"};
        for (String field : requiredFields) {
            if (!packageJSON.has(field) || packageJSON.getString(field).isEmpty()) {
                return ValidationResult.failure("The " + field + " field is required for publishing.");
            }
        }

        // Validate jdeploy object
        if (!packageJSON.has("jdeploy")) {
            return ValidationResult.failure("This package.json is missing the jdeploy object which is required.");
        }

        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");

        // Validate jar selection
        if (!jdeploy.has("jar")) {
            return ValidationResult.failure("Please select a jar file before publishing.");
        }

        // Validate jar file extension
        String jarPath = jdeploy.getString("jar");
        if (!jarPath.endsWith(".jar")) {
            return ValidationResult.failure(
                    "The selected jar file is not a jar file. Jar files must have the .jar extension"
            );
        }

        // Get jar file
        File jarFile = new File(absDirectory, toNativePath(jarPath));

        // Check if jar needs to be built
        if (!jarFile.exists()) {
            ValidationResult buildResult = validateAndBuildJarIfNeeded(jarFile, absDirectory);
            if (!buildResult.isValid()) {
                return buildResult;
            }
        }

        // Final check that jar exists after potential build
        if (!jarFile.exists()) {
            return ValidationResult.failure(
                    "The selected jar file does not exist. Please check the selected jar file and try again."
            );
        }

        // Validate jar is executable
        ValidationResult jarValidation = validateJar(jarFile);
        if (!jarValidation.isValid()) {
            return jarValidation;
        }

        // Check version publication status
        String rawVersion = packageJSON.getString("version");
        String version = VersionCleaner.cleanVersion(rawVersion);
        String packageName = packageJSON.getString("name");
        String source = packageJSON.has("source") ? packageJSON.getString("source") : "";

        if (isNpmPublishingEnabled()) {
            JDeploy jdeploy0 = new JDeploy(absDirectory, false);
            if (npmToken != null) {
                jdeploy0.setNpmToken(npmToken);
            }
            if (jdeploy0.getNPM().isVersionPublished(packageName, version, source)) {
                return ValidationResult.failure(
                        "The package " + packageName + " already has a published version " + version + ". " +
                                "Please increment the version number and try to publish again."
                );
            }

            // Check NPM login status
            if (!jdeploy0.getNPM().isLoggedIn()) {
                return ValidationResult.failure(
                        "You must be logged into NPM in order to publish",
                        ValidationResult.ERROR_TYPE_NOT_LOGGED_IN
                );
            }
        }

        if (isGitHubPublishingEnabled()) {
            try {
                PublishTargetInterface githubTarget = publishTargetService
                        .getTargetsForProject(absDirectory.getAbsolutePath(), true)
                        .stream()
                        .filter(t -> t.getType() == PublishTargetType.GITHUB)
                        .findFirst()
                        .orElse(null);

                if (githubTarget != null && (
                        gitHubPublishDriver.isVersionPublished(packageName, version, githubTarget)
                                || gitHubPublishDriver.isVersionPublished(packageName, rawVersion, githubTarget)
                )) {
                    return ValidationResult.failure(
                            "The package " + packageName + " already has a published version " + version + " on Github. " +
                                    "Please increment the version number and try to publish again."
                    );
                }
            } catch (IOException ex) {
                return ValidationResult.failure("Failed to load github publish target");
            }
        }

        return ValidationResult.success();
    }

    /**
     * Validates that a jar file is an executable jar (has Main-Class manifest entry).
     *
     * @param jar the jar file to validate
     * @return ValidationResult indicating success or validation error
     */
    public ValidationResult validateJar(File jar) {
        try (JarFile jarFile = new JarFile(jar)) {
            if (jarFile.getManifest() != null &&
                    jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS) != null) {
                return ValidationResult.success();
            }
            return ValidationResult.failure(
                    "Selected jar file is not an executable Jar file. " +
                            "Please see https://www.jdeploy.com/docs/manual/#_appendix_building_executable_jar_file",
                    ValidationResult.ERROR_TYPE_JAR_VALIDATION
            );
        } catch (IOException ex) {
            return ValidationResult.failure("Failed to load jar file", ValidationResult.ERROR_TYPE_JAR_VALIDATION);
        }
    }

    /**
     * Validates and builds the jar file if needed.
     * Attempts to build the project if the jar doesn't exist and building is supported.
     *
     * @param jarFile the jar file to check
     * @param absDirectory the project directory
     * @return ValidationResult indicating success or build error
     */
    public ValidationResult validateAndBuildJarIfNeeded(File jarFile, File absDirectory) {
        PackagingContext buildContext = PackagingContext.builder()
                .directory(absDirectory)
                .exitOnFail(false)
                .isBuildRequired(true)
                .build();

        if (!projectBuilderService.isBuildSupported(buildContext)) {
            return ValidationResult.failure(
                    "The selected jar file does not exist and automatic build is not supported on this platform."
            );
        }

        try {
            File buildLogFile = File.createTempFile("jdeploy-build-log", ".txt");
            try {
                projectBuilderService.buildProject(buildContext, buildLogFile);
                return ValidationResult.success();
            } catch (Exception ex) {
                return ValidationResult.failure(
                        "Failed to build project before publishing. See build log at " +
                                buildLogFile.getAbsolutePath(),
                        ValidationResult.ERROR_TYPE_BUILD_FAILED,
                        buildLogFile
                );
            }
        } catch (IOException ex) {
            return ValidationResult.failure("Failed to create build log file");
        }
    }

    /**
     * Executes the publishing workflow with the given context and progress callback.
     *
     * @param packagingContext the packaging context for the publish operation
     * @param jdeployInstance the JDeploy instance with configured tokens
     * @param otpProvider provider for one-time passwords
     * @param progressCallback callback for progress reporting
     * @param githubToken the GitHub token for authentication (may be null if not publishing to GitHub)
     * @throws IOException if an IO error occurs during publishing
     */
    public void publish(
            PackagingContext packagingContext,
            JDeploy jdeployInstance,
            OneTimePasswordProviderInterface otpProvider,
            ProgressCallback progressCallback,
            String githubToken
    ) throws IOException {
        try {
            PublishingContext publishingContext = PublishingContext
                    .builder()
                    .setPackagingContext(packagingContext)
                    .setNPM(jdeployInstance.getNPM())
                    .setGithubToken(githubToken)
                    .build();

            jdeployInstance.publish(publishingContext, otpProvider);

            if (progressCallback != null) {
                progressCallback.onProgress(PublishProgress.complete());
            }
        } catch (Exception ex) {
            packagingContext.err.println("An error occurred during publishing");
            ex.printStackTrace(packagingContext.err);
            if (progressCallback != null) {
                progressCallback.onProgress(PublishProgress.failed());
            }
            throw ex;
        }
    }

    /**
     * Checks if NPM publishing is enabled for this project.
     *
     * @return true if NPM publishing target exists
     */
    public boolean isNpmPublishingEnabled() {
        try {
            return publishTargetService
                    .getTargetsForProject(
                            packageJSONFile.getAbsoluteFile().getParentFile().getAbsolutePath(),
                            true
                    )
                    .stream()
                    .anyMatch(t -> t.getType() == PublishTargetType.NPM);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Checks if GitHub publishing is enabled for this project.
     *
     * @return true if GitHub publishing target exists
     */
    public boolean isGitHubPublishingEnabled() {
        try {
            return publishTargetService
                    .getTargetsForProject(
                            packageJSONFile.getAbsoluteFile().getParentFile().getAbsolutePath(),
                            true
                    )
                    .stream()
                    .anyMatch(t -> t.getType() == PublishTargetType.GITHUB);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Gets the list of enabled publish targets.
     *
     * @return comma-separated list of publish target names
     */
    public String getPublishTargetNames() {
        try {
            return publishTargetService
                    .getTargetsForProject(
                            packageJSONFile.getAbsoluteFile().getParentFile().getAbsolutePath(),
                            true
                    )
                    .stream()
                    .map(t -> t.getType().name())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("unknown");
        } catch (IOException e) {
            return "unknown";
        }
    }

    /**
     * Gets build preferences for this project.
     *
     * @return PackagingPreferences for the project
     */
    public PackagingPreferences getBuildPreferences() {
        return packagingPreferencesService.getPackagingPreferences(
                packageJSONFile.getAbsolutePath()
        );
    }

    /**
     * Updates build preferences for this project.
     *
     * @param preferences the preferences to save
     */
    public void setBuildPreferences(PackagingPreferences preferences) {
        packagingPreferencesService.setPackagingPreferences(preferences);
    }

    // Helper method to convert path separators
    private static String toNativePath(String path) {
        return path.replace("/", File.separator).replace("\\", File.separator);
    }
}

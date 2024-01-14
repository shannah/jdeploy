package ca.weblite.jdeploy.records;

import ca.weblite.jdeploy.services.ProjectInitializer;
import org.json.JSONObject;

import java.io.File;

public class PackageJsonInitializerContext {
    private final File directory;

    private final JSONObject packageJSONContent;

    private final boolean shouldUpdateExistingPackageJson;

    public PackageJsonInitializerContext(
            File directory,
            JSONObject packageJSONContent,
            boolean shouldUpdateExistingPackageJson
    ) {
        this.directory = directory;
        this.packageJSONContent = packageJSONContent;
        this.shouldUpdateExistingPackageJson = shouldUpdateExistingPackageJson;
    }

    public PackageJsonInitializerContext withPackageJSONContent(JSONObject packageJSONContent) {
        return new PackageJsonInitializerContext(directory, packageJSONContent, shouldUpdateExistingPackageJson);
    }

    public File getDirectory() {
        return directory;
    }

    public JSONObject getPackageJSONContent() {
        return packageJSONContent;
    }

    public boolean shouldUpdateExistingPackageJson() {
        return shouldUpdateExistingPackageJson;
    }

    public static PackageJsonInitializerContextBuilder builder() {
        return new PackageJsonInitializerContextBuilder();
    }

    public static class PackageJsonInitializerContextBuilder {
        private File directory;

        private JSONObject packageJSONContent;

        private boolean shouldUpdateExistingPackageJson;

        public PackageJsonInitializerContextBuilder setDirectory(File directory) {
            this.directory = directory;
            return this;
        }


        public PackageJsonInitializerContextBuilder setPackageJSONContent(JSONObject packageJSONContent) {
            this.packageJSONContent = packageJSONContent;
            return this;
        }

        public PackageJsonInitializerContextBuilder setShouldUpdateExistingPackageJson(boolean shouldUpdateExistingPackageJson) {
            this.shouldUpdateExistingPackageJson = shouldUpdateExistingPackageJson;
            return this;
        }

        public PackageJsonInitializerContext createPackageJsonInitializerContext() {
            return new PackageJsonInitializerContext(directory, packageJSONContent, shouldUpdateExistingPackageJson);
        }
    }

    public static class Factory {
        public PackageJsonInitializerContext fromProjectInitializerContext(ProjectInitializer.Context context) {
            return PackageJsonInitializerContext.builder()
                    .setDirectory(context.getDirectory())
                    .setShouldUpdateExistingPackageJson(context.isFixPackageJson())
                    .createPackageJsonInitializerContext();
        }
    }

}

package ca.weblite.jdeploy.publishing;

import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.packaging.PackagingContext;

import java.io.File;
import java.io.PrintStream;

public class PublishingContext {
    public final PackagingContext packagingContext;

    public final boolean alwaysPackageOnPublish;

    public final NPM npm;

    public PublishingContext(
            PackagingContext packagingContext,
            boolean alwaysPackageOnPublish,
            NPM npm
    ) {
        this.packagingContext = packagingContext;
        this.alwaysPackageOnPublish = alwaysPackageOnPublish;
        this.npm = npm;
    }

    public static Builder builder() {
        return new Builder();
    }

    public File getPublishDir() {
        return new File(packagingContext.directory,"jdeploy" + File.separator+ "publish");
    }

    public File getPublishJdeployBundleDir() {
        return new File(getPublishDir(), "jdeploy-bundle");
    }

    public File getPublishPackageJsonFile() {
        return new File(getPublishDir(), "package.json");
    }

    public PrintStream out() {
        return packagingContext.out;
    }

    public PrintStream err() {
        return packagingContext.err;
    }

    public File directory() {
        return packagingContext.directory;
    }

    public static class Builder {
        private PackagingContext packagingContext;
        private boolean alwaysPackageOnPublish = !Boolean.getBoolean("jdeploy.doNotPackage");;
        private NPM npm;

        public Builder setPackagingContext(PackagingContext packagingContext) {
            this.packagingContext = packagingContext;
            return this;
        }

        public Builder setAlwaysPackageOnPublish(boolean alwaysPackageOnPublish) {
            this.alwaysPackageOnPublish = alwaysPackageOnPublish;
            return this;
        }

        public Builder setNPM(NPM npm) {
            this.npm = npm;
            return this;
        }

        private PackagingContext packagingContext() {
            if (packagingContext == null) {
                packagingContext = PackagingContext.builder().build();
            }
            return packagingContext;
        }

        private NPM npm() {
            if (npm == null) {
                npm = new NPM(packagingContext().out, packagingContext().err);
            }
            return npm;
        }

        public PublishingContext build() {
            return new PublishingContext(packagingContext(), alwaysPackageOnPublish, npm());
        }
    }
}

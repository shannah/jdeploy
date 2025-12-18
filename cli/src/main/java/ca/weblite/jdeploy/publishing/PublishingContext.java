package ca.weblite.jdeploy.publishing;

import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.packaging.PackagingContext;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;

public class PublishingContext {
    public final PackagingContext packagingContext;

    public final boolean alwaysPackageOnPublish;

    public final NPM npm;

    private String githubToken;

    public final String githubRepository;

    public final String githubRefName;

    public final String githubRefType;

    private final String distTag;

    public PublishingContext(
            PackagingContext packagingContext,
            boolean alwaysPackageOnPublish,
            NPM npm,
            String githubToken,
            String githubRepository,
            String githubRefName,
            String githubRefType,
            String distTag
    ) {
        this.packagingContext = packagingContext;
        this.alwaysPackageOnPublish = alwaysPackageOnPublish;
        this.npm = npm;
        this.githubToken = githubToken;
        this.githubRepository = githubRepository;
        this.githubRefName = githubRefName;
        this.githubRefType = githubRefType;
        this.distTag = distTag;
    }

    public String getGithubToken() {
        return githubToken;
    }

    public PublishingContext withPackagingContext(PackagingContext packagingContext) {
        return new PublishingContext(
                packagingContext,
                alwaysPackageOnPublish,
                npm,
                githubToken,
                githubRepository,
                githubRefName,
                githubRefType,
                distTag
        );
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

    public InputStream in() {
        return packagingContext.in;
    }

    public File directory() {
        return packagingContext.directory;
    }

    public File getGithubReleaseFilesDir() {
        return new File(directory(), "jdeploy" + File.separator + "github-release-files");
    }

    public String getDistTag() {
        return distTag;
    }

    public static class Builder {
        private PackagingContext packagingContext;
        private boolean alwaysPackageOnPublish = !Boolean.getBoolean("jdeploy.doNotPackage");;
        private NPM npm;

        private String githubToken;

        private String githubRepository;

        private String githubRefName;

        private String githubRefType;

        private String distTag;

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

        public Builder setGithubToken(String githubToken) {
            this.githubToken = githubToken;
            return this;
        }

        public Builder setGithubRepository(String githubRepository) {
            this.githubRepository = githubRepository;
            return this;
        }

        public Builder setGithubRefName(String githubRefName) {
            this.githubRefName = githubRefName;
            return this;
        }

        public Builder setGithubRefType(String githubRefType) {
            this.githubRefType = githubRefType;
            return this;
        }

        public Builder setDistTag(String distTag) {
            this.distTag = distTag;
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
            return new PublishingContext(
                    packagingContext(),
                    alwaysPackageOnPublish,
                    npm(),
                    githubToken,
                    githubRepository,
                    githubRefName,
                    githubRefType,
                    distTag
            );
        }
    }
}

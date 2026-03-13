package ca.weblite.jdeploy.publishing;

import ca.weblite.jdeploy.models.BundleArtifact;
import ca.weblite.jdeploy.models.BundleManifest;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;
import ca.weblite.jdeploy.publishing.s3.S3BundleUploader;
import ca.weblite.jdeploy.publishing.s3.S3Config;
import org.apache.commons.io.FileUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Routes bundle uploads to the appropriate destination:
 * - If JDEPLOY_S3_BUCKET is set: uploads to S3
 * - Else if target is GitHub: copies JARs to GitHub release files directory
 * - Else: skips with warning (NPM without S3)
 */
@Singleton
public class BundleUploadRouter {

    public enum UploadDestination {
        S3,
        GITHUB_RELEASE,
        NONE
    }

    private final S3Config s3Config;
    private final S3BundleUploader s3Uploader;

    @Inject
    public BundleUploadRouter(S3Config s3Config, S3BundleUploader s3Uploader) {
        this.s3Config = s3Config;
        this.s3Uploader = s3Uploader;
    }

    /**
     * Determines the upload destination based on configuration and target.
     */
    public UploadDestination getDestination(PublishTargetInterface target) {
        if (s3Config.isConfigured()) {
            return UploadDestination.S3;
        }
        if (target.getType() == PublishTargetType.GITHUB) {
            return UploadDestination.GITHUB_RELEASE;
        }
        return UploadDestination.NONE;
    }

    /**
     * Uploads bundles to the appropriate destination and sets URLs on artifacts.
     *
     * @param manifest         the bundle manifest
     * @param target           the publish target
     * @param releaseFilesDir  the GitHub release files directory (for GITHUB_RELEASE destination)
     * @param version          the version being published (for GitHub release URL construction)
     * @param out              print stream for status messages
     * @throws IOException if upload fails
     */
    public void uploadBundles(
            BundleManifest manifest,
            PublishTargetInterface target,
            File releaseFilesDir,
            String version,
            PrintStream out
    ) throws IOException {
        UploadDestination destination = getDestination(target);

        switch (destination) {
            case S3:
                s3Uploader.uploadAll(manifest, out);
                break;

            case GITHUB_RELEASE:
                uploadToGitHubRelease(manifest, target, releaseFilesDir, version, out);
                break;

            case NONE:
                out.println("Warning: Bundle publishing requires S3 for NPM targets. " +
                        "Set JDEPLOY_S3_BUCKET to enable bundle uploads. Skipping.");
                break;
        }
    }

    private void uploadToGitHubRelease(
            BundleManifest manifest,
            PublishTargetInterface target,
            File releaseFilesDir,
            String version,
            PrintStream out
    ) throws IOException {
        out.println("Adding " + manifest.getArtifacts().size() + " bundle artifact(s) to GitHub release...");

        String repositoryUrl = target.getUrl();

        for (BundleArtifact artifact : manifest.getArtifacts()) {
            // Copy JAR to release files directory so it gets uploaded with the release
            File destFile = new File(releaseFilesDir, artifact.getFilename());
            FileUtils.copyFile(artifact.getJarFile(), destFile);

            // Construct the GitHub release download URL
            String downloadUrl = repositoryUrl + "/releases/download/" + version + "/" + artifact.getFilename();
            artifact.setUrl(downloadUrl);

            out.println("  Added to release: " + artifact.getFilename());
        }
    }
}

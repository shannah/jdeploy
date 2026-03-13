package ca.weblite.jdeploy.publishing.s3;

import ca.weblite.jdeploy.environment.Environment;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Reads and validates S3 configuration from environment variables.
 *
 * Environment variables:
 * - JDEPLOY_S3_BUCKET: S3 bucket name (required to enable S3)
 * - JDEPLOY_S3_REGION: AWS region (default: us-east-1)
 * - JDEPLOY_S3_PREFIX: Key prefix within bucket (default: jdeploy-bundles)
 * - AWS_ACCESS_KEY_ID: AWS credentials
 * - AWS_SECRET_ACCESS_KEY: AWS credentials
 */
@Singleton
public class S3Config {

    private static final String DEFAULT_REGION = "us-east-1";
    private static final String DEFAULT_PREFIX = "jdeploy-bundles";

    private final Environment environment;

    @Inject
    public S3Config(Environment environment) {
        this.environment = environment;
    }

    public boolean isConfigured() {
        String bucket = getBucket();
        return bucket != null && !bucket.trim().isEmpty();
    }

    public String getBucket() {
        return environment.get("JDEPLOY_S3_BUCKET");
    }

    public String getRegion() {
        String region = environment.get("JDEPLOY_S3_REGION");
        return (region != null && !region.trim().isEmpty()) ? region : DEFAULT_REGION;
    }

    public String getPrefix() {
        String prefix = environment.get("JDEPLOY_S3_PREFIX");
        return (prefix != null && !prefix.trim().isEmpty()) ? prefix : DEFAULT_PREFIX;
    }

    public String getAccessKeyId() {
        return environment.get("AWS_ACCESS_KEY_ID");
    }

    public String getSecretAccessKey() {
        return environment.get("AWS_SECRET_ACCESS_KEY");
    }

    /**
     * Returns the public URL for an uploaded artifact.
     */
    public String getPublicUrl(String filename) {
        return "https://" + getBucket() + ".s3." + getRegion() + ".amazonaws.com/" + getPrefix() + "/" + filename;
    }

    /**
     * Returns the S3 key for a file.
     */
    public String getKey(String filename) {
        return getPrefix() + "/" + filename;
    }
}

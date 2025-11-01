package ca.weblite.jdeploy.installer;

/**
 * Data class representing cached bundle information from the jDeploy registry.
 * Immutable once created since the registry mapping is permanent.
 */
public class BundleInfo {
    private final String projectSource;
    private final String packageName;
    private final long timestamp;

    public BundleInfo(String projectSource, String packageName, long timestamp) {
        this.projectSource = projectSource;
        this.packageName = packageName;
        this.timestamp = timestamp;
    }

    /**
     * Parse BundleInfo from cache format: {project_source}|{package_name}|{timestamp}
     */
    public static BundleInfo fromCacheValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String[] parts = value.split("\\|");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BundleInfo(parts[0], parts[1], Long.parseLong(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Convert to cache format: {project_source}|{package_name}|{timestamp}
     */
    public String toCacheValue() {
        return projectSource + "|" + packageName + "|" + timestamp;
    }

    public String getProjectSource() {
        return projectSource;
    }

    public String getPackageName() {
        return packageName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "BundleInfo{" +
                "projectSource='" + projectSource + '\'' +
                ", packageName='" + packageName + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
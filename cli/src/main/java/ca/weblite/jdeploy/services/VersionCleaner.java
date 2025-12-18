package ca.weblite.jdeploy.services;

public class VersionCleaner {
    public static String cleanVersion(String version) {
        if (version == null) {
            return null;
        }

        if (version.isEmpty()) {
            return version;
        }
        // Extract suffix from version to make it exempt from cleaning.  We re-append at the end
        String suffix = "";
        int suffixIndex = version.indexOf("-");
        if (suffixIndex != -1) {
            suffix = version.substring(suffixIndex);
            version = version.substring(0, suffixIndex);
        }

        // strip leading zeroes from each component of the version
        String[] parts = version.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(".");
            }
            try {
                sb.append(Integer.parseInt(parts[i]));
            } catch (NumberFormatException ex) {
                sb.append(parts[i]);
            }
        }
        return sb.toString() + suffix;
    }
}

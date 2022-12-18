package ca.weblite.jdeploy.services;

public class JavaVersionExtractor {
    private int extractJavaVersionFromString(final String javaVersionString, final int defaultValue) {
        if (javaVersionString == null || javaVersionString.isEmpty()) {
            return defaultValue;
        }
        if (javaVersionString.startsWith("1.")) {
            return extractJavaVersionFromString(javaVersionString.substring(2), defaultValue);
        }
        final int dotPos = javaVersionString.indexOf('.');
        final String javaMajorVersionString = (dotPos > 0
                ? javaVersionString.substring(0, dotPos)
                : javaVersionString
        ).trim().replaceAll("[^\\d]", "");

        try {
            return Integer.parseInt(javaMajorVersionString);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public int extractJavaVersionFromSystemProperties(final int defaultValue) {
        return extractJavaVersionFromString(System.getProperty("java.version"), defaultValue);
    }
}

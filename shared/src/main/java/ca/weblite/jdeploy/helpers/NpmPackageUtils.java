package ca.weblite.jdeploy.helpers;

/**
 * Utilities for working with npm package names.
 */
public final class NpmPackageUtils {

    private NpmPackageUtils() {
    }

    /**
     * Derives a default title from an npm package name. For scoped packages
     * (e.g. {@code @scope/name}), the {@code @scope/} prefix is stripped so the
     * resulting title can safely be used as a file or directory name without
     * the slash being interpreted as a path separator.
     *
     * @param npmPackage The npm package name. May be {@code null}.
     * @return The package name with any leading {@code @scope/} prefix removed,
     *         or the original value if it is not a scoped package or is
     *         {@code null}.
     */
    public static String deriveDefaultTitle(String npmPackage) {
        if (npmPackage == null) {
            return null;
        }
        if (npmPackage.startsWith("@")) {
            int slash = npmPackage.indexOf('/');
            if (slash >= 0 && slash < npmPackage.length() - 1) {
                return npmPackage.substring(slash + 1);
            }
        }
        return npmPackage;
    }
}

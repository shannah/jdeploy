package ca.weblite.jdeploy.helpers;

public class PrereleaseHelper {
    public static boolean isPrereleaseVersion(String versionString) {
        if (versionString == null) return false;
        versionString = versionString.toLowerCase();
        return versionString.indexOf("-alpha") > 0 ||
                versionString.indexOf("-pre") > 0 ||
                versionString.indexOf("-beta") > 0 ||
                versionString.indexOf("-snap") > 0;

    }
}

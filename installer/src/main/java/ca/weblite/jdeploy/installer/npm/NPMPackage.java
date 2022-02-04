package ca.weblite.jdeploy.installer.npm;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Locale;

public class NPMPackage {

    private JSONObject packageInfo;

    NPMPackage(JSONObject packageInfo) {
        this.packageInfo = packageInfo;
    }

    public String getName() {
        return packageInfo.getString("name");
    }



    private static boolean isPrerelease(String version) {
        if (!version.contains("-")) return false;
        String lcVersion = version.toLowerCase();
        if (lcVersion.contains("-pre") || lcVersion.contains("-alpha") || lcVersion.contains("beta") || lcVersion.contains("-snap")) {
            return true;
        }
        return false;
    }

    private static boolean versionMatches(String v, String semVer) {
        if (semVer.startsWith("^")) {
            int pos = semVer.indexOf(".");
            if (pos < 0) {
                semVer += ".0";
                pos = semVer.indexOf(".");
            }
            String prefix = semVer.substring(1, pos);
            if (prefix.equals(v) || v.startsWith(prefix+".")) {
                return true;
            }

        } else if (semVer.startsWith("~")) {

            int pos = semVer.indexOf(".");

            if (pos < 0) {
                semVer += ".0";
                pos = semVer.indexOf(".");
            }
            int p1 = pos;
            pos = semVer.indexOf(".", p1+1);
            if (pos < 0) {
                semVer += ".0";
                pos = semVer.indexOf(".", p1+1);
            }
            p1 = pos;

            String prefix = semVer.substring(1,p1);

            if (prefix.equals(v) || v.startsWith(prefix+".")) {
                return true;
            }
        } else if ("latest".equals(semVer)){
            return true;

        } else {
            return v.equals(semVer);
        }
        return false;
    }

    public NPMPackageVersion getLatestVersion(boolean prerelease, String semVer) {
        String versionNumber = null;
        if (semVer == null) semVer = "latest";
        if (prerelease && "latest".equals(semVer)) {
            versionNumber = packageInfo.getJSONObject("dist-tags").getString("latest");
        } else {
            JSONObject versions = packageInfo.getJSONObject("versions");
            Iterator<String> keys = versions.keys();

            while (keys.hasNext()) {


                String v = keys.next();
                if (v.equals(semVer)) {
                    // Exact match matches regardless of prerelease settings.
                    versionNumber = v;
                    break;
                }
                boolean matches = versionMatches(v, semVer);

                if (matches && (prerelease || !isPrerelease(v))) {
                    versionNumber = v;
                    break;
                }
            }
        }
        if (versionNumber == null) {
            return null;
        }
        return new NPMPackageVersion(this, versionNumber, packageInfo.getJSONObject("versions").getJSONObject(versionNumber));
    }




}

package ca.weblite.jdeploy.installer.npm;

public class NPMPackageVersion {
    private final NPMPackage npmPackage;
    private final String version;

    NPMPackageVersion(NPMPackage pkg, String version) {
        this.npmPackage = pkg;
        this.version = version;
    }


    public NPMPackage getNpmPackage() {
        return npmPackage;
    }

    public String getVersion() {
        return version;
    }
}

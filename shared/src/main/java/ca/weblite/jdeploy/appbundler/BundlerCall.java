package ca.weblite.jdeploy.appbundler;

public interface BundlerCall {
    public BundlerResult bundle(BundlerSettings settings, String destDir, String releaseDir) throws Exception;
}

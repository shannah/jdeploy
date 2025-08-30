package ca.weblite.jdeploy.installer;

import org.apache.commons.io.FileUtils;
import java.io.File;

public class MainDebug {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: MainDebug <code> [version]");
            System.err.println("  code: The jDeploy bundle code");
            System.err.println("  version: The version (defaults to 'latest' if not specified)");
            System.exit(1);
        }

        String code = args[0];
        String version = args.length > 1 ? args[1] : "latest";

        File tempDir = File.createTempFile("jdeploy-debug-", "");
        tempDir.delete();
        tempDir.mkdirs();
        
        File originalDir = new File(System.getProperty("user.dir"));
        System.setProperty("user.dir", tempDir.getAbsolutePath());
        
        try {
            File jdeployFilesDir = DefaultInstallationContext.downloadJDeployBundleForCode(code, version, null);
            File targetJDeployFilesDir = new File(tempDir, ".jdeploy-files");
            if (jdeployFilesDir != null && jdeployFilesDir.exists()) {
                FileUtils.copyDirectory(jdeployFilesDir, targetJDeployFilesDir);
                System.out.println("Downloaded .jdeploy-files to: " + targetJDeployFilesDir.getAbsolutePath());
            } else {
                System.err.println("Failed to download .jdeploy-files for code: " + code + ", version: " + version);
                System.exit(1);
            }
            
            Main.main(new String[0]);
        } finally {
            System.setProperty("user.dir", originalDir.getAbsolutePath());
        }
    }
}
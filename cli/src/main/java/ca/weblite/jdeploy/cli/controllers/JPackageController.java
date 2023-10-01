package ca.weblite.jdeploy.cli.controllers;

import ca.weblite.jdeploy.services.JPackageService;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.Callable;

public class JPackageController extends BaseController implements Runnable {
    public JPackageController(File packageJSONFile, String[] args) {
        super(packageJSONFile, args);
    }

    @Override
    public void run() {
        try {
            out.println("Generating native bundle for current platform with jPackage");
            out.println("TIP: You can override the path to your jpackage binary with the JDEPLOY_JPACKAGE environment variable.");
            JPackageService jPackageService = new JPackageService(packageJSONFile, null);
            if (args != null) jPackageService.setArgs(args);
            jPackageService.execute();
            out.println("jpackage succeeded.");
            out.println("The bundle was generated in "+new File(packageJSONFile.getParentFile(), "jdeploy" + File.separator + "jpackage").getAbsolutePath());
            System.exit(0);
        } catch (Exception ex) {
            err.println("jpackage failed.");
            ex.printStackTrace(err);
            System.exit(1);
        }
    }

    public boolean doesJdkIncludeJavafx() {
        try {
            return new JPackageService(packageJSONFile, null).doesJdkIncludeJavaFX();
        } catch (Exception ex) {
            return false;
        }
    }
}

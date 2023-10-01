package ca.weblite.jdeploy.cli.controllers;

import ca.weblite.jdeploy.services.CheerpjService;

import java.io.File;
import java.io.IOException;

public class CheerpjController extends BaseController implements Runnable {

    private CheerpjService cheerpjService;

    public CheerpjController(File packageJSONFile, String[] args) {
        super(packageJSONFile, args);

    }

    @Override
    public void run() {
        try {
            cheerpjService = new CheerpjService(packageJSONFile, null);
            if (args != null) cheerpjService.setArgs(args);
            cheerpjService.execute();
            out.println("Web app created in jdeploy/cheerpj directory");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

package ca.weblite.jdeploy.cli.controllers;

import ca.weblite.jdeploy.services.CheerpjService;

import java.io.File;
import java.io.IOException;

public class CheerpjController extends BaseController implements Runnable {

    private CheerpjService cheerpjService;

    public CheerpjController(File packageJSONFile, String[] args) {
        super(packageJSONFile, args);

    }

    private CheerpjService getCheerpjService() throws IOException {
        if (cheerpjService == null) {
            cheerpjService = new CheerpjService(packageJSONFile, null);
        }

        return cheerpjService;
    }

    public boolean isEnabled() {
        try {
            return getCheerpjService().isEnabled();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void run() {
        try {
            CheerpjService cheerpjService = getCheerpjService();
            if (args != null) cheerpjService.setArgs(args);
            cheerpjService.execute();
            out.println("Web app created in jdeploy/cheerpj directory");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

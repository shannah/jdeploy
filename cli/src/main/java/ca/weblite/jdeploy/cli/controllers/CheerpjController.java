package ca.weblite.jdeploy.cli.controllers;

import ca.weblite.jdeploy.http.StaticFileServer;
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
            CheerpjService.Result result = cheerpjService.execute();
            StaticFileServer server = result.server;
            out.println("Web app created in jdeploy/cheerpj directory");
            if (server != null) {
                out.println("Serving at http://localhost:" + server.getListeningPort());
                System.out.println("Press ENTER to stop the server...");

                System.in.read(); // Wait for user input
                server.stop();
                System.out.println("Server stopped.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

package ca.weblite.jdeploy.cli.controllers;

import ca.weblite.jdeploy.npm.NPM;

import java.io.IOException;

public class NPMLoginController {

    public void run() {
        NPM npm = new NPM(System.out, System.err, true);
        try {
            npm.startInteractiveLogin();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

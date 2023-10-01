package ca.weblite.jdeploy.cli.controllers;

import java.io.File;
import java.io.PrintStream;

public class BaseController {
    protected File packageJSONFile;
    protected PrintStream out = System.out;
    protected PrintStream err = System.err;
    protected String[] args;

    public BaseController(File packageJSONFile, String[] args) {
        this.packageJSONFile = packageJSONFile;
        this.args = args;
    }

    public PrintStream getOut() {
        return out;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public PrintStream getErr() {
        return err;
    }

    public void setErr(PrintStream err) {
        this.err = err;
    }

}

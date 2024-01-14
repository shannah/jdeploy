package ca.weblite.jdeploy.records;

import java.io.PrintStream;
import java.io.PrintWriter;

public class IOContext {
    private final PrintStream out;
    private final PrintStream err;

    public IOContext(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public PrintStream getOut() {
        return out;
    }

    public PrintStream getErr() {
        return err;
    }
}

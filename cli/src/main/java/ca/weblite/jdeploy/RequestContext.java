package ca.weblite.jdeploy;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class RequestContext {

    private RequestContext parent;
    private final File packageJSONFile;

    private JSONObject packageJSON;

    private String[] args = null;

    public RequestContext(File packageJSONFile, JSONObject packageJSON) {
        this.packageJSONFile = packageJSONFile;
        this.packageJSON = packageJSON;
    }

    public RequestContext(File packageJSONFile) {
        this(packageJSONFile, null);
    }

    public RequestContext() {
        this(new File("."), null);
    }

    public File getPackageJSONFile() {
        if (parent != null) {
            return parent.getPackageJSONFile();
        }
        return packageJSONFile;
    }

    public JSONObject getPackageJSON() {
        if (parent != null && packageJSON == null) {
            return parent.getPackageJSON();
        }
        if (packageJSON == null) {
            try {
                packageJSON = new JSONObject(FileUtils.readFileToString(packageJSONFile, "UTF-8"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
        return packageJSON;
    }

    public String[] getArgs() {
        if (args == null && parent != null) {
            return parent.getArgs();
        }
        return args;
    }

    public RequestContext withArgs(String[] args) {
        RequestContext ctx = new RequestContext(packageJSONFile, packageJSON);
        ctx.args = args;
        ctx.parent = this;
        return ctx;
    }
}

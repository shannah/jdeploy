package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.cheerpj.services.BuildCheerpjAppService;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class CheerpjService extends BaseService {
    private BuildCheerpjAppService buildCheerpjAppService;

    public CheerpjService(File packageJSONFile, JSONObject packageJSON) throws IOException {
        super(packageJSONFile, packageJSON);
        buildCheerpjAppService = new BuildCheerpjAppService();
    }

    protected File getDestDirectory() throws IOException {
        File dest = new File(super.getDestDirectory(), "cheerpj");
        dest.mkdirs();
        return dest;
    }

    private void run() throws IOException {
        File mainJar = this.getMainJarFile();
        File dest = this.getDestDirectory();
        buildCheerpjAppService.build(mainJar, dest);
    }

    public void execute() throws IOException {
        this.run();
    }
}

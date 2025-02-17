package ca.weblite.jdeploy.factories;

import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.services.CheerpjService;
import org.json.JSONObject;

import javax.inject.Singleton;
import java.io.IOException;

@Singleton
public class CheerpjServiceFactory {
    public CheerpjService create(PackagingContext context) throws IOException {
        return new CheerpjService(context.packageJsonFile, new JSONObject(context.packageJsonMap));
    }
}

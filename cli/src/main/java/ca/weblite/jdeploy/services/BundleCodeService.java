package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.packaging.PackagingConfig;
import ca.weblite.tools.io.IOUtil;
import ca.weblite.tools.io.URLUtil;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class BundleCodeService {

    private final PackagingConfig config;
    private Map<String,String> bundleCodeCache = new HashMap<String,String>();

    @Inject
    public BundleCodeService(PackagingConfig config) {
        this.config = config;
    }
    public String fetchJdeployBundleCode(String fullPackageName) throws IOException {
        if (bundleCodeCache.containsKey(fullPackageName)) {
            return bundleCodeCache.get(fullPackageName);
        }
        String url = config.getJdeployRegistry()+"register.php?package=" +
                URLEncoder.encode(fullPackageName, "UTF-8");

        try (InputStream inputStream = URLUtil.openStream(new URL(url))) {
            JSONObject jsonResponse = new JSONObject(IOUtil.readToString(inputStream));
            String code =  jsonResponse.getString("code");
            if (code != null && !code.isEmpty()) {
                bundleCodeCache.put(fullPackageName, code);
            }
            return code;
        } catch (Exception ex) {
            throw ex;
        }
    }

    public String fetchJdeployBundleCode(AppInfo appInfo) throws IOException {
        if (appInfo.getNpmPackage() == null) {
            throw new IllegalArgumentException("Cannot fetch jdeploy bundle code without package and version");
        }

        return fetchJdeployBundleCode(getFullPackageName(appInfo.getNpmSource(), appInfo.getNpmPackage()));
    }

    private String getFullPackageName(String source, String packageName) {
        if (source == null || source.isEmpty()) {
            return packageName;
        }

        return source + "#" + packageName;
    }
}

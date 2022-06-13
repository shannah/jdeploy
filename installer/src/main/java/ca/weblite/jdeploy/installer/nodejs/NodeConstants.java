package ca.weblite.jdeploy.installer.nodejs;

import java.io.File;

public interface NodeConstants {
    static final String DIST_URL = "https://nodejs.org/dist/";

    static final String JDEPLOY_HOME = System.getProperty("user.home") +
            File.separator +
            ".jdeploy";

    static final String INDEX_URL = DIST_URL + "index.json";
    static final String NODE_BASE = JDEPLOY_HOME + File.separator + "node";
}

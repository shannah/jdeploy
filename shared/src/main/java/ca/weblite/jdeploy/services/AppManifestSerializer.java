package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.AppManifest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppManifestSerializer {
    public String serializeManifest(AppManifest appManifest) {
        StringBuilder sb = new StringBuilder();
        List<String> parts = new ArrayList<String>();
        for (AppManifest.FileDescription fileDescription : appManifest.getFiles()) {
            parts.add("["+fileDescription.getFile()+"]("+fileDescription.getMD5()+")");
        }
        Collections.sort(parts);
        for (String part : parts) {
            sb.append(part);
        }
        return toString();
    }
}

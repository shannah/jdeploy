package ca.weblite.jdeploy.cli.dtos;

import java.util.HashMap;
import java.util.Map;

public class UpdateReleaseLinksRequest {
    private final Map<String, String> releaseLinks = new HashMap<>();

    public UpdateReleaseLinksRequest(String... links) {
        for (int i=0; i<links.length; i+=2) {
            releaseLinks.put(links[i], links[i+1]);
        }
    }

    public String[] getLinkTypes() {
        return releaseLinks.keySet().toArray(new String[0]);
    }

    public String getLink(String type) {
        return releaseLinks.get(type);
    }
}

package ca.weblite.jdeploy.models;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DeveloperIdentities implements Iterable<DeveloperIdentity> {
    private List<DeveloperIdentity> identities = new ArrayList<>();

    @Override
    public Iterator<DeveloperIdentity> iterator() {
        return identities.iterator();
    }

    public void add(DeveloperIdentity identity) {
        identities.add(identity);
    }
}

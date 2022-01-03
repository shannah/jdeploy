/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.tools.git;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author shannah
 */
public class GitRemotes implements Iterable<GitRemote> {
    private List<GitRemote> remotes = new ArrayList<>();

    public void add(GitRemote remote) {
        remotes.add(remote);
    }
    
    @Override
    public Iterator<GitRemote> iterator() {
        return remotes.iterator();
    }
    
    public GitRemote find(String name) {
        for (GitRemote remote : this) {
            if (name.equals(remote.getName())) {
                return remote;
            }
        }
        return null;
    }
    
}

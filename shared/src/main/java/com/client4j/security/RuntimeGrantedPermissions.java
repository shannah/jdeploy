/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.security;



import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

/**
 *
 * @author shannah
 */
public class RuntimeGrantedPermissions extends Observable implements Iterable<RuntimeGrantedPermission> {
    private List<RuntimeGrantedPermission> grants = new ArrayList<>();
    
   
    public RuntimeGrantedPermissions() {
        
    }

    @Override
    public String toString() {
        return grants.toString();
    }
    
    
    
    public boolean isEmpty() {
        return grants.isEmpty();
    }
    
    public RuntimeGrantedPermissions(Iterable<RuntimeGrantedPermission> perms) {
        for (RuntimeGrantedPermission p : perms) {
            add(p);
        }
    }
    
    public boolean containsIgnoreExpiry(RuntimeGrantedPermission perm) {
        for (RuntimeGrantedPermission p : this) {
            if (Objects.equals(p.getClassName(), perm.getClassName()) &&
                    Objects.equals(p.getName(), perm.getName()) &&
                    Objects.equals(p.getActions(), perm.getActions())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gets permissions that are contained in this set that are not in the provide {@literal perms} set.
     * @param perms
     * @return 
     */
    public RuntimeGrantedPermissions minusIgnoreExpiry(RuntimeGrantedPermissions perms) {
        RuntimeGrantedPermissions out = new RuntimeGrantedPermissions();
        for (RuntimeGrantedPermission p : this) {
            if (!perms.containsIgnoreExpiry(p)) {
                out.add(p);
            }
        }
        return out;
    }
    
    public RuntimeGrantedPermissions plusIgnoreExpiry(RuntimeGrantedPermissions toAdd) {
        RuntimeGrantedPermissions out = new RuntimeGrantedPermissions(this);
        for (RuntimeGrantedPermission p : toAdd) {
            if (!out.containsIgnoreExpiry(p)) {
                out.add(p);
            }
        }
        return out;
    }
    
    @Override
    public Iterator<RuntimeGrantedPermission> iterator() {
        return grants.iterator();
    }
    
    public void add(RuntimeGrantedPermission perm) {

        grants.add(perm);

        setChanged();
    }
    
    public void load(InputStream inputStream) throws IOException {

        grants.clear();
        RuntimeGrantedPermissionReader reader = new RuntimeGrantedPermissionReader(inputStream);
        for (RuntimeGrantedPermission perm : reader) {
            add(perm);
        }
        setChanged();
    }
    
    public void save(OutputStream outputStream) throws IOException {
        RuntimeGrantedPermissionWriter writer = new RuntimeGrantedPermissionWriter(outputStream);
        writer.write(this);
        writer.flush();
    }

    public String toPolicyString() {
        StringBuilder sb = new StringBuilder();
        for (RuntimeGrantedPermission p : this) {
            sb.append(p.toPolicyString()).append(System.lineSeparator());
        }
        return sb.toString().trim();
    }

    
    public void injectProperties(Properties props) {
        for (RuntimeGrantedPermission p : this) {
            p.injectProperties(props);
        }
    }

    public RuntimeGrantedPermission get(int i) {
        return grants.get(i);
    }
}

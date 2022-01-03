/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.permissions;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.app.AppInfo.Permission;
import com.client4j.permissions.groups.*;
import com.client4j.security.RuntimeGrantedPermission;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author shannah
 */
public class PermissionsRequest {
    private List<PermissionsGroup> groups;
    
    // Used only for incremental permissions requests where 
    // the permissions request includes only the permissions that are being added
    private PermissionsRequest existingPermissions;
    
    public boolean includesAllPermissions() {
        for (PermissionsGroup g : groups) {
            if (g.matches(new Permission("java.security.AllPermission", "", ""))) {
                return true;
            }
        }
        return false;
    }
    
    public PermissionsRequest() {
        groups = new ArrayList<>();
        PermissionsGroup[] groupsArr = new PermissionsGroup[]{
            new AWTPermissionsGroup(),
            new AudioPermissionsGroup(),
            new ClipboardPermissionsGroup(),
            new FilePermissionsGroup(),
            new FullTrustPermissionsGroup(),
            new LoadLibraryPermissionsGroup(),
            new NetPermissionsGroup(),
            new PreferencesPermissionsGroup(),
            new PrintingPermissionsGroup(),
            new PropertyPermissionsGroup(),
            new ReadEnvironmentVariablesPermissionsGroup(),
            new RuntimePermissionsGroup(),
            new SecurityPermissionsGroup(),
            new SocketPermissionsGroup(),
            new URLPermissionsGroup()
        };
        
        groups.addAll(Arrays.asList(groupsArr));
        PermissionsGroup other = new PermissionsGroup() {
            @Override
            public int getPriority() {
                return -1;
            }
        };
        other.setName("Miscellaneous Access");
        other.setDescription("This app has requested some additional restricted permissions.  Please review before installing this app.");
        other.getPatterns(true).add(new PermissionPattern("*", null));
        groups.add(other);
        
        Collections.sort(groups, (o1, o2)->{
            return o2.getPriority() - o1.getPriority();
        });
    }
    
    /**
     * Set this perimssions request to be incremental.  In this case, the permissions in 
     * the request only represent new permissions that weren't previously granted, and
     * the existing permissions are the rest of the permissions.
     * @param existing 
     */
    public void setExistingPermissions(PermissionsRequest existing) {
        this.existingPermissions = existing;
    }
    
    public PermissionsRequest getExistingPermissions() {
        return existingPermissions;
    }
    
    /**
     * Checks if this is an incremental permissions request.  If it is incremental, then
     * the permissions in this direct request only represent permissions that are being
     * added.  The existing permissions can be retrieved from the existing permissions.
     * @return 
     */
    public boolean isIncremental() {
        return existingPermissions != null;
    }
    
    public boolean isEmpty() {
        for (PermissionsGroup group : groups) {
            if (group.getPermissions() != null && !group.getPermissions().isEmpty()) {
                return false;
            }
        }
        return true;
    }
    
    public void addPermission(AppInfo.Permission permission) {
        for (PermissionsGroup group : groups) {
            if (group.matches(permission)) {
                group.getPermissions(true).add(permission);
                return;
            }
        }
        throw new IllegalArgumentException("No groups found that match the given permission.  This should never happen. "+permission);
        
    }
    
    public void addPermissions(Iterable<RuntimeGrantedPermission> perms) {
        for (RuntimeGrantedPermission p : perms) {
            addPermission(AppInfo.Permission.fromRuntimeGrantedPermssion(p));
        }
    }
    
    public void addPermissions(AppInfo appInfo) {
        if (appInfo.getPermissions() != null) {
            for (AppInfo.Permission perm : appInfo.getPermissions()) {
                addPermission(perm);
            }
        }
    }
    
    /**
     * Gets all of the permissions in this permissions request.
     * @return 
     */
    public List<PermissionsGroup> getRequestedPermissionsGroups() {
        ArrayList<PermissionsGroup> out = new ArrayList<PermissionsGroup>();
        for (PermissionsGroup group : groups) {
            if (group.getPermissions() != null && !group.getPermissions().isEmpty()) {
                out.add(group);
            }
        }
        return out;
    }
    
    
    
}

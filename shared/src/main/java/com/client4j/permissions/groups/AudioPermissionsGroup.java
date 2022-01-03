/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j.permissions.groups;

import com.client4j.permissions.PermissionPattern;
import com.client4j.permissions.PermissionsGroup;

/**
 *
 * @author shannah
 */
public class AudioPermissionsGroup extends PermissionsGroup {
    public AudioPermissionsGroup() {
        setName("Access to Play or Record Audio");
        setDescription("This app has requested access to play and/or record audio.");
        getPatterns(true).add(new PermissionPattern("javax.sound.sampled.AudioPermission", null));
    }
}

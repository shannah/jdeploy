/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.app.AppInfo.Dependency;

import ca.weblite.jdeploy.app.Workspace;
import com.client4j.CommonLibraries.CommonLibrary;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 *
 * @author shannah
 */
public class CommonLibraries extends ArtifactIndex<CommonLibrary> {


    public CommonLibraries(Workspace workspace) {
        super(workspace);
    }

    
    
    
    public static class CommonLibrary extends Artifact {

        @Override
        public boolean isInstalled(Workspace workspace) {
            Library lib = new Library(workspace, getUrl());
            return lib.isInstalled();
        }
        
    }


}

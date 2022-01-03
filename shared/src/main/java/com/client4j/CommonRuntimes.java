/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.app.Workspace;
import com.client4j.CommonRuntimes.CommonRuntime;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author shannah
 */
public class CommonRuntimes extends ArtifactIndex<CommonRuntime>{
    
    public static final String ARTIFACT_NAME = "JRE";
    
    public CommonRuntimes(Workspace workspace) {
        super(workspace);
    }

    /**
     * Describes a common java runtime, probably from OpenJDK.
     * 
     */
    public static class CommonRuntime extends Artifact {
        private boolean fx;
        
        
        public CommonRuntime() {
            setName(ARTIFACT_NAME);
        }
        
        public boolean isFx() {
            return fx;
        }
        
        public void setFx(boolean fx) {
            this.fx = fx;
        }
        
        @Override
        public boolean isInstalled(Workspace workspace) {
            JavaRuntime rt = new JavaRuntime(workspace, getUrl());
            return rt.isInstalled();
        }
        


        @Override
        public boolean equals(Object obj) {
            if (obj instanceof CommonRuntime) {
                CommonRuntime crt = (CommonRuntime)obj;
                return super.equals(obj) && Objects.equals(fx, crt.fx);
            }
            return super.equals(obj);
        }

        @Override
        public int hashCode() {
            int out = super.hashCode();
            if (fx) {
                out += 1;
            }
            return out;
        }

        @Override
        public String toString() {
            return super.toString() + (fx?"+fx":"");
        }
        
        
        
        
                
    }
    

    


}

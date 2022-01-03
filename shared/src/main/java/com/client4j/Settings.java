/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j;

import ca.weblite.jdeploy.app.AppInfo;


import java.net.URL;
import java.util.*;

/**
 *
 * @author shannah
 */
public class Settings extends Observable {
    
    
    
    public Settings() {

    }
    
    /**
     * @return the sources
     */
    public SourceList getSources() {
        return sources;
    }

    

    /**
     * @return the parent
     */
    public Settings getParent() {
        return parent;
    }

    /**
     * @param parent the parent to set
     */
    public void setParent(Settings parent) {
        if (this.parent != parent) {
            this.parent = parent;
            setChanged();
        }
    }
    
    private Settings parent;
    
    public static enum SourceType {
        Trusted,
        Untrusted,
        Any
    }
    
    public class SourceList extends Observable implements Iterable<Source> {
        private List<Source> sources = new ArrayList<>();



        @Override
        public Iterator<Source> iterator() {
            return sources.iterator();
        }
        
        
        public void addAll(Iterable<Source> sources) {
            for (Source src : sources) {
                add(src);
            }
        }
        
        public void add(Source source) {

            this.sources.add(source);
            setChanged();
        }
        
        public void remove(Source source) {
            if (this.sources.remove(source)) {

                setChanged();
            }
        }
        
        public int size() {
            return sources.size();
        }
        
        public Source get(int i) {
            return sources.get(i);
        }
        
        public Source findByUrl(URL url) {
            for (Source src : this) {
                if (url.equals(src.getUrl())) {
                    return src;
                }
            }
            return null;
        }
        
        public Source remove(URL url) {
            Source src = findByUrl(url);
            if (src != null) {
                remove(src);
            }
            return src;
        }
                


    }
    
    public static class Source extends Observable {

        public Source() {
            
        }
        
        public Source(Source toCopy) {
            url = toCopy.url;
            trust = toCopy.trust;
        }
        
        /**
         * @return the url
         */
        public URL getUrl() {
            return url;
        }

        /**
         * @param url the url to set
         */
        public void setUrl(URL url) {
            if (!Objects.equals(url, this.url)) {
                this.url = url;
                setChanged();
            }
        }

        /**
         * @return the trust
         */
        public boolean isTrust() {
            return trust;
        }

        /**
         * @param trust the trust to set
         */
        public void setTrust(boolean trust) {
            if (this.trust != trust) {
                this.trust = trust;
                setChanged();
            }
        }
        private URL url;
        private boolean trust;
    }
    
    public static enum PermissionType {
        Grant,
        Deny
    }
    
    public static class PermissionRuleList extends Observable implements Iterable<PermissionRule> {
        private final List<PermissionRule> rules = new ArrayList<>();
        @Override
        public Iterator<PermissionRule> iterator() {
            return rules.iterator();
        }
        
        public void add(PermissionRule rule) {

            if (rules.add(rule)) {
                setChanged();
            }
        }
        
        public boolean remove(PermissionRule rule) {
            if (rules.remove(rule)) {

                setChanged();
                return true;
            }
            return false;
        }

        public PermissionRule findByNameTargetActionSourceType(PermissionRule rule) {
            return findByNameTargetActionSourceType(rule.getName(), rule.getTarget(), rule.getAction(), rule.getSourceType());
        }
        
        public PermissionRule findByNameTargetActionSourceType(String name, String target, String action, SourceType sourceType) {
            for (PermissionRule rule : rules) {
                if (Objects.equals(name, rule.name) && Objects.equals(target, rule.target) && Objects.equals(action, rule.action) && Objects.equals(sourceType, rule.sourceType)) {
                    return rule;
                }
            }
            return null;
        }
        
        public PermissionRule get(int row) {
            return rules.get(row);
        }

        public PermissionRule addOrUpdateRule(PermissionRule rule) {
            PermissionRule existing = findByNameTargetActionSourceType(rule);
            if (existing == null) {
                add(rule);
                return findByNameTargetActionSourceType(rule);
            } else {
                existing.setPermissionType(rule.getPermissionType());
                return existing;
            }
        }
        
    }
    
    public static class PermissionRule extends Observable {

        public PermissionRule() {
            
        }
        
        public PermissionRule(PermissionRule rule) {
            name = rule.name;
            target = rule.target;
            this.permissionType = rule.permissionType;
            this.sourceType = rule.sourceType;
            this.action = rule.action;
        }
        
        public String getSignature() {
            return new StringBuilder()
                    .append(name)
                    .append(";")
                    .append(target)
                    .append(";")
                    .append(action)
                    .append(";")
                    .append(sourceType)
                    .toString();
        }

        public boolean matches(AppInfo.Permission permission) {
            return (matchesClassName(permission) && matchesPermissionName(permission) && matchesAction(permission));
            
        }
        
        private boolean matchesClassName(AppInfo.Permission permission) {
            if (name == null || "*".equals(name)) return true;
            if (name.contains("*")) {
                if (name.indexOf("*") == 0) {
                    return permission.getName().endsWith(name.substring(1));
                } else if (name.indexOf("*") == name.length()-1) {
                    return permission.getName().startsWith(name.substring(0, name.length()-1));
                } else {
                    return permission.getName().startsWith(name.substring(0, name.indexOf("*"))) &&
                            permission.getName().endsWith(name.substring(name.indexOf("*")+1));
                }
            } else {
                return permission.getName().equals(name);
            }
        }
        
        private boolean matchesPermissionName(AppInfo.Permission permission) {
            //TODO Handle FilePermission better
            
            if (target == null || "*".equals(target) || target.isEmpty()) {
                return true;
            }
            if (permission.getTarget() == null || "*".equals(permission.getTarget())) {
                return false;
            }
            
            if (target.contains("*")) {
                if (target.indexOf("*") == 0) {
                    return permission.getTarget().endsWith(target.substring(1));
                } else if (target.indexOf("*") == target.length()-1) {
                    return permission.getTarget().startsWith(target.substring(0, target.length()-1));
                } else {
                    return permission.getTarget().startsWith(target.substring(0, target.indexOf("*"))) &&
                            permission.getTarget().endsWith(target.substring(target.indexOf("*")+1));
                }
            } else {
                return permission.getTarget().equals(target);
            }
        }
        
        private boolean matchesAction(AppInfo.Permission permission) {
            if (action == null || "*".equals(action)) {
                return true;
            }
            if (permission.getAction() == null || "*".equals(permission.getAction())) {
                return false;
            }
            
            // TODO:  Handle this better
            
            return false;
        }
        
        /**
         * @return the className
         */
        public String getName() {
            return name;
        }

        /**
         * @param className the className to set
         */
        public void setName(String className) {
            if (!Objects.equals(className, this.name)) {
                this.name = className;
                setChanged();
            }
        }

        /**
         * @return the permissionName
         */
        public String getTarget() {
            return target;
        }

        /**
         * @param permissionName the permissionName to set
         */
        public void setTarget(String permissionName) {
            if (!Objects.equals(permissionName, this.target)) {
                this.target = permissionName;
                setChanged();
            }
        }

        /**
         * @return the action
         */
        public String getAction() {
            return action;
        }

        /**
         * @param action the action to set
         */
        public void setAction(String action) {
            if (!Objects.equals(action, this.action)) {
                this.action = action;
                setChanged();
            }
        }

        /**
         * @return the sourceType
         */
        public SourceType getSourceType() {
            return sourceType;
        }

        /**
         * @param sourceType the sourceType to set
         */
        public void setSourceType(SourceType sourceType) {
            if (!Objects.equals(sourceType, this.sourceType)) {
                this.sourceType = sourceType;
                setChanged();
            }
        }

        /**
         * @return the permissionType
         */
        public PermissionType getPermissionType() {
            return permissionType;
        }

        /**
         * @param permissionType the permissionType to set
         */
        public void setPermissionType(PermissionType permissionType) {
            if (!Objects.equals(permissionType, this.permissionType)) {
                this.permissionType = permissionType;
                setChanged();
            }
        }
        private String name, target, action;
        private SourceType sourceType;
        private PermissionType permissionType;
    }

    
    private SourceList sources;
    private PermissionRuleList permissionRules;
    
    public SourceList getSources(boolean init) {
        if (sources == null && init) {
            sources = new SourceList();

        }
        return sources;
    }

    public PermissionRuleList getAllPermissionRules() {
        PermissionRuleList out = new PermissionRuleList();
        Set<String> added = new HashSet<String>();
        if (permissionRules != null) {
            for (PermissionRule rule : permissionRules) {
                if (!added.contains(rule.getSignature())) {
                    added.add(rule.getSignature());
                    out.add(rule);
                }
            }
        }
        if (parent != null) {
            for (PermissionRule rule : parent.getAllPermissionRules()) {
                if (!added.contains(rule.getSignature())) {
                    added.add(rule.getSignature());
                    out.add(rule);
                }
            }
        }
        return out;
    }
    
    public SourceList getAllTrustedSources() {
        SourceList out = new SourceList();
        Map<URL,Source> sourceMap = new HashMap<URL,Source>();
        if (getParent() != null) {
            out.addAll(getParent().getAllTrustedSources());
            for (Source src : out) {
                sourceMap.put(src.getUrl(), src);
            }
        }
        if (getSources() != null) {
            for (Source src : getSources()) {
                if (sourceMap.containsKey(src.getUrl())) {
                    if (!src.isTrust()) {
                        out.remove(sourceMap.get(src.getUrl()));
                        sourceMap.remove(src.getUrl());
                    }
                } else {
                    if (src.isTrust()) {
                        sourceMap.put(src.getUrl(), src);
                        out.add(src);
                    }
                }
            }
        }
        return out;
        
    }
    
    public SourceList getAllSources() {
        SourceList out = new SourceList();

        Map<URL,Source> sourceMap = new HashMap<URL,Source>();
        if (sources != null) {
            for (Source src : sources) {
                if (!sourceMap.containsKey(src.getUrl())) {
                    out.add(src);
                    sourceMap.put(src.getUrl(), src);
                }
            }
        }
        if (getParent() != null) {
            for (Source src : getParent().getAllSources()) {
                if (!sourceMap.containsKey(src.getUrl())) {
                    out.add(src);
                    sourceMap.put(src.getUrl(), src);
                }
            }
            
        }
        
        return out;
        
    }
    
    
    public boolean isTrusted(URL url) {
        if ("true".equals(System.getProperty("client4j.trustAll", "true"))) {
            return true;
        }
        //System.out.println("Trusted sources: "+getAllTrustedSources());
        for (Source src : getAllTrustedSources()) {
            //System.out.println("URL: "+url);
            
            if (url.toString().startsWith(src.getUrl().toString())) {
                return true;
            }
        }
        return false;
    }
    
    public boolean isPermissionRequestAllowed(URL appURL, AppInfo.Permission permission) {
        
        boolean trusted = isTrusted(appURL);
        if (parent != null) {
            if (parent.isPermissionRequestAllowed(appURL, permission)) {
                // Let's see if we deny it
               if (permissionRules != null) {
                   for (PermissionRule rule : permissionRules) {
                        if (rule.getPermissionType() == PermissionType.Deny) {
                            if (rule.matches(permission)) {
                                if (!trusted || rule.getSourceType() == SourceType.Trusted || rule.getSourceType() == SourceType.Any) {
                                    // This "deny" rule matches the permission, and 
                                    // is either targeting trusted sources, or the source is not trusted
                                    // anyways so we deny the permission request.
                                    if (permission.getName().contains("sampled")) {
                                        int foo = 1;
                                        
                                    }
                                    return false;
                                }
                            }
                        }
                   }
               }
               return true;
            } 
        }
        
        // Parent permission request was not allowed
        // Give us a chance to override that
        if (permissionRules != null) {
            for (PermissionRule rule : permissionRules) {
                if (rule.getPermissionType() == PermissionType.Grant && rule.matches(permission)) {
                    if (trusted || rule.getSourceType() == SourceType.Untrusted || rule.getSourceType() == SourceType.Any) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    public PermissionRuleList getPermissionRules() {
        return permissionRules;
    }
    
    public PermissionRuleList getPermissionRules(boolean init) {
        if (permissionRules == null && init) {
            permissionRules = new PermissionRuleList();

        }
        return permissionRules;
    }
            
}

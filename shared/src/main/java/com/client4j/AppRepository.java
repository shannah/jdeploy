/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author shannah
 */
public class AppRepository {

    /**
     * @return the links
     */
    public List<Link> getLinks() {
        return links;
    }

    /**
     * @param links the links to set
     */
    public void setLinks(List<Link> links) {
        this.links = links;
    }

    /**
     * @return the categories
     */
    public Map<String,Category> getCategories() {
        return categories;
    }
    
    public Map<String,Category> getCategories(boolean init) {
        if (categories == null && init) {
            categories = new HashMap<String,Category>();
        }
        return categories;
    }

    /**
     * @param categories the categories to set
     */
    public void setCategories(Map<String,Category> categories) {
        this.categories = categories;
    }

    /**
     * @return the icon
     */
    public URL getIcon() {
        return icon;
    }

    /**
     * @param icon the icon to set
     */
    public void setIcon(URL icon) {
        this.icon = icon;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the vendor
     */
    public String getVendor() {
        return vendor;
    }

    /**
     * @param vendor the vendor to set
     */
    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the newsFeed
     */
    public URL getNewsFeed() {
        return newsFeed;
    }

    /**
     * @param newsFeed the newsFeed to set
     */
    public void setNewsFeed(URL newsFeed) {
        this.newsFeed = newsFeed;
    }
    
    private List<Link> links;
    private Map<String,Category> categories;
    private URL icon;
    private String name;
    private String vendor;
    private String description;
    private URL newsFeed;
    
    public static class Category {

        /**
         * @return the icon
         */
        public URL getIcon() {
            return icon;
        }

        /**
         * @param icon the icon to set
         */
        public void setIcon(URL icon) {
            this.icon = icon;
        }

        /**
         * @return the name
         */
        public String getName() {
            return name;
        }

        /**
         * @param name the name to set
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * @return the apps
         */
        public List<AppLink> getApps() {
            return apps;
        }
        
        public List<AppLink> getApps(boolean init) {
            if (apps == null && init) {
                apps = new ArrayList<AppLink>();
            }
            return apps;
        }

        /**
         * @param apps the apps to set
         */
        public void setApps(List<AppLink> apps) {
            this.apps = apps;
        }
        
        
        public String toString() {
            return name;
        }
        
        public boolean containsAppWithURL(URL url) {
            if (apps != null) {
                for (AppLink app : apps) {
                    if (url.toString().equals(app.getUrl().toString())) {
                        return true;
                    }
                }
            }
            return false;
        }
        
        public AppLink findAppWithURL(URL url) {
            if (apps != null) {
                for (AppLink app : apps) {
                    if (url.toString().equals(app.getUrl().toString())) {
                        return app;
                    }
                }
            }
            return null;
        }
        
        public boolean removeAppWithURL(URL url) {
            if (apps != null) {
                AppLink app = findAppWithURL(url);
                if (app != null) {
                    return apps.remove(app);
                }
            }
            return false;
        }
        
        private URL icon;
        private String name;
        private List<AppLink> apps;
    }
    
    public static class AppLink {

        /**
         * @return the category
         */
        public Category getCategory() {
            return category;
        }

        /**
         * @param category the category to set
         */
        public void setCategory(Category category) {
            this.category = category;
        }

        /**
         * @return the icon
         */
        public URL getIcon() {
            return icon;
        }

        /**
         * @param icon the icon to set
         */
        public void setIcon(URL icon) {
            this.icon = icon;
        }

        /**
         * @return the installed
         */
        public boolean isInstalled() {
            return installed;
        }

        /**
         * @param installed the installed to set
         */
        public void setInstalled(boolean installed) {
            this.installed = installed;
        }

        /**
         * @return the name
         */
        public String getName() {
            return name;
        }

        /**
         * @param name the name to set
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * @return the vendor
         */
        public String getVendor() {
            return vendor;
        }

        /**
         * @param vendor the vendor to set
         */
        public void setVendor(String vendor) {
            this.vendor = vendor;
        }

        /**
         * @return the version
         */
        public String getVersion() {
            return version;
        }

        /**
         * @param version the version to set
         */
        public void setVersion(String version) {
            this.version = version;
        }

        /**
         * @return the source
         */
        public URL getSource() {
            return source;
        }

        /**
         * @param source the source to set
         */
        public void setSource(URL source) {
            this.source = source;
        }

        /**
         * @return the originalSource
         */
        public URL getOriginalSource() {
            return originalSource;
        }

        /**
         * @param originalSource the originalSource to set
         */
        public void setOriginalSource(URL originalSource) {
            this.originalSource = originalSource;
        }
        private boolean installed;
        private String name;
        private String vendor;
        private String version;
        private URL source;
        private URL originalSource;
        private URL url;
        private URL icon;
        private Category category;

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
            this.url = url;
        }
        
        
    }
    
    public static class Link {

        /**
         * @return the title
         */
        public String getTitle() {
            return title;
        }

        /**
         * @param title the title to set
         */
        public void setTitle(String title) {
            this.title = title;
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
            this.url = url;
        }
        private String title;
        private URL url;
    }
    
    public List<AppLink> getAllApps() {
        List<AppLink> out = new ArrayList<AppLink>();
        for (Category cat : getCategories().values()) {
            out.addAll(cat.getApps());
        }
        return out;
        
    }
    
    public List<Category> getNonEmptyCategories() {
        List<Category> out = new ArrayList<Category>();
        for (Category cat : getCategories().values()) {
            if (!cat.getApps().isEmpty()) {
                out.add(cat);
            }
        }
        return out;
    }
}

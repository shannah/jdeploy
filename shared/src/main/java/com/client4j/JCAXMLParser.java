/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.app.Workspace;
import ca.weblite.tools.io.XMLUtil;
import ca.weblite.jdeploy.app.AppInfo.Dependency;
import ca.weblite.jdeploy.app.AppInfo.JRE;
import ca.weblite.jdeploy.app.AppInfo.Permission;
import ca.weblite.jdeploy.app.AppInfo.QuickLink;
import com.client4j.AppRepository.AppLink;
import com.client4j.AppRepository.Category;
import com.client4j.AppRepository.Link;
import com.client4j.CommonLibraries.CommonLibrary;
import com.client4j.CommonRuntimes.CommonRuntime;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static ca.weblite.tools.io.URLUtil.url;
import static ca.weblite.tools.io.XMLUtil.children;

/**
 *
 * @author shannah
 */
class JCAXMLParser {
    private URL repositoryURL;
    private Workspace workspace;
    private static final Logger logger = Logger.getLogger(JCAXMLParser.class.getName());
    
    public JCAXMLParser(Workspace workspace, URL repositoryURL) {
        this.repositoryURL = repositoryURL;
        this.workspace = workspace;
    }
    
    
    
    public void load(InputStream input, AppRepository target) throws IOException, SAXException {
        Document doc = XMLUtil.parse(input);
        load(doc, target);
    }
    
    public JCAXMLFile load(InputStream input) throws IOException , SAXException {
        Document doc = XMLUtil.parse(input);
        return load(doc);
    }
    
    public JCAXMLFile load(Document doc) throws IOException {
        Element root = doc.getDocumentElement();
        if ("repository".equals(root.getTagName())) {
            AppRepository repo = new AppRepository();
            load(doc, repo);
            
            return new JCAXMLFile(workspace, repo);
        } else if ("app".equals(root.getTagName())) {
            AppInfo info = new AppInfo();
            load(doc, info);
            return new JCAXMLFile(workspace, info);
        } else if ("settings".equals(root.getTagName())) {
            Settings settings = new Settings();
            load(doc, settings);
            return new JCAXMLFile(workspace, settings);
        }  else if ("runtimes".equals(root.getTagName())) {
            CommonRuntimes runtimes = new CommonRuntimes(workspace);
            load(doc, runtimes);
            return new JCAXMLFile(workspace, runtimes);
        } else if ("libraries".equals(root.getTagName())) {
            CommonLibraries libraries = new CommonLibraries(workspace);
            load(doc, libraries);
            return new JCAXMLFile(workspace, libraries);
        } else {
            throw new IllegalArgumentException("Root element of jcaxml file must be either repository or app, but received "+root.getTagName());
        }
    }
    
    
    private void load(Document doc, CommonRuntimes target) throws IOException {
        Element root = doc.getDocumentElement();
        load(root, target);
    }
    
    private void load(Element el, CommonRuntimes target) throws IOException {
        for (Element child : children(el)) {
            if ("runtime".equals(child.getTagName())) {
                CommonRuntime rt = new CommonRuntime();
                load(child, rt);
                target.register(rt);
            }
        }
    }
    
    private void load(Element el, Artifact target) throws IOException {
        if (el.hasAttribute("arch")) {
            target.setArch(el.getAttribute("arch"));
        }
        if (el.hasAttribute("platform")) {
            target.setPlatform(el.getAttribute("platform"));
        }
        if (el.hasAttribute("url")) {
            target.setUrl(new URL(el.getAttribute("url")));
        }
        if (el.hasAttribute("version")) {
            target.setVersion(el.getAttribute("version"));
        }
        if (el.hasAttribute("name")) {
            target.setName(el.getAttribute("name"));
        }
    }
    
    private void load(Document doc, CommonLibraries target) throws IOException {
        Element root = doc.getDocumentElement();
        load(root, target);
    }
    
    private void load(Element el, CommonLibraries target) throws IOException {
        for (Element child : children(el)) {
            if ("library".equals(child.getTagName())) {
                CommonLibrary rt = new CommonLibrary();
                load(child, rt);
                target.register(rt);
            }
        }
    }
    
    private void load(Element el, CommonRuntime target) throws IOException {
        if (el.hasAttribute("arch")) {
            target.setArch(el.getAttribute("arch"));
        }
        if (el.hasAttribute("platform")) {
            target.setPlatform(el.getAttribute("platform"));
        }
        if (el.hasAttribute("url")) {
            target.setUrl(new URL(el.getAttribute("url")));
        }
        if (el.hasAttribute("version")) {
            target.setVersion(el.getAttribute("version"));
        }
        if (el.hasAttribute("fx") && "true".equals(el.getAttribute("fx"))) {
            target.setFx(true);
        } else {
            target.setFx(false);
        }
    }



    
    private void load(Document doc, Settings target) throws IOException {
        Element root = doc.getDocumentElement();
        load(root, target);
    }
    
    private void load(Element el, Settings target) throws IOException {
        for (Element child : children(el)) {
            
            if ("source".equals(child.getTagName())) {
                Settings.Source src = new Settings.Source();
                load(child, src);
                target.getSources(true).add(src);
                continue;
            }
            if ("permissionRule".equals(child.getTagName())) {
                Settings.PermissionRule rule = new Settings.PermissionRule();
                load(child, rule);
                target.getPermissionRules(true).add(rule);
            }
        }
    }
    
    private void load(Element el, Settings.PermissionRule target) throws IOException {
        if (el.hasAttribute("name")) target.setName(el.getAttribute("name"));
        if (el.hasAttribute("target")) target.setTarget(el.getAttribute("target"));
        if (el.hasAttribute("action")) target.setAction(el.getAttribute("action"));
        if (el.hasAttribute("sourceType")) target.setSourceType(Settings.SourceType.valueOf(el.getAttribute("sourceType")));
        if (el.hasAttribute("permissionType")) target.setPermissionType(Settings.PermissionType.valueOf(el.getAttribute("permissionType")));
        
    }
    
    private void load(Element el, Settings.Source target) throws IOException {
        target.setTrust("true".equals(el.getAttribute("trust")));
        if (el.hasAttribute("url")) {
            target.setUrl(url(repositoryURL, el.getAttribute("url")));
        } else {
            throw new IllegalArgumentException("source tag requires url attribute");
        }
    }
    
    private void load(Document doc, AppInfo target) throws IOException {
        Element root = doc.getDocumentElement();
        load(root, target);
    }
    
    private void load(Element el, AppInfo target) throws IOException {
        if (el.hasAttribute("macAppUrl")) target.setMacAppUrl(el.getAttribute("macAppUrl").trim());
        if (el.hasAttribute("windowsAppUrl")) target.setWindowsAppUrl(el.getAttribute("windowsAppUrl").trim());
        if (el.hasAttribute("windowsInstallerUrl")) target.setWindowsInstallerUrl(el.getAttribute("windowsInstallerUrl").trim());
        if (el.hasAttribute("linuxAppUrl")) target.setLinuxAppUrl(el.getAttribute("linuxAppUrl").trim());
        if (el.hasAttribute("linuxInstallerUrl")) target.setLinuxInstallerUrl(el.getAttribute("linuxInstallerUrl").trim());
        if (el.hasAttribute("title")) target.setTitle(el.getAttribute("title").trim());
        if (el.hasAttribute("version")) target.setVersion(el.getAttribute("version").trim());
        if (el.hasAttribute("changes")) target.setChanges(el.getAttribute("changes").trim());
        if (el.hasAttribute("description")) target.setDescription(el.getAttribute("description").trim());
        if (el.hasAttribute("tagline")) target.setTagline(el.getAttribute("tagline").trim());
        if (el.hasAttribute("githubRepositoryUrl")) target.setGithubRepositoryUrl(el.getAttribute("githubRepositoryUrl").trim());
        if (el.hasAttribute("vendor")) target.setVendor(el.getAttribute("vendor").trim());
        if (el.hasAttribute("url")) target.setAppURL(url(repositoryURL, el.getAttribute("url").trim()));
        if (el.hasAttribute("updates")) target.setUpdates(AppInfo.Updates.valueOf(el.getAttribute("updates").trim()));
        if (el.hasAttribute("usePrivateJVM")) target.setUsePrivateJVM(Boolean.parseBoolean(el.getAttribute("usePrivateJVM")));
        if (el.hasAttribute("useBundledJVM")) target.setUseBundledJVM(Boolean.parseBoolean(el.getAttribute("useBundledJVM")));
        if (el.hasAttribute("codeSignSettings")) {
            target.setCodeSignSettings(AppInfo.CodeSignSettings.values()[Integer.parseInt(el.getAttribute("codeSignSettings"))]);
        }
        if (el.hasAttribute("macAppBundleId")) {
            target.setMacAppBundleId(el.getAttribute("macAppBundleId"));
        }
        target.setIcon(url(repositoryURL, "icon.png"));
        if (el.hasAttribute("numScreenshots")) {
            int num = 0;
            try {
                num =Integer.parseInt(el.getAttribute("numScreenshots"));
                for (int i=0; i<num; i++) {
                    target.addScreenshot(url(repositoryURL, "screenshot"+(i+1)+".png"));
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Failed to parse screenshots: "+el.getAttribute("numScreenshots"));
            }     
            target.setNumScreenshots(num);
        }
        
        for (Element child : children(el)) {
            String tagName = child.getTagName();
            if ("description".equals(tagName)) {
                target.setDescription(child.getTextContent());
                continue;
            }
            if ("changes".equals(tagName)) {
                target.setChanges(child.getTextContent());
                continue;
            }
            if ("dependency".equals(tagName)) {
                Dependency dep = new Dependency();
                load(child, dep);
                target.getDependencies(true).add(dep);
                continue;
            }
            if ("jre".equals(tagName)) {
                JRE jre = new JRE();
                load(child, jre);
                target.getRuntimes(true).add(jre);
                continue;
            }
            if ("permission".equals(tagName)) {
                Permission perm = new Permission();
                load(child, perm);
                target.getPermissions(true).add(perm);
                continue;
            }

        }
        
        
    }
    
    private void load(Element el, QuickLink link) throws IOException {
        if (el.hasAttribute("title")) link.setTitle(el.getAttribute("title"));
        else throw new IllegalArgumentException("Link missing title attribute: "+el);
        if (el.hasAttribute("url")) link.setUrl(url(repositoryURL, el.getAttribute("url")));
        else throw new IllegalArgumentException("Link missing url");
    }
    
    private void load(Element el, Permission target) throws IOException {
        if (el.hasAttribute("name")) target.setName(el.getAttribute("name"));
        if (el.hasAttribute("target")) target.setTarget(el.getAttribute("target"));
        if (el.hasAttribute("action")) target.setAction(el.getAttribute("action"));
    }
    
    private void load(Element el, JRE target) throws IOException {
        if (el.hasAttribute("url")) target.setUrl(url(repositoryURL, el.getAttribute("url")));
        if (el.hasAttribute("version")) target.setVersion(el.getAttribute("version"));
        if (el.hasAttribute("os")) target.setOS(el.getAttribute("os"));
        if (el.hasAttribute("arch")) target.setOS(el.getAttribute("arch"));
        if (el.hasAttribute("fx") && "true".equals(el.getAttribute("fx"))) {
            target.setFx(true);
        } else {
            target.setFx(false);
        }
    }
    
    private void load(Element el, Dependency target) throws IOException {
        if (el.hasAttribute("jarName")) target.setJarName(el.getAttribute("jarName"));
        //else throw new IllegalArgumentException("dependency tag requires jarName attribute");
        if (el.hasAttribute("url")) {
            target.setUrl(url(repositoryURL, el.getAttribute("url")));
        } else {
            if (el.hasAttribute("commonName") && el.hasAttribute("version")) {
                target.setCommonName(el.getAttribute("commonName"));
                target.setVersion(el.getAttribute("version"));
            } else {
                throw new IllegalArgumentException("dependency tag requires either a url attribute or both commonName and version attribute . Elment: "+el.toString());
            }
        }
        if (el.hasAttribute("os")) target.setPlatform(el.getAttribute("os"));
        if (el.hasAttribute("arch")) target.setArch(el.getAttribute("arch"));
        
        
        target.setTrusted(Boolean.parseBoolean(el.getAttribute("trusted")));
        
    }
    
    private void load(Document doc, AppRepository target) throws IOException {
        Element root = doc.getDocumentElement();
        load(root, target);
    }
    
    private void load(Element el, AppRepository target) throws IOException {
        if (el.hasAttribute("name")) target.setName(el.getAttribute("name"));
        if (el.hasAttribute("description")) target.setDescription(el.getAttribute("description"));
        if (el.hasAttribute("vendor")) target.setVendor(el.getAttribute("vendor"));
        target.setIcon(url(repositoryURL, "icon.png"));
        if (el.hasAttribute("newsFeed")) {
            target.setNewsFeed(url(repositoryURL, el.getAttribute("newsFeed")));
        }
        
        Category uncategorized = new Category();
        Map<String,Category> categories = new HashMap<String,Category>();
        List<Link> links = new ArrayList<Link>();
        for (Element child : children(el)) {
            if ("app".equals(child.getTagName())) {
                AppRepository.AppLink app = new AppRepository.AppLink();
                load(child, app);
                uncategorized.setName("Uncategorized");
                if (uncategorized.getApps() == null) {
                    uncategorized.setApps(new ArrayList<AppLink>());
                }
                uncategorized.getApps().add(app);
                app.setCategory(uncategorized);
                merge(categories, uncategorized);
            } else if ("category".equals(child.getTagName())) {
                Category cat = new Category();
                load(child, cat);
                merge(categories, cat);
            } else if ("link".equals(child.getTagName())) {
                Link link = new Link();
                load(child, link);
                links.add(link);
            }
        }
        target.setCategories(categories);
        target.setLinks(links);
        
        
        
        
    }
    
    private void load(Element el, Link target) throws IOException {
        if (el.hasAttribute("title")) target.setTitle(el.getAttribute("title"));
        if (el.hasAttribute("title")) target.setUrl(url(repositoryURL, "url"));
    }
    
    private void load(Element el, AppLink target) throws IOException {
        if (el.hasAttribute("name")) target.setName(el.getAttribute("name"));
        if (el.hasAttribute("url")) {
            String ustr = el.getAttribute("url");
            if (!ustr.endsWith(Client4J.APPINFO_EXTENSION)) {
                if (!ustr.endsWith("/")) {
                    ustr += "/";
                }
                ustr += "index"+Client4J.APPINFO_EXTENSION;
            }
            target.setUrl(url(repositoryURL, ustr));
        }
        if (el.hasAttribute("vendor")) target.setVendor(el.getAttribute("vendor"));
        if (el.hasAttribute("version")) target.setVersion(el.getAttribute("version"));
        if (el.hasAttribute("source")) target.setSource(url(repositoryURL, el.getAttribute("source")));
        if (el.hasAttribute("originalSource")) target.setOriginalSource(url(repositoryURL, el.getAttribute("originalSource")));
        if (target.getUrl() != null) {
            target.setIcon(url(target.getUrl(), "icon.png"));
        }
    }
    
    private void load(Element el, Category target) throws IOException {
        if (el.hasAttribute("name")) {
            target.setName(el.getAttribute("name"));
        } else {
            target.setName("Uncategorized");
        }
        List<AppLink> apps = new ArrayList<AppLink>();
        for (Element child : children(el)) {
            if ("app".equals(child.getTagName())) {
                AppRepository.AppLink app = new AppRepository.AppLink();
                load(child, app);
                app.setCategory(target);
                apps.add(app);
            } 
        }
        target.setApps(apps);
        if (el.hasAttribute("icon")) {
            target.setIcon(url(repositoryURL, el.getAttribute("icon")));
        }
        
       
    }
    
    private void merge(Map<String,Category> categories, Category cat) {
        if (categories.containsKey(cat.getName())) {
            Category existing = categories.get(cat.getName());
            if (existing == cat) {
                return;
            }
            if (existing.getIcon() == null && cat.getIcon() != null) {
                existing.setIcon(cat.getIcon());
            }
            List<AppLink> apps = existing.getApps();
            if (apps == null) apps = new ArrayList<AppLink>();
            if (cat.getApps() != null) {
                apps.addAll(cat.getApps());
            }
            for (AppLink app: apps) {
                app.setCategory(existing);
            }
            existing.setApps(apps);
        } else {
            categories.put(cat.getName(), cat);
        }
    }

    
    
}

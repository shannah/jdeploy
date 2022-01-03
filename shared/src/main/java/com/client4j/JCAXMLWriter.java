/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.client4j;

import ca.weblite.jdeploy.app.AppInfo;
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

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;

/**
 *
 * @author shannah
 */
class JCAXMLWriter {
    private URL repositoryURL;
    
    public JCAXMLWriter(URL url) {
        this.repositoryURL = url;
    }
    
    
    public void write(JCAXMLFile file, OutputStream output) throws IOException {
        switch (file.getType()) {
            case App :
                write(file.getAppInfo(), output);
                break;
            case Repository:
                write(file.getRepo(), output);
                break;
            case Settings:
                write(file.getSettings(), output);
                break;

            default:
                throw new IllegalArgumentException("Unsupported JCA file type: "+file.getType()+" Only repo and app supported.");
        }
    }

    public void write(Settings settings, OutputStream output) throws IOException {
        Document doc = XMLUtil.newDocument();
        write(settings, doc);
        XMLUtil.write(doc, output);
    }
    
    private void write(Settings settings, Document doc) throws IOException {
        Element root = doc.createElement("settings");
        doc.appendChild(root);
        write(settings, root);
    }
    
    private void write(Settings settings, Element el) throws IOException {
        if (settings.getSources() != null) {
            for (Settings.Source src : settings.getSources()) {
                Element srcEl = el.getOwnerDocument().createElement("source");
                write(src, srcEl);
                el.appendChild(srcEl);
            }
        }
        if (settings.getPermissionRules() != null) {
            for (Settings.PermissionRule rule : settings.getPermissionRules()) {
                Element permEl = el.getOwnerDocument().createElement("permissionRule");
                write(rule, permEl);
                el.appendChild(permEl);
            }
        }
        
    }
    
    private void write(Settings.PermissionRule rule, Element el) {
        if (rule.getName() != null) {
            el.setAttribute("name", rule.getName());
        }
        if (rule.getTarget() != null) {
            el.setAttribute("target", rule.getTarget());
        }
        if (rule.getAction() != null) {
            el.setAttribute("action", rule.getAction());
        }
        if (rule.getPermissionType() != null) {
            el.setAttribute("permissionType", rule.getPermissionType().toString());
        }
        if (rule.getSourceType() != null) {
            el.setAttribute("sourceType", rule.getSourceType().toString());
        }
    }
    
    private void write(Settings.Source source, Element el) {
        if (source.getUrl() != null) {
            el.setAttribute("url", source.getUrl().toString());
        }
        el.setAttribute("trust", String.valueOf(source.isTrust()));
    }
    
    public void write(CommonRuntimes runtimes, OutputStream output) throws IOException {
        Document doc = XMLUtil.newDocument();
        write(runtimes, doc);
        XMLUtil.write(doc, output);
    }
    
    private void write(CommonRuntimes runtimes, Document doc) throws IOException {
        Element root = doc.createElement("runtimes");
        doc.appendChild(root);
        write(runtimes, root);
    }
    
    private void write(CommonRuntimes runtimes, Element el) throws IOException {
        for (CommonRuntime rt : runtimes) {
            Element rtel = el.getOwnerDocument().createElement("runtime");
            write(rt, rtel);
            el.appendChild(rtel);
        }
    }
    
    private void write(Artifact runtime, Element el) throws IOException {
        if (runtime.getArch() != null) {
            el.setAttribute("arch", runtime.getArch());
        }
        if (runtime.getPlatform() != null) {
            el.setAttribute("platform", runtime.getPlatform());
        }
        if (runtime.getUrl() != null) {
            el.setAttribute("url", runtime.getUrl().toString());
        }
        if (runtime.getVersion() != null) {
            el.setAttribute("version", runtime.getVersion());
        }
        if (runtime.getName() != null) {
            el.setAttribute("name", runtime.getName());
        }
        if (runtime instanceof CommonRuntime) {
            CommonRuntime crt = (CommonRuntime)runtime;
            if (crt.isFx()) {
                el.setAttribute("fx", "true");
            }
        }
    }
    
    public void write(CommonLibraries libraries, OutputStream output) throws IOException {
        Document doc = XMLUtil.newDocument();
        write(libraries, doc);
        XMLUtil.write(doc, output);
    }
    
    private void write(CommonLibraries libraries, Document doc) throws IOException {
        Element root = doc.createElement("libraries");
        doc.appendChild(root);
        write(libraries, root);
    }
    
    private void write(CommonLibraries libraries, Element el) throws IOException {
        for (CommonLibrary rt : libraries) {
            Element rtel = el.getOwnerDocument().createElement("library");
            write(rt, rtel);
            el.appendChild(rtel);
        }
    }
    
    
    public void write(AppInfo app, OutputStream output) throws IOException {
        Document doc = XMLUtil.newDocument();
        write(app, doc);
        XMLUtil.write(doc, output);
    }
    
    private void write(AppInfo app, Document doc) throws IOException {
        Element root = doc.createElement("app");
        doc.appendChild(root);
        write(app, root);
    }
    
    private void write(AppInfo app, Element el) throws IOException {
        if (app.getMacAppUrl() != null) {
            el.setAttribute("macAppUrl", app.getMacAppUrl());
        }
        if (app.getWindowsAppUrl() != null) {
            el.setAttribute("windowsAppUrl", app.getWindowsAppUrl());
        }
        if (app.getWindowsInstallerUrl()!= null) {
            el.setAttribute("windowsInstallerUrl", app.getWindowsInstallerUrl());
        }
        if (app.getLinuxAppUrl() != null) {
            el.setAttribute("linuxAppUrl", app.getLinuxAppUrl());
        }
        if (app.getLinuxInstallerUrl()!= null) {
            el.setAttribute("linuxInstallerUrl", app.getLinuxInstallerUrl());
        }
        if (app.getAppURL() != null) {
            el.setAttribute("url", app.getAppURL().toString());
        }
        if (app.getGithubRepositoryUrl() != null) {
            el.setAttribute("githubRepositoryUrl", app.getGithubRepositoryUrl().toString());
        }
        if (app.getTagline() != null) {
            el.setAttribute("tagline", app.getTagline());
        }
        if (app.getCodeSignSettings() != null) {
            el.setAttribute("codeSignSettings", String.valueOf(app.getCodeSignSettings().ordinal()));
        }
        if (app.getMacAppBundleId() != null) {
            el.setAttribute("macAppBundleId", app.getMacAppBundleId());
        }
        if (app.getTitle() != null) el.setAttribute("title", app.getTitle());
        if (app.getVendor() != null) el.setAttribute("vendor", app.getVendor());
        if (app.getVersion() != null) el.setAttribute("version", app.getVersion());
        el.setAttribute("numScreenshots", String.valueOf(app.getNumScreenshots()));
        Document doc = el.getOwnerDocument();
        if (app.getDescription() != null) {
            Element desc = doc.createElement("description");
            desc.appendChild(doc.createCDATASection(app.getDescription()));
            el.appendChild(desc);
        }
        if (app.getChanges() != null) {
            Element changes = doc.createElement("changes");
            changes.appendChild(doc.createCDATASection(app.getChanges()));
            el.appendChild(changes);
        }
        if (app.getDependencies() != null) {
            for (Dependency dep : app.getDependencies()) {
                Element depEl = doc.createElement("dependency");
                write(dep, depEl);
                el.appendChild(depEl);
            }
        }
        if (app.getRuntimes(false) != null) {
            for (JRE runtime : app.getRuntimes(false)) {
                Element jre = doc.createElement("jre");
                write(runtime, jre);
                el.appendChild(jre);
            }
        }
        if (app.getPermissions() != null) {
            for (Permission perm : app.getPermissions()) {
                Element permEl = doc.createElement("permission");
                write(perm, permEl);
                el.appendChild(permEl);
            }
        }
        if (app.getUpdates() != null) {
            el.setAttribute("updates", app.getUpdates().name());
        }

    }
    
    private void write(AppInfo.QuickLink link, Element el) throws IOException {
        el.setAttribute("title", String.valueOf(link.getTitle()));
        el.setAttribute("url", String.valueOf(link.getUrl()));
    }
    
    private void write(AppInfo.Permission perm, Element el) throws IOException {
        if (perm.getName() != null) {
            el.setAttribute("name", perm.getName());
        }
        if (perm.getTarget() != null) {
            el.setAttribute("target", perm.getTarget());
        }
        if (perm.getAction() != null) {
            el.setAttribute("action", perm.getAction());
        }
    }
    
    private void write(AppInfo.JRE runtime, Element el) throws IOException {
        if (runtime.getUrl() != null) {
            el.setAttribute("url", runtime.getUrl().toString());
        }
        if (runtime.getArch() != null) {
            el.setAttribute("arch", runtime.getArch());
        }
        if (runtime.getOS() != null) {
            el.setAttribute("os", runtime.getOS());
        }
        if (runtime.getVersion() != null) {
            el.setAttribute("version", runtime.getVersion());
        }
        if (runtime.isFx()) {
            el.setAttribute("fx", "true");
        }
    }
    
    private void write(Dependency dep, Element el) throws IOException {
        if (dep.getJarName() != null) {
            el.setAttribute("jarName", dep.getJarName());
        }
        if (dep.getUrl() != null) {
            el.setAttribute("url", dep.getUrl().toString());
        }
        if (dep.getArch() != null) {
            el.setAttribute("arch", dep.getArch());
        }
        if (dep.getPlatform() != null) {
            el.setAttribute("os", dep.getPlatform());
        }
        if (dep.getCommonName() != null) {
            el.setAttribute("commonName", dep.getCommonName());
        }
        if (dep.getVersion() != null) {
            el.setAttribute("version", dep.getVersion());
        }
        
        el.setAttribute("trusted", String.valueOf(dep.isTrusted()));
        
    }
    
    public void write(AppRepository repo, OutputStream output) throws IOException {
        Document doc = XMLUtil.newDocument();
        write(repo, doc);
        XMLUtil.write(doc, output);
    }
    
    private void write(AppRepository repo, Document doc) throws IOException {
        Element root = doc.createElement("repository");
        doc.appendChild(root);
        write(repo, root);
    }
    
    private void write(AppRepository repo, Element el) throws IOException {
        if (repo.getName() != null) {
            el.setAttribute("name", repo.getName());
        }
        if (repo.getVendor() != null) {
            el.setAttribute("vendor", repo.getVendor());
        }
        if (repo.getDescription() != null) {
            el.setAttribute("description", repo.getDescription());
        }
        
        if (repo.getNewsFeed() != null) {
            el.setAttribute("newsFeed", repo.getNewsFeed().toString());
        }
        if (repo.getCategories() != null) {
            for (Category cat : repo.getCategories().values()) {
                Element catEl = el.getOwnerDocument().createElement("category");
                write(repo, cat, catEl);
                el.appendChild(catEl);
            }
        }
        if (repo.getLinks() != null) {
            for (Link link : repo.getLinks()) {
                Element linkEl = el.getOwnerDocument().createElement("link");
                write(repo, link, linkEl);
                el.appendChild(linkEl);
            }
        }
    }
    
    private void write(AppRepository repo, Category cat, Element el) throws IOException {
        if (cat.getName() != null) {
            el.setAttribute("name", cat.getName());
        }
        if (cat.getIcon() != null) {
            el.setAttribute("icon", cat.getIcon().toString());
        }
        for (AppLink app : cat.getApps()) {
            Element appEl = el.getOwnerDocument().createElement("app");
            write(repo, app, appEl);
            el.appendChild(appEl);
        }
        
    }
    
    private void write(AppRepository repo, AppLink app, Element el) throws IOException {
        if (app.getName() != null) {
            el.setAttribute("name", app.getName());
        }
        if (app.getUrl() != null) {
            el.setAttribute("url", app.getUrl().toString());
        }
        if (app.getVendor() != null) {
            el.setAttribute("vendor", app.getVendor());
        }
        
        if (app.getVersion() != null) {
            el.setAttribute("version", app.getVersion());
        }
        if (app.getSource() != null) {
            el.setAttribute("source", app.getSource().toString());
        }
        if (app.getOriginalSource() != null) {
            el.setAttribute("originalSource", app.getOriginalSource().toString());
        }
    }
    
    private void write(AppRepository repo, Link link, Element el) throws IOException {
        if (link.getTitle() != null) {
            el.setAttribute("title", link.getTitle());
        }
        if (link.getUrl() != null) {
            el.setAttribute("url", link.getUrl().toString());
        }
    }
}

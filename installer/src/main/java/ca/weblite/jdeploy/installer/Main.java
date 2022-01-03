package ca.weblite.jdeploy.installer;


import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.appbundler.Bundler;
import ca.weblite.tools.io.FileUtil;
import ca.weblite.tools.io.IOUtil;
import ca.weblite.tools.platform.Platform;
import com.izforge.izpack.util.os.ShellLink;
import net.coobird.thumbnailator.Thumbnails;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import net.sf.image4j.codec.ico.ICOEncoder;

import static ca.weblite.tools.io.IOUtil.copyResourceToFile;


public class Main implements Runnable {

    private Document appXMLDocument;
    private AppInfo appInfo;
    private JFrame frame;
    private boolean addToDesktop, addToPrograms, addToStartMenu, addToDock;

    private Document getAppXMLDocument() throws IOException {
        if (appXMLDocument == null) {
            File appXml = findAppXmlFile();
            if (appXml == null) {
                return null;
            }

            try (FileInputStream fis = new FileInputStream(appXml)) {
                appXMLDocument = parseXml(fis);
            } catch (Exception ex) {
                throw new IOException("Failed tyo parse app.xml: "+ex.getMessage(), ex);
            }
        }
        return appXMLDocument;
    }

    private class InvalidAppXMLFormatException extends IOException {
        InvalidAppXMLFormatException(String message) {
            super("The app.xml file is invalid. "+message);
        }
    }

    private void loadAppInfo() throws IOException {
        File appXml = findAppXmlFile();
        if (appXml == null) {
            throw new IOException("Cannot load app info because the app.xml file could not be found");
        }
        Document doc = getAppXMLDocument();
        if (doc == null) {
            throw new IOException("There was a problem parsing app.xml.");
        }
        Element root = doc.getDocumentElement();
        appInfo = new AppInfo();
        appInfo.setAppURL(appXml.toURI().toURL());
        appInfo.setTitle(ifEmpty(root.getAttribute("title"), root.getAttribute("package"), null));
        appInfo.setNpmPackage(ifEmpty(root.getAttribute("package"), null));
        appInfo.setNpmVersion(ifEmpty(root.getAttribute("version"), "latest"));
        appInfo.setMacAppBundleId(ifEmpty(root.getAttribute("macAppBundleId"), "ca.weblite.jdeploy.apps."+appInfo.getNpmPackage()));

        if (appInfo.getNpmPackage() == null) {
            throw new InvalidAppXMLFormatException("Missing package attribute");
        }
    }

    private static String ifEmpty(String... value) {
        for (String s : value) {
            if (s != null && !s.isEmpty()) return s;
        }
        return null;

    }

    public static void main(String[] args) {

        EventQueue.invokeLater(new Main());
    }


    private File findInstallFilesDir() {
        return findInstallFilesDir(new File(System.getProperty("user.dir")));
    }

    private File findInstallFilesDir(File startDir) {
        if (startDir == null) return null;
        File candidate = new File(startDir, ".jdeploy-files");
        if (candidate.exists() && candidate.isDirectory()) return candidate;
        return findInstallFilesDir(startDir.getParentFile());
    }

    private File findAppXmlFile() {
        File installFilesDir = findInstallFilesDir();
        if (installFilesDir == null) {
            return null;
        }
        File appXml =  new File(installFilesDir, "app.xml");
        if (!appXml.exists()) {
            return null;
        }
        return appXml;


    }

    @Override
    public void run() {
        try {
            run0();
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            JOptionPane.showMessageDialog((Component)null, ex.getMessage(), "Installation failed.", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }




    }

    private void buildUI() {
        frame = new JFrame("Install "+appInfo.getTitle());

        JButton installButton = new JButton("Install");
        installButton.addActionListener(evt->{
           new Thread(()->{
               try {
                   install();
                   EventQueue.invokeLater(()->{
                       JOptionPane.showMessageDialog(frame, "Installation was completed successfully", "Insallation Complete", JOptionPane.INFORMATION_MESSAGE);
                   });
               } catch (Exception ex) {
                   ex.printStackTrace(System.err);
                   EventQueue.invokeLater(()->{
                       JOptionPane.showMessageDialog(frame, "Installation failed. "+ex.getMessage(), "Failed",  JOptionPane.ERROR_MESSAGE);
                   });
               }
           }).start();
        });



        frame.getContentPane().setLayout(new BorderLayout());

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.add(installButton);

        frame.getContentPane().add(buttonsPanel, BorderLayout.SOUTH);

    }

    private void run0() throws Exception {
        loadAppInfo();
        buildUI();
        frame.setLocationRelativeTo(null);
        frame.setMinimumSize(new Dimension(640, 480));
        frame.pack();
        frame.setVisible(true);
    }

    private void install() throws Exception {
        File tmpDest = File.createTempFile("jdeploy-installer-"+appInfo.getNpmPackage(), "");
        tmpDest.delete();
        tmpDest.mkdir();
        File tmpBundles = new File(tmpDest, "bundles");
        File tmpReleases = new File(tmpDest, "releases");
        tmpBundles.mkdir();
        tmpReleases.mkdir();
        String target;
        if (Platform.getSystemPlatform().isMac()) {
            target = "mac";
        } else if (Platform.getSystemPlatform().isWindows()) {
            target = "win";
        } else if (Platform.getSystemPlatform().isLinux()) {
            target = "linux";
        } else {
            throw new RuntimeException("Installation failed.  Your platform is not currently supported.");
        }
        {
            File icon = new File(findAppXmlFile().getParentFile(), "icon.png");
            if (!icon.exists()) {
                copyResourceToFile(Main.class, "icon.png", icon);
            }
        }

        Bundler.runit(appInfo, findAppXmlFile().toURI().toURL().toString(), target, tmpBundles.getAbsolutePath(), tmpReleases.getAbsolutePath());

        if (Platform.getSystemPlatform().isWindows()) {
            File tmpExePath = null;
            for (File exeCandidate : new File(tmpBundles, "win").listFiles()) {
                if (exeCandidate.getName().endsWith(".exe")) {
                    tmpExePath = exeCandidate;
                }
            }
            if (tmpExePath == null) {
                throw new RuntimeException("Failed to find exe file after creation.  Something must have gone wrong in generation process");
            }
            File userHome = new File(System.getProperty("user.home"));
            File jdeployHome = new File(userHome, ".jdeploy");
            File appsDir = new File(jdeployHome, "apps");
            File appDir = new File(appsDir, appInfo.getNpmPackage());
            appDir.mkdirs();
            File exePath = new File(appDir, tmpExePath.getName());
            FileUtil.copy(tmpExePath, exePath);

            // Copy the icon.png if it is present
            File bundleIcon = new File(findAppXmlFile().getParentFile(), "icon.png");
            File iconPath = new File(exePath.getParentFile(), "icon.png");
            if (bundleIcon.exists()) {
                FileUtil.copy(bundleIcon, iconPath);
            }
            installWindowsLinks(exePath);

        } else if (Platform.getSystemPlatform().isMac()) {
            File jdeployAppsDir = new File(System.getProperty("user.home") + File.separator + "Applications" + File.separator + "jDeploy Apps");
            if (!jdeployAppsDir.exists()) {
                jdeployAppsDir.mkdirs();
            }


            File installAppPath = new File(jdeployAppsDir, appInfo.getTitle()+".app");
            File tmpAppPath = null;
            for (File candidateApp : new File(tmpBundles, "mac").listFiles()) {
                System.out.println("Candidate app: "+candidateApp);
                if (candidateApp.getName().endsWith(".app")) {
                    Runtime.getRuntime().exec(new String[]{"mv", candidateApp.getAbsolutePath(), installAppPath.getAbsolutePath()});
                    break;
                }
            }
            if (!installAppPath.exists()) {
                throw new RuntimeException("Failed to copy app to "+jdeployAppsDir);
            }

            if (addToDesktop) {
                File desktopAlias = new File(System.getProperty("user.home") + File.separator + "Desktop" + File.separator + appInfo.getTitle() + ".app");
                Runtime.getRuntime().exec(new String[]{"ln", "-s", installAppPath.getAbsolutePath(), desktopAlias.getAbsolutePath()});
            }

            if (addToDock) {
                /*
                #!/bin/bash
                    myapp="//Applications//System Preferences.app"
                    defaults write com.apple.dock persistent-apps -array-add "<dict><key>tile-data</key><dict><key>file-data</key><dict><key>_CFURLString</key><string>$myapp</string><key>_CFURLStringType</key><integer>0</integer></dict></dict></dict>"
                    osascript -e 'tell application "Dock" to quit'
                    osascript -e 'tell application "Dock" to activate'
                 */
                String[] commands = new String[]{
                        "defaults write com.apple.dock persistent-apps -array-add \"<dict><key>tile-data</key><dict><key>file-data</key><dict><key>_CFURLString</key><string>"+installAppPath.getAbsolutePath()+"</string><key>_CFURLStringType</key><integer>0</integer></dict></dict></dict>\"",
                        "osascript -e 'tell application \"Dock\" to quit'",
                        "osascript -e 'tell application \"Dock\" to activate'"

                };
                for (String cmd : commands) {
                    Runtime.getRuntime().exec(cmd);
                }
            }
        } else if (Platform.getSystemPlatform().isLinux()) {
            File tmpExePath = null;
            for (File exeCandidate : new File(tmpBundles, "linux").listFiles()) {
                tmpExePath = exeCandidate;
            }
            if (tmpExePath == null) {
                throw new RuntimeException("Failed to find launcher file after creation.  Something must have gone wrong in generation process");
            }
            File userHome = new File(System.getProperty("user.home"));
            File jdeployHome = new File(userHome, ".jdeploy");
            File appsDir = new File(jdeployHome, "apps");
            File appDir = new File(appsDir, appInfo.getNpmPackage());
            appDir.mkdirs();
            File exePath = new File(appDir, tmpExePath.getName());
            FileUtil.copy(tmpExePath, exePath);

            // Copy the icon.png if it is present
            File bundleIcon = new File(findAppXmlFile().getParentFile(), "icon.png");
            File iconPath = new File(exePath.getParentFile(), "icon.png");
            if (bundleIcon.exists()) {
                FileUtil.copy(bundleIcon, iconPath);
            }
            installLinuxLinks(exePath);

        }

        File tmpPlatformBundles = new File(tmpBundles, target);

    }

    private void convertWindowsIcon(File srcPng, File destIco) throws IOException, SAXException {
        List<BufferedImage> images = new ArrayList<>();
        for (int i : new int[]{16, 24, 32, 48, 256}) {
            images.add(Thumbnails.of(srcPng).size(i, i).asBufferedImage());
        }
        ICOEncoder.write(images, destIco);

    }

    private static Document parseXml(InputStream input) throws IOException, SAXException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            return builder.parse(input);
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void installWindowsLink(int type, File exePath, File iconPath) throws Exception {
        ShellLink link = new ShellLink(type, appInfo.getTitle());
        //ShellLink link = new ShellLink(exePath.getAbsolutePath(), ShellLink.CURRENT_USER);
        //link.setLinkName(appTitle);
        link.setDescription(appInfo.getDescription());
        link.setIconLocation(iconPath.getAbsolutePath(), 0);
        link.setTargetPath(exePath.getAbsolutePath());
        link.setUserType(ShellLink.CURRENT_USER);


        link.save();
    }

    private void installWindowsLinks(File exePath) throws Exception {
        File pngIconPath = new File(exePath.getParentFile(), "icon.png");
        File icoPath = new File(exePath.getParentFile(), "icon.ico");
        if (!icoPath.exists()) {

            if (!pngIconPath.exists()) {
                copyResourceToFile(Main.class, "icon.png", pngIconPath);
            }
            convertWindowsIcon(pngIconPath, icoPath);
        }

        if (addToDesktop) {
            try {
                installWindowsLink(ShellLink.DESKTOP, exePath, icoPath);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to install desktop shortcut", ex);

            }
        }
        if (addToPrograms) {
            try {
                installWindowsLink(ShellLink.PROGRAM_MENU, exePath, icoPath);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to install program menu shortcut", ex);

            }
        }
        if (addToStartMenu) {
            try {
                installWindowsLink(ShellLink.START_MENU, exePath, icoPath);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to install start menu shortcut", ex);

            }
        }


    }

    private void writeLinuxDesktopFile(File dest, String appTitle, File appIcon, File launcher) throws IOException {
        String contents = IOUtil.readToString(Main.class.getResourceAsStream("LinuxDesktopFileTemplate.desktop"));
        contents = contents
                .replace("{{APP_TITLE}}", appTitle)
                .replace("{{APP_ICON}}", appIcon.getAbsolutePath())
                .replace("{{LAUNCHER_PATH}}", launcher.getAbsolutePath());
        FileUtil.writeStringToFile(contents, dest);
    }

    private void addLinuxDesktopFile(File desktopDir, String filePrefix, String title, File pngIcon, File launcherFile) throws IOException {
        if (desktopDir.exists()) {
            File desktopFile = new File(desktopDir, filePrefix+".desktop");
            while (desktopFile.exists()) {
                int index = 2;
                String baseName = desktopFile.getName();
                if (desktopFile.getName().lastIndexOf(" ") != -1) {
                    String indexStr = desktopFile.getName().substring(desktopFile.getName().lastIndexOf(" ")+1);
                    try {
                        index = Integer.parseInt(indexStr);
                        index++;
                        baseName = desktopFile.getName().substring(0, desktopFile.getName().lastIndexOf(" "));
                    } catch (Throwable t) {
                        index = 2;
                    }
                }
                String newName = baseName + " " + index;
                desktopFile = new File(desktopFile.getParentFile(), newName);
            }
            writeLinuxDesktopFile(desktopFile, title, pngIcon, launcherFile);
            desktopFile.setExecutable(true);

        }



    }

    public void installLinuxLinks(File launcherFile) throws Exception {
        if (!launcherFile.exists()) {
            throw new IllegalStateException("Launcher "+launcherFile+" does not exist so we cannot install a shortcut to it.");
        }

        launcherFile.setExecutable(true, false);

        File pngIcon = new File(launcherFile.getParentFile(), "icon.png");
        if (!pngIcon.exists()) {
            IOUtil.copyResourceToFile(Main.class, "icon.png", pngIcon);

        }

        if (addToDesktop) {
            File desktopDir = new File(System.getProperty("user.home"), "Desktop");
            addLinuxDesktopFile(desktopDir, appInfo.getTitle(), appInfo.getTitle(), pngIcon, launcherFile);
        }
        if (addToPrograms) {
            File homeDir = new File(System.getProperty("user.home"));
            File applicationsDir = new File(homeDir, ".local"+File.separator+"share"+File.separator+"applications");
            applicationsDir.mkdirs();
            addLinuxDesktopFile(applicationsDir, appInfo.getTitle(), appInfo.getTitle(), pngIcon, launcherFile);
        }
    }

}

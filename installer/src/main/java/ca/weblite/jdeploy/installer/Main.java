package ca.weblite.jdeploy.installer;


import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.appbundler.Bundler;
import ca.weblite.jdeploy.installer.npm.NPMPackage;
import ca.weblite.jdeploy.installer.npm.NPMPackageVersion;
import ca.weblite.jdeploy.installer.npm.NPMRegistry;
import ca.weblite.tools.io.FileUtil;
import ca.weblite.tools.io.IOUtil;
import ca.weblite.tools.platform.Platform;
import com.izforge.izpack.util.os.ShellLink;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.TimerTask;

import net.sf.image4j.codec.ico.ICOEncoder;

import static ca.weblite.tools.io.IOUtil.copyResourceToFile;


public class Main implements Runnable {

    private Document appXMLDocument;
    private AppInfo appInfo;
    private JFrame frame;
    private boolean addToDesktop=true, addToPrograms=true, addToStartMenu=true, addToDock=true, prerelease=false;
    private boolean overwriteApp=true;
    private NPMPackageVersion npmPackageVersion;

    private static ResourceBundle strings =  ResourceBundle.getBundle("ca.weblite.jdeploy.installer.Strings");


    private static enum AutoUpdateSettings {
        Stable,
        MinorOnly,
        PatchesOnly,
        Off;

        private String label;

        public void setLabel(String label) {
            this.label = label;
        }


        @Override
        public String toString() {
            if (label != null) return label;
            return strings.getString(this.name());
        }
    };



    private AutoUpdateSettings autoUpdate = AutoUpdateSettings.Stable;

    private Document getAppXMLDocument() throws IOException {
        if (appXMLDocument == null) {
            File appXml = findAppXmlFile();
            if (appXml == null) {
                return null;
            }

            try (FileInputStream fis = new FileInputStream(appXml)) {
                appXMLDocument = parseXml(fis);
            } catch (Exception ex) {
                throw new IOException("Failed to parse app.xml: "+ex.getMessage(), ex);
            }
        }
        return appXMLDocument;
    }

    private void loadNPMPackageInfo() throws IOException {
        if (npmPackageVersion == null) {
            if (appInfo == null) {
                throw new IllegalStateException("App Info must be loaded before loading the package info");
            }
            NPMPackage pkg = new NPMRegistry().loadPackage(appInfo.getNpmPackage());
            if (pkg == null) {
                throw new IOException("Cannot find NPMPackage named "+appInfo.getNpmPackage());
            }
            npmPackageVersion = pkg.getLatestVersion(prerelease, appInfo.getNpmVersion());
            if (npmPackageVersion == null) {
                throw new IOException("Cannot find version "+appInfo.getNpmVersion()+" for package "+appInfo.getNpmPackage());
            }

            // Update labels for the combobox with nice examples to show exactly which versions will be auto-updated
            // to with the given setting.
            AutoUpdateSettings.MinorOnly.setLabel(
                    AutoUpdateSettings.MinorOnly.toString() +
                            " [" +
                            createUserReadableSemVerForVersion(npmPackageVersion.getVersion(), AutoUpdateSettings.MinorOnly) +
                            "]");
            AutoUpdateSettings.PatchesOnly.setLabel(
                    AutoUpdateSettings.PatchesOnly.toString() +
                            " [" +
                            createUserReadableSemVerForVersion(npmPackageVersion.getVersion(), AutoUpdateSettings.PatchesOnly) +
                            "]"
                    );

        }
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

        String installerVersion = ifEmpty(root.getAttribute("version"), "latest");

        // First we set the version in appInfo according to the app.xml file
        appInfo.setNpmVersion(ifEmpty(root.getAttribute("version"), "latest"));
        // Next we use that version to load the package info from the NPM registry.
        loadNPMPackageInfo();


        appInfo.setMacAppBundleId(ifEmpty(root.getAttribute("macAppBundleId"), "ca.weblite.jdeploy.apps."+appInfo.getNpmPackage()));

        if (appInfo.getNpmPackage() == null) {
            throw new InvalidAppXMLFormatException("Missing package attribute");
        }
    }

    private static String createUserReadableSemVerForVersion(String version, AutoUpdateSettings updateSettings) {
        switch (updateSettings) {
            case Stable:
                return "*";
            case MinorOnly: {
                StringBuilder sb = new StringBuilder();

                int pos = version.indexOf("-");
                if (pos > 0) version = version.substring(0, pos);
                pos = version.indexOf(".");
                if (pos < 0) {
                    pos = version.length();
                }

                sb.append(version.substring(0, pos)).append(".*");
                return sb.toString();
            }
            case PatchesOnly: {
                StringBuilder sb = new StringBuilder();

                int pos = version.indexOf("-");
                if (pos > 0) version = version.substring(0, pos);
                pos = version.indexOf(".");

                if (pos < 0) {
                    version += ".0.0";
                    pos = version.indexOf(".");
                }
                int p1 = pos;
                pos = version.indexOf(".", p1 + 1);
                if (pos < 0) {
                    version += ".0";
                    pos = version.indexOf(".", p1 + 1);
                }
                sb.append(version.substring(0, pos)).append(".*");
                return sb.toString();
            }
            case Off:
            default:
                return version;
        }
    }

    private static String createSemVerForVersion(String version, AutoUpdateSettings updateSettings) {
        switch (updateSettings) {
            case Stable:
                return "latest";
            case MinorOnly: {
                StringBuilder sb = new StringBuilder();
                sb.append("^");
                int pos = version.indexOf("-");
                if (pos > 0) version = version.substring(0, pos);
                pos = version.indexOf(".");
                if (pos < 0) {
                    pos = version.length();
                }

                sb.append(version.substring(0, pos));
                return sb.toString();
            }
            case PatchesOnly: {
                StringBuilder sb = new StringBuilder();
                sb.append("~");
                int pos = version.indexOf("-");
                if (pos > 0) version = version.substring(0, pos);
                pos = version.indexOf(".");

                if (pos < 0) {
                    version += ".0.0";
                    pos = version.indexOf(".");
                }
                int p1 = pos;
                pos = version.indexOf(".", p1 + 1);
                if (pos < 0) {
                    version += ".0";
                    pos = version.indexOf(".", p1 + 1);
                }
                sb.append(version.substring(0, pos));
                return sb.toString();
            }
            case Off:
            default:
                return version;
        }

    }

    private static String ifEmpty(String... value) {
        for (String s : value) {
            if (s != null && !s.isEmpty()) return s;
        }
        return null;

    }

    public static void main(String[] args) {
        File logFile = new File(System.getProperty("user.home") + File.separator + ".jdeploy" + File.separator + "log" + File.separator + "jdeploy-installer.log");
        logFile.getParentFile().mkdirs();
        try {
            PrintStream originalOut = System.out;
            System.setOut(new PrintStream(new FileOutputStream(logFile)));
            originalOut.println("Redirecting output to "+logFile);
            System.setErr(System.out);
        } catch (IOException ex) {
            System.err.println("Failed to redirect output to "+logFile);
            ex.printStackTrace(System.err);
        }

        EventQueue.invokeLater(new Main());
    }


    private File findInstallFilesDir() {
        if (System.getProperty("client4j.launcher.path") != null) {
            System.out.println("Found client4.launcher.path property: "+System.getProperty("client4j.launcher.path"));
            return findInstallFilesDir(new File(System.getProperty("client4j.launcher.path")));
        }
        System.out.println("User dir: "+new File(System.getProperty("user.dir")).getAbsolutePath());
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
        frame = new JFrame("Install "+appInfo.getTitle()+" "+npmPackageVersion.getVersion());

        JButton installButton = new JButton("Install");
        installButton.addActionListener(evt->{
           new Thread(()->{
               try {
                   install();
                   EventQueue.invokeLater(()->{
                       String[] options = new String[]{
                            "Open "+appInfo.getTitle(),
                            "Reveal app in "+(Platform.getSystemPlatform().isMac()?"Finder":"Explorer"),
                            "Close"
                       };

                       int choice = JOptionPane.showOptionDialog(frame,
                               "Installation was completed successfully",
                               "Insallation Complete",
                               JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                               null, options, options[0]);
                       switch (choice) {
                           case 0: {
                               if (Platform.getSystemPlatform().isMac()) {
                                   try {
                                       Runtime.getRuntime().exec(new String[]{"open", installedApp.getAbsolutePath()});
                                       java.util.Timer timer = new java.util.Timer();
                                       TimerTask tt = new TimerTask() {

                                           @Override
                                           public void run() {
                                               System.exit(0);
                                           }
                                       };
                                       timer.schedule(tt, 2000);


                                   } catch (Exception ex) {
                                       ex.printStackTrace(System.err);
                                       JOptionPane.showMessageDialog(frame, "Failed to open app: "+ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                                   }

                               } else {
                                   try {
                                       Runtime.getRuntime().exec(new String[]{installedApp.getAbsolutePath()});
                                       java.util.Timer timer = new java.util.Timer();
                                       TimerTask tt = new TimerTask() {

                                           @Override
                                           public void run() {
                                               System.exit(0);
                                           }
                                       };
                                       timer.schedule(tt, 2000);
                                   } catch (Exception ex) {
                                       ex.printStackTrace(System.err);
                                       JOptionPane.showMessageDialog(frame, "Failed to open app: "+ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                                   }
                               }
                               break;
                           }
                           case 1: {

                               if (Desktop.isDesktopSupported()) {
                                   try {
                                       Desktop.getDesktop().open(installedApp.getParentFile());
                                       java.util.Timer timer = new java.util.Timer();
                                       TimerTask tt = new TimerTask() {

                                           @Override
                                           public void run() {
                                               System.exit(0);
                                           }
                                       };
                                       timer.schedule(tt, 2000);
                                   } catch (Exception ex) {
                                       ex.printStackTrace(System.err);
                                       JOptionPane.showMessageDialog(frame, "Failed to open directory: "+ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);

                                   }
                               } else {
                                   JOptionPane.showMessageDialog(frame, "Reveal in explorer is not supported on this platform.", "Not supported", JOptionPane.ERROR_MESSAGE);
                               }
                               break;

                           }
                           default: {
                               java.util.Timer timer = new java.util.Timer();
                               TimerTask tt = new TimerTask() {

                                   @Override
                                   public void run() {
                                       System.exit(0);
                                   }
                               };
                               timer.schedule(tt, 2000);
                           }


                       }
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

        File filesDir = findInstallFilesDir();
        File splash = new File(filesDir, "installsplash.png");
        if (splash.exists()) {
            try {
                ImageIcon splashImage = new ImageIcon(splash.toURI().toURL());
                frame.getContentPane().add(new JLabel(splashImage), BorderLayout.CENTER);
            } catch (Exception ex) {

            }
        }
        String desktopLabel = "Add Desktop Shortcut";
        if (Platform.getSystemPlatform().isMac()) {
            desktopLabel = "Add Desktop Alias";
        }
        JCheckBox desktopCheckbox = new JCheckBox(desktopLabel);
        desktopCheckbox.setSelected(addToDesktop);
        desktopCheckbox.addActionListener(evt->{
            addToDesktop = desktopCheckbox.isSelected();
        });


        JCheckBox addToDockCheckBox = new JCheckBox("Add to Dock");
        addToDockCheckBox.setSelected(addToDock);
        addToDockCheckBox.addActionListener(evt->{
            addToDock = addToDockCheckBox.isSelected();
        });

        JCheckBox addToStartMenuCheckBox = new JCheckBox("Add to Start Menu");
        addToStartMenuCheckBox.setSelected(addToStartMenu);
        addToStartMenuCheckBox.addActionListener(evt->{
            addToStartMenu = addToStartMenuCheckBox.isSelected();
        });

        JPanel checkboxesPanel = new JPanel();
        if (Platform.getSystemPlatform().isWindows()) {
            checkboxesPanel.add(addToStartMenuCheckBox);
        } else if (Platform.getSystemPlatform().isMac()) {
            checkboxesPanel.add(addToDockCheckBox);
        }
        checkboxesPanel.add(desktopCheckbox);
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));


        JComboBox<AutoUpdateSettings> autoUpdateSettingsJComboBox = new JComboBox<>(AutoUpdateSettings.values());
        autoUpdateSettingsJComboBox.setSelectedIndex(0);
        autoUpdateSettingsJComboBox.addItemListener(evt->{
            if (evt.getStateChange() == ItemEvent.SELECTED) {
                autoUpdate = (AutoUpdateSettings) evt.getItem();
            }
        });

        JCheckBox prereleaseCheckBox = new JCheckBox("Prereleases");
        prereleaseCheckBox.setToolTipText("Check this box to automatically update to pre-releases.  Warning: This not recommended unless you are a developer or beta tester of the application as prereleases may be unstable.");
        prereleaseCheckBox.setSelected(prerelease);
        prereleaseCheckBox.addActionListener(evt->{
            prerelease = prereleaseCheckBox.isSelected();
        });


        southPanel.add(checkboxesPanel);

        JPanel updatesPanel = new JPanel();
        updatesPanel.add(new JLabel("Auto Update Settings:"));
        updatesPanel.add(autoUpdateSettingsJComboBox);
        updatesPanel.add(prereleaseCheckBox);
        southPanel.add(updatesPanel);

        southPanel.add(buttonsPanel);

        frame.getContentPane().add(southPanel, BorderLayout.SOUTH);

    }

    private void run0() throws Exception {
        loadAppInfo();
        buildUI();

        frame.setMinimumSize(new Dimension(640, 480));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private File installedApp;

    private void install() throws Exception {

        // Based on the user's settings, let's update the version in the appInfo
        // to correspond with the auto-update settings.
        appInfo.setNpmVersion(createSemVerForVersion(npmPackageVersion.getVersion(), autoUpdate));
        appInfo.setNpmAllowPrerelease(prerelease);

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
            installedApp = exePath;

        } else if (Platform.getSystemPlatform().isMac()) {
            File jdeployAppsDir = new File(System.getProperty("user.home") + File.separator + "Applications" + File.separator + "jDeploy Apps");
            if (!jdeployAppsDir.exists()) {
                jdeployAppsDir.mkdirs();
            }


            File installAppPath = new File(jdeployAppsDir, appInfo.getTitle()+".app");
            if (installAppPath.exists() && overwriteApp) {
                FileUtils.deleteDirectory(installAppPath);
            }
            File tmpAppPath = null;
            for (File candidateApp : new File(tmpBundles, "mac").listFiles()) {
                if (candidateApp.getName().endsWith(".app")) {
                    int result = Runtime.getRuntime().exec(new String[]{"mv", candidateApp.getAbsolutePath(), installAppPath.getAbsolutePath()}).waitFor();
                    if (result != 0) {
                        throw new RuntimeException("Failed to copy app to "+jdeployAppsDir);
                    }
                    break;
                }
            }
            if (!installAppPath.exists()) {
                throw new RuntimeException("Failed to copy app to "+jdeployAppsDir);
            }
            installedApp = installAppPath;

            if (addToDesktop) {
                File desktopAlias = new File(System.getProperty("user.home") + File.separator + "Desktop" + File.separator + appInfo.getTitle() + ".app");
                if (desktopAlias.exists()) {
                    desktopAlias.delete();
                }
                int result = Runtime.getRuntime().exec(new String[]{"ln", "-s", installAppPath.getAbsolutePath(), desktopAlias.getAbsolutePath()}).waitFor();
                if (result != 0) {
                    throw new RuntimeException("Failed to make desktop alias.");
                }
            }

            if (addToDock) {
                /*
                #!/bin/bash
                    myapp="//Applications//System Preferences.app"
                    defaults write com.apple.dock persistent-apps -array-add "<dict><key>tile-data</key><dict><key>file-data</key><dict><key>_CFURLString</key><string>$myapp</string><key>_CFURLStringType</key><integer>0</integer></dict></dict></dict>"
                    osascript -e 'tell application "Dock" to quit'
                    osascript -e 'tell application "Dock" to activate'
                 */
                String myapp = installAppPath.getAbsolutePath().replace('/', '#').replace("#", "//");
                File shellScript = File.createTempFile("installondock", ".sh");
                shellScript.deleteOnExit();

                System.out.println("Adding to dock: "+myapp);
                String[] commands = new String[]{
                        "/usr/bin/defaults write com.apple.dock persistent-apps -array-add \"<dict><key>tile-data</key><dict><key>file-data</key><dict><key>_CFURLString</key><string>"+myapp+"</string><key>_CFURLStringType</key><integer>0</integer></dict></dict></dict>\"",
                        "/usr/bin/osascript -e 'tell application \"Dock\" to quit'",
                        "/usr/bin/osascript -e 'tell application \"Dock\" to activate'"

                };
                try (PrintWriter printWriter = new PrintWriter(new FileOutputStream(shellScript))) {
                    printWriter.println("#!/bin/bash");
                    for (String cmd : commands) {
                        printWriter.println(cmd);
                    }
                }
                shellScript.setExecutable(true, false);
                Runtime.getRuntime().exec(shellScript.getAbsolutePath());
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
            installedApp = exePath;

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

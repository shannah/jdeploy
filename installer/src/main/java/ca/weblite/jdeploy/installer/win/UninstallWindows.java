package ca.weblite.jdeploy.installer.win;

import ca.weblite.tools.io.MD5;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class UninstallWindows {

    private String packageName;

    private String fullyQualifiedPackageName;

    private String version;
    private String appTitle;
    private InstallWindowsRegistry installWindowsRegistry;
    private String source;

    private String appFileName;

    public UninstallWindows(String packageName, String source, String version, String appTitle, InstallWindowsRegistry installer) {
        this.packageName = packageName;
        this.source = source;
        this.version = version;
        this.appTitle = appTitle;
        this.installWindowsRegistry = installer;
        this.fullyQualifiedPackageName = installer.getFullyQualifiedPackageName();
        this.appFileName = this.appTitle;
        if (version != null && version.startsWith("0.0.0-")) {
            this.appFileName += " " + version.substring(version.indexOf("-")+1);
        }
    }

    private File getJDeployHome() {
        return new File(System.getProperty("user.home") + File.separator + ".jdeploy");
    }

    private File getPackagePath() {
        if (source == null || source.isEmpty()) {
            if (version == null) {
                return new File(getJDeployHome(), "packages" + File.separator + new File(packageName).getName());
            } else {
                return new File(getJDeployHome(), "packages" + File.separator + new File(packageName).getName() + File.separator + new File(version).getName());
            }
        }

        if (version == null) {
            return new File(getJDeployHome(), "gh-packages" + MD5.getMd5(source) + "." + packageName);
        } else {
            return new File(getJDeployHome(), "gh-packages" + MD5.getMd5(source) + "." + packageName + File.separator + new File(version).getName());
        }
    }

    private File getStartMenuPath() {
        return new File(System.getProperty("user.home") + File.separator +
                "AppData" + File.separator +
                "Roaming" + File.separator +
                "Microsoft" + File.separator +
                "Windows" + File.separator +
                "Start Menu");
    }

    private File getStartMenuLink() {
        return new File(getStartMenuPath(), new File(appFileName).getName() + ".lnk");
    }

    private File getProgramsMenuPath() {
        return new File(getStartMenuPath(), "Programs");
    }

    private File getProgramsMenuLink() {
        return new File(getProgramsMenuPath(), new File(appFileName).getName() + ".lnk");
    }

    private File getDesktopLink() {
        return new File(System.getProperty("user.home") + File.separator + "Desktop" + File.separator + new File(appFileName).getName() + ".lnk");
    }

    private File getAppDirPath() {
        return new File(getJDeployHome(), "apps" + File.separator + new File(fullyQualifiedPackageName).getName());
    }

    private File getUninstallerPath() {

        String suffix = "";
        if (version != null && version.startsWith("0.0.0-")) {
            suffix = "-" + version.substring(version.indexOf("-")+1);
        }
        return new File(getJDeployHome(),
                "uninstallers" + File.separator +
                        new File(fullyQualifiedPackageName).getName() + File.separator +
                        new File(packageName)+suffix+"-uninstall.exe");
    }

    private Iterable<File> getVersionDirectories() {
        List<File> out = new ArrayList<>();
        if (version == null && getPackagePath().exists())  {
            for (File child : getPackagePath().listFiles()) {
                if (child.isDirectory() && !child.getName().isEmpty() && Character.isDigit(child.getName().charAt(0))) {
                    out.add(child);
                }
            }

        } else {
            if (getPackagePath().exists()) {
                out.add(getPackagePath());
            }
        }
        return out;
    }

    private void deletePackage() throws IOException {
        for (File versionDir : getVersionDirectories()) {
            if (versionDir.exists()) {
                FileUtils.deleteDirectory(versionDir);
            }
        }
    }

    private void deleteApp() throws IOException {
        if (getAppDirPath().exists()) {
            FileUtils.deleteDirectory(getAppDirPath());
        }
    }

    private void scheduleDeleteUninstaller() throws IOException {
        //cmd.exe /C TIMEOUT 10 && del "{your uninstaller path}"
        Runtime.getRuntime().exec("cmd.exe /C TIMEOUT 5 && rd /s /q \""+getUninstallerPath().getParentFile().getAbsolutePath()+"\"");
        //Runtime.getRuntime().exec(new String[]{"cmd.exe", "/C", "TIMEOUT", "5", )
    }




    private void removeDesktopAlias() {
        if (getDesktopLink().exists()) {
            getDesktopLink().delete();
        }
    }

    private void removeStartMenuLink() {
        if (getStartMenuLink().exists()) {
            getStartMenuLink().delete();
        }
    }

    private void removeProgramsMenuLink() {
        if (getProgramsMenuLink().exists()) {
            getProgramsMenuLink().delete();
        }
    }


    public void uninstall() throws IOException {
        removeDesktopAlias();
        removeProgramsMenuLink();
        removeStartMenuLink();
        deletePackage();
        deleteApp();
        installWindowsRegistry.unregister(null);
        File uninstallerJDeployFiles = new File(getUninstallerPath().getParentFile(), ".jdeploy-files");
        if (uninstallerJDeployFiles.exists()) {
            FileUtils.deleteDirectory(uninstallerJDeployFiles);
        }
        scheduleDeleteUninstaller();

    }







}

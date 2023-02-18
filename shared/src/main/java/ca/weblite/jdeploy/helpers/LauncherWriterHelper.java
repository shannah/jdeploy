package ca.weblite.jdeploy.helpers;

import ca.weblite.jdeploy.appbundler.AppDescription;
import ca.weblite.jdeploy.appbundler.Bundler;
import ca.weblite.tools.io.IOUtil;
import com.joshondesign.appbundler.win.WindowsBundler2;
import com.joshondesign.xml.XMLWriter;

import java.io.*;

public class LauncherWriterHelper {
    public void writeLauncher(AppDescription app, File destFile, InputStream launcherInput) throws Exception {

        try (FileOutputStream fos = new FileOutputStream(destFile)) {
            IOUtil.copy(launcherInput, new FileOutputStream(destFile));
        }
        long origSize = destFile.length();
        File appXml = new File(destFile.getParentFile(), "app.xml");
        processAppXml(app, appXml);
        try (FileOutputStream fos = new FileOutputStream(destFile, true)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (FileInputStream fis = new FileInputStream(appXml)) {
                IOUtil.copy(fis, baos);
            }
            fos.write(invertBytes(baos.toByteArray()));
            byte[] bytes = invertBytes(String.valueOf(origSize).getBytes("UTF-8"));


            // Record the position of the start of the data file
            // As a UTF-8 string
            fos.write(bytes);

            // Record the length of the position string
            // When we read this from golang, we will walk backwards.
            // First read the last byte of the file which will tell us
            // where to start reading the position string from.
            // Then read the position string, convert it to a long,
            // and then we can read the data file from that position
            // in the exe

            fos.write(bytes.length);
        }

        destFile.setExecutable(true, false);
        appXml.delete();

    }

    private static void processAppXml(AppDescription app, File dest) throws Exception {
        p("Processing the app.xml file");
        XMLWriter out = new XMLWriter(dest);
        out.header();

        if (app.getNpmPackage() != null && app.getNpmVersion() != null) {

            out.start("app",
                    "name", app.getName(),
                    "package", app.getNpmPackage(),
                    "source", app.getNpmSource(),
                    "version", app.getNpmVersion(),
                    "icon", app.getIconDataURI(),
                    "prerelease", app.isNpmPrerelease()+"",
                    "fork", ""+app.isFork()
            ).end();
        } else {
            out.start("app", "name", app.getName(), "url", app.getUrl(), "icon", app.getIconDataURI()).end();
        }
        out.close();
    }

    private static void p(String s) {
        System.out.println(s);
    }

    // Because windows and chrome think that exes with base64 appended is a virus,
    // trying to make the bytes not base64.
    private byte[] invertBytes(byte[] bytes) {
        int len = bytes.length;
        byte[] out = new byte[len];
        for (int i=0; i<len; i++) {
            out[i] = (byte) (255 - bytes[i]);
        }

        return out;
    }
}

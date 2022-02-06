package ca.weblite.jdeploy.installer.linux;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

public class MimeTypeHelper {

    private File getSystemMimeFile(String mimetype) {
        return new File("/usr/share/mime/"+mimetype+".xml");
    }

    private File getUserMimeFile(String mimetype) {
        return new File(getUserMimeDirectory(), mimetype+".xml");
    }

    private File getUserMimeDirectory() {
        return new File(System.getProperty("user.home")+"/share/mime");
    }

    public boolean isInstalled(String mimetype) {
        return getSystemMimeFile(mimetype).exists()
                || getUserMimeFile(mimetype).exists();
    }

    private String escapeXml(String str) {
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void validateMimetype(String mimetype) {
        if (mimetype == null || !mimetype.contains("/")) {
            throw new RuntimeException("Invalid mimetype: "+mimetype);
        }
        String[] parts = mimetype.split("/");
        if (parts.length != 2) {
            throw new RuntimeException("Invalid mimetype: "+mimetype);
        }
        for (String part : parts) {
            if (part.isEmpty() || part.startsWith(".") || !part.matches("^[a-z0-9A-Z\\-_\\.]+$")) {
                throw new RuntimeException("Invalid mimetype: "+mimetype);
            }
        }
    }

    public void install(String mimetype, String programName, String... extensions) throws IOException {
        if (isInstalled(mimetype)) return;
        validateMimetype(mimetype);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\"?>\n" +
                "           <mime-info xmlns='http://www.freedesktop.org/standards/shared-mime-info'>\n" +
                "             <mime-type type=\"");

        sb.append(escapeXml(mimetype)).append("\">\n" +
                "               <comment>"+escapeXml(programName)+" Document</comment>\n");
        for (String extension : extensions) {
            sb.append("               <glob pattern=\"*.").append(escapeXml(extension)).append("\"/>\n");
        }
        sb.append("             </mime-type>\n" +
                "           </mime-info>");

        File temp = File.createTempFile("jdeploy-mime-"+mimetype, ".xml");
        temp.deleteOnExit();

        FileUtils.writeStringToFile(temp, sb.toString(), "UTF-8");
        // https://unix.stackexchange.com/questions/564816/how-to-install-a-new-custom-mime-type-on-my-linux-system-using-cli-tools
        {
            Process p = Runtime.getRuntime().exec(new String[]{"xdg-mime", "install", "--mode", "user", temp.getAbsolutePath()});
            try {
                int result = p.waitFor();
                if (result != 0) {
                    throw new IOException("Failed to install mimetype " + mimetype + " with xdg-mime.  Exit code " + result);
                }
            } catch (InterruptedException ex) {
                throw new IOException("xdg-mime was interrupted.  Failed to install mimetype " + mimetype, ex);
            }
        }

        {
            Process p = Runtime.getRuntime().exec(new String[]{"update-mime-database", getUserMimeDirectory().getAbsolutePath()});
            try {
                int result = p.waitFor();
                if (result != 0) {
                    throw new IOException("Failed to install mimetype " + mimetype + " with update-mime-database.  Exit code " + result);
                }
            } catch (InterruptedException ex) {
                throw new IOException("update-mime-database was interrupted.  Failed to install mimetype " + mimetype, ex);
            }
        }



    }
}

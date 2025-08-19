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

    public boolean isInstalled(String mimetype, String... extensions) {
        java.util.List<String> extensionsToTest = new java.util.ArrayList<>();
        
        // Add provided extensions
        for (String ext : extensions) {
            extensionsToTest.add(ext);
        }
        
        // Always add the portion after / in mimetype as fallback extension
        String[] parts = mimetype.split("/");
        if (parts.length == 2 && !parts[1].isEmpty()) {
            extensionsToTest.add(parts[1]);
        }
        
        // Test each extension with xdg-mime using actual temporary files
        for (String extension : extensionsToTest) {
            try {
                File tempFile = File.createTempFile("xdg-mime-test", "." + extension);
                tempFile.deleteOnExit();
                
                Process p = Runtime.getRuntime().exec(new String[]{"xdg-mime", "query", "filetype", tempFile.getAbsolutePath()});
                p.waitFor();
                
                // Read the output to see if it matches our mimetype
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                String detectedMimetype = reader.readLine();
                reader.close();
                
                tempFile.delete();
                
                if (mimetype.equals(detectedMimetype)) {
                    return true;
                }
            } catch (Exception ex) {
                System.err.println("Warning: Failed to query MIME type for extension ." + extension + ": " + ex.getMessage());
            }
        }
        
        // Fallback to file-based check
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

    public void install(String mimetype, String programName, String... extensions) {
        if (isInstalled(mimetype, extensions)) {
            System.err.println("Warning: MIME type " + mimetype + " is already installed, skipping installation");
            return;
        }
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

        File temp;
        try {
            temp = File.createTempFile("jdeploy-mime-"+mimetype, ".xml");
            temp.deleteOnExit();
            FileUtils.writeStringToFile(temp, sb.toString(), "UTF-8");
        } catch (IOException ex) {
            System.err.println("Warning: Failed to create temporary MIME type file for " + mimetype + ": " + ex.getMessage());
            return;
        }

        // Determine if we need --novendor flag
        boolean isVendorType = mimetype.startsWith("application/vnd");
        String[] xdgMimeCmd = isVendorType ? 
            new String[]{"xdg-mime", "install", "--mode", "user", temp.getAbsolutePath()} :
            new String[]{"xdg-mime", "install", "--mode", "user", "--novendor", temp.getAbsolutePath()};

        // https://unix.stackexchange.com/questions/564816/how-to-install-a-new-custom-mime-type-on-my-linux-system-using-cli-tools
        try {
            Process p = Runtime.getRuntime().exec(xdgMimeCmd);
            try {
                int result = p.waitFor();
                if (result != 0) {
                    System.err.println("Warning: xdg-mime command failed with exit code " + result + " for mimetype " + mimetype);
                    return;
                }
            } catch (InterruptedException ex) {
                System.err.println("Warning: xdg-mime command was interrupted for mimetype " + mimetype);
                return;
            }
        } catch (IOException ex) {
            System.err.println("Warning: Failed to execute xdg-mime command for mimetype " + mimetype + ": " + ex.getMessage());
            return;
        }

        try {
            Process p = Runtime.getRuntime().exec(new String[]{"update-mime-database", getUserMimeDirectory().getAbsolutePath()});
            try {
                int result = p.waitFor();
                if (result != 0) {
                    System.err.println("Warning: update-mime-database command failed with exit code " + result + " for mimetype " + mimetype);
                    return;
                }
            } catch (InterruptedException ex) {
                System.err.println("Warning: update-mime-database command was interrupted for mimetype " + mimetype);
                return;
            }
        } catch (IOException ex) {
            System.err.println("Warning: Failed to execute update-mime-database command for mimetype " + mimetype + ": " + ex.getMessage());
            return;
        }



    }
}

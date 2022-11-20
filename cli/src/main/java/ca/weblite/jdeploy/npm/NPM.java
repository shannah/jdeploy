package ca.weblite.jdeploy.npm;

import ca.weblite.jdeploy.JDeploy;
import ca.weblite.tools.io.IOUtil;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NPM {
    private static final String REGISTRY_URL="https://registry.npmjs.org/";
    private static final String GITHUB_URL = "https://github.com/";

    private Process pendingLoginProcess;
    private PrintStream out=System.out, err=System.err;
    static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
    static String npm = isWindows() ? "npm.cmd" : "npm";

    public NPM(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public void cancelLogin() {
        if (pendingLoginProcess != null && pendingLoginProcess.isAlive()) {
            pendingLoginProcess.destroyForcibly();
            pendingLoginProcess = null;
        }
    }

    public JSONObject fetchPackageInfoFromNpm(String packageName, String source) throws IOException {
        URL u = new URL(getPackageUrl(packageName, source));
        HttpURLConnection conn = (HttpURLConnection)u.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setUseCaches(false);
        if (conn.getResponseCode() != 200) {
            throw new IOException("Failed to fetch Package info for package "+packageName+". "+conn.getResponseMessage());
        }
        return new JSONObject(IOUtil.readToString(conn.getInputStream()));
    }

    public boolean isVersionPublished(String packageName, String version, String source) {
        try {
            JSONObject jsonObject = fetchPackageInfoFromNpm(packageName, source);
            return jsonObject.has("versions") && jsonObject.getJSONObject("versions").has(version);
        } catch (Exception ex) {
            return false;
        }
    }

    private static void pipe(InputStream is, OutputStream os) throws IOException {
        int n;
        byte[] buffer = new byte[1024];
        while ((n = is.read(buffer)) > -1) {
            os.write(buffer, 0, n);   // Don't allow any extra bytes to creep in, final write
            os.flush();
        }
        //os.close();
    }

    public static interface OTPCallback {
        public String getOTPPassword();
    }

    public static class LoginTimeoutException extends IOException {
        public LoginTimeoutException() {

        }
    }

    public boolean login(String username, String password, String email, OTPCallback otpCallback) throws IOException{
        final boolean[] timeout = new boolean[1];
        try {


            ProcessBuilder pb = new ProcessBuilder();

            pb.command(npm, "login");
            final Process p = pb.start();
            pendingLoginProcess = p;
            final boolean[] usernameEntered = new boolean[]{false};
            final boolean[] passwordEntered = new boolean[]{false};
            final boolean[] emailEntered = new boolean[]{false};

            Timer timer = new Timer();

            timer.schedule(new TimerTask() {

                @Override
                public void run() {
                    if (!usernameEntered[0] || !passwordEntered[0] || !emailEntered[0]) {
                        timeout[0] = true;
                        p.destroyForcibly();
                    }
                }
            }, 5000);

            Scanner input = new Scanner(p.getInputStream(), "UTf-8");
            PrintStream output = new PrintStream(p.getOutputStream());


            while (input.hasNext()) {
                String line = input.next();
                //System.out.println("line="+line);
                if (!usernameEntered[0] && line.toLowerCase().equals("username:")) {
                    usernameEntered[0] = true;
                    output.println(username);
                    output.flush();
                    //input.nextLine();
                } else if (!passwordEntered[0] && line.toLowerCase().equals("password:")) {
                    passwordEntered[0] = true;
                    output.println(password);
                    output.flush();
                    //input.nextLine();
                } else if (!emailEntered[0] && line.toLowerCase().startsWith("email:")) {
                    emailEntered[0] = true;
                    output.println(email);
                    output.flush();
                    //input.nextLine();
                } else if (usernameEntered[0] && passwordEntered[0] && emailEntered[0] && line.toLowerCase().contains("password:")) {
                    output.println(otpCallback.getOTPPassword());
                    output.flush();
                    //input.nextLine();
                    output.close();
                    timer.schedule(new TimerTask() {
                        public void run() {
                            if (p.isAlive()) {
                                timeout[0] = true;
                                p.destroyForcibly();
                            }
                        }
                    }, 10000);
                } else if (emailEntered[0] && line.toLowerCase().startsWith("email:")) {
                    output.close();
                    throw new IOException("Email address was invalid");
                }
            }



            int result = p.waitFor();
            if (result != 0) {
               return false;
            }
            return true;
        } catch (InterruptedException ex) {
            if (timeout[0]) {
                throw new LoginTimeoutException();
            }
            Logger.getLogger(JDeploy.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }

    public void publish(File publishDir, boolean exitOnFail) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.directory(publishDir);
            if (out == System.out) {
                pb.inheritIO();
            }
            pb.command(npm, "publish");
            Process p = pb.start();
            if (out != System.out) {
                new Thread(()->{
                    try {
                        pipe(p.getInputStream(), out);
                    } catch (Exception ex){
                        ex.printStackTrace(System.err);
                    }
                }).start();
            }
            if (err != System.err) {
                new Thread(()->{
                    try {
                        pipe(p.getErrorStream(), err);
                    } catch (Exception ex){
                        ex.printStackTrace(System.err);
                    }
                }).start();
            }
            int result = p.waitFor();
            if (result != 0) {
                if (exitOnFail) {
                    System.exit(result);
                } else {
                    throw new JDeploy.FailException("npm publish command failed.  Please ensure npm is installed and in your PATH.", result);
                }
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(JDeploy.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }

    public void pack(File publishDir, File outputDir, boolean exitOnFail) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.directory(publishDir);
            if (out == System.out) {
                pb.inheritIO();
            }
            out.println("Running npm pack --pack-destination" + outputDir);
            pb.command(npm, "pack", "--pack-destination", outputDir.getAbsolutePath());
            Process p = pb.start();
            if (out != System.out) {
                new Thread(()->{
                    try {
                        pipe(p.getInputStream(), out);
                    } catch (Exception ex){
                        ex.printStackTrace(System.err);
                    }
                }).start();
            }
            if (err != System.err) {
                new Thread(()->{
                    try {
                        pipe(p.getErrorStream(), err);
                    } catch (Exception ex){
                        ex.printStackTrace(System.err);
                    }
                }).start();
            }
            int result = p.waitFor();
            if (result != 0) {
                if (exitOnFail) {
                    System.exit(result);
                } else {
                    throw new JDeploy.FailException("npm pack command failed.  Please ensure npm is installed and in your PATH.", result);
                }
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(JDeploy.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }

    public void link(boolean exitOnFail) throws IOException  {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.inheritIO();
            pb.command(npm, "link");
            Process p = pb.start();
            int result = p.waitFor();
            if (result != 0) {
                if (exitOnFail) {
                    System.exit(result);
                } else {
                    throw new JDeploy.FailException("Failed to run npm link.  Please ensure npm is installed and in your PATH.", result);
                }
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(JDeploy.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }

    public boolean isLoggedIn()  {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            //pb.inheritIO();
            pb.command(npm, "whoami");
            Process p = pb.start();
            int result = p.waitFor();
            if (result != 0) {
                return false;
            }
        } catch (Exception ex) {
            return false;
        }
        return true;
    }

    private String getPackageUrl(String packageName, String source) throws UnsupportedEncodingException {
        if (source.startsWith(GITHUB_URL)) {
            String[] parts = packageName.split("/");
            return GITHUB_URL +
                    URLEncoder.encode(parts[1], "UTF-8") + "/" +
                    URLEncoder.encode(parts[2], "UTF-8") + "/releases/download/jdeploy/package-info.json";
        } else {
            return REGISTRY_URL+ URLEncoder.encode(packageName, "UTF-8");
        }
    }
}

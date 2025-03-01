package ca.weblite.jdeploy.npm;

import ca.weblite.jdeploy.JDeploy;
import ca.weblite.tools.io.IOUtil;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
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

    private final boolean useManagedNode;

    private String npmToken;

    private NPMManager npmManager;
    public NPM(PrintStream out, PrintStream err) {
        this(out, err, false);
    }

    public NPM(PrintStream out, PrintStream err, boolean useManagedNode) {
        this.out = out;
        this.err = err;
        this.useManagedNode = useManagedNode;
        if (useManagedNode) {
            npmManager = new NPMManager();
        }
    }

    public void setNpmToken(String npmToken) {
        this.npmToken = npmToken;
    }

    public String getNpmToken() {
        return npmToken;
    }

    public boolean isUseManagedNode() {
        return useManagedNode;
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

    private void pipe(InputStream input, StringBuilder builder) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append(System.lineSeparator());
                out.println(line); // Echoing to console
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static interface OTPCallback {
        public String getOTPPassword();
    }

    public static class LoginTimeoutException extends IOException {
        public LoginTimeoutException() {

        }
    }

    public void startInteractiveLogin() throws IOException {
        try {
            ProcessBuilder pb;
            Map<String,String> env = new HashMap<String,String>();
            if (useManagedNode) {
                npmManager.install();
                pb = npmManager.npmExecBuilder(env, null, new String[]{"login"});
            } else {
                pb = new ProcessBuilder();
                pb.environment().putAll(env);
                pb.command(npm, "login");
            }
            pb.inheritIO();
            Process p = pb.start();

            int result = p.waitFor();
            if (result != 0) {
                throw new JDeploy.FailException("Failed to run npm login.  Please ensure npm is installed and in your PATH.", result);
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(JDeploy.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }

    public boolean login(String username, String password, String email, OTPCallback otpCallback) throws IOException{
        final boolean[] timeout = new boolean[1];
        try {


            ProcessBuilder pb;
            if (useManagedNode) {
                npmManager.install();
                pb = npmManager.npmExecBuilder(new HashMap<>(), null, new String[]{"login"});
            } else {
                pb = new ProcessBuilder();
                pb.command(npm, "login");
            }
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

    public void publish(
            File publishDir,
            boolean exitOnFail,
            String oneTimePassword
    ) throws IOException, OneTimePasswordRequestedException {
        try {
            ProcessBuilder pb;
            if (useManagedNode) {
                npmManager.install();
                if (oneTimePassword != null) {
                    pb = npmManager.npmExecBuilder(getEnvironment(), publishDir, new String[]{"publish", "--otp=" + oneTimePassword});
                } else {
                    pb = npmManager.npmExecBuilder(getEnvironment(), publishDir, new String[]{"publish"});
                }
            } else {
                pb = new ProcessBuilder();
                pb.environment().putAll(getEnvironment());
                pb.directory(publishDir);
                if (oneTimePassword != null) {
                    pb.command(npm, "publish", "--otp=" + oneTimePassword);
                } else {
                    pb.command(npm, "publish");
                }
            }

            out.println("Using command " + pb.command());
            Process p = pb.start();
            // Capture stdout/stderr
            StringBuilder outputBuilder = new StringBuilder();
            StringBuilder errorBuilder = new StringBuilder();

            Thread stdoutThread = new Thread(() -> pipe(p.getInputStream(), outputBuilder));
            Thread stderrThread = new Thread(() -> pipe(p.getErrorStream(), errorBuilder));

            stdoutThread.start();
            stderrThread.start();

            int result = p.waitFor();
            stdoutThread.join();
            stderrThread.join();

            if (result != 0) {
                if (oneTimePassword == null && isOtpRequired("" + outputBuilder + errorBuilder)) {
                    throw new OneTimePasswordRequestedException();
                }
                if (exitOnFail) {
                    System.exit(result);
                } else {
                    throw new JDeploy.FailException(
                            "npm publish command failed.  Please ensure npm is installed and in your PATH.",
                            result
                    );
                }
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(JDeploy.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }

    public void pack(File publishDir, File outputDir, boolean exitOnFail) throws IOException {
        try {
            ProcessBuilder pb;
            if (useManagedNode) {
                npmManager.install();
                pb = npmManager.npmExecBuilder(
                        new HashMap<>(),
                        publishDir,
                        new String[]{"pack", "--pack-destination", outputDir.getAbsolutePath()}
                );
            } else {

                pb = new ProcessBuilder();
                pb.directory(publishDir);

                pb.command(npm, "pack", "--pack-destination", outputDir.getAbsolutePath());
            }
            if (out == System.out) {
                pb.inheritIO();
            }
            out.println("Running npm pack --pack-destination" + outputDir);
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
            ProcessBuilder pb;
            if (useManagedNode) {
                npmManager.install();
                pb = npmManager.npmExecBuilder(getEnvironment(), null, new String[]{"link"});
            } else {
                pb = new ProcessBuilder();
                pb.environment().putAll(getEnvironment());
                pb.command(npm, "link");
            }
            pb.inheritIO();

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

    public boolean isLoggedIn() {
        if (npmToken != null) {
            return true;
        }
        Path npmrcPath = Paths.get(System.getProperty("user.home"), ".npmrc");
        if (Files.exists(npmrcPath)) {
            List<String> lines = null;
            try {
                lines = Files.readAllLines(npmrcPath);
            } catch (IOException e) {
                return false;
            }
            for (String line : lines) {
                if (line.startsWith("//registry.npmjs.org/:_authToken=")) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getPackageUrl(String packageName, String source) throws UnsupportedEncodingException {
        if (source.startsWith(GITHUB_URL)) {
            return source + "/releases/download/jdeploy/package-info.json";
        } else {
            return REGISTRY_URL+ URLEncoder.encode(packageName, "UTF-8");
        }
    }

    private Map<String,String> getEnvironment() {
        Map<String,String> env = new HashMap<String,String>();
        if (npmToken != null) {
            env.put("NPM_TOKEN", npmToken);
        }
        return env;
    }

    private boolean isOtpRequired(String npmOutput) {
        return npmOutput.toLowerCase().contains("one-time password")
                || npmOutput.toLowerCase().contains("otp code")
                || npmOutput.contains("npm ERR! code EOTP");
    }
}

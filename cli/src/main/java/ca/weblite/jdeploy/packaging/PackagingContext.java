package ca.weblite.jdeploy.packaging;

import ca.weblite.jdeploy.JDeploy;
import ca.weblite.jdeploy.factories.JDeployKeyProviderFactory;
import ca.weblite.jdeploy.services.PackageSigningService;
import ca.weblite.tools.security.KeyProvider;
import com.codename1.io.JSONParser;
import com.codename1.processing.Result;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PackagingContext {
    public final File directory;
    public final Map packageJsonMap;
    public final File packageJsonFile;

    public final boolean alwaysClean;

    private Result packageJsonResult;
    public final boolean doNotStripJavaFXFiles;

    private Set<String> bundlesOverride;

    private Set<String> installersOverride;

    public final KeyProvider keyProvider;

    public final PackageSigningService packageSigningService;

    public final PrintStream out;

    public final PrintStream err;

    public final boolean exitOnFail;

    public static Builder builder() {
        return new Builder();
    }

    public PackagingContext(
            File directory,
            Map packageJsonMap,
            File packageJsonFile,
            boolean alwaysClean,
            boolean doNotStripJavaFXFiles,
            Set<String> bundlesOverride,
            Set<String> installersOverride,
            KeyProvider keyProvider,
            PackageSigningService packageSigningService,
            PrintStream out,
            PrintStream err,
            boolean exitOnFail
    ) {
        this.directory = directory;
        this.packageJsonMap = packageJsonMap;
        this.packageJsonFile = packageJsonFile;
        this.alwaysClean = alwaysClean;
        this.doNotStripJavaFXFiles = doNotStripJavaFXFiles;
        this.bundlesOverride = bundlesOverride;
        this.installersOverride = installersOverride;
        this.keyProvider = keyProvider;
        this.packageSigningService = packageSigningService;
        this.out = out;
        this.err = err;
        this.exitOnFail = exitOnFail;
    }

    public PackagingContext withInstallers(String... installers) {
        return new PackagingContext(
                directory,
                packageJsonMap,
                packageJsonFile,
                alwaysClean,
                doNotStripJavaFXFiles,
                bundlesOverride,
                new LinkedHashSet<>(Arrays.asList(installers)),
                keyProvider,
                packageSigningService,
                out,
                err,
                exitOnFail
        );
    }

    public PackagingContext withoutStrippingJavaFXFiles() {
        return new PackagingContext(
                directory,
                packageJsonMap,
                packageJsonFile,
                alwaysClean,
                true,
                bundlesOverride,
                installersOverride,
                keyProvider,
                packageSigningService,
                out,
                err,
                exitOnFail
        );
    }

    public Result rj() {
        return Result.fromContent(mj());
    }

    private Result r() {
        return getPackageJsonResult();
    }

    public Map mj() {
        if (!m().containsKey("jdeploy")) {
            m().put("jdeploy", new HashMap());
        }
        return (Map)m().get("jdeploy");
    }

    public Map m() {
        return packageJsonMap;
    }

    public boolean isPackageSigningEnabled() {
        return rj().getAsBoolean("signPackage");
    }

    public Result getPackageJsonResult() {
        if (packageJsonResult == null) {
            packageJsonResult = Result.fromContent(packageJsonMap);
        }
        return packageJsonResult;
    }

    public Set<String> bundles() {
        if (bundlesOverride != null) {
            return new LinkedHashSet<String>(bundlesOverride);
        }
        Map m = mj();
        HashSet<String> out = new HashSet<String>();
        if (m.containsKey("bundles")) {
            List bundlesList = (List) m.get("bundles");
            for (Object o : bundlesList) {
                out.add((String)o);
            }
        }
        return out;
    }

    public String getString(String property, String defaultValue) {
        if (mj().containsKey(property)) {
            return r().getAsString("jdeploy/"+property);
        }
        return defaultValue;
    }

    public int getInt(String property, int defaultValue) {
        if (mj().containsKey(property)) {
            return r().getAsInteger("jdeploy/"+property);
        }
        return defaultValue;
    }

    public List getList(String property, boolean defaultEmptyList) {
        if (mj().containsKey(property)) {
            return r().getAsArray("jdeploy/"+property);
        }
        return defaultEmptyList ? new ArrayList() : null;
    }

    public int getJavaVersion(int defaultValue) {
        return getInt("javaVersion", defaultValue);
    }

    public String getPreCopyScript(String defaultValue) {
        return getString("preCopyScript", defaultValue);
    }
    public String getAntFile(String defaultVal) {
        return getString("antFile", defaultVal);
    }

    public String getPreCopyTarget(String defaultValue) {
        return getString("preCopyTarget", defaultValue);
    }

    public String getPostCopyTarget(String defaultValue) {
        return getString("postCopyTarget", defaultValue);
    }

    public String getBinDir() {
        return "jdeploy-bundle";
    }

    public File getJdeployBundleDir() {
        return new File(directory, "jdeploy-bundle");
    }

    public String getJar(String defaultVal) {
        return getString("jar", defaultVal);
    }

    public String getWar(String defaultVal) {
        return getString("war", defaultVal);
    }

    public String getClassPath(String defaultVal) {
        return getString("classPath", defaultVal);
    }

    public String getMainClass(String defaultVal) {
        return getString("mainClass", defaultVal);
    }

    public int getPort(int defaultValue) {
        return getInt("port", defaultValue);
    }

    public Set<String> installers() {
        if (installersOverride != null) {
            return new LinkedHashSet<>(installersOverride);
        }
        Map m = mj();
        HashSet<String> out = new HashSet<String>();
        if (m.containsKey("installers")) {
            List installersList = (List) m.get("installers");
            for (Object o : installersList) {
                out.add((String)o);
            }
        }
        return out;
    }

    public File getInstallersDir() {
        return new File("jdeploy" + File.separator + "installers");
    }

    public void set(String property, String value) {
        mj().put(property, value);
    }

    public void set(String property, int value) {
        mj().put(property, value);
    }

    public void set(String property, List value) {
        mj().put(property, value);
    }

    public void setWar(String war) {
        set("war", war);
    }

    public void setJar(String jar) {
        set("jar", jar);
    }

    public void setClassPath(String cp) {
        set("classPath", cp);
    }

    public void setMainClass(String mainClass) {
        set("mainClass", mainClass);
    }

    public String getPostCopyScript(String defaultValue) {
        return getString("postCopyScript", defaultValue);
    }

    public static class Builder {
        private File directory;
        private Map packageJsonMap;
        private File packageJsonFile;
        private boolean alwaysClean = !Boolean.getBoolean("jdeploy.doNotClean");
        private boolean doNotStripJavaFXFiles;
        private Set<String> bundlesOverride;
        private Set<String> installersOverride;
        private KeyProvider keyProvider;
        private PackageSigningService packageSigningService;
        private PrintStream out;
        private PrintStream err;
        private boolean exitOnFail = true;

        public Builder directory(File directory) {
            this.directory = directory;
            return this;
        }

        public Builder packageJsonMap(Map packageJsonMap) {
            this.packageJsonMap = packageJsonMap;
            return this;
        }

        public Builder packageJsonFile(File packageJsonFile) {
            this.packageJsonFile = packageJsonFile;
            return this;
        }

        public Builder alwaysClean(boolean alwaysClean) {
            this.alwaysClean = alwaysClean;
            return this;
        }

        public Builder doNotStripJavaFXFiles(boolean doNotStripJavaFXFiles) {
            this.doNotStripJavaFXFiles = doNotStripJavaFXFiles;
            return this;
        }

        public Builder bundlesOverride(Set<String> bundlesOverride) {
            this.bundlesOverride = bundlesOverride;
            return this;
        }

        public Builder installersOverride(Set<String> installersOverride) {
            this.installersOverride = installersOverride;
            return this;
        }

        public Builder keyProvider(KeyProvider keyProvider) {
            this.keyProvider = keyProvider;
            return this;
        }

        public Builder packageSigningService(PackageSigningService packageSigningService) {
            this.packageSigningService = packageSigningService;
            return this;
        }

        public Builder out(PrintStream out) {
            this.out = out;
            return this;
        }

        public Builder err(PrintStream err) {
            this.err = err;
            return this;
        }

        public Builder exitOnFail(boolean exitOnFail) {
            this.exitOnFail = exitOnFail;
            return this;
        }

        private File directory() {
            if (directory != null) {
                return directory;
            } else {
                return new File(".").getAbsoluteFile();
            }
        }

        private Map packageJsonMap()  {
            if (packageJsonMap == null) {
                try {
                    JSONParser p = new JSONParser();
                    packageJsonMap = (Map)p.parseJSON(
                            new StringReader(
                                    FileUtils.readFileToString(packageJsonFile(),
                                            "UTF-8"
                                    )
                            )
                    );
                    if (packageJsonMap.containsKey("jdeploy")) {
                        ((Map)packageJsonMap.get("jdeploy")).putAll(getJdeployConfigOverrides());
                    } else {
                        packageJsonMap.put("jdeploy", getJdeployConfigOverrides());
                    }

                } catch (IOException ex) {
                    Logger.getLogger(JDeploy.class.getName()).log(Level.SEVERE, null, ex);
                    throw new RuntimeException(ex);
                }
            }
            return packageJsonMap;
        }

        private void initPackageJsonMap() {
            packageJsonMap();
        }

        private void initPackageSigningConfig() {
            initPackageJsonMap();
            setupPackageSigningConfig((Map)packageJsonMap.get("jdeploy"));
        }

        private Map<String,?> getJdeployConfigOverrides() {
            Map<String,?> overrides = new HashMap<String,Object>();
            if (System.getenv("JDEPLOY_CONFIG") != null) {
                System.out.println("Found JDEPLOY_CONFIG environment variable");
                System.out.println("Injecting jdeploy config overrides from environment variable");
                System.out.println(System.getenv("JDEPLOY_CONFIG"));
                try {
                    JSONParser p = new JSONParser();
                    Map m = (Map)p.parseJSON(new StringReader(System.getenv("JDEPLOY_CONFIG")));
                    overrides.putAll(m);
                } catch (IOException ex) {
                    Logger.getLogger(JDeploy.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            return overrides;
        }

        private void setupPackageSigningConfig(Map jdeployConfig) {
            keyProvider = new JDeployKeyProviderFactory().createKeyProvider(createKeyProviderFactoryConfig(jdeployConfig));
            packageSigningService = new PackageSigningService(keyProvider);
        }

        private JDeployKeyProviderFactory.KeyConfig createKeyProviderFactoryConfig(final Map jdeployConfig) {

            String developerIdKey = "jdeployDeveloperId";
            String keystorePathKey = "keystorePath";

            final String developerId = jdeployConfig.containsKey(developerIdKey)
                    ? (String)jdeployConfig.get(developerIdKey) : null;
            final String keystorePath = jdeployConfig.containsKey(keystorePathKey)
                    ? (String)jdeployConfig.get(keystorePathKey) : null;
            class LocalConfig extends JDeployKeyProviderFactory.DefaultKeyConfig {
                @Override
                public String getKeystorePath() {
                    return keystorePath == null ? super.getKeystorePath() : keystorePath;
                }

                @Override
                public String getDeveloperId() {
                    return developerId == null ? super.getDeveloperId() : developerId;
                }

                @Override
                public char[] getKeystorePassword() {
                    return super.getKeystorePassword();
                }
            }

            return new LocalConfig();
        }

        private File packageJsonFile() {
            if (packageJsonFile != null) {
                return packageJsonFile;
            }
            return new File(directory(), "package.json");
        }

        private PrintStream out() {
            if (out != null) {
                return out;
            }
            return System.out;
        }

        private PrintStream err() {
            if (err != null) {
                return err;
            }
            return System.err;
        }

        public PackagingContext build() {
            initPackageJsonMap();
            initPackageSigningConfig();
            return new PackagingContext(
                    directory(),
                    packageJsonMap,
                    packageJsonFile(),
                    alwaysClean,
                    doNotStripJavaFXFiles,
                    bundlesOverride,
                    installersOverride,
                    keyProvider,
                    packageSigningService,
                    out(),
                    err(),
                    exitOnFail
            );
        }
    }

}

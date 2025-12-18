package ca.weblite.jdeploy.cli.controllers;

import ca.weblite.jdeploy.services.VerifyPackageService;

public class CLIVerifyPackageController {
    private final VerifyPackageService verifyPackageService;

    public CLIVerifyPackageController(VerifyPackageService verifyPackageService) {
        this.verifyPackageService = verifyPackageService;
    }

    public void verifyPackage(String[] args) {
        VerifyPackageService.Result result;
        try {
            result = verifyPackageService.verifyPackage(parseParameters(args));
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            printUsage();
            System.exit(1);
            return;
        }

        if (result.verified) {
            System.out.println("Package verified successfully");
            System.exit(0);
        } else {
            System.out.println("Package verification failed: " + result.errorMessage);
            if (result.verificationResult != null) {
                System.out.println("Verification result: " + result.verificationResult);
                System.exit(90 + result.verificationResult.ordinal());
            } else {
                printUsage();
                System.exit(1);
            }
        }
    }

    private VerifyPackageService.Parameters parseParameters(String[] args) {
        VerifyPackageService.Parameters result = new VerifyPackageService.Parameters();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "-v":
                case "--version":
                    if (i + 1 < args.length) {
                        result.version = args[++i];
                    } else {
                        throw new IllegalArgumentException("Missing value for version");
                    }
                    break;

                case "-k":
                case "--keystore":
                    if (i + 1 < args.length) {
                        result.keyStore = args[++i];
                    } else {
                        throw new IllegalArgumentException("Missing value for keystore");
                    }
                    break;

                default:
                    if (result.jdeployBundlePath == null) {
                        result.jdeployBundlePath = arg;
                    } else {
                        throw new IllegalArgumentException("Unknown argument: " + arg);
                    }
                    break;
            }
        }

        if (result.version == null || result.jdeployBundlePath == null || result.keyStore == null) {
            throw new IllegalArgumentException("Required parameters: --version, jdeployBundlePath, --keystore");
        }

        return result;
    }

    private void printUsage() {
        System.out.println("Usage: jdeploy verify-package [options] <jdeployBundlePath>");
        System.out.println("Options:");
        System.out.println("  -v, --version <version>       Specify the version of the package to verify.");
        System.out.println("  -k, --keystore <keystore>     Specify the path to the keystore containing trusted certificates.");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  jdeployBundlePath             Path to the jdeploy bundle directory to verify.");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  jdeploy verify-package -v 1.0.0 -k /path/to/keystore /path/to/jdeploy-bundle");
        System.out.println();
        System.out.println("Description:");
        System.out.println("  This command verifies the integrity and authenticity of a jdeploy package.");
        System.out.println("  The command checks the signature of the package against the provided version and keystore.");
        System.out.println("  If the verification is successful, the package is considered authentic and untampered.");
    }

}

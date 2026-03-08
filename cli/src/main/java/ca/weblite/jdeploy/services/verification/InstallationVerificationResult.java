package ca.weblite.jdeploy.services.verification;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Result of an installation or uninstallation verification.
 */
public class InstallationVerificationResult {

    public enum Status {
        PASSED,
        FAILED,
        PARTIAL  // Some checks passed, some skipped, none failed
    }

    public enum VerificationType {
        INSTALLATION,
        UNINSTALLATION
    }

    private final VerificationType verificationType;
    private final Status status;
    private final String appTitle;
    private final String packageName;
    private final String platform;
    private final File installLocation;
    private final List<VerificationCheck> checks;
    private final List<String> warnings;

    private InstallationVerificationResult(Builder builder) {
        this.verificationType = builder.verificationType;
        this.appTitle = builder.appTitle;
        this.packageName = builder.packageName;
        this.platform = builder.platform;
        this.installLocation = builder.installLocation;
        this.checks = Collections.unmodifiableList(new ArrayList<>(builder.checks));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(builder.warnings));
        this.status = calculateStatus();
    }

    private Status calculateStatus() {
        boolean hasFailed = checks.stream().anyMatch(VerificationCheck::isFailed);
        if (hasFailed) {
            return Status.FAILED;
        }

        boolean hasSkipped = checks.stream().anyMatch(VerificationCheck::isSkipped);
        if (hasSkipped) {
            return Status.PARTIAL;
        }

        return Status.PASSED;
    }

    public VerificationType getVerificationType() {
        return verificationType;
    }

    public Status getStatus() {
        return status;
    }

    public String getAppTitle() {
        return appTitle;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getPlatform() {
        return platform;
    }

    public File getInstallLocation() {
        return installLocation;
    }

    public List<VerificationCheck> getChecks() {
        return checks;
    }

    public List<VerificationCheck> getFailedChecks() {
        return checks.stream()
                .filter(VerificationCheck::isFailed)
                .collect(Collectors.toList());
    }

    public List<VerificationCheck> getSkippedChecks() {
        return checks.stream()
                .filter(VerificationCheck::isSkipped)
                .collect(Collectors.toList());
    }

    public List<VerificationCheck> getPassedChecks() {
        return checks.stream()
                .filter(VerificationCheck::isPassed)
                .collect(Collectors.toList());
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public boolean passed() {
        return status == Status.PASSED || status == Status.PARTIAL;
    }

    public int getExitCode() {
        return status == Status.FAILED ? 1 : 0;
    }

    /**
     * Prints the verification result to the output stream.
     * Shows summary + errors only (details only for failures).
     */
    public void print(PrintStream out) {
        String typeLabel = verificationType == VerificationType.INSTALLATION
                ? "Installation" : "Uninstallation";

        if (status == Status.FAILED) {
            out.println("X " + typeLabel + " verification failed for \"" + appTitle + "\" (" + packageName + ")");
        } else {
            out.println("OK " + typeLabel + " verified for \"" + appTitle + "\" (" + packageName + ")");
        }

        out.println("  Platform: " + platform);

        if (installLocation != null) {
            out.println("  Location: " + installLocation.getAbsolutePath());
        }

        // Show passed checks count
        List<VerificationCheck> passed = getPassedChecks();
        if (!passed.isEmpty()) {
            out.println("  Checks passed: " + passed.size());
        }

        // Show skipped checks (warnings)
        List<VerificationCheck> skipped = getSkippedChecks();
        if (!skipped.isEmpty()) {
            out.println();
            out.println("  Skipped (legacy mode):");
            for (VerificationCheck check : skipped) {
                out.println("    - " + check.getName() + ": " + check.getSkipReason());
            }
        }

        // Show additional warnings
        if (!warnings.isEmpty()) {
            out.println();
            out.println("  Warnings:");
            for (String warning : warnings) {
                out.println("    - " + warning);
            }
        }

        // Show errors (failed checks)
        List<VerificationCheck> failed = getFailedChecks();
        if (!failed.isEmpty()) {
            out.println();
            out.println("  Errors:");
            for (VerificationCheck check : failed) {
                out.println("    - " + check.getName() + ": " + check.getDetails());
            }
        }
    }

    /**
     * Prints verbose output showing all checks.
     */
    public void printVerbose(PrintStream out) {
        print(out);

        out.println();
        out.println("  All checks:");
        for (VerificationCheck check : checks) {
            String statusSymbol;
            switch (check.getStatus()) {
                case PASSED:
                    statusSymbol = "[OK]";
                    break;
                case FAILED:
                    statusSymbol = "[FAIL]";
                    break;
                case SKIPPED:
                    statusSymbol = "[SKIP]";
                    break;
                default:
                    statusSymbol = "[?]";
            }
            out.println("    " + statusSymbol + " " + check.getName());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private VerificationType verificationType = VerificationType.INSTALLATION;
        private String appTitle;
        private String packageName;
        private String platform;
        private File installLocation;
        private List<VerificationCheck> checks = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();

        public Builder verificationType(VerificationType type) {
            this.verificationType = type;
            return this;
        }

        public Builder appTitle(String appTitle) {
            this.appTitle = appTitle;
            return this;
        }

        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder platform(String platform) {
            this.platform = platform;
            return this;
        }

        public Builder installLocation(File installLocation) {
            this.installLocation = installLocation;
            return this;
        }

        public Builder addCheck(VerificationCheck check) {
            this.checks.add(check);
            return this;
        }

        public Builder addChecks(List<VerificationCheck> checks) {
            this.checks.addAll(checks);
            return this;
        }

        public Builder addWarning(String warning) {
            this.warnings.add(warning);
            return this;
        }

        public InstallationVerificationResult build() {
            return new InstallationVerificationResult(this);
        }
    }
}

package ca.weblite.jdeploy.cli.controllers;

import ca.weblite.jdeploy.services.GeneratePublisherIdentityCertService;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.io.PrintStream;

/**
 * CLI front-end for {@code jdeploy generate-publisher-cert}.
 * <p>
 * See {@code rfc/website-publisher-verification-plan.md}.
 */
public final class GeneratePublisherIdentityCertCliController {

    public static int run(String[] args, PrintStream out, PrintStream err) {
        Options opts = options();
        CommandLine line;
        try {
            line = new DefaultParser().parse(opts, args);
        } catch (ParseException e) {
            err.println("Error: " + e.getMessage());
            usage(opts, err);
            return 2;
        }
        if (line.hasOption("help") || line.hasOption("h")) {
            usage(opts, out);
            return 0;
        }

        GeneratePublisherIdentityCertService.Request req =
                new GeneratePublisherIdentityCertService.Request();
        try {
            req.issuerKeyFile(requireFile(line, "issuer-key"))
                    .issuerCertFile(requireFile(line, "issuer-cert"))
                    .outputFile(requireFile(line, "out"));
            if (line.hasOption("chain")) req.chainFile(new File(line.getOptionValue("chain")));
            if (line.hasOption("validity-days")) {
                req.validityDays(Integer.parseInt(line.getOptionValue("validity-days")));
            }
            if (line.hasOption("subject-cn")) req.subjectCommonName(line.getOptionValue("subject-cn"));
            if (line.hasOption("organization")) req.subjectOrganization(line.getOptionValue("organization"));
            if (line.hasOption("out-key")) req.outputKeyFile(new File(line.getOptionValue("out-key")));
            if (line.hasOption("no-key")) req.writePrivateKey(false);
            for (String d : valuesOrEmpty(line, "domain")) req.addDomain(d);
            for (String g : valuesOrEmpty(line, "github")) req.addGithub(g);
        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            usage(opts, err);
            return 2;
        }

        GeneratePublisherIdentityCertService.Result result;
        try {
            result = new GeneratePublisherIdentityCertService().generate(req);
        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            usage(opts, err);
            return 2;
        } catch (Exception e) {
            err.println("Failed to generate publisher identity certificate: " + e.getMessage());
            e.printStackTrace(err);
            return 1;
        }

        out.println("Wrote publisher identity certificate: " + result.certificateFile.getAbsolutePath());
        if (result.privateKeyFile != null) {
            out.println("Wrote identity private key (optional, not used at install time): "
                    + result.privateKeyFile.getAbsolutePath());
        }
        out.println();
        out.println("Upload the certificate to:");
        for (String url : result.uploadUrls) out.println("  " + url);
        out.println();
        out.println("Add to package.json:");
        out.println("  \"jdeploy\": {");
        out.println("    \"publisherVerificationUrls\": [");
        for (int i = 0; i < result.verificationUrls.size(); i++) {
            String comma = (i == result.verificationUrls.size() - 1) ? "" : ",";
            out.println("      \"" + result.verificationUrls.get(i) + "\"" + comma);
        }
        out.println("    ]");
        out.println("  }");
        return 0;
    }

    private static File requireFile(CommandLine line, String opt) {
        if (!line.hasOption(opt)) throw new IllegalArgumentException("--" + opt + " is required");
        return new File(line.getOptionValue(opt));
    }

    private static String[] valuesOrEmpty(CommandLine line, String opt) {
        String[] v = line.getOptionValues(opt);
        return v == null ? new String[0] : v;
    }

    private static Options options() {
        Options o = new Options();
        o.addOption(Option.builder().longOpt("issuer-key").hasArg().argName("file")
                .desc("PEM-encoded private key that will sign the identity cert (root or codesign)").build());
        o.addOption(Option.builder().longOpt("issuer-cert").hasArg().argName("file")
                .desc("PEM-encoded issuer certificate that matches --issuer-key").build());
        o.addOption(Option.builder().longOpt("chain").hasArg().argName("file")
                .desc("Optional additional intermediate cert(s) in PEM, appended to the output file").build());
        o.addOption(Option.builder().longOpt("domain").hasArg().argName("host").numberOfArgs(Option.UNLIMITED_VALUES)
                .desc("Domain to bind in the cert SAN (repeatable). Becomes https://<host>/.well-known/jdeploy-publisher.cer").build());
        o.addOption(Option.builder().longOpt("github").hasArg().argName("owner/repo").numberOfArgs(Option.UNLIMITED_VALUES)
                .desc("GitHub repo to bind (repeatable). Canonical SAN: https://github.com/<owner>/<repo>").build());
        o.addOption(Option.builder().longOpt("validity-days").hasArg().argName("n")
                .desc("Cert validity in days (default 365)").build());
        o.addOption(Option.builder().longOpt("subject-cn").hasArg().argName("name")
                .desc("Subject common name (default: first SAN host)").build());
        o.addOption(Option.builder().longOpt("organization").hasArg().argName("name")
                .desc("Subject organization (O=...)").build());
        o.addOption(Option.builder().longOpt("out").hasArg().argName("file")
                .desc("Output PEM file for the identity cert").build());
        o.addOption(Option.builder().longOpt("out-key").hasArg().argName("file")
                .desc("Output PEM file for the identity private key (default: <out>.key)").build());
        o.addOption(Option.builder().longOpt("no-key")
                .desc("Do not write the identity private key to disk").build());
        o.addOption("h", "help", false, "Show help");
        return o;
    }

    private static void usage(Options opts, PrintStream stream) {
        HelpFormatter f = new HelpFormatter();
        f.printHelp(new java.io.PrintWriter(stream, true), 100,
                "jdeploy generate-publisher-cert", "", opts, 2, 4, "", true);
    }

    private GeneratePublisherIdentityCertCliController() {}
}

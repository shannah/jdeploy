package ca.weblite.jdeploy.cli.controllers;

import ca.weblite.jdeploy.BundleConstants;
import ca.weblite.jdeploy.cli.dtos.UpdateReleaseLinksRequest;
import ca.weblite.jdeploy.cli.parsers.UpdateReleaseLinksRequestParser;
import ca.weblite.jdeploy.environment.Environment;
import ca.weblite.jdeploy.helpers.GithubReleaseNotesMutator;
import ca.weblite.jdeploy.helpers.GithubReleaseNotesPatcher;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

public class UpdateReleaseLinksCLIController implements BundleConstants {
    private final GithubReleaseNotesMutator mutator;

    private final GithubReleaseNotesPatcher patcher;

    private final UpdateReleaseLinksRequestParser parser;

    private final PrintStream err;
    private final PrintStream out;

    public UpdateReleaseLinksCLIController(File directory) {
        this(directory, System.err, System.out);
    }

    public UpdateReleaseLinksCLIController(File directory, PrintStream err, PrintStream out) {
        this(directory, err, out, new Environment());
    }

    public UpdateReleaseLinksCLIController(File directory, PrintStream err, PrintStream out, Environment env) {
        this(
                err,
                out,
                new GithubReleaseNotesMutator(directory, err, env),
                new GithubReleaseNotesPatcher(env),
                new UpdateReleaseLinksRequestParser()
        );
    }
    public UpdateReleaseLinksCLIController(
            PrintStream err,
            PrintStream out,
            GithubReleaseNotesMutator mutator,
            GithubReleaseNotesPatcher patcher,
            UpdateReleaseLinksRequestParser parser
    ) {
        this.mutator = mutator;
        this.patcher = patcher;
        this.parser = parser;
        this.err = err;
        this.out = out;
    }

    public int run(String[] args) {
        if (args.length < 2) {
            err.println("Usage: jdeploy github-update-release-links <type> <link> [<type> <link> ...]");
            return 1;
        }
        try {
            updateReleaseLinks(args);
            return 0;
        } catch (Exception e) {
            e.printStackTrace(err);
            return 1;
        }
    }

    private void updateReleaseLinks(String[] args) throws IOException {
        String releaseNotes = patcher.get();
        UpdateReleaseLinksRequest request = parser.parse(args);
        boolean modified = false;
        for (String type : request.getLinkTypes()) {
            releaseNotes = mutator.updateLinkInGithubReleaseNotes(releaseNotes, type, request.getLink(type));
            modified = true;
        }

        if (!modified) {
            return;
        }

        patcher.patch(releaseNotes);
    }
}
